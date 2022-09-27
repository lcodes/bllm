(ns bllm.ecs
  (:require [bllm.meta :as meta]
            [bllm.util :as util :refer [defm]]))

(defm defc
  "Defines a new data component type."
  [sym & fields]
  (let [m   (meta sym)
        ast (meta/parse-struct &env fields)]
    `(do (def ~sym (fn ~'TODO [])) ; wrapper type (need defclass to generate getter/setter fields)
         (bllm.ecs/component
          (bit-or ~(util/small-id sym)
                  0)
          ~sym
          ~(util/js-array (:in  m))
          ~(util/js-array (:out m))))))

(defm defsys
  [sym & args]
  `(defn ~sym []
     ))
