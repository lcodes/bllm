(ns repl.game
  "The main simulation view."
  (:require [bllm.ecs   :as ecs]
            [bllm.gpu   :as gpu]
            [bllm.input :as input]
            [bllm.util  :as util :refer [def1]]
            [bllm.view  :as view]
            [repl.dock  :as dock]))

(def1 scene "The ECS world currently being simulated." nil)
