(ns bllm.view
  (:require [bllm.ecs   :as ecs]
            [bllm.math  :as math]
            [bllm.meta  :as meta]
            [bllm.scene :as scene]
            [bllm.util  :as util]))

(ecs/defc Camera
  "A camera component is its view matrix."
  {:type :mat4})

(ecs/defc Projection
  "The projection matrix associated with a camera."
  {:type :mat4})

(ecs/defc Perspective
  "Update a camera using a perspective projection. Usually 3D."
  fov    :f32
  aspect :f32
  near   :f32
  far    :f32)

(ecs/defc Ortographic
  "Update a camera using an ortographic projection. Usually 2D."
  rect :uvec4
  size :f32
  near :f32
  far  :f32)

(meta/defenum frustum-plane
  plane-top
  plane-right
  plane-bottom
  plane-left
  plane-near
  plane-far)

(ecs/defc Frustum
  "Contains the six planes of a view frustum."
  {:type :vec3
   :size 6})

;; input router -> multiple views in single canvas, but single source of browser keyboard/mouse
;; - the same canvas could display 2+ different ecs worlds! inputs inevitably route through here

;; TODO what about XR and multiview? -> absolutely use if available, shader system needs to support these variants too
;; - variants dont just affect the final pass, but just about every pass contributing towards it
;; - all output textures become 2d arrays of 2 slices (left & right eye) and all camera uniform accesses now need a viewId
;; - no different than instancing, except at the other end of the rendering I/O pipeline
(ecs/defc Target
  "A view target defines a camera's area over the render canvas."
  canvas   :u32    ; display canvas index
  clear    :u32    ; TODO Clear enum (none, solid color, gradient, sky, custom entity)
  viewport :uvec4) ; TODO really a rect, XY/WH, not XYZW -> important distinction when generating display labels

(ecs/def? active-cameras
  Camera Projection Frustum Target ecs/Enabled)

(defn- perspective [fov aspect near far]
  ;; TODO
  )

(defn- ortographic []
  ;; TODO
  )

(ecs/defsys Update
  (cameras
   active-cameras
   [w scene/World
    c :out Camera]
   (math/mat4-set c (math/mat4-inverse w)))

  (perspective-projections
   active-cameras
   [c      Perspective
    p :out Projection
    f :out Frustum]
   (math/mat4-set p (perspective (.-fov c) (.-aspect c)
                                 (.-near c) (.-far c)))
   (.set f 0 util/axis-x+) ; TODO
   (.set f 1 util/axis-x+)
   (.set f 2 util/axis-x+)
   (.set f 3 util/axis-x+)
   (.set f 4 util/axis-x+)
   (.set f 5 util/axis-x+))

  (ortographic-projections
   active-cameras
   [c      Ortographic
    p :out Projection
    f :out Frustum]
   (math/mat4-set p (ortographic )) ; TODO
   (.set f 0 util/axis-x+) ; TODO
   (.set f 1 util/axis-x+)
   (.set f 2 util/axis-x+)
   (.set f 3 util/axis-x+)
   (.set f 4 util/axis-x+)
   (.set f 5 util/axis-x+)))
