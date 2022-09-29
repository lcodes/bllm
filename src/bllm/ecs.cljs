(ns bllm.ecs
  (:require [bllm.cli  :as cli]
            [bllm.data :as data]
            [bllm.meta :as meta]
            [bllm.util :as util :refer [def1 ++ += -= |=]]))

(set! *warn-on-infer* true)

(cli/defgroup config)

(def1 ^:private version
  "Incremented on structural changes to mark existing worlds dirty."
  0)

;; TODO once rough ECS is working, rewrite this storage into the engine world.
;; already many patterns where deftypes repeat half the fields, ECS is union type on steroids.
;; - bootstrap problem, need to manually inject the initial definitions without macros
(def1 ^:private index (js/Map.)) ; key -> Def
(def1 ^:private types (js/Map.)) ; id  -> Def
(def1 ^:private links (js/Map.)) ; id  -> (Set id)
(def1 ^:private dirty (js/Set.)) ; ids to update next frame

(comment (js/console.log index)
         (js/console.log types)
         (js/console.log links))


;;; Entity Worlds
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(cli/defvar default-entity-count 5000)

(meta/defbits entity
  block-idx 14  ; 16k blocks
  entity-id 18) ; 256k entities per block
;; TODO version too?

(deftype World [version  ; Used to trigger data remaps on structural change.
                entities ; A lookup of `entity`
                systems  ; Units of work streaming. Batch processors of queries.
                layouts  ; Dynamic entity types. Block owners. Indexes queries.
                queries  ; Entity layout filtering, direct access to data blocks.
                lookup   ; Maps component types to sets of layouts using them.
                blocks   ; Typed storage of data components. Queries index here.
                usage])  ; A bit-array of the entity IDs currently in use.

(defn world
  "Creates an empty ECS `World`."
  ([]
   (world default-entity-count))
  ([initial-entity-count]
   (->World 0
            (js/Uint32Array. initial-entity-count)
            #js []    ; World specific state for all entity component `systems`.
            (js/Map.) ; Entity `layouts` maintain their blocks, resolve queries.
            (js/Map.) ; Component `queries` keep references inside block arrays.
            (js/Map.) ; Type `lookup` to find layouts for a single component.
            #js []    ; `blocks` are indexed by position. Random access only.
            (util/bit-array initial-entity-count))))

;; REPL commands sent to root binding.
;; Simulation binds its own world every frame.
;; Commands (editor, load/save, etc) bind their own world.
;; Time travel/debugging binds a previous version of the world.
;; Speculative evaluation binds a copy-on-write clone of the current world.
(def ^:dynamic ^World *world* nil)

(defn- world-alloc
  "Reserves as many entities in the active world as needed to fill `out`."
  [^js/Uint32Array out]
  (let [mem (.-usage *world*)
        num (.-length out)
        idx 0] ; mutable
    (util/doarray [bits n mem]
      (when (not= 0xffffffff bits)
        (let [o (* n 32)]
          (dotimes [shift 32]
            (let [bit (bit-shift-left 1 shift)]
              (when (zero? (bit-and bits bit))
                (aset out idx (+ o shift))
                (++ idx)
                (|= bits bit)
                (when (= index num)
                  (aset mem n bits) ; partially consumed
                  (util/return num))))))
        (assert (= -1 bits)) ; fully consumed bit-array element
        (aset mem n -1))))
  (assert "TODO grow entities index")) ; TODO world options (ie entities cap, grow size, etc)

(defn- world-free
  "Releases the entity IDs from the active world, taken from `in`."
  [^js/Uint32Array in] ; TODO variant optimized on sorted input? profile
  (let [mem (.-entities *world*)]
    (util/doarray [id in]
      (util/bit-array-clear mem id))))

(comment
  (def test-world (world))
  (js/console.log test-world)
  (let [out (js/Uint32Array. 169)]
    (binding [*world* test-world] (world-alloc out))
    (js/console.log out))
  )


