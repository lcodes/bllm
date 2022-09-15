(ns repl.ui
  (:require [bllm.util :as util :refer [defm]]
            [clojure.string :as str]))

(defn- emit-event [reg sym params handler]
  (let [f `(fn ~sym ~params ~handler)]
    `(do (def ~sym ~(keyword (str *ns*) (str/replace (name sym) #"^on-" "")))
         ~(if-let [w (:with (meta sym))]
            `(~reg ~sym ~w ~f)
            `(~reg ~sym ~f))
         ~sym)))

(defm defevent
  [sym params & handler]
  (emit-event 're-frame.core/reg-event-db sym params handler))

(defm defeffect
  [sym params & handler]
  (emit-event 're-frame.core/reg-event-fx sym params handler))

(defm defhandler
  [sym params & handler]
  (emit-event 're-frame.core/reg-event-ctx sym params handler))

(defm deframe
  "Defines a configurable UI view container. Pulls `re-frame` off the wall."
  [sym & initial-views]

  )

(defm defview
  "Defines a configurable UI view panel."
  [sym & view]
  `(def ~sym
     (repl.ui/view
      )))

(defm defmode
  "Defines a UI mode. Has an associated asset definition and selection."
  [sym & pane]
  `(def ~sym
     (repl.dock/pane ~(util/unique-id sym) (fn ~sym ~@pane)
                     ~(or (:label (meta sym)) (util/label sym)))))
