(ns repl.ui
  "User Interface controls for views with models. Opinionated use of `re-frame`.

  `repl` -> Emacs! :: Inspired a lot from emacs, then expanding a bit, or a lot.
  --------------
  `dock` -> TILING :: Complete UI is made from `frame` layouts or `panel` views.
  `pane` -> WINDOW :: A view used to quickly switch between multiple user views.
  `view` -> BUFFER :: UI view function over the corresponding information model.
  `mode` ->  MODE  :: Interactive functionality instantiated in a model context.
  `data` -> OBJECT :: Any object whose type has an associated metadata protocol.
  `time` -> STREAM :: First-class timelines as the foundation of task execution."
  (:require-macros [repl.ui])
  (:require [reagent.core  :as rc]
            [re-frame.core :as rf]
            [bllm.cli  :as cli]
            [bllm.html :as html]
            [bllm.util :as util :refer [def1]]))

(set! *warn-on-infer* true)

(comment (js/console.log (deref re-frame.db/app-db)))

(cli/defgroup config)


;;; Application Schema - Make re-frame even more declarative than it already is.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; https://day8.github.io/re-frame/subscriptions/

;; full app has a model, making global modes possible, or global defaults
;; - more emacs features emerging back from the design, neat!

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
  "Creates an event vector and dispatches it."
  ([k a]
   (! (vector k a)))
  ([k a b]
   (! (vector k a b)))
  ([k a b c]
   (! (vector k a b c)))
  ([k a b c d]
   (! (vector k a b c d))))

(defn !?
  "Conditionally create an event vector and dispatch it with (f x) as argument
  only when it returns a value."
  [k f x]
  (some->> (f x) (vector k) !))

(defn event
  "Returns an event handler invoking the preregistered handler `k` when called.

  The first overload always dispatches without event data. On the other hand,
  the second overload invokes `f` on the event, and only dispatches when it
  returns a value. This value is then passed on to the event handler."
  ([k]
   (let [ev [k]]
     (fn dispatch [e]
       (! ev)
       (util/prevent-default e))))
  ([k f]
   (fn dispatch [e]
     (!? k f e)
     (util/prevent-default e))))

(defn !event
  "Adds an event listener invoking the pregistered handler `k` on changes."
  ([^js/Node elem event-name k]
   (.addEventListener elem event-name (event k)))
  ([^js/Node elem event-name k f]
   (.addEventListener elem event-name (event k f))))

(defn !watch
  "Adds an identity watch invoking the preregistered handler `k` on changes."
  ([id k]
   (!watch id k identity))
  ([id k f]
   (!? k f @id)
   (add-watch id k (fn watch [_ _ _ v]
                     ;; TODO defer to frame boundaries -> fires too often already
                     (!? k f v)))))

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
(declare views)

(defn- upsert-static-view
  "Inserts the initial state of a view with the `:static` tag, or updates it when
  it already exists and it's value hasn't been manipulated by user events."
  [m k new-v old-v]
  (let [v (m k)]
    (if (not= old-v v)
      m
      (cond new-v (assoc m k new-v)
            old-v (dissoc m k)
            :else (do (assert (nil? v)) m)))))

(comment (-> {}
             (upsert :x 1 nil)
             (upsert :x 2 1)
             (upsert :x 3 1)))

(defn- do-register
  [m {:as node :keys [name tags]}]
  (cond-> (assoc-in m [nodes name] node)
    (contains? tags :static) (update views upsert-static-view name
                                     (:init node) (-> m nodes name :init))))

(repl.ui/defevent ^:private on-register
  "Adds a UI node definition to the UI state."
  [db node]
  (update db state do-register node))

(defn register-node
  "Registers a managed UI view, to be displayed using `node` as a host."
  [node] ; TODO if this gets too trigger-happy, batch between ticks
  (rf/dispatch-sync [on-register node])
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
      (update state do-register m)))

(defn schema
  [key specs]
  (let [node {:kind ::schema :name key :specs specs}]
    (rf/dispatch-sync [on-schema node])
    key))

