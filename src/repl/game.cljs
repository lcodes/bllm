(ns repl.game
  "The main simulation view."
  (:require [bllm.ecs   :as ecs]
            [bllm.gpu   :as gpu]
            [bllm.input :as input]
            [bllm.util  :as util :refer [def1]]
            [bllm.view  :as view]
            [repl.dock  :as dock]))

(def1 scene "The ECS world currently being simulated." nil)

(def1 camera "The camera Entity within the ECS world." 0)

;; controls
;; - canvas mode (single, split, )
;; - view mode (per view; camera settings, render path (ie debug view), input mode, etc)
;; - track which view has focus -> only one canvas element to get events from

;; TODO more than one game view?
;; - fine if they're both on the same world
;; - more than one world is beginning to push things; duplicate system workload for each new world
;; - subscenes are usually tiny (displaying a handful of meshes, materials and maybe a skybox)
;;   - mostly self-contained worlds to edit one partifular asset within a simulated environment
;;   - right now `app` system loop is hardcoded, but possible to do once ECS takes over systems

;; bigger component
;; - on mount: register input events, create swapchain as state, connect viewport to camera
(defn view []
  [:canvas.pane.game-view])
