(ns bllm.view
  (:require [bllm.cull :as cull]
            [bllm.disp :as disp]
            [bllm.ecs  :as ecs]
            [bllm.pass :as pass]))

;; highest level of the rendering abstraction stack; above it is `<canvas>` (or `OffscreenCanvas`)
;; scene camera -> viewport -> swapchain -> final texture view

;; input router -> multiple views in single canvas, but single source of browser keyboard/mouse
;; - the same canvas could display 2+ different ecs worlds! inputs inevitably route through here

;; - local multiplayer (good old days!)
;; - scene previews, split display editors (single canvas = single composition unit for browser)

;; think emacs window (while the current tab is an emacs frame)
;; the closest this entire engine will get to a view-model-controller pattern.

(ecs/defc Camera
  "Compute a render view every frame."
  ;; TODO not that many cameras overall, large ECS buckets wasteful; fit memory & realloc on rare structural changes
  ;; - need tooling to browse buckets, need ECS buckets to explore first
  {:count-hint 4}
  ;; clear flags  -> depth/stencil always cleared; color always overwritten
  viewport  :uvec4 ;; TODO really a rect, XY/WH, not XYZW -> important distinction when generating display labels
  cull-mask :u32
  ;; - entities without a culling mask component just need to pass the frustum test

  ;; TODO more distant future: occlusion culling, GPU culling
  )

(ecs/defc Projection
  "Computed camera matrices, feeds to GPU side camera."
  view :mat4
  proj :mat4)
#_
(ecs/defc Perspective
  {:require [Data]}
  )
#_
(ecs/defc Orthographic
  {:require [Data]}
  )

;; frame render entry points
;; - collect cameras, cull objects & lights, bucket into passes, setup & upload state, render graph, emit cmds, submit

;; TODO what about XR and multiview? -> absolutely use if available, shader system needs to support these variants too
;; - variants dont just affect the final pass, but just about every pass contributing towards it
;; - all output textures become 2d arrays of 2 slices (left & right eye) and all camera uniform accesses now need a viewId
;; - no different than instancing, except at the other end of the rendering I/O pipeline
