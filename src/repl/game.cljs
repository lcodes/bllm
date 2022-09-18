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

(defn- fullscreen
  "Requests a `viewport` canvas to be displayed fullscreen."
  []
  )

#_
(ui/defclass Viewport
  (component-did-mount []
    ;; register input events, create swapchain as state, connect viewport to camera
    (disp/add-viewport :TODO js/devicePixelRatio))
  (component-will-unmount []
    (disp/remove-viewport :TODO))
  (reagent-render []
    [:canvas]))

#_
(ui/defview port
  {:elem Viewport}
  [state] ; associated camera views

  ;; use element visibility to enable/disable associated views
  ;; - draw every enabled view based on their configured framerate & state
  ;;   (ie idle at 5fps, editor at 10fps, preview at 30, run at 60)
  )


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

;; `view` is system content -> no association of object selection ->
;; `pane` is assets content -> associated to its object selection -> ie emacs window associated to a buffer buffer
;; - every edited asset selection therefore needs to track a list of pane states (scroll position, camera, cursor, etc)
;; - reason is their panels can swap to other panes, just like emacs window switching buffers -> but more states than assets
;; - dont want to lose different states, so when switching back to say "scene A" then its possible to also pick the pane state
;;   - common editor view has 4 panes of the same scene, one for each of the 3 axes and a perspective camera
;;   - `find-asset` switches to the scene, which now has an abiguity of 4 pane states to choose from (4 could be any number)
;;   - more specific `find-pane` could list titles such as "scene A:pane 1", then directly getting leaf state
;;   - `mode` bar or `select-asset-pane` can then list and switch between pane states for the currently viewed asset selection
;;   - also gives all the data to store this state across sessions; page load should always restore the complete layout state
