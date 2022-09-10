(ns bllm.ecs
  (:require [bllm.util :refer [defm]]))

(defm defc
  [sym]
  )

(defm defsystem
  [sym]
  `(defn ~sym []
     ))
