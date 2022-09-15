(ns repl.ui
  "User Interface controls for views with models. Simplified use of `re-frame`."
  (:require-macros [repl.ui])
  (:require [reagent.core  :as rc]
            [re-frame.core :as rf]
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

(def sub rf/subscribe)

(def extraction-sub-key :db)

(rf/reg-sub extraction-sub-key (fn extract [db [_ k]] (get db k)))

(defn- extract
  "Specialized subscription to extract top-level data from the app-db.

  Doesn't look inside the data, doesn't support nested lookups. By design.

  NOTE prefer this over `sub` for 'Layer 2' subscriptions. The root of the
  app-db has to be fairly shallow, for all its keys are potential flows on
  update. This pattern ensures high-efficiency of the entire signal graph."
  [k]
  (sub (vector extraction-sub-key k)))

(defn- merge-schema-1 [db init]
  (if (map? init)
    (merge db init)
    (or db init)))

(def ^:private merge-schema (partial merge-with merge-schema-1))

(repl.ui/defevent init-schema
  "Initialize schema fragments defined by `defschema`."
  [db k specs]
  (update db k merge-schema specs))

(repl.ui/defschema state
  "UI state describing the state of the UI. What condition was my condition in?"
  theme :default
  style {}
  menus {}
  panes {}
  views {}
  prefs {})

(comment (js/console.log (deref re-frame.db/app-db)))

(repl.ui/defevent init-frame
  ;; pull `k` from localStorage as coeffect, use instead of specs if present
  [db k specs]
  (js/console.log db k specs)
  db)

(defn frame [k]
  [:div "TODO"])


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


;;; System Views - Managed components with durable state and a delegated render.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn view
  "Registers a managed UI view, to be displayed as part of a `container`."
  []
  ;; view hash, label, view function, context flags, preferred container
  ;; flags include singleton, system, pane etc
  )

(defn view-container
  "Displays the `view` components matching the given `view-key`."
  [view-key]
  [:div.view-container {:class (name view-key)}
   (cljs.pprint/pprint @(views view-key))])


;;; Modal Panes - Managed views with associated selection data and editor modes.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn mode
  "Modes are sets of features that customize the behavior of view panes."
  []
  )

(defn pane
  []
  )


;;; UI System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init [frame]
  ;; hook input events
  ;; init app db
  ;; restore UI state
  )
:hello
