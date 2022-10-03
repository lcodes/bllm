(ns bllm.ecs
  (:require-macros [bllm.ecs])
  (:require [bllm.cli  :as cli]
            [bllm.data :as data]
            [bllm.meta :as meta]
            [bllm.util :as util :refer [def1 defn* ++ += -= |=]]))

(set! *warn-on-infer* true)

(cli/defgroup config)

(def1 ^:private version
  "Incremented on structural changes to mark existing worlds dirty."
  0)

(def1 ^:private id->index (js/Map.)) ; id -> index
(def1 ^:private id->links (js/Map.)) ; id -> (Set id)
(def1 ^:private id->hash  (js/Map.)) ; id -> generated content hash

(comment (js/console.log id->index)
         (js/console.log id->links)
         (js/console.log id->hash))


;;; Entity Worlds
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(cli/defvar default-entity-count
  "How many entities to reserve storage for when creating a new generic world."
  1000)

(meta/defbits entity-key
  "Logical handle to a specific entity. Remains valid after structural changes."
  entity-index 24 ; 16m entities, reused before pushing the highest ID
  entity-ver   8) ; 256 versions, odds of collisions across cycles should be low

(meta/defbits place-key
  "Physical handle to a specific entity. Invalidated following a layout change."
  block-index 14  ; 16k blocks
  place-index 18) ; 256k entities per block

(deftype World [total   ; Number of allocated entities in this World.
                version ; Used to trigger data remaps on structural changes.
                systems ; Units of work streaming. Batch processors of queries.
                queries ; Entity class filtering, direct access to data blocks.
                groups  ; Dynamic entity types. Block owners. Indexes queries.
                lookup  ; An array indexing the group for every single block.
                blocks  ; Typed storage of data components. Queries index here.
                usage   ; A bit-array of the entity IDs currently in use.
                place   ; Lookup of `entity` IDs to block and place indices.
                check]) ; Current version numbers of allocated entities.

(defn world
  "Creates an empty ECS `World`."
  ([]
   (world default-entity-count))
  ([initial-entity-count]
   (->World 0 0
            #js []    ; World specific state for all entity component `systems`.
            (js/Map.) ; Component `queries` keep references inside block arrays.
            (js/Map.) ; Entity `groups` maintain their blocks, resolve queries.
            #js []    ; Block `lookup` to access the layout of any given block.
            #js []    ; `blocks` are indexed by position. Random access only.
            (util/bit-array initial-entity-count)    ; usage
            (util/u32-array initial-entity-count -1) ; place
            (js/Uint8Array. initial-entity-count)))) ; check

;; REPL commands sent to root binding.
;; Simulation binds its own world every frame.
;; Commands (editor, load/save, etc) bind their own world.
;; Time travel/debugging binds a previous version of the world.
;; Speculative evaluation binds a copy-on-write clone of the current world.
(def ^:dynamic ^World *world* nil)

(defn- world-alloc
  "Reserves as many entities in the active world as needed to fill `out`."
  [^js/Uint32Array out]
  (let [chk (.-check *world*)
        mem (.-usage *world*)
        num (.-length out)
        idx 0] ; mutable
    (util/doarray [bits n mem]
      (when (not= 0xffffffff bits)
        (let [o (* n 32)]
          (dotimes [shift 32]
            (let [bit (bit-shift-left 1 shift)]
              (when (zero? (bit-and bits bit))
                (let [id  (+ o shift)
                      ver (inc (aget chk id))]
                  (aset chk id ver)
                  (aset out idx (entity-key id ver)))
                (++ idx)
                (|= bits bit)
                (when (= idx num)
                  (aset mem n bits) ; partially consumed
                  (util/return num))))))
        (assert (= -1 bits)) ; fully consumed bit-array element
        (aset mem n -1))))
  (assert "TODO grow entities index")) ; TODO world options (ie entities cap, grow size, etc)