;;; Component Types
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

;; Component types specify how to instantiate data arrays inside entity blocks.
;; They are referenced by queries to match layouts and perform I/O over arrays.
(deftype Component [key  ; Unique ID, array size & options.
                    hash ; Content hash, generated from `defc` to detect live changes.
                    type ; Unique type. Implements component data access.
                    ctor ; Block constructor.
                    init ; Value initializer.
                    ^js/Array ins    ; Input components. (From `:require`)
                    ^js/Array outs]) ; Output components. (From `:target`)

(defn component
  "Registers a new component structure type."
  [hash key type ins outs]
  (let [id (component-id key)]
    ;; Update old component.
    (when-let [existing (.get types id)]
      (when (= hash (.-hash existing))
        (util/return)) ; No changes, idempotent early exit.
      ;; TODO ILiveUpdate protocol
      ;; TODO full update of existing component
      ;; - query invalidation
      ;; - remap existing data (reinitialize, reload, etc -> if struct layout changes, existing data is void)
      (util/doarray [o outs]
        (.delete (.get links o) id)))
    ;; Insert new component.
    (let [c (->Component hash key type nil nil ins outs)] ; TODO ctor init
      (.set index key c)
      (.set types id  c)
      (util/doarray [o ins]
        (-> (util/get-or-new links o js/Set)
            (.add id))))))

;; TODO component array allocation, management & access


;;; Block Layouts
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype Block [index    ; Unique identifier of the block inside its `World`.
                capacity ; Maximum number of entities the block can allocate.
                length   ; Number of used entities, always packed from zero.
                layout   ; Entity layout giving meaning to the component arrays.
                arrays]) ; Component data views. Uses at most one `ArrayBuffer`.

;; Layouts are used to organize component arrays into logical entity blocks.
;; Queries then pull data directly from arrays of the blocks they match on.
(deftype Layout [num-entities num-shared num-empty
                 ^js/Uint16Array types     ; `component-id` elements.
                 ^js/Array       blocks    ; Blocks for this layout.
                 ^js/Array       queries]) ; Active block observers.

(defn- block-new [layout]
  )

(defn- block-add [layout block]
  )

(defn- block-del [layout block]
  )

(defn- ^Block block-for [layout]
  ;; TODO reuse free blocks
  (block-new layout))

(defn- block-update [^Block block count]
  (let [cap (.-capacity block)
        len (.-length   block)]
    (assert (not= count len))
    (assert (<=   count cap))
    (if (zero? count)
      nil ; TODO release block
      (do (cond (= cap count) (block-del (.-layout block) block)
                (= cap len)   (block-add (.-layout block) block))
          (set! (.-length block) count)))))

(defn- block-alloc [^Block block count]
  (let [cap (.-capacity block)
        len (.-length   block)]
    (assert (not= cap len))
    (let [num (js/Math.min count (- cap len))]
      (block-update block (+ len num))
      ;; (+= ) TODO increment entity count in block layout ?
      num)))


;;; Query Views
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype Query [key
                hash
                disabled  ; Component IDs preventing this query from matching.
                required  ; Component IDs required to be present for a match.
                optional  ; Component IDs are either read if present or nil.
                writes    ; Component IDs this query writes to.
                layouts]) ; Layout IDs this query matches with.

(defn query
  []
  )


;;; System Batches
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(meta/defbits ^:transient system-key
  system-id 16)

(meta/defflag system-opts
  {:from 16}
  system-empty  ; Pseudo system without queries, used as anchor points.
  system-group  ; System acting as a scheduling container for other systems.
  system-async  ; System execution is triggered from async events, ie coroutines.
  system-check  ; Performs sanity checks to warn about errors before they happen.
  system-debug) ; Development-only system usually to introspecting other systems.

