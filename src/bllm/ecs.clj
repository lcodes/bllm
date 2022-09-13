(ns bllm.ecs
  (:require [bllm.meta :as meta]
            [bllm.util :as util :refer [defm]]))

(defm defc
  [sym & fields]
  (let [ast (meta/parse-struct &env fields)]
    `(def ~sym "TODO")))

(defm defsys
  [sym & args]
  `(defn ~sym []
     ))