(defn- world-free
  "Releases the entity IDs from the active world, taken from `in`."
  [^js/Uint32Array in]
  (let [loc (.-place *world*)
        mem (.-usage *world*)]
    (util/doarray [id in]
      (aset loc id -1)                 ; Invalidate the block index.
      (util/bit-array-clear mem id)))) ; Mark the entity ID for reuse.

(comment
  (def test-world (world))
  (js/console.log test-world)
  (let [out (js/Uint32Array. 9)]
    (binding [*world* test-world] (world-alloc out))
    (js/console.log out))
  )


;;; Component Types
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment (js/console.log (.slice components 0 components-num)))

(def1 ^:private components-num 0)
(def1 ^:private components (js/Array. 200))

(defn- ^Component component-get
  "Returns an existing `Component` given its `component-index`."
  [idx]
  (aget components idx))

(meta/defbits component-key
  "A component's key acts as it's definition index, its element count and flags.

  See `component-opts` to use the key as a bitfield."
  component-index 16 ; Up to 65k component types.
  component-array 4) ; Up to 16 inline elements.

(meta/defflag component-opts
  "Flags complementing the `component-key` constituents."
  {:from 20} ; TODO from constant expr (component-key-bits ?)
  component-static  ; Component doesn't affect entity layout, separate store.
  component-shared  ; Component belongs to a components block, not an entity.
  component-buffer  ; Component stored in `ArrayBuffer`, `SharedArrayBuffer`.
  component-wrapper ; Access the component through the block's `DataView`.
  component-typed   ; Access the component through a block's `TypedArray`.
  component-empty)  ; Component has no data, acting as an entity tag only.

;; Component types specify how to instantiate data arrays inside entity blocks.
;; They are referenced by queries to match layouts and perform I/O over arrays.
(deftype Component [key    ; Index, array size & option flags.
                    type   ; Unique type. Implements component data access.
                    ctor   ; Array constructor.
                    init   ; Value initializer.
                    ins    ; Input components. (From `:require`)
                    outs]) ; Output components. (From `:target`)

(defn component
  "Registers a new component structure type."
  [id hash opts size type ctor init ins outs]
  (if (= hash (.get id->hash id))
    (.-key (component-get (.get id->index id)))
    (do (.set id->hash id hash)
        (let [cur (.get id->index id)
              idx (or cur (++ components-num))]
          (if-not cur
            ;; Index new component. (Small indirection now, no map lookups in ticks.)
            (.set id->index id idx)
            ;; Dirty old component.
            (let [existing (component-get idx)]
              ;; TODO ILiveUpdate protocol
              ;; TODO full update of existing component
              ;; - query invalidation
              ;; - remap existing data (reinitialize, reload, etc -> if struct layout changes, existing data is void)
              #_(util/doarray [o (.-outs existing)]
                  (.delete (.get links o) id))))
          ;; Insert new component.
          (let [k (-> (component-key idx size) (bit-or opts))
                c (->Component k type ctor init ins outs)]
            (aset components idx c)
            #_(util/doarray [o ins]
                (-> (util/get-or-new links o js/Set)
                    (.add id)))
            k)))))

;; TODO component array allocation, management & access


;;; Entity Classes, World Groups and State Blocks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(meta/defbits class-key
  class-index     20  ; 1m possible entity classes.
  class-num-shared 6  ; 64 shared components per class.
  class-num-empty  6) ; 64 empty components per class.

(meta/defbits block-key
  block-world-index 20  ; 1m entity blocks per world.
  block-group-group 12) ; 4k blocks per entity class.

;; Classes are used to organize component arrays into logical entity blocks.
(deftype Class [key          ; class index & special component counts.
                components]) ; `component-id` elements. Shared first, empty last.

;; Groups are layout instances for a specific world, responsible of blocks.
;; Queries then pull data directly from arrays of the blocks they match on.
(deftype Group [total     ; Number of entities queries match on.
                class     ; Index to class data shared across worlds.
                blocks    ; Indices to blocks using the layout.
                queries]) ; Active block observers.

