(ns bllm.ecs
  (:require [bllm.cli  :as cli]
            [bllm.data :as data]
            [bllm.meta :as meta]
            [bllm.util :as util :refer [def1]]))

(set! *warn-on-infer* true)

(cli/defgroup config)

;; TODO cvars for buffer sizes, array lengths


;;; Component Structures and Entity Layouts
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(meta/defbits component-key
  "A component's key acts as a unique identifier, its element count and options.

  See `component-opts` to use the key as a bitfield."
  component-id    16
  component-array 4) ; Up to 16 inline elements.

(meta/defflag component-opts
  "Flags complementing the `component-key` constituents."
  {:from 20}
  component-standalone ; Component doesn't affect entity layout,
  component-shared     ; Component belongs to entity layout, not component block.
  component-buffer     ; Component is stored in an `ArrayBuffer`, shared or not.
  component-wrapper    ; Access the component through the block's `DataView`.
  component-typed      ; Access the component through a block's `TypedArray`.
  component-empty)     ; Component has no data, acting as an entity marker only.

(deftype Component [key    ; Unique ID, array size & options.
                    type   ; Unique type. Implements component data access.
                    ;;ctor   ; Block constructor.
                    ;;init   ; Value initializer.
                    ins    ; Input components. (From `:require`)
                    outs]) ; Output components. (From `:target`)

(def1 ^:private version
  "Incremented on structural changes to mark existing worlds dirty."
  0)

(def1 ^:private component-index (js/Map.)) ; component-key -> Component
(def1 ^:private component-types (js/Map.)) ; component-id  -> Component
(def1 ^:private component-links (js/Map.)) ; component-id  -> (Set component-id)

(comment (js/console.log component-index)
         (js/console.log component-types)
         (js/console.log component-links))

(defn component
  "Registers a new component structure type."
  [key type ins outs]
  (let [id (component-id key)]
    ;; Update old component.
    (when-let [existing (.get component-types id)]
      ;; TODO full update of existing component
      ;; - definition hash -> direct change check
      ;; - query invalidation
      ;; - remap existing data (reinitialize, reload, etc -> if struct layout changes, existing data is void)
      (util/doarray [o outs]
        (.delete (.get component-links o) id)))
    ;; Insert new component.
    (let [c (->Component key type ins outs)]
      (.set component-index key c)
      (.set component-types id  c)
      (util/doarray [o ins]
        (-> (util/get-or-new component-links o js/Set)
            (.add id))))))


;;; Simulation Worlds
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



;; TODO lightweight rust-like lifetime semantics -> only tracking high level systems flow
;; - far simpler than entity flow -> just tracking links between a graph of batches now
;; - IDEALLY systems write the full component data (easy to redefine components otherwise)
;; - then doesnt matter if writes back to same buffer, or another one to keep the original
;; - immutability at the frame boundary, with structural sharing of ECS worlds.
(deftype World [version
                entities
                queries
                blocks
                systems]
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
