(ns repl.ui
  "User Interface controls for views with models. Simplified use of `re-frame`."
  (:require-macros [repl.ui])
  (:require [reagent.core  :as rc]
            [re-frame.core :as rf]
            [bllm.html :as html]
            [bllm.util :as util :refer [def1]]))

(set! *warn-on-infer* true)

;; TODO local/session storage as state store
;; - indexeddb is async, this triggers a lot more and is sync optimized for small data
;; - only tradeoff is having to do the JSON serialization
;; - BUT, can debounce all writes to batches (well then, whats different from async IDB?)
;;   - more to amortize JSON serialization cost when user preferences are iterated on
;;   - all late game implementations, but useful to hook ahead of time in the design


;;; Application Schema - Make re-frame even more declarative than it already is.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; https://day8.github.io/re-frame/subscriptions/

;; Don't forget:
;; - reading and writing to the app-db are *completely* decoupled, by design.
;; - reason for single app-db instead of spread data fragments is frame unit.
;;   - no matter the scope and depth of an event handler's reach, one update.
;; - doesn't mean a few rules can't be bent first, like a partial subs graph.
;;   - common patterns in data extraction also emit schemas and initializers.
;;   - as convenience, of course, all UI macros just expand the scaffoldings.

(util/defalias ! rf/dispatch)
(util/defalias $ rf/subscribe)

(defn !>
  ([k a]
   (! (vector k a)))
  ([k a b]
   (! (vector k a b)))
  ([k a b c]
   (! (vector k a b c)))
  ([k a b c d]
   (! (vector k a b c d))))

(defn !event
  ([k]
   (let [ev [k]]
     (fn dispatch [e]
       (! ev)
       (util/prevent-default e))))
  ([k f]
   (fn dispatch [e]
     (! [k (f e)])
     (util/prevent-default e))))

(util/defalias cofx rf/inject-cofx)

(util/defconst db-key  ::db)
(util/defconst get-key ::get)
(util/defconst sub-key ::sub)

(rf/reg-cofx
 sub-key
 (fn $cofx [cofx [k sub]]
   (assoc cofx k @sub)))

(defn $cofx [k sub]
  (-> (cofx sub-key [k sub])
      (assoc :id k)))

(rf/reg-sub
 db-key
 ;; `parent-sub` is `re-frame.db/app-db` implicitly.
 (fn $db [db [_ k]]
   (get db k)))

(defn $db
  "Specialized subscription to extract top-level data from the app-db.

  Doesn't look inside the data, doesn't support nested lookups. By design.

  NOTE prefer this over `sub` for 'Layer 2' subscriptions. The root of the
  app-db has to be fairly shallow, for all its keys are potential flows on
  update. This pattern ensures high-efficiency of the entire signal graph.

  NOTE automatically used by `defschema` to extract its defined UI state."
  [k]
  ($ (vector db-key k)))

(rf/reg-sub
 get-key
 (fn $get-q [[_ _ parent-sub]]
   parent-sub)
 (fn $get [m [_ k]]
   (get m k)))

(defn $get
  "Subscription extracting the requested key from the given stream of maps.

  Doesn't look inside the data, doesn't support nested lookups. By design.

  Primarily used to extract individual elements from Layer 2 or 3 subscriptions,
  to be transformed in materialized views or consumed in user interface views."
  [from k]
  ($ (vector get-key k from)))

(declare state)
(declare nodes)

(defn- do-register [m node]
  (assoc-in m [state nodes (:name node)] node))

(repl.ui/defevent ^:private on-register
  [db node]
  (do-register db node))

(defn register
  "Registers a managed UI `node`."
  [node]
  (rf/dispatch-sync [on-register node]) ; TODO if this gets too trigger-happy, batch between ticks
  (:name node))

(defn- merge-schema-spec [db init]
  (if (map? init)
    (merge init db)
    (or db init))) ; TODO unless type changed? (ie refactor [] to #{})

(def ^:private merge-schema (partial merge-with merge-schema-spec))

(repl.ui/defevent ^:private on-schema
  [db {:as m :keys [name specs]}]
  (-> db
      (update name merge-schema specs)
      (do-register m)))

(defn schema
  [key specs]
  (let [node {:kind :schema :name key :specs specs}]
    (rf/dispatch-sync [on-schema node])
    key))

(repl.ui/defschema state
  "UI state describing the state of the UI. What condition was my condition in?"
  {:store :user} ; TODO use this to make durable and specify data store (:user delegates to session/local storage, or cloud later)
  theme :default ; TODO hardcoded -> swap css vars -> swap `style` entries -> zen garden
  style {} ; "CSS 'components' (later -> raw CSS used now)"
  menus {} ; "Command components (view ID -> view trigger)"
  panes {} ; "Instanced components (view ID -> view state)"
  views {} ; "Singleton components (viewkey -> view state)"
  nodes {} ;
  prefs {} ; "Configurable user options (opt key -> value)"
  ;; TODO docstring syntax pattern ^ (same style as defprotocol?)
  )

(comment @($get nodes-sub state))

#_(repl.ui/defcofx local
  []
  )

