(ns bllm.geom
  (:require [bllm.wgsl :refer [defkernel]]))

;; generate shapes
;;
;; all pack in single index/vertex buffers, return views

;; recreate entire buffer when new shapes are created
;; - dont get an explosion of buffers, dont limit draw/compute batching

;; 3D Shapes:
;; - Box (simpler cube, rounded corners)
;; - Cylinder (simpler cone, capsule)
;; - Sphere
;; - Frustum
;; - Upload a teapot?

;; 2D Shapes:
;; - Plane
;; - Triangle
;; - Rectangle (simpler square)
;; - Ellipse (simpler circle)

;; GENERATE IT ON THE GPU
;; - got compute shaders, got shader system
;; - compute kernel per shape, one indirect dispatch per shape for all variants
;; - can reuse buffer for longer before it needs realloc, can copy existing data

;; generic storage bindings here
#_
(defkernel sphere
  {:workgroup [32 32]}
  [global-invocation-id :builtin] :uvec3
  (var index global-invocation-id.x
       vVel  (-> (aget particlesA.particles index) .-pos)
       ))
#_
(defkernel box
  []
  )
