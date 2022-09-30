(ns bllm.ecs
  (:require [bllm.meta :as meta]
            [bllm.util :as util :refer [defm]]))

(defm defc
  "Defines a new data component type."
  [sym & fields]
  (let [m   (meta sym)
        ast (meta/parse-struct &env fields)]
    `(def ~sym
       (bllm.ecs/->component
        ~(util/unique-id   sym)
        ~(util/unique-hash sym fields m)
        0 ; TODO meta -> options
        ~(:size m 0)
        nil ; TODO type
        nil ; TODO ctor
        nil ; TODO init
        ~(util/u16-array (:in  m))
        ~(util/u16-array (:out m))))))

(defm defsys
  [sym & queries] ; TODO state ctor? or queries a series of members, with ctor in them?
  (let [m (meta sym)]
    `(def ~sym
       (bllm.ecs/->system
        ~(util/unique-id   sym)
        ~(util/unique-hash sym queries m)
        0 ; TODO meta -> options
        nil ; TODO state ctor
        nil ; TODO queries
        nil ; TODO code
        ))))
