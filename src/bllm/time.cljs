(ns bllm.time
  "Sandboxed time. Because of Spectre we're down to millisecond precision."
  (:require [bllm.ecs  :as ecs]
            [bllm.meta :as meta]
            [bllm.util :as util :refer [def1 ++ +=]]))

(meta/defstruct State
  "Common time definition used by all time related systems."
  unscaled-delta :f32
  unscaled-now   :f64
  scale :f32  ; Current time scale, multiplies input time.
  delta :f32  ; Relative time from the last frame.
  now   :f64  ; Absolute time from the beginning of the world.
  seq   :u32) ; Sequence number, index of the current tick.

(defn tick [^State state delta]
  (set! (.-unscaled-delta state) delta)
  (+=   (.-unscaled-now   state) delta)
  (let [d (* (.-scale state) delta)]
    (set! (.-delta state) d)
    (+=   (.-now   state) d)
    (++   (.-seq   state))))

(ecs/defsys frame
  "Frame time represents a continuous logical timeline. Frame duration can vary.

  Its delta is used to smooth values relative to real time, such as animations."
  {:init State}
  (tick []
    ))

(ecs/defsys ^:manual fixed
  "Fixed time represents a discrete physical timeline. Delta time never changes.

  The delta is used to compute values predicted over time, such as simulations."
  {:init State}
  (tick []
    ))

(ecs/defsys ^:manual share
  "Share time represents a networked virtual timeline. World sync between peers."
  {:init State}
  (tick []
    ))

(ecs/defsys ^:manual slice
  "Slice time represents a computational timeline. Hard budgets actor processes."
  {:init State}
  (tick []
    ))

;; global system needed here -> global frame number, unscaled now, etc
;; - same in other systems; audio device, gpu device, db assets, etc
;; - global state fine, still want systems graph across worlds
;; - think of global state as pseudo world, at the root of the world tree
;; - entities are worlds, assets, etc -> high level sees one data access API






;; TODO deprecated now

(def1 frame-number 0)

(def1 unscaled-now   0)
(def1 unscaled-delta 0)

(defn pre-tick []
  (util/++ frame-number)
  (let [time (js/performance.now)]
    (set! unscaled-delta (- time unscaled-now))
    (set! unscaled-now time))
  ;; TODO derived, scaled time
  )

(defn start []
  (pre-tick)) ; Ensures the first frame doesn't have a large delta.

(defn zero!
  "Resets global time to absolute zero."
  []
  (set! unscaled-now 0)
  (set! unscaled-delta 0)
  ;; TODO
  )