#_(repl.ui/defx local!
  [_event k v]
  )

#_(repl.ui/defeffect set-pref
  {:args false}
  [{:keys [db]} [_event k v :as event]]
  {:db db ; TODO update
   local! event}) ; TODO not always to local storage


(comment (js/console.log (deref re-frame.db/app-db)))


;;; Composable Style Sets -
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; css vars and themes are boring -> different language, even with figwheel reload
;; outside the "experience", cant scope styles within namespaces next to views
;; javascript->css is generally opposite to the spirit of zen garden, fix that
;; -  -> css vars
;; - `ui/defclass` not OOP class, but class="scroll flex grow gap-2" ??
;; - `ui/defstyle` separate semantics from syntactic classes, assign values, less/sass-style composability but turing complete
;; - `ui/deftheme` final composition layer -> emits css rules
;; then got all the meta to generate a view pane to live edit the app's styling

;; future -> dont need pretty now, wont live without later


;;; System Components - Self-contained implementation, no managed customization.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def error-boundary
  "Works in similar fashion to `try`/`catch`, but across the UI component tree."
  ;; TODO extend to work like Common Lisp's condition system async across the UI
  (rc/create-class
   {:display-name "repl.ui/error-boundary"
    :component-did-catch (fn component-did-catch [this e info]
                           ;; TODO register to retry on figwheel after-load
                           (rf/clear-subscription-cache!)
                           (rc/set-state this {:error e}))
    :render (fn [this]
              (rc/as-element
               (if-let [^js/Error e (:error (rc/state this))]
                 [:section.error-boundary.scroll
                  [:h3 "Error"]
                  [:p [:button.btn.btn-sm.btn-secondary
                       {:type "button" :on-click #(rc/set-state this {:error nil})}
                       "Resume"]] ; TODO caller-defined resume points (ie resume, reset state, change selection)
                  [:pre (.-stack e)]]
                 (let [c (first (rc/children this))]
                   (if (fn? c) (c) c)))))})) ; Consistently Compound Components.

(defn strict-mode
  "Enables React's `StrictMode` over child nodes. Development only."
  []
  )

(defmulti node*
  "Every method implements a unique kind of managed view, see also `node`."
  (fn node-dispatch [n v]
    (:kind n)))

(defmethod node* :default [{:as node :keys [kind]} data]
  [:div.error
   [:h3 (cond (nil? node) "Missing node"
              (nil? kind) "Invalid node"
              :else       "Missing kind")]
   [:pre (cljs.pprint/pprint node)]
   [:pre (cljs.pprint/pprint data)]])

(defn node
  "Component host to a managed `view` and its state data."
  ([node-k] ; Singleton view
   (node node-k node-k))
  ([node-k view-k] ; Instanced view
   (let [n @($get nodes-sub node-k)
         v @($get views-sub view-k)]
     (node* n v))))

(def space "Reusable spacer view component." [:div.space.grow])

(def split "Reusable resize handle component." [:div.split])

(defn pretty [x]
  ;; TODO throw this to a code highlighter (embedded text editor or custom built)
  [:pre.tty (with-out-str (cljs.pprint/pprint x))])


;;; System Views - Managed components with durable state and a delegated render.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn frame
  [k tags elem class layout transient? initial-views]
  (register {:kind :frame
             :name k
             :tags tags
             :elem (or elem :div)
             :class class
             :layout (or layout :col)
             :transient? transient?
             :views initial-views}))