;; Blocks provide storage for entity batches. Data-oriented-design as JS allows.
(deftype Block [key      ; Index of the block in world and group arrays.
                size     ; Maximum number of entities the block can allocate.
                length   ; Number of used entities, always packed from zero.
                parent   ; Entity group giving meaning to the component arrays.
                lookup   ; Entity lookup, maps `place-index` to world `entity`.
                arrays   ; Component data views. Uses at most one `ArrayBuffer`.
                shared]) ; Instanced components. Each item is a block singleton.

(def1 ^:private class-num 0)
(def1 ^:private classes (js/Array. 100))

(def ^:private class-build-n 0)
(def ^:private class-builder util/temp-u32)

(defn ^Class class-get [idx]
  (aget classes idx))

(defn- class-build-add! [k]
  (let [x (bit-or k (cond (pos? (bit-and k component-shared)) 0
                          (pos? (bit-and k component-empty))  0x40000000
                          :else                               0x20000000))]
    (loop [i class-build-n]
      (if (zero? i)
        (aset class-builder i x)
        (let [j (dec i)
              y (aget class-builder j)]
          (cond (= x y) (throw (ex-info "Duplicate component in class"
                                        {:c (component-get
                                             (component-index x))}))
                (> x y)   (aset class-builder i x)
                :else (do (aset class-builder i y) (recur j))))))
    (++ class-build-n)))

(comment (set! class-build-n 0) class-builder
         (class-build-add! bllm.scene/World)
         (class-build-add! Tags)
         (class-build-gen!))

(defn- class-build-gen! []
  (loop [n 0
         h 0]
    (if (>= n class-build-n)
      h
      (let [id (component-index (aget class-builder n))]
        (->> (bit-and id 0xf)
             (bit-shift-left id)
             (bit-xor h)
             (recur (inc n)))))))

(defn- class-sum [keys from flag]
  (loop [n from
         c 0]
    (if (= n (alength keys))
      c
      (let [k (aget keys n)]
        (recur (inc n) (if (zero? (bit-and k flag))
                         c
                         (inc c)))))))

(defn- class-for [component-keys from]
  (set! class-build-n 0)
  (let [len (alength component-keys)
        num (- len from)]
    ;; Make sure the class builder is large enough.
    (when (< (alength class-builder) num)
      (js/console.warn "growing ecs class builder to" num)
      (set! class-builder (js/Uint16Array. num)))
    ;; Sort the components, so A+B and B+A are the same.
    (util/dorange [n from len]
      (let [k (aget component-keys n)]
        (assert (number? k))
        (when (zero? (bit-and k component-static))
          (class-build-add! k))))
    ;; Resolve the existing class or create a new one.
    (let [id (class-build-gen!)]
      (if-let [idx (.get id->index id)]
        (class-get idx)
        (let [idx (++ class-num)
              key (class-key idx
                             (class-sum component-keys from component-shared)
                             (class-sum component-keys from component-empty))
              ids (js/Uint16Array. class-build-n) ; TODO from bump alloc?
              cls (->Class key ids)]
          ;; Gather component indices, discard sort bits.
          (dotimes [n class-build-n]
            (->> (aget class-builder n)
                 (component-index)
                 (aset ids n)))
          ;; TODO match against existing queries
          (.set id->index id idx)
          (aset classes idx cls)
          cls)))))

(defn* class []
  (class-for (js-arguments) 0))

(comment (js/console.log (class Name)
                         (class Name Tags)
                         (class Tags Name))
         (js/console.log (.slice classes 0 class-num)))

