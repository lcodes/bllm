(ns repl.game
  "The main simulation view."
  (:require [bllm.disp  :as disp]
            [bllm.ecs   :as ecs]
            [bllm.gpu   :as gpu]
            [bllm.input :as input]
            [bllm.util  :as util :refer [def1]]
            [bllm.view  :as view]
            [repl.ui    :as ui]))

(set! *warn-on-infer* true)

(def1 scene "The ECS world currently being simulated." nil)

(def1 camera "The camera Entity within the ECS world." 0)

(ui/defview port
  {:elem :canvas}
  []
  ;; on mount: register input events, create swapchain as state, connect viewport to camera

  ;; use element visibility to enable/disable associated views
  ;; - draw every enabled view based on their configured framerate & state
  ;;   (ie idle at 5fps, editor at 10fps, preview at 30, run at 60)
  )

;;(disp/add-viewport canvas js/devicePixelRatio)


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