(defmethod node* :frame [{:keys [elem class layout views]} state]
  `[~elem {:class ~(html/class "frame" (name layout) class)}
    ~@(or state views)]) ; TODO use `views` when creating the frame state, should never be nil here

(defn view
  [k hiccup]
  ;; view hash, label, view function, context flags, preferred container
  ;; flags include singleton, system, pane etc
  (register {:kind :view :name k :view hiccup}))

(defmethod node* :view [{:keys [view]} v]
  ;; view options (ID/class, managed hooks)
  (view v))


;;; Modal Panes - Managed views with associated selection data and editor modes.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn mode
  "Modes are sets of features that customize the behavior of view panes."
  [k init]
  k)

(defn pane
  [k base view]
  ;; kinda analogue to model shaders -> where views are effect shaders, and components are system shaders.
  ;; GOOD -> UI shaders is the next logical step
  (register {:kind :pane :name k :base base :view view}))

(defmethod node* :pane [n v]
  ;; TODO what really makes pane different from view here?
  ;; - decorations & modeline handled by `dock/panel`
  ;; - anything else is view specific, and composes from there (ie text editor isnt texture viewer isnt material editor isnt shader graph)
  ;;
  ;; only difference seems to be in declaration access/visibility
  ;; - views are system components (controls), panes are asset components (editors)
  ;; - but panes are views, whose state happen to include a selection and edit modes


  ;; here is the wrapper view of panes, to wrap every pane with common logic/validations
  ;; - empty selection, modeless (elden ring memes here)
  ;; - PANE INTERCEPTORS -> start building the modal stack from here
  ;; - its clojure data all the way down, only becoming React when not looking
  ;;   - can transform the UI freely, turing completely, like server-side req/res middleware
  ;; - ie "highlight current line" in text editor builds the view while "hijacking" the [text/line] component
  ;;   - add a sub for the selected line number, got rendered line as prop, select style accordingly
  ;;   - trivial example, most likely "selected" will be baked in, but if thats possible, anything is
  ;; - data is easily debugged live, no need to pause the world


  ;; actually need this indirection -> pane can change to display another selection
  ;; - which also swaps the current modes, and therefore the associated view
  ;; - without touching the pane itself
  ;;
  ;; in this regard, panels are containers of these, they handle splitting and merging and layout
  ;; - the pane-asset association is done here -> but this is logic for `dock`
  ;; - dont need to do much tho, and selection state is shared (not defined by dock)
  ;; - meaning the number of selections, panes, panels and frames vary independently, and all are views
  [:div "PANE"])

;; what are selections?
;; - not just data store assets
;; - could be any JS object really -> so long as it has a matching mode
;;   - turns everything into different layers of potential selection
;;   - ie an UI editor might want to select which UI control it is editing
;;     - drag a control from another pane onto it -> transfer ID -> edit data model -> live changes
;;     - sprite editor -> drag button -> edit icon (or edit label if dragged onto text editor)
;;     - same for 3d scene objects, asset files, shader nodes; this is a general indirection
;;   - selection itself needs very little data (list of JS objects and their types)
;;   - selection handlers (pane views, modes, or just cut/copy/paste commands & co) dispatch on type
;;   - either default behavior or incompatible selection (ie texture dragged in text box is invalid, in address box yields asset ID)
;;
;; "nested" selections
;; select "hello.txt" -> show editor pane -> select single paragraph -> display selection in another pane
;; - unselecting with movements in the first editor will keep the selection until the other pane is closed
;;   - could be useful to go through macroexpansions, file navigation, literate subviews, live previews etc


;;; Context Menus -
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; popup menus (same logic as main menu, but on button press or context events)

(defn menu
  [k init]
  k) ; TODO

(defmethod node* :menu [n v]
  [:ul.menu "MENU"])


;;; UI System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init [app-container]
  ;; pull initial state from sessionStorage, localStorage and IndexedDB?
  )


;;; Misc. Components & Labels
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; https://www.compart.com/en/unicode/block/U+1F300
(def quebec "Made in" "⚜")

(def more-label "…")
(def minimize-label "🗕")
(def maximize-label "🗖")
(def close-label "🗙")
(def add-label "＋")
(def del-label "－")
(def inf-label "∞")

(def bullet-dot "•")
(def middle-dot "·")

(def arrow-circle-cw "↻")
(def arrow-circle-ccw "↺")

(def check-label "✔")
(def cross-label "✘")

(def time-label "⏳")
(def clock-label "⏰")

(def edit-label "✍")
(def cut-label "✂")

(def star-0 "☆")
(def star-1 "★")
(def heart-0 "♡")
(def heart-1 "❤")

(def warn-label "⚠")
(def debug-label "☣")
(def error-label "☢")
(def fatal-label "☠")

(def binary-0 "⚬")
(def binary-1 "⚭")
(def binary-2 "⚮")
(def binary-3 "⚯")

(def dice #js ["⚀" "⚁" "⚂" "⚃" "⚄" "⚅"])

(def hex "" #js ["⚊" "⚋" "⚌" "⚍" "⚎" "⚏"])
(def hexa "☯" #js ["☰" "☱" "☲" "☳" "☴" "☵" "☶" "☷"])

(def roman
  #js ["Ⅰ" "Ⅱ" "Ⅲ" "Ⅳ" "Ⅴ" "Ⅵ" "Ⅶ" "Ⅷ" "Ⅸ" "Ⅹ" "Ⅹ" "Ⅻ"])

(defn btn
  ([label click]
   (btn label nil click))
  ([label class click]
   [:button {:class class :on-click click} label]))

(defn close-btn [click]
  (btn close-label "close" click))