(defn- group-for
  "Get the group corresponding to the given class key in the current world."
  [k]
  (let [^js/Map groups (.-groups *world*)]
    (or (.get groups k)
        (let [g (->Group 0 (class-index k) #js [] #js [])] ; TODO to uint16array, will need resize handling
          (.set groups k g)
          g))))

(defn- block-add [group block]
  )

(defn- block-del [group block]
  )

(defn- block-new [components group]
  )

(defn- ^Block block-for [components group]
  ;; TODO reuse free blocks
  (block-new components group))

(defn- block-update [^Block block count]
  (let [cap (.-size   block)
        len (.-length block)]
    (assert (not= count len))
    (assert (<=   count cap))
    (if (zero? count)
      nil ; TODO release block
      (do (cond (= cap count) (block-del (.-layout block) block)
                (= cap len)   (block-add (.-layout block) block))
          (set! (.-length block) count)))))

(defn- block-alloc [^Block block count]
  (let [cap (.-size   block)
        len (.-length block)]
    (assert (not= cap len))
    (let [num (js/Math.min count (- cap len))]
      (block-update block (+ len num))
      ;; (+= ) TODO increment entity count in block layout ?
      num)))

(defn- entity-alloc [^Block block index count ids offset]
  (let [b (block-world-index (.-key block))
        l (.-lookup block)
        i (.-index *world*)]
    (dotimes [n count]
      (let [id (aget ids (+ offset n))
            e  (+ index n)
            k  (place-key b e)]
        (aset l e id)
        (aset i id k)))))


;;; Query Views
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype Query [key
                hash
                disabled  ; Component IDs preventing this query from matching.
                required  ; Component IDs required to be present for a match.
                optional  ; Component IDs are either read if present or nil.
                writes    ; Component IDs this query writes to.
                layouts]) ; Layout IDs this query matches with.

(deftype View [])

(defn query
  []
  )


;;; System Batches
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(meta/defbits ^:transient system-key
  system-id 20)

(meta/defflag system-opts
  {:from 20}
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
  [id hash opts ctor queries code]
  #_(let [id (system-id key)]
    (when-let [existing (.get types id)]
      ;; TODO replace existing, reinit state, update query ref-counts
      )
    (let [sys (->System key hash ctor nil code)] ; TODO resolve queries
      (.set types id sys)
      id)))

;; TODO lightweight rust-like lifetime semantics -> only tracking high level systems flow
;; - far simpler than entity flow -> just tracking links between a graph of batches now
;; - IDEALLY systems write the full component data (easy to redefine components otherwise)
;; - then doesnt matter if writes back to same buffer, or another one to keep the original
;; - immutability at the frame boundary, with structural sharing of ECS worlds.


;;; High-Level Interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn entities-into
  "Allocates entity IDs and constructs block storage for their data components."
  [^Class class ids]
  (let [c (.-components class)
        g (group-for (.-key class))
        o 0 ; both mutable
        n (world-alloc ids)]
    (+= (.-total *world*) n)
    (+= (.-total g) n)
    (while (pos? n)
      (let [block (block-for c g)
            index (.-length block)
            count (block-alloc block n)]
        (entity-alloc block index count ids o)
        (+= o count)
        (-= n count)))
    ids))

(defn entities-from [class count]
  (entities-into class (js/Uint32Array. count)))

(defn entity-from [class]
  (aget (entities-into class util/unit-u32) 0))

(defn entity-remove [ids]
  )

(defn entity-delete [id]
  (aset util/unit-u32 0 id)
  (entity-remove util/unit-u32))

(defn* entity
  "Allocates a single entity given its data component types."
  []
  (entity-from (class-for (js-arguments) 0)))

(defn* entities
  "Allocates many entities given their data component types."
  [count]
  (entities-from (class-for (js-arguments) 1) count))

;; TODO adding/removing components potentially changes layout, moves array elements
(defn* add [] nil)
(defn* del [] nil)


;;; Builtin Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- name-get [e]
  ;; TODO find `NameIndex` in world (def as direct index? need sparse lookup)
  ;; - return name from system
  )

(defn- name-set [e name]
  ;; - update name in system
  )

;; TODO having get/set make it an empty component
(bllm.ecs/defc Name
  "Unique display name of the entity. Optional, unnamed entity if missing."
  {:type :str
   :get  name-get
   :set  name-set})

(bllm.ecs/defsys NameIndex
  {:state #js []})

(bllm.ecs/defc Tags
  "Semantic tags of the entity. Optional, assume 0 if missing."
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
