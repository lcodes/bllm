(ns bllm.ecs
  (:require [bllm.cli  :as cli]
            [bllm.data :as data]
            [bllm.util :as util :refer [def1]]))

(set! *warn-on-infer* true)

(cli/defgroup config)

;; TODO cvars for buffer sizes, array lengths


;;; Component Structures and Entity Layouts
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 ^:private version 0) ; Incremented on struct changes to mark worlds dirty.
(def1 ^:private structs (js/Map.))
(def1 ^:private layouts (js/Map.))

(defn component
  "Registers a new component structure type."
  []
  )


;;; Simulation Worlds
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



;; TODO lightweight rust-like lifetime semantics -> only tracking high level systems flow
;; - far simpler than entity flow -> just tracking links between a graph of batches now
;; - IDEALLY systems write the full component data (easy to redefine components otherwise)
;; - then doesnt matter if writes back to same buffer, or another one to keep the original
;; - immutability at the frame boundary, with structural sharing of ECS worlds.
(deftype World [version queries blocks systems]
  ICloneable
  (-clone [this]
    this)) ;

;; REPL commands sent to root binding.
;; Simulation binds its own world every frame.
;; Commands (editor, load/save, etc) bind their own world.
;; Time travel/debugging binds a previous version of the world.
;; Speculative evaluation binds a copy-on-write clone of the current world.
(def ^:dynamic ^World *world* nil)

(defn query
  "Upserts a component query."
  []
  )

(defn create
  []
  )

(defn destroy
  []
  )

(defn pre-tick []
  ;; Respond to events accumulated between frames
  ;; - including component type changes
  )

;;; Serialization
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(data/defstore Scene
  )

(data/defstore Prefab
  )
