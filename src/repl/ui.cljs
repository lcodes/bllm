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

(util/defalias $ rf/subscribe)

(util/defconst extraction-sub-key ::db)
(util/defconst extraction-get-key ::get)

(rf/reg-sub
 extraction-sub-key
 ;; `parent-sub` is `re-frame.db/app-db` implicitly.
 (fn $db [db [_ k]]
   (get db k)))

(defn- $db
  "Specialized subscription to extract top-level data from the app-db.

  Doesn't look inside the data, doesn't support nested lookups. By design.

  NOTE prefer this over `sub` for 'Layer 2' subscriptions. The root of the
  app-db has to be fairly shallow, for all its keys are potential flows on
  update. This pattern ensures high-efficiency of the entire signal graph.

  NOTE automatically used by `defschema` to extract its defined UI state."
  [k]
  ($ (vector extraction-sub-key k)))

(rf/reg-sub
 extraction-get-key
 (fn $get-q [[_ _ parent-sub]]
   parent-sub)
 (fn $get [m [_ k]]
   (get m k)))

(defn- $get
  "Wild idea. Hope it works."
  [from k]
  ($ (vector extraction-get-key k from)))

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
  (rf/dispatch [on-register node])
  (:name node))

(defn- merge-schema-spec [db init]
  (if (map? init)
    (merge init db)
    (or db init))) ; TODO unless type changed? (ie refactor [] to #{})

(def ^:private merge-schema (partial merge-with merge-schema-spec))

(repl.ui/defevent ^:private on-schema
  [db {:as m :keys [name init]}]
  (-> db
      (update name merge-schema init)
      (do-register m)))

(defn schema
  [key specs]
  (let [node {:kind :schema :name key :init specs}]
    (rf/dispatch [on-schema node])
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
   ;; TODO retry button (refresh at REPL without figwheel reload)
   [:pre (cljs.pprint/pprint node)]
   [:pre (cljs.pprint/pprint data)]])

(defn node
  "Component host to a managed `view` and its state data."
  ([node-k] ; Singleton view
   (node node-k node-k))
  ([node-k view-k] ; Instanced view
   (let [n @($get nodes-sub node-k)
         v @($get views-sub view-k)]
     ;; TODO every `n` can be wrapped by `error-boundary` or `strict-mode`, state is `v`
     (node* n v)))) ; TODO further transforms? ie automated ID/classnames or data-*

(defn pretty [x]
  ;; TODO throw this to a code highlighter (embedded text editor or custom built)
  [:pre.tty (with-out-str (cljs.pprint/pprint x))])



;;; System Views - Managed components with durable state and a delegated render.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn frame
  [k elem class initial-views]
  (register {:kind :frame :name k :init initial-views :elem elem :class class}))

(defmethod node* :frame [{:keys [init elem class]} v]
  ;; TODO layout (vertical, horizontal) (reverse) (align, justify)
  ;; TODO id (keyword or number) and class names (semantic styles)
  `[~(or elem :div) {:class ~(html/class "frame" class)} ~@init])

(defn view
  [k hiccup]
  ;; view hash, label, view function, context flags, preferred container
  ;; flags include singleton, system, pane etc
  (register {:kind :view :name k :init hiccup}))

(defmethod node* :view [{:keys [init]} v]
  ;; view options (ID/class, managed hooks)
  (init v))


;;; Modal Panes - Managed views with associated selection data and editor modes.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn mode
  "Modes are sets of features that customize the behavior of view panes."
  [k init]
  k)

(defn pane
  [k view]
  ;; kinda analogue to model shaders -> where views are effect shaders, and components are system shaders.
  ;; GOOD -> UI shaders is the next logical step
  (register {:kind :pane :name k :init view}))

(defmethod node* :pane [n v]
  [:div "PANE"])


;;; UI System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init [app-container]
  ;; hook input events

  ;; pull initial state from sessionStorage, localStorage and IndexedDB?
  )
