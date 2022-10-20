(ns bllm.scene
  (:require [bllm.ecs :as ecs]))

(set! *warn-on-infer* true)

;; TODO system to track entity IDs of load units? small-ish uint32array
;; - dont want everything to start affecting entity classes
;; - tons of classes with almost no entities in their blocks is no good
;; - make it like ecs/Name, where the component is static

(ecs/defc World
  "Matrix positioning an entity in world space, absolute to a `Camera`."
  {:type :mat4
   :init mat4-identity})

(ecs/defc Local
  "Matrix positioning an entity in local space, relative to a `Parent`."
  {:type :mat4
   :init mat4-identity
   :io  [World]})

(ecs/defc Translation
  {:type :vec3
   :init zero
   :io  [World]
   :out [Local]})

(ecs/defc Rotation
  {:type :quat
   :init quat-identity
   :io  [World]
   :out [Local]})

(ecs/defc Scale
  {:type :f32
   :init 1
   :io  [World]
   :out [Local]})

(ecs/defc Scale3
  {:type :vec3
   :init one
   :io  [World]
   :out [Local]})

(ecs/defc Parent
  {:type ecs/entity
   :io  [World]})

(ecs/defc ^:system Child
  {:type ecs/entity
   :size 4})

(ecs/defc ^:system Depth
  "Number of parent entities until a scene root is reached. A root is always 0."
  {:type :u8 :sorted :desc})

(ecs/defc Static
  "Disables a transform entity every time its `World` matrix is updated.")

;; TODO this more or less turns certain components as the derived value of others
;; - graph of this generated from wirings; all of which goes through meta emits
;; - first-class queries form links in a graph of systems and components.
;; - got execution order (topsort) and semantics (metadata), control everything
;; - short circuit of work transparent for systems definitions, with opt-out
;;
;; TODO extensibility
;; - make sure to still allow any other system function to be spliced/adviced in here
;; - without touching this definition; ie {:after scene/update-local} or {:group Transform}
(ecs/defsys Transform
  {:with ecs/enabled}

  ;; TODO Euler -> quaternion
  ;; TODO composite rotation / scale

  ;; projection Π :: array, layout of the output set
  ;; selection  σ :: query, possibly filtering (when block/index can be leveraged)
  ;; rename     ρ :: param, fed to system functions bound to a view of the output set
  ;; join       ⋈ :: TODO can something be done with linked components? sorted? indexed?

  (update-local
    {:select []
     :into Local
     :with Depth}
    []
    )

  (update-world-children
    {:select []
     :with Depth} ; already sorted by descending depth; all children guaranteed before their parents
    ;;
    )

  (update-world
    {:select []
     :without Depth}
    )

  (disable-static
    {:with Static}
    [group]
    ;; TODO compute class change (get current, remove Enabled)
    (ecs/group-change-class group disabled-class)))

;; TODO got linked components, sorted components
;; - need PRIMARY KEY components -> or rather a relational entity/actor thingy
;; - could be the "central" entity of a group, or one dedicated to the role
;; - scene node makes sense when its there; fairly central, standalone works too
;; - low-level systems most likely dont care, but high-level "glued" object nice
;;
;; actors are entities whose components are other entities (higher order entities?)
;; - how to model this? reuse components? ideally, could be tricky, or unified
;; - use class-id instead of component-id to build actor classes
;; - always store `entity` components; extend with actor components
