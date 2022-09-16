(ns repl.ui
  (:require [bllm.util :as util :refer [defm]]
            [clojure.string :as str]))

(defn- emit-event [reg sym params handler]
  (let [a (gensym "args")
        f (if (and (symbol? params) (empty? handler))
            params
            (let [[node & args] params]
              `(fn ~sym [~node ~a]
                 ~(if (empty? args)
                    `(do ~@handler)
                    `(let [[_# ~@args] ~a]
                       ~@handler)))))]
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

(defn- sub [sym]
  (symbol (str sym "-sub")))

(defn- emit-schema-sub
  [sym key]
  `(do (def ~sym ~key)
       (re-frame.core/reg-sub ~sym ~'q ~sym)
       (def ~(sub sym) (repl.ui/$ [~sym]))))

(defm defschema
  "Defines a data schema as a view model of the `re-frame.db/app-db` structure.

  NOTE only specify data here, indexing happens downstream in the signal graph."
  [sym & spec+inits]
  (let [specs (partition 2 spec+inits)
        inits (map second specs)
        syms  (map first  specs)
        keys  (map util/ns-keyword syms)
        atom  (sub sym)]
    `(binding [reagent.ratom/*ratom-context* true] ; Don't warn, globals are fun
       (def ~sym ~(util/ns-keyword sym))
       (def ~atom (repl.ui/$db ~sym))
       (let [~'q (fn [] ~atom)] ; Privately reusable intermediate sub signal.
         ~@(map emit-schema-sub syms keys))
       (repl.ui/schema ~sym ~(zipmap keys inits)))))

(defm deframe
  "Defines a configurable UI view container. Pulls `re-frame` off the wall."
  [sym & initial-views]
  (let [m (meta sym)]
    `(def ~sym (repl.ui/frame ~(util/ns-keyword sym)
                              ~(:elem m) ~(:class m)
                              [~@initial-views]))))

(defm defview
  "Defines a configurable UI view panel."
  [sym & view]
  `(def ~sym (repl.ui/view ~(util/ns-keyword sym) (fn ~sym [] ~@view))))

(defm defmode
  "Defines a UI mode. Has an associated asset definition and selection."
  [sym & init]
  `(def ~sym
     (repl.ui/mode
      ~(util/unique-id sym)
      ~(or (:label (meta sym)) (util/label sym))
      (fn ~sym ~@init))))

(defm defpane
  ""
  [sym & init]
  `(def ~sym
     (repl.ui/pane
      ~(util/unique-id sym)
      (fn ~sym ~@init))))
