(ns bllm.core
  "Wires the low-level engine sub-systems."
  (:require [bllm.ai    :as ai]
            [bllm.anim  :as anim]
            [bllm.audio :as audio]
            [bllm.data  :as data]
            [bllm.disp  :as disp]
            [bllm.draw  :as draw]
            [bllm.ecs   :as ecs]
            [bllm.gpu   :as gpu]
            [bllm.input :as input]
            [bllm.meta  :as meta]
            [bllm.net   :as net]
            [bllm.scene :as scene]
            [bllm.time  :as time]
            [bllm.util  :as util :refer [def1]]
            [bllm.video :as video]
            [bllm.wgsl  :as wgsl]
            [bllm.world :as world]))

(set! *warn-on-infer* true)


;;; Application Life-Cycle
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init
  "Executes the initialization logic of internal sub-systems independently."
  []
  (js/Promise.all
   #js [(meta/init)
        (data/init)
        (audio/init)
        (gpu/init)
        (disp/init)
        (input/init)]))

(defn start
  "Executes post-initialization logic. Safe time to wire systems together."
  []
  (js/Promise.all
   #js [(disp/start)
        (time/start)]))

(defn pre-tick
  "Ticks the input systems to evaluate the current state of the outside world."
  []
  ;; Time-independent input systems. Flushes into low-level sub-systems.
  (data/pre-tick) ; Changed schemas and serialization, load or import events.
  (disp/pre-tick) ; Resized canvas has to resizes its linked render targets.
  (wgsl/pre-tick) ; Defined shader nodes and first time module compilation.
  (gpu/pre-tick)  ; Defined resources created when the GPU device is ready.
  (ecs/pre-tick)  ; Defined component types and structural data migrations.

  ;; Time-dependent input systems. Flushes into high-level sub-systems.
  (time/pre-tick)  ; Binds the dimension of time as a transaction.
  (input/pre-tick) ; Binds the user input as a sequence of actions.
  (net/pre-tick)   ; Binds the received network data, sends inputs to host.
  )

(defn tick
  "Ticks the simulation systems to compute the new internal frame of reference."
  []
  ;; TODO these can "easily" run off different Workers concurrently
  ;; - don't want to care, let ECS graph drive this
  ;; - need better control over build output -> separate systems in modules for workers to lazy load
  ;; - app will grow large, workers are specialized runners; like cells on the PS3, similar "limits"

  ;;(scene/tick) ; Deterministic updates based on hierarchy in transformation spaces.
  ;;(world/tick) ; Deterministic updates based on fixed-time and physics integration.
  ;;(anim/tick)  ; Deterministic updates based on delta-time and logical computation.
  ;;(ai/tick)    ; Deterministic updates based on lower-time and pseudo intelligence.

  ;; TODO also where most "game" systems will end up, regardless of application
  ;; - pre-tick/post-tick reserved for side effects, not many of these systems

  ;; JavaScript also being what it is, might be interesting to model system updates
  ;;   as "one-at-a-time", but through macroexpansion really implement "all-at-once"
  ;;   - hide all the ugly scaffholding and tricks to avoid allocations, indirections
  ;;   - definitely offer different layers of such handlers (like re-frame events do)
  ;;     - not everything can be simplified; can also scan the ECS buffers in a system
  )

(defn post-tick
  "Ticks the output systems to execute the new frame upon the outside world."
  []
  (audio/post-tick) ; Creates and update AudioNodes, collects playback events.
  (video/post-tick) ; Updates video streams and syncs their textures or channels.
  (draw/post-tick)  ; Renders the active cameras on their associated viewports.
  (net/post-tick)   ; Sends non-deterministic outputs to peers.
  (input/post-tick) ; Resets accumulators to collect inter-frame events.
  )
