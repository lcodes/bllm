(ns bllm.ecs
  (:require [bllm.util :refer [defm]]))

(defm defc
  [sym & args]
  `(def ~sym "TODO"))

(defm defsys
  [sym & args]
  `(defn ~sym []
     ))
