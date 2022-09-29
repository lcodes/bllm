(ns bllm.scene
  "Scene management."
  (:require [bllm.ecs :as ecs]))

(ecs/defc World
  "Matrix positioning an entity in world space, absolute to a `Camera`."
  {:type :mat4
   :init mat4-identity}) ; TODO maths, types

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

(ecs/defc ScalarScale
  {:type :f32
   :init 1
   :io  [World]
   :out [Local]})

(ecs/defc VectorScale
  {:type :vec3
   :init one
   :io  [World]
   :out [Local]})

(ecs/defc Parent
  {:type :entity
   :io  [World]})

(ecs/defc ^:system Child
  {:type :entity
   :size 8})

(ecs/defsys Transform
  ;; TODO queries -> reactions -> frame graph
  )

(defn tick []
  ;; TODO manually tick system here or refactor tick loop to use ECS first?
  )
