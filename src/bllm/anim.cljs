(ns bllm.anim
  (:require [bllm.data :as data]
            [bllm.ecs  :as ecs]
            [bllm.meta :as meta]))

(set! *warn-on-infer* true)


;; TODO asset definitions

(meta/defenum easing
  linear
  sine-in
  sine-out
  sine-in-out
  ;; TODO full list
  custom) ; TODO skip enum, all easing fns custom, test with sorting

(ecs/defc ^:sorted Easing
  "The curve function associated with an interpolation."
  {:type :u8 #_easing})

(ecs/defc ^:entity Tween
  "Defines the value range of an interpolation."
  from :f32
  to   :f32)

(ecs/defc Duration
  "The ending time of a playable animation."
  {:type :f32})

(ecs/defc Time
  "The current time of a playable animation."
  {:type :f32})

(ecs/defc Loop
  "Whether an animation plays once or repeats.")

(ecs/defc Playing
  "Marks an animated entity as currently playing.")

;; foundation of skeletal animation -> but define skeletons & interpolations of these in another module
;; - need extensible timelines, without indirections; define all timeline target types this way
;; - let system match on timeline + output component, theres the extension point; new outputs and systems

(data/defstore Timeline
  "A sequence of keyframes over time. "
  ;; - scale/offset default values here, move to ECS world; every Track can override
  ;; - separate arrays: keyframes from keyframe data; time vs values & events
  ;;
  ;; single target: component type, need to know offset to write to (direct component match simplest)
  ;; - ie write interpolated floats to a `Float` result, write integer changes to an `Int`
  ;; - write resource changes to their target component (ie `Sprite` -> single value component -> no cache waste)

  ;; Timeline types (all keyframes of this type):
  ;; - scalar
  ;; - vector
  ;; - color
  ;; - pose (specialized interpolation between bone poses)
  ;; - boolean (packed bit-array, no interpolation, ie enable/disable another entity -> timed toggles)
  ;; - asset ref (sprite, texture, material, buffer, etc) (no interpolation, swap value at keyframe)
  )

(data/defstore Scenario
  "A collection of timelines as a single load and execution unit."
  ;; 50 timelines not uncommon, hundreds of instances not uncommon either
  ;; - individual timelines can be enabled/disabled from code
  )

;; about block sort keys:
;; - sort by asset key, multiple micro system batches per macro component blocks
;; - periodic branch misprediction on key far better than break in components data
;; - pushes different values across blocks; ends up acting like a shared component
;; - but more finely grained, (also use for easing)
(ecs/defc ^:sorted ^:entity Track
  " Refers to the animation timeline asset."
  {:type Timeline})

(ecs/defc Scale
  {:type :f32})

(ecs/defc Param
  scale  :f32  ; Playback speed. Multiplies delta then adds offset.
  offset :f32) ; Playback position. After scale.

(ecs/defc ^:entity Score
  "All the tracks of an animation."
  ;; TODO need something bigger than component size for this; max 16 but anims can go WILD in track count
  )

;; TODO IDEA merge class/actor definitions, becomes composable
;; - an actor emerges back as a class made only of entity components, or with an Actor tag
;; - first entity component defines entity kind, all other entity components become links
;; - implicit one-to-one links are common between entities of the same actor
;;   - want batching at the system level, optimize for that; also can't avoid random accesses
;;   - different systems compose a single actor; culling, graphics, physics, audio, network, ai, and so on
;;   - all using different entities, to avoid big cross-system classes; avoid conflicting constraints
;;     - ie culling batches per scene region, graphics per buffer/shader, physics per active clusters
;;     - actors need components in all these systems; could have multiple visuals, or playing sounds
;;     - could be different actors; again for less total class types, same one-to-one entity relationship


(def TweenFloat (ecs/class Tween Time Duration Easing ecs/Float)) ; tween -> value <- consumer

(def TweenTrack (ecs/class Track Time Duration Param TweenFloat))

(def score-class (ecs/class Score ))

(ecs/defc Add)
(ecs/defc Remove)
(ecs/defc Frame)

;; TODO systems are defined as a single unit sharing state
;; - but queries have to be top-level definitions
;; - use system to init/term state along with world
;; - wait why; shared system state belongs in world components (which are systems)
;; - systems can refer to each other's state anyways, LINKED SYSTEMS then
;;   - linked system dont have state, they depend on another and use their state
;;   - then got a 1:1 assoc between systems and tick functions
;; - can still run multiple queries per tick, enable/disable on system for tick
(ecs/deftick Group
  (:event Remove) ; First: don't tick anims to be discarded

  ;; generic tweens
  (advance-time-bounded [l Loop
                         d Duration
                         t ^:io Time]
    ;; t + delta, either clamp to duration or wrap around
    )

  ;; normalized tweens, timelines, etc
  (advance-time-normalized [s Scale
                            t ^:io Time]
    (ecs/+= t (* s *delta-time*))) ; TODO timelines use scale to speed up/down, tweens use it to normalize delta time; tweens need to end at 1 or loop

  (update-timelines []
    )

  (sync-keyframes []
    )

  (tick-tweens [e Easing
                a Tween
                t Time
                d Duration
                x ^:out ecs/Float]
    (ecs/set x (e t a.from a.to d)))

  (:event Frame) ; keyframe events, could add (now) or remove anims (next frame)
  (:event Add))  ; new anims start with their initial value, this frame is time 0
