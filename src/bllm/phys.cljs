(ns bllm.phys
  (:require [bllm.ecs   :as ecs]
            [bllm.scene :as scene]
            [bllm.time  :as time]))

;; TODO ecs system update checks
;; - dont update derived values if their dependent inputs haven't changed
;; - version as a system shared block (system shared = unindexed)
;; - when signaling frame graph, short-circuit derived nodes where versions match
;; - use different component types for different signaling/checks
;; - here faster to batch through naively on version change, than compute hashes
;;   - shader graph in ECS might want more guarantees a compilation is worth it

(set! *warn-on-infer* true)

(ecs/defc Kinematic
  "Transforms integrated from velocity, not affected by forces and collisions.")

(def Position scene/World)

(ecs/defc Rotation
  "The scene rotation quaternion represented as a 3x3 matrix."
  {:type :mat3})

(ecs/defc InvInertiaTensorWorld
  {:type :mat3})

(ecs/defc InvInertiaTensorLocal
  {:type :vec3})

(ecs/defc InvMass
  {:type :f32})

(ecs/defc LinearFactor
  {:type :vec3})

(ecs/defc AngularFactor
  {:type :vec3})

(ecs/defc LinearVelocity
  {:type :vec3})

(ecs/defc AngularVelocity
  {:type :vec3})

(ecs/defc LinearDamping
  {:type :f32
   :init 0.5})

(ecs/defc AngularDamping
  {:type :f32
   :init 0.5})

;; TODO reuse cull regions?
;; - only doing distance-based culling (dont simulate far objects as often/precisely)
;; - also multiple potential observers (doesnt have to be cameras)

;; physics entities likely to change class independently of their culling/rendering/logical counterparts
;; - linked components ftw again; might be good to introduce some kind of `Actor` as a logical entity view
;;
;; single Actor will have many entities, each independently changing class from the others:
;; - Scene node     (transform, hierarchy)
;; - Culling object (camera mask, bounds, region)
;; - Render object  (mesh, material, passes)
;; - Physics object (dynamics, collisions, sleepers)
;; - AI object      (goals, memory, personality)

(ecs/defc ^:group RigidBody
  {:not [scene/Parent]
   :in [Rotation InvMass
        LinearFactor  LinearDamping  LinearVelocity
        AngularFactor AngularDamping AngularVelocity
        InvInertiaTensorLocal InvInertiaTensorWorld]})

;; TODO group systems, "event" dispatch and entity "sync" are common enough to lift here
;; - only "toplevel" systems are instantiated in worlds
;; - allows systems to specify either a parent (direct link) or a sibling (indirect parent)
;; - parents instantiate their children, state order is the same as execution order, roughly
;; - flat arrays in world, dont traverse systems as a tree!
;; - event and sync are specified here as initial systems, appended at the end by default
;;   - can inject between or after with {:in <group> :after :event}
;;   - events always trigger before sync, as they are the most likely to generate syncs

(ecs/defsys fixed-tick
  "Update group for systems ticking inside the physics fixed time step."
  {:event []
   :sync true}

  (tick []
    ;; calc fixed time here
    (time/fixed)
    ;; run fixed step zero or more times
    (ecs/run-group))

  (apply-damping [ld LinearDamping
                  ad AngularDamping
                  lv :out LinearVelocity
                  av :out AngularVelocity]
   (math/vec3-scale lv lv (js/Math.pow (- 1 ld) fixed-delta))
   (math/vec3-scale av av (js/Math.pow (- 1 ad) fixed-delta)))

  (predict-unconstrained-motion []
    )

  (solve-constraints []
    )

  (predict-integrated-transform [lv LinearVelocity
                                 av AngularVelocity
                                 p  :out Position
                                 r  :out Rotation]
    ;; Position
    (->> (math/vec3-scale ))
    ;; Rotation
    (let [angle (math/vec3-length av)]
      (when (> (* angle fixed-delta) angular-motion-threshold)
        (set! angle (/ angular-motion-threshold fixed-delta)))
      (if (>= angle 0.001)
        (math/vec3-scale axis av (/ (* 0.5 fixed-delta angle) angle))
        (math/vec3-scale axis av (- (* 0.5 fixed-delta)
                                    (* fixed-delta fixed-delta fixed-delta
                                       0.02083333333 angle angle))))
      (aset d 3 (js/Math.cos (* angle fixed-delta 0.5)))
      (math/mat3->quat o r)
      (math/quat-mul p d o)
      (math/quat-normalize p p)
      (math/quat->mat3 pr p)))

  ;; TODO continuous integration

  (set-center-of-mass-transform [p Position
                                 r Rotation]
    ))

(defn apply-torque [e torque]
  )

(defn apply-force [e force relative-pos]
  )

(defn apply-impulse [e impulse relative-pos]
  )
