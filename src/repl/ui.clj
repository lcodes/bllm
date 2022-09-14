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

(defm defpane
  [sym & view]
  `(def ~sym
     (repl.dock/pane ~(util/unique-id sym) (fn ~sym ~@view)
                     ~(or (:label (meta sym)) (util/label sym)))))
