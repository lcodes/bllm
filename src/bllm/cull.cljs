(ns bllm.cull
  (:require [bllm.ecs   :as ecs]
            [bllm.scene :as scene]
            [bllm.view  :as view]))

;; TODO entity components? need a way to separate actors from entities from components
;; - but actors's components are entities, and entities's components are components
;; - system queries don't care (ie query cull objects, join into scene world matrices)
(ecs/defc ^:entity Object
  "A culling object simply defines its own culling mask.

  A bounding component is also required to make it active."
  {:type :u32 :init 0xffffffff})

(ecs/defc Mask
  "Attached to a camera to filter the culling objects it can see in the scene.

  Cameras with no culling mask match on all objects inside the view frustum."
  {:type :u32 :init 0xffffffff})

(ecs/defc Point
  "A culling object with a position only."
  center :vec3)

(ecs/defc Sphere
  "The space a culling object occupies in the scene, using a bounding sphere."
  center :vec3
  radius :f32)

(ecs/defc AABB
  "The space a culling object occupies in the scene, using a bounding box."
  center :vec3
  extent :vec3)

(ecs/defc ^:shared Region
  "Organizes culling blocks by splitting them into regions of the scene.

  Forms a geospatial tree used to quickly cull large quantities of entities."
  center :vec3
  extent :vec3)

(defn- point-in-frustum? []
  )

(defn- sphere-in-frustum? []
  )

(defn- box-in-frustum? []
  )

;; how to organize entities here?
;; - got visual & render parts, with different block groupings (one over visibility, the other over visuals)
;; - same world transform for both -> extend to physics too
;;
;; smaller classes = easier batching
;; but also more random access, to access data from related classes (visual -> visibility -> transform)

;; different buckets for static vs dynamic entities
;; - maintain independently from render entities
;; - static is simple, never move, set bounds in world space and done
;;   - easily decoupled entities, culling yields render object IDs
;; - dynamic is more complex, could track when entities move to another region and move visibility block
;;   - in both cases, filtered object IDs need to be looked up -> translate to places, sort, batch
;;   - batching per render block, shared GPU vertex/index buffers and shader technique per block
;;   - define material properties per entity, as components; pack & upload to GPU -> compute unpack in place
;; - decouple transforms from render and visibility objects -> better batch transforms, random access after filtering

;; dont want: blocks of 1 entities, because meshes can be unique in scene
;; dont want: lots of different classes with slight variations, when few would fill blocks

;; entity lookup has to be fast
;; Entity ID -> Place -> world.blocks[place.block].arrays[classComponentIndex][place.index]
;; - can it be better?
;; - could decouple arrays from blocks, store them directly on world
;; world.arrays[place.block][classComponentIndex][place.index]
;;
;; if places can be sorted first, this isn't too cache unfriendly, for javascript at least

(ecs/defsys Visibility
  regions (ecs/? :block Region)

  (camera-regions
   ""
   []
   )

  (region-entities
   ""
   [])
  )