(repl.ui/defschema state
  "UI state describing the state of the UI. What condition was my condition in?"
  {:store :user} ; TODO use this to make durable and specify data store (:user delegates to session/local storage, or cloud later)
  theme :default ; TODO hardcoded -> swap css vars -> swap `style` entries -> zen garden
  style {} ; "CSS 'components' (later -> raw CSS used now)"
  focus {} ; TODO where is the UI/dock state separation?
  popup {} ; Menus, dialogs, modals, slides, asides, notifications and other self-positioned content.
  panes {} ; View models for the opened selections; panel content & tabs index.
  views {} ; Durable view models (TODO actually implement durability)
  links {} ; Node/View relations
  nodes {} ; UI node definitions (TODO turning this into an AST registry -> reuse `node` for engine data views (audio/render/shader graph, CLI groups, ...))
  ;; TODO docstring syntax pattern ^ (same style as defprotocol?)
  )

(comment @($get nodes-sub state))

#_(repl.ui/defeffect set-pref
  {:args false}
  [{:keys [db]} [_event k v :as event]]
  {:db db ; TODO update
   local! event}) ; TODO not always to local storage


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
                 [:section.error-boundary.content.scroll
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

(defn- key-view [k]
  [:p.lead "View Key: " (str k)])

(defn pretty [x]
  ;; TODO throw this to a code highlighter (embedded text editor or custom built)
  [:pre.tty (with-out-str (cljs.pprint/pprint x))])

(def space "Reusable spacer view component." [:div.space.grow])

(def split "Reusable resize handle component." [:div.split]) ; TODO use


;;; Simple Views - Managed components with durable state and a delegated render.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti node*
  "Every method implements a unique kind of managed view, see also `node`."
  (fn node-dispatch [n k v]
    (:kind n)))

(defmethod node* :default [{:as node :keys [kind]} {:as data :keys [key]}]
  [:div.error.content
   [:h3 (cond (nil? node) "Missing node"
              (nil? kind) "Invalid node"
              :else       "Missing kind")]
   (key-view key)
   (pretty node)
   (pretty data)])

(def1 interceptors
;; - main :: globally executed on every node (state mgmt, dynamic vars, error-boundary, strict-mode)
;; - kind :: executed on every node of its kind (track current *frame*)
;; - node :: specifed on the definition (dock frames get splitter elements interposed)
;; - view :: featured in the view state (optional shared components, final composition)
;;
;; node interceptors embedded in definitions, view in app-db.state.views
  (js/Map.))

(comment (js/console.log interceptors))

(defn interceptor [k before after]
  {:name k :before before :after after})

