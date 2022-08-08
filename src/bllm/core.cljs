(ns bllm.core
  "Wires the low-level engine sub-systems."
  (:require [bllm.disp  :as disp]
            [bllm.gpu   :as gpu]
            [bllm.input :as input]
            [bllm.meta  :as meta]
            [bllm.time  :as time]
            [bllm.util  :as util :refer [def1]]
            [bllm.wgsl  :as wgsl]))

(set! *warn-on-infer* true)

(defn init
  "Executes the initialization logic of internal sub-systems independently."
  []
  (js/Promise.all
   #js [(meta/init)
        (gpu/init)
        (disp/init)
        (input/init)]))

(defn start
  "Executes post-initialization logic. Safe time to wire systems together."
  []
  (js/Promise.all
   #js [(disp/start)
        (time/start)]))

(defn pre
  "Ticks the input systems."
  []
  (time/tick)
  ;;(input/pre)
  (disp/tick)
  (wgsl/tick)
  )

(defn tick
  "Ticks the simulation systems."
  []
  )

(defn post
  "Ticks the output systems."
  []
  ;;(input/post)
  )
