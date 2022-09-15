(ns repl.ui
  (:require [bllm.util :as util :refer [defm]]
            [clojure.string :as str]))

(defn- emit-event [reg sym [node & args] handler]
  (let [a (gensym "args")
        f `(fn ~sym [~node ~a]
             ~(if (empty? args)
                `(do ~@handler)
                `(let [[_# ~@args] ~a]
                   ~@handler)))]
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

(defn- emit-schema-sub
  [sym key init]
  (let [kind (cond (keyword? init) :key
                   (vector?  init) :vec
                   (set?     init) :set
                   (map?     init) :map)]
    `(let [~'kw ~key]
       (re-frame.core/reg-sub
        ~'kw ~'q
        ~(case kind
           (:key
            :vec
            :set) 'kw
           (:map) `(fn ~sym [data# [_ key#]]
                     (get data# key#))))
       ~(case kind
          (:key
           :vec
           :set) `(def  ~sym      (repl.ui/sub [~'kw]))
          (:map) `(defn ~sym [k#] (repl.ui/sub [~'kw k#]))))))

(defm defschema
  "Defines a data schema as a view model of the `re-frame.db/app-db` structure.

  NOTE only specify data here, indexing happens downstream in the signal graph."
  [sym & spec+inits]
  (let [specs (partition 2 spec+inits)
        inits (map second specs)
        syms  (map first  specs)
        keys  (map util/ns-keyword syms)]
    `(binding [reagent.ratom/*ratom-context* true] ; Don't warn, globals are fun
       (let [schema# ~(util/ns-keyword sym)]
         (re-frame.core/dispatch-sync
          [repl.ui/init-schema schema# ~(zipmap keys inits)])
         (def ~sym (repl.ui/extract schema#))
         (let [~'q (fn [] ~sym)]
           ~@(map emit-schema-sub syms keys inits))))))

(defm deframe
  "Defines a configurable UI view container. Pulls `re-frame` off the wall."
  [sym & initial-views]
  `(let [key# ~(util/ns-keyword sym)]
     (re-frame.core/dispatch-sync
      [repl.ui/init-frame key# [~@initial-views]])
     (defn ~sym []
       (repl.ui/frame key#))))

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