(defn intercept
  ([at k before after]
   (intercept at (interceptor k before after)))
  ([at x]
   (if-let [xs (.get interceptors at)]
     (let [k (:name x)]
       (if-let [n (.findIndex xs #(= (:name %) k))]
         (aset xs n x)
         (.push xs x)))
     (.set interceptors at #js [x]))))

(intercept :main ::error-boundary nil
           (fn wrap-error-boundary [ctx]
             #_TODO))

(defn node
  "Component host to a managed `view` and its index data."
  [view-k]
  (let [node-k (if (keyword? view-k)
                 view-k
                 @($get links-sub view-k))
        n @($get nodes-sub node-k)
        v @($get views-sub view-k)]
    (assert (= node-k (:name n)))
    ;; TODO run interceptors
    [error-boundary (node* n view-k v)]))

(defn view-index [views view-key]
  (let [cnt (count views)]
    (loop [i 0]
      (let [[v k] (nth views i)]
        (if (and #_(= v node) ; TODO doesn't account for `node` being updated at the REPL
                 (= k view-key))
          i
          (let [i (inc i)]
            (when (>= i cnt)
              (throw (ex-info "Missing view in frame" {:frame views :view view-key})))
            (recur i)))))))

(defn view
  [k tags init v]
  (register-node
   {:kind ::view :name k :tags tags :init init :view v}))

(defmethod node* ::view [{:keys [view]} view-key data]
  (let [html (view data view-key)
        attr (second html)]
    (if-not (map? attr)
      (apply vector (first html) {:data-view (util/key-of view-key)} (rest html))
      (if (contains? attr :data-view)
        html
        (update html 1 assoc :data-view (util/key-of view-key))))))

(defn frame
  [k tags elem class layout initial-views]
  (register-node
   {:kind ::frame
    :name k
    :tags tags
    :elem (or elem :div)
    :class class
    :layout (or layout :col)
    :init initial-views}))

(defn key-data [x]
  (if (keyword? x)
    (util/fqn x)
    x))

(defmethod node* ::frame [{:keys [elem class layout]} view-key data]
  (apply vector elem
         {:class      (html/class "frame" (name layout) class)
          :data-frame (key-data view-key)}
         data))

(repl.ui/defevent update-view
  [db view-key f & args]
  (apply update-in db [state views view-key] f args))


;;; Modal Panes - Managed views with associated selection data and editor modes.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn mode
  "Modes are sets of features that customize the behavior of view panes."
  [k init]
  k)

;; popup menus (same logic as main menu, but on button press or context events)

;; modal ::
;; slide ::

(defn menu
  [k init]
  k) ; TODO

(defmethod node* ::menu [n k v]
  [:ul.menu "MENU"])


;;; Engine CLI Integration - Adds UI command definitions and `app-db` sync.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Cmd [kind name icon grp doc tags event])

(defn cmd [k icon grp doc tags event]
  (cli/register (->Cmd ::cmd k icon (cli/find-group grp) doc tags event)))

(defmethod cli/call* ::cmd [x args]
  (!> (:event x) args))

(repl.ui/defevent ^:private sync-nodes
  [db m]
  (update db state update nodes merge m))


;;; UI System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init []
  ;; TODO pull initial state from sessionStorage, localStorage and IndexedDB?
  (!watch cli/defs sync-nodes))


;;; Misc. Components & Labels
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 ^:private next-view-id (atom 0))

(defn gen-view-id
  ([]
   (gen-view-id "view"))
  ([prefix]
   (str \# (util/key-of prefix) \# (swap! next-view-id inc))))

(defn- read-key [^string x]
  (if (= 35 (.charCodeAt x 0))
    x
    (keyword x)))

(defn view-key
  "Coerce its argument to a view key."
  ([x]
   (view-key "data-view" x))
  ([k x]
   (cond (instance? js/Event x) (some-> (html/find-attr x k) read-key)
         (keyword? x) x
         (string?  x) x
         :else (throw (ex-info "Can't coerce view-key" {:value x})))))

(def frame-key (partial view-key "data-frame"))
(def panel-key (partial view-key "data-panel"))

(defn node-of
  [state k]
  (let [v (if (keyword? k)
            k
            (-> state links (get k) (or (throw (ex-info "Missing UI link"
                                                        {:ui state :link k})))))]
    (-> state nodes v (or (throw (ex-info "Missing UI node"
                                          {:ui state :node v}))))))

(defn view-of
  [state k]
  (-> state views (get k) (or (throw (ex-info "Missing UI view"
                                        {:ui state :view k})))))

(repl.ui/defeffect ^:cmd test-cmd
  [_ e]
  (js/console.log e))

(repl.ui/defview sample-view
  "Used to debug `node` dispatch."
  [view me]
  [:div.sample.content
   [:h2 "Sample View"]
   [:div.row.grow
    [:div.grow
     (key-view view)
     [:button {:data-cmd (util/fqn test-cmd)} #_{:on-click (repl.ui/cb [e] (!> update-view view inc))} "Click Me"]
     (pretty me)]
    [:div.grow {:data-cmd (util/fqn test-cmd)}
     [node :--invalid--]]]])

;; TODO this grew a bit wildly while listening to a podcast, move to another module, find way to extract meta
;; - could also dump all of unicode into IDB and load by name or codepoint, ie `:unicode/fleur-de-lis`
;; - how big is that store? no good if user ends up with one copy per domain hosting this, db isnt for "builtin" data
;; - remote is a fuckton of queries, even with batches, death by a thousand cuts on the server side to scale this
;; - character info is minimal in browser, embedding a table is still large, and doesn't have character names
;;
;; could work like UI fonts -> specify codepoint ranges to include, and import only these
;; - only need full information for display labels
;; - in any case, builtin asciitable/unicodetable would be neat (long term, bored time)
;;   - hex view better to do short term, lots of buffers to inspect soon
;;   - got the meta too, can select a binary view and project it to a type
;;     - got the UI too, can dispatch to allow interactive/media views, not just text

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

(def check-label "✔")
(def cross-label "✘")

(def cross-mark "❌")
(def ?-0 "❔")
(def ?-1 "❓")
(def !-0 "❕")
(def !-1 "❗")

(def ?obj "￼")
(def ???? "�")

(def time-label "⏳")
(def clock-label "⏰")

(def pick "⛏")
(def curl-1 "➰")
(def curl-2 "➿")
(def undo-label "⎌")
(def edit-label "✍")
(def cut-label "✂")

(def ribbon "🎀")
(def drink "🍹")
(def cocktail "🍸")
(def wine "🍷")
(def sake "🍶")
(def tea "🍵")
(def beer "🍺")
(def beers "🍻")
(def dinner "🍽")
(def bottle "🍾")
(def popcorn "🍿")
(def present "🎁")
(def cake "🎂")
(def urn "🏺")
(def fishing "🎣")
(def coaster "🎢")
(def ferris "🎡")
(def carousel "🎠")
(def cinema "🎦")
(def camera "🎥")
(def keyboard-jack "🎘")
(def level-slider "🎚")
(def control-knob "🎛")
(def studio-mic "🎙")
(def microphone "🎤")
(def headphone "🎧")
(def palette "🎨")
(def top-hat "🎩")
(def circus "🎪")
(def slots "🎰")
(def movie "🎬")
(def billards "🎱")
(def game-die "🎲")
(def bowling "🎳")
(def acting "🎭")
(def gamepad "🎮")
(def target "🎯")
(def ticket "🎫")
(def tickets "🎟")
(def frames "🎞")
(def historic "⛬")
(def church "⛪")
(def castle "⛫")
(def chains "⛓")
(def anchor "⚓")
(def tea-hot "☕")
(def rain "⛆")
(def fuel "⛽")
(def tape "✇")
(def rainbow "🌈")
(def foggy "🌁")
(def sunrise-0 "🌄")
(def sunrise-1 "🌅")
(def dusk "🌆")
(def sunset "🌇")
(def stars "🌃")
(def night "🌉")
(def sailboat "⛵")
(def fountain "⛲")
(def umbrella-0 "🌂")
(def umbrella-1 "☔")
(def snowman-0 "☃")
(def snowman-1 "⛇")
(def airplane "✈")
(def shamrock "☘")
(def helm-label "⎈")
(def enter-label "⎆")
(def clear-label "⎚")
(def print-label "⎙")
(def prev-page-label "⎗")
(def next-page-label "⎘")

(def wave "🌊")
(def volcano "🌋")
(def cyclone "🌀")
(def milky-way "🌌")

(def earth-grid "🌐")
(def earth #js ["🌍" "🌎" "🌏"])
(def moon #js ["🌑" "🌒" "🌓" "🌔" "🌕" "🌖" "🌗" "🌘"])

(def moon-face-0 "🌚")
(def moon-face-1 "🌝")
(def moon-crescent "🌙")
(def moon-l "🌜")
(def moon-r "🌛")
(def sun-face "🌞")
(def star-glow "🌟")
(def star-sm-0 "⭒")
(def star-sm-1 "⭑")
(def star "⭐")
(def star-0 "☆")
(def star-1 "★")
(def heart-0 "♡")
(def heart-1 "❤")
(def rosette-0 "🏵")
(def rosette-1 "🏶")

(def light "💡")
(def bomb "💣")
(def sleep "💤")
(def collision "💥")
(def sweat "💦")
(def droplet "💧")
(def dash "💨")
(def poo "💩")
(def flex "💪")
(def dizzy "💫")
(def speech "💬")
(def thought "💭")
(def flower "💮")
(def hundred "💯")
(def money "💰")
(def currency "💱")
(def dollar "💲")
(def credit "💳")
(def yen-note "💴")
(def dollar-note "💵")
(def euro-note "💶")
(def pound-note "💷")
(def money-wings "💸")
(def seat "💺")
(def computer "💻")
(def briefcase "💼")
(def minidisc "💽")
(def floppy "💾")
(def cd "💿")
(def dvd "📀")

(def folder-0 "📁")
(def folder-1 "📂")
(def page-curl "📃")
(def page "📄")
(def calendar-0 "📅")
(def calendar-1 "📆")
(def card-index "📇")
(def chart-0 "📈")
(def chart-1 "📉")
(def chart "📊")
(def clipboard "📋")
(def pushpin-0 "📌")
(def pushpin-1 "📍")
(def paperclip "📎")
(def ruler "📏")
(def rulers "📐")
(def tabs "📑")
(def ledger "📒")
(def notebook-0 "📓")
(def notebook-1 "📔")
(def book-0 "📕")
(def book-1 "📖")
(def books "📚")
(def name-badge "📛")
(def scroll "📜")
(def memo "📝")
(def receiver "📞")
(def pager "📟")
(def fax "📠")
(def antenna "📡")
(def loudspeaker "📢")
(def megaphone "📣")
(def outbox "📤")
(def inbox "📥")
(def package "📦")
(def email "📧")
(def envelope-in "📨")
(def envelope-down "📩")
(def mailbox-lowered-0 "📪")
(def mailbox-lowered-1 "📬")
(def mailbox-raised-0 "📫")
(def mailbox-raised-1 "📭")
(def postbox "📮")
(def postal-horn "📯")
(def newspaper "📰")
(def mobile "📱")
(def mobile-> "📲")
(def mobile= "📳")
(def mobile-off "📴")
(def mobile-no "📵")
(def antenna-bars "📶")
(def photo-camera "📷")
(def flash-camera "📸")
(def video-camera "📹")
(def television "📺")
(def radio "📻")
(def stereo "📾")
(def videocasette "📼")
(def projector "📽")
(def beads "📿")
(def bright-0 "🔅")
(def bright-1 "🔆")
(def speaker- "🔇")
(def speaker-0 "🔈")
(def speaker-1 "🔉")
(def speaker-2 "🔊")
(def battery "🔋")
(def plug "🔌")
(def glass<- "🔍")
(def glass-> "🔎")
(def lock-0 "🔒")
(def lock-1 "🔓")
(def bell "🔔")
(def bookmark "🔖")
(def link "🔗")
(def radio-btn "🔘")
;; TODO U+1F525 need better way to get icons here

(def nope-label "⛔")
(def info-label "⚡")
(def warn-label "⚠")
(def debug-label "☣")
(def error-label "☢")
(def fatal-label "☠")
(def label-label "🏷")

(def arrow-circle-cw "↻")
(def arrow-circle-ccw "↺")
(def dotted-circle "◌")
(def sun-rays "☀")
(def snowflake "❄")
(def comet "☄")
(def pentagram "⛤")
(def reload-label "⟳")
(def gear "⛭")
(def gear- "⛮")

(def spinners
  #js [arrow-circle-cw
       dotted-circle
       historic
       sun-rays
       pentagram
       gear
       gear-
       nope-label
       debug-label
       error-label
       tape
       snowflake
       "⛶" "⛚"
       "✻" "✼" "✽" "✾" "✿" "❀" "❁" "❂" "❃" ""])

(def dice #js ["⚀" "⚁" "⚂" "⚃" "⚄" "⚅"])

(def pulsar #js ["⚬" "⚭" "⚮" "⚯"])

(def square-0 "□")
(def square-1 "⬛")

(def bin-0 #js ["⚆" "⚇"])
(def bin-1 #js ["⚈" "⚉"])

(def unit "☯" "𝌀")
(def dual "☯" #js ["⚊" "⚋"])
(def di-2 "☯" #js ["⚌" "⚍" "⚎" "⚏"])
(def di-3 "☯" #js ["𝌁" "𝌂" "𝌃" "𝌄" "𝌅"])
(def tri "☯"
  #js ["☰" "☱" "☲" "☳"
       "☴" "☵" "☶" "☷"])

(def hexa "☯"
  #js ["䷀" "䷁" "䷂" "䷃" "䷄" "䷅" "䷆" "䷇"
       "䷈" "䷉" "䷊" "䷋" "䷌" "䷍" "䷎" "䷏"
       "䷐" "䷑" "䷒" "䷓" "䷔" "䷕" "䷖" "䷗"
       "䷘" "䷙" "䷚" "䷛" "䷜" "䷝" "䷞" "䷟"
       "䷠" "䷡" "䷢" "䷣" "䷤" "䷥" "䷦" "䷧"
       "䷨" "䷩" "䷪" "䷫" "䷬" "䷭" "䷮" "䷯"
       "䷰" "䷱" "䷲" "䷳" "䷴" "䷵" "䷶" "䷷"
       "䷸" "䷹" "䷺" "䷻" "䷼" "䷽" "䷾" "䷿"])

(def tetra "☯"
  #js ["𝌆" "𝌇" "𝌈" "𝌉" "𝌊" "𝌋" "𝌌" "𝌍" "𝌎"
       "𝌏" "𝌐" "𝌑" "𝌒" "𝌓" "𝌔" "𝌕" "𝌖" "𝌗"
       "𝌘" "𝌙" "𝌚" "𝌛" "𝌜" "𝌝" "𝌞" "𝌟" "𝌠"
       "𝌡" "𝌢" "𝌣" "𝌤" "𝌥" "𝌦" "𝌧" "𝌨" "𝌩"
       "𝌪" "𝌫" "𝌬" "𝌭" "𝌮" "𝌯" "𝌰" "𝌱" "𝌲"
       "𝌳" "𝌴" "𝌵" "𝌶" "𝌷" "𝌸" "𝌹" "𝌺" "𝌻"
       "𝌼" "𝌽" "𝌾" "𝌿" "𝍀" "𝍁" "𝍂" "𝍃" "𝍄"
       "𝍅" "𝍆" "𝍇" "𝍈" "𝍉" "𝍊" "𝍋" "𝍋" "𝍍"
       "𝍎" "𝍏" "𝍐" "𝍑" "𝍒" "𝍓" "𝍓" "𝍕" "𝍖"])

;; TODO U+1F000 mahjong
;;      U+1F030 domino
;;      U+1F0A0 playing cards
;;      U+1F32D food
;;      U+1F400 animals
;;      shove it all into meta, macros to define ranges
;;      -> assoc with scales, ranges, numbers, colors, shapes -> list "bullet" styles, animations, countdowns, etc
;;      -> simplify exploration of different topics by having various "alphabets" to enumerate and decorate symbols
;;      -> create card games or change books in UI views, roll dices -> live coded collaborative decentralized D&D

(def roman
  #js ["Ⅰ" "Ⅱ" "Ⅲ" "Ⅳ" "Ⅴ" "Ⅵ" "Ⅶ" "Ⅷ" "Ⅸ" "Ⅹ" "Ⅹ" "Ⅻ"])

(def ansi
  #js ["␀" "␁" "␂" "␃" "␄" "␅" "␆" "␇" "␈" "␉" "␊" "␋"
       "␌" "␍" "␎" "␏" "␐" "␑" "␒" "␒" "␓" "␔" "␕" "␖"
       "␗" "␘" "␙" "␚" "␛" "␜" "␝" "␞" "␟" "␠" "␠" "␢"
       "␣" "␤" "␥" "␦"])

(defn btn
  ([label click]
   (btn label nil click))
  ([label class click]
   [:button {:class class :on-click click} label]))

(def close-btn  (partial btn close-label  "close"))
(def reload-btn (partial btn reload-label "reload"))

;; icon fonts -> fontawesome? materialicons? both? more?
;; - naively load all initially, then find ways to load only used icons on demand
;; - tower of lisp would be useful once more -> compiler should be told about this