(deftype System [key   ; Unique identifier and option flags.
                 hash  ; Content hash to detect live changes.
                 ctor  ; State constructor for World instances.
                 data  ; Resolved query IDs. Queries model the flow graph.
                 code] ; Functions associated with every query for this system.
  IHash
  (-hash [_] hash))
;; global frame execution is a materialized view of [state-delta query-function query-params ...]
;; frame execution just streams through that like microcode; reverse polish notation? Forth interesting model here

(defn system
  "Register a system definition. Each system is a composable batch of work in a
  larger frame tick, with feedback possible across frames.

  Systems maintain internal state specific to a `World`. State shared across
  worlds should be made global within their definition namespaces. These are
  the very boundaries of the engine and few in number.

  Work is defined from query functions, similar to methods in a class. They
  specify the data components being read and written to, along with a few more
  constraints to add filtering capabilities. Depending on the query type, its
  function will be called once per layout, block or entity. Instanced components
  are also supported, allowing each data component to vary in access frequency.

  Finally, frame tick ordering is determined from hints specified on systems,
  usually a parent system and preferred position relative to sibblings. These
  form the links of a system graph, which is then topologically sorted."
  [key hash ctor queries code]
  (let [id (system-id key)]
    (when-let [existing (.get types id)]
      ;; TODO replace existing, reinit state, update query ref-counts
      )
    (let [sys (->System key hash ctor nil code)] ; TODO resolve queries
      (.set index key sys)
      (.set types id  sys)
      id)))

;; TODO lightweight rust-like lifetime semantics -> only tracking high level systems flow
;; - far simpler than entity flow -> just tracking links between a graph of batches now
;; - IDEALLY systems write the full component data (easy to redefine components otherwise)
;; - then doesnt matter if writes back to same buffer, or another one to keep the original
;; - immutability at the frame boundary, with structural sharing of ECS worlds.


;;; High-Level Interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- entity-alloc [^Block block index count ids offset]
  (dotimes [n count]
    (let [id (aget ids (+ offset n))
          e  (+ index n)]
      (aset (.-entities block) e id)
      (aset (.-index *world*) id (entity (.-index block) e)))))

(defn entities-into
  "Allocates entity IDs and constructs block storage for their data components."
  [layout ids]
  (let [o 0 ; both mutable
        n (world-alloc ids)]
    (+= (.-num-entities *world*) n)
    (while (pos? n)
      (let [block (block-for layout)
            index (.-length block)
            count (block-alloc block n)]
        (entity-alloc block index count ids o)
        (+= o count)
        (-= n count)))
    ids))

(defn entities-from [layout count]
  (entities-into layout (js/Uint32Array. count)))

(defn entity-from [layout]
  (aget (entities-into util/temp-u32 layout) 0))

(defn entity-remove [ids]
  )

(defn entity-delete [id]
  (aset util/temp-u32 0 id)
  (entity-remove util/temp-u32))

;; TODO adding/removing components potentially changes layout, moves array elements
(defn add [])
(defn del [])


;;; Builtin Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(bllm.ecs/defc Name
  "Display name of the entity. Optional."
  {:type :str})

(bllm.ecs/defc Tags
  "Semantic tags of the entity."
  {:type :u32})

;; TODO as a fundamental system
(defn pre-tick []
  ;; Respond to events accumulated between frames
  ;; - including component type changes
  )

;; TODO global execution order?
;; - got systems graph, got worlds with system instances, global topsort ftw!
;; - batching across simulation worlds!
;; - SystemA world1 SystemA world2 SystemB world1 and so on


;;; Serialization
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(data/defstore Save
  "Save State!"
  ;; TODO each save entry is a snapshot followed by a stream of deltas?
  ;; - periodic snapshots as the stream gets longer, max length, turn off, long term cloud storage
  ;; -
  )

(data/defstore Scene
  "Generalized scene data for the entirety of entities. Load adds to a `World`."
  )

(data/defstore Prefab
  "Specialized scene data for a collection of entities. Multiplies the `World`."
  )
