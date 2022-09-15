(ns repl.ui
  "User Interface convenience utilities. Simplified use of `re-frame`."
  (:require-macros [repl.ui :as ui])
  (:require [re-frame.core :as rf]
            [bllm.util :as util :refer [def1]]))

(set! *warn-on-infer* true)


;;; Utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def sub rf/subscribe)


;;; System Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn error-boundary
  "Like `try`/`catch`, but across the UI component tree."
  []
  )

(defn strict-mode
  "Enables React's `StrictMode` over child nodes. Development only."
  []
  )


;;; User Configurable Views
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-sub :repl/ui :repl/ui)

(rf/reg-sub ::containers
            :<- [:repl/ui]
            ::containers)

(rf/reg-sub ::views
            :<- [::containers]
            (fn [db [_ k]]
              (get db k)))

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
   (cljs.pprint/pprint @(sub [::views view-key]))])


;;; Content Views
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn mode
  []
  )


;;; UI System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init [frame]
  ;; hook input events
  ;; init app db
  ;; restore UI state
  )
