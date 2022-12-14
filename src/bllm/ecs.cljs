(ns bllm.ecs
  (:require-macros [bllm.ecs :as ecs])
  (:require [bllm.cli  :as cli]
            [bllm.data :as data]
            [bllm.meta :as meta]
            [bllm.util :as util :refer [def1 defn* === !== ++ += -= |=]]))

(set! *warn-on-infer* true)

(cli/defgroup config)

(cli/defvar default-world-entities
  "How many entities to reserve storage for when creating a new generic world."
  1000)

(cli/defvar default-world-blocks
  100)

(cli/defvar default-group-blocks
  10)

(cli/defvar block-min-cap 32)
(cli/defvar block-max-cap 4096) ; 4096 hard limit from 12-bit indexing

;; TODO handle id/hash collisions -> better hashing, kind bits, ns bits, etc
(def1 ^:private id->hash (js/Map.))

(def1 ^:private id->index (js/Map.))

(def1 ^:private index->links (js/Map.))

(def1 ^:private classes-num 0)
(def1 ^:private classes (js/Array. 400))

(def1 ^:private components-num 0) ; TODO rename to structs? CSS is now Class-Struct-System, naming is hard
(def1 ^:private components (js/Array. 200))

(def1 ^:private queries-num 0)
(def1 ^:private queries (js/Array. 200))

(def1 ^:private systems-num 0)
(def1 ^:private systems (js/Array. 100))

(comment (js/console.log "data" id->index
                         "hash" id->hash
                         "rels" index->links)
         (js/console.log *world* version)

         (js/console.log (.slice components 0 components-num))
         (js/console.log (.slice classes    0 classes-num   ))
         (js/console.log (.slice queries    0 queries-num   ))
         (js/console.log (.slice systems    0 systems-num   )))

(meta/defbits class-key
  class-index     19  ; 512k class definitions.
  class-linked     1  ; Whether this class contains linked entities.
  class-num-shared 6  ; Up to 64 shared components per class.
  class-num-empty  6) ; Up to 64 empty components per class.

(meta/defbits group-limits
  group-blocks 12  ; Allocated. May not all be full, or empty.
  group-allocs 20) ; 1m entities across all blocks.

(meta/defbits group-counts
  group-full-blocks 12  ; Index of the first free block. Matches group limits.
  group-class-index 19) ; 512k possible classes. Matches class key.

(meta/defbits block-key
  block-world-index 20  ; 1m entity blocks per world. Matches place limit.
  block-group-index 12) ; 4k possible blocks per entity class.

(meta/defbits block-cap
  block-length 12 ; 4k possible entities per block. Matches place limit.
  block-allocs 12)

(meta/defbits entity-key
  "Logical handle to a specific entity. Remains valid after structural changes."
  entity-index 22 ; 4m entities, reused before pushing the highest ID
  entity-ver   8) ; 256 versions, odds of collisions across cycles should be low

(meta/defbits place-key
  "Physical handle to a specific entity. Invalidated following a layout change."
  block-index 20  ; 1m blocks per world
  place-index 12) ; 4k entities per block

(meta/defbits ^:transient query-key
  query-index 20) ; 1m query definitions.

(meta/defflag query-flags
  {:from 20}
  query-filter-output)

(meta/defbits component-key
  "A component's key acts as it's definition index, its element count and flags.

  See `component-opts` to use the key as a bitfield."
  component-index 16 ; 65k component definitions.
  component-array 4) ; 16 inline elements.

(meta/defflag component-opts
  "Flags complementing the `component-key` constituents."
  {:from 20} ; TODO from constant expr (component-key-bits ?)
  component-static  ; Component doesn't affect entity layout, separate store. TODO rethink this
  component-system  ; Component belongs to a system, not deleted with entity.
  component-shared  ; Component belongs to a components block, not an entity.
  component-buffer  ; Component stored in `ArrayBuffer`, `SharedArrayBuffer`.
  component-wrapper ; Access the component through the block's `DataView`. TODO
  component-typed   ; Access the component through a block's `TypedArray`. TODO
  component-empty   ; Component has no data, acting as an entity tag only.
  component-linked) ; Component is a direct place link between two entities.

(meta/defbits component-mem
  "Memory requirements for components with values stored in an `ArrayBuffer`."
  component-align 4
  component-size 12)

(meta/defbits query-info
  query-num-disabled 8  ; Component IDs preventing this query from matching.
  query-num-required 8  ; Component IDs required to be present for a match.
  query-num-optional 8  ; Component IDs are either read if present or nil.
  query-num-written  8) ; Component IDs this query writes to.

(meta/defbits ^:transient system-key
  system-id 20) ; 1m system definitions.

(meta/defflag system-opts
  {:from 20}
  system-empty  ; Pseudo system without queries, used as anchor points.
  system-group  ; System acting as a scheduling container for other systems.
  system-async  ; System execution is triggered from async events, ie coroutines.
  system-check  ; Performs sanity checks to warn about errors before they happen.
  system-debug) ; Development-only system usually to introspecting other systems.

(deftype World [total   ; Number of allocated entities in this World.
                version ; Used to trigger data remaps on structural changes.
                systems ; Units of work streaming. Batch processors of queries.
                queries ; Entity class filtering, direct access to data blocks.
                groups  ; Dynamic entity types. Block owners. Indexes queries.
                states  ; An array indexing the state for every single system.
                lookup  ; An array indexing the group for every single block.
                blocks  ; Typed storage of data components. Queries index here.
                index   ; A bit-array of the block IDs currently in use.
                usage   ; A bit-array of the entity IDs currently in use.
                place   ; Lookup of `entity` IDs to block and place indices.
                check]) ; Current version numbers of allocated entities.

;; Classes are used to organize component arrays into logical entity blocks.
(deftype Class [key        ; class index & special component counts.
                components ; `component-id` elements. Shared first, empty last.
                queries])  ; queries matching on groups & blocks of this class.

;; Groups are class instances for a specific world, responsible for blocks.
;; Queries then pull data directly from arrays of the blocks they match on.
(deftype Group [limits  ; Total number of entities and blocks for this group.
                counts  ; Index to class data and to the first free block.
                blocks  ; Indices to all blocks made from this group's class.
                views]) ; Active block observers.

;; Blocks provide storage for entity batches. Data-oriented-design as JS allows.
(deftype Block [key      ; Index of the block in world and group arrays.
                cap      ; Packed count and capacity of allocated entities.
                lookup   ; Entity lookup, maps `place-index` to world `entity`.
                arrays   ; Component data views. Uses at most one `ArrayBuffer`.
                shared]) ; Instanced components. Each item is a block singleton.

;; Component types specify how to instantiate data arrays inside entity blocks.
;; They are referenced by queries to match layouts and perform I/O over arrays.
(deftype Component [key    ; Index, array size & option flags.
                    mem    ; Memory sizes, per entity and per block.
                    type   ; Unique type. Implements component data access.
                    ctor   ; Array constructor.
                    init   ; Value initializer.
                    ins    ; Input components.
                    outs]) ; Output components.

(deftype Query [key
                info
                components
                classes]) ; Class IDs this query matched with.

(deftype View []) ; TODO instanced query for specific world

(deftype System [key    ; Unique identifier and option flags.
                 hash   ; Content hash to detect live changes.
                 ctor]) ; State constructor for World instances.

;; TODO system graph representation (parent, siblings)

(def1 ^:private version
  "Incremented on structural changes to mark existing worlds dirty."
  0)

;; REPL commands sent to root binding.
;; Simulation binds its own world every frame.
;; Commands (editor, load/save, etc) bind their own world.
;; Time travel/debugging binds a previous version of the world.
;; Speculative evaluation binds a copy-on-write clone of the current world.
(def1 ^:dynamic ^World *world* nil)

(defn ^Class class-get
  [idx]
  (aget classes idx))

(defn ^Block block-get
  [idx]
  (aget (.-blocks *world*) idx))

(defn ^Component component-get
  "Returns an existing `Component` given its `component-index`."
  [idx]
  (aget components idx))

(defn ^Query query-get
  [idx]
  (aget queries idx))

(defn ^System system-get
  [idx]
  (aget systems idx))

;; TODO "placed" blocks: guaranteed not to move, always changes layout in batch
;; - any uses? entities inside can still move, unless that can be enforced too?
;; - ie static scene content; already chunked by region and matches load units.
;; - adding/removing tags to perform system updates -> no existing blocks / too small.
;;
;; not for all content, but good to have when needed


;;; Entity Worlds
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn world
  "Creates an empty ECS `World`."
  ([]
   (world default-world-entities default-world-blocks))
  ([initial-entity-count initial-block-count]
   (let [ec (util/align 31 initial-entity-count) ; Always fill bit-array words
         bc (util/align 31 initial-block-count)] ; in `index` and `usage`.
     (->World 0 0
              #js []    ; Active entity `systems`, directly holds query functions.
              (js/Map.) ; Component `queries` keep references inside block arrays.
              (js/Map.) ; Class `groups` maintain their blocks, resolve queries.
              (js/Array. systems-num) ; System `states` resolved by system index.
              (js/Array. bc) ; Group `lookup` for every block.
              (js/Array. bc) ; Entity `blocks` store components.
              (util/bit-array bc)     ; index
              (util/bit-array ec)     ; usage
              (util/u32-array ec -1)  ; place
              (js/Uint8Array. ec))))) ; check

(defn- world-block
  "Reserves a `Block` index in the current world."
  []
  (let [mem (.-index *world*)]
    (util/doarray [bits n mem]
      (when (not= 0xffffffff bits)
        (dotimes [shift 32]
          (let [bit (bit-shift-left 1 shift)]
            (when (zero? (bit-and bits bit))
              (aset mem n (bit-or bits bit))
              (util/return (+ (* n 32) shift)))))))
    (assert false "TODO block index full")))

(defn- world-block-free
  "Releases a `Block` index and associated references."
  [idx]
  (aset (.-blocks *world*) idx nil)
  (util/bit-array-clear (.-index *world*) idx))

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

(defn- entity-alloc [^Block block index count ids offset]
  (let [b (block-world-index (.-key block))
        l (.-lookup block)
        p (.-place *world*)]
    (dotimes [n count]
      (let [id (entity-index (aget ids (+ offset n)))
            e  (+ index n)
            k  (place-key b e)]
        (aset l e id)
        (aset p id k)))))

(defn- query*class [^Query query ^Class class]
  (let [^js/Uint16Array
        ids  (.-components class)
        comp (.-components query)
        info (.-info       query)
        n-disabled (query-num-disabled info)
        n-required (query-num-required info)]
    (util/dorange [n 0 n-disabled]
      (when-not (.includes ids (aget comp n))
        (util/return false)))
    (util/dorange [n n-disabled (+ n-disabled n-required)]
      (when (.includes ids (aget comp n))
        (util/return false)))
    true))


;;; Entity Component Definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn component
  "Registers a new component structure type."
  [id hash opts align size array-size type ctor init ins outs]
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
          (let [m (component-mem align size)
                k (-> (component-key idx array-size) (bit-or opts))
                c (->Component k m type ctor init ins outs)]
            (aset components idx c)
            #_(util/doarray [o ins]
                (-> (util/get-or-new links o js/Set)
                    (.add id)))
            k)))))

;; TODO component array allocation, management & access


;;; Entity Class Definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private class-build-n 0)
(def ^:private class-builder util/temp-u32)

(defn- class-build-add [k]
  (let [x (bit-or k (cond (pos? (bit-and k component-shared)) 0x20000000
                          (pos? (bit-and k component-empty))  0x40000000 ; Last
                          :else                               0))] ; First
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

(defn- class-build-gen []
  (loop [n 0 h 0]
    (if (= n class-build-n)
      h
      (let [id (component-index (aget class-builder n))]
        (->> (bit-and id 0xf)
             (bit-shift-left id)
             (bit-xor h)
             (recur (inc n)))))))

(comment (set! class-build-n 0) class-builder
         (class-build-add bllm.scene/World)
         (class-build-add Tags)
         (class-build-gen))

(defn- class-sum [keys from flag]
  (let [end (alength keys)]
    (loop [n from c 0]
      (if (= n end)
        c
        (let [k (aget keys n)]
          (recur (inc n) (if (zero? (bit-and k flag))
                           c
                           (inc c))))))))

(defn- class-bit [keys from flag]
  (let [end (alength keys)]
    (loop [n from]
      (if (= n end)
        false
        (if (pos? (bit-and (aget keys n) flag))
          true
          (recur (inc n)))))))

(defn- class-for [component-keys from]
  (set! class-build-n 0)
  (let [len (alength component-keys)
        num (- len from)]
    ;; Make sure the class builder is large enough.
    (when (< (alength class-builder) num)
      (js/console.warn "growing ecs class builder to" num)
      (set! class-builder (js/Uint16Array. num)))
    ;; Sort the components, so A+B and B+A are the same. Shared and empty last.
    (util/dorange [n from len]
      (let [k (aget component-keys n)]
        (assert (number? k))
        (when (zero? (bit-and k component-static))
          (class-build-add k))))
    ;; Resolve the existing class or create a new one.
    (let [id (class-build-gen)]
      (if-let [idx (.get id->index id)]
        (class-get idx)
        (let [q   (js/Set.)
              idx (++ classes-num)
              key (class-key idx
                             (class-bit component-keys from component-linked)
                             (class-sum component-keys from component-shared)
                             (class-sum component-keys from component-empty))
              ids (js/Uint16Array. class-build-n) ; TODO from bump alloc?
              cls (->Class key ids q)]
          ;; Gather component indices, discard sort bits.
          (dotimes [n class-build-n]
            (->> (aget class-builder n)
                 (component-index)
                 (aset ids n)))
          (dotimes [n queries-num]
            ())
          (.set id->index id idx)
          (aset classes idx cls)
          cls)))))

(defn* class []
  (class-for (js-arguments) 0))

(comment (js/console.log (class)
                         (class Name)
                         (class Name Tags)
                         (class Tags Name)
                         (class Name Tags Enabled)))

;;; World Groups (Entity classes instanced in a specific world)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- group-for
  "Get the group corresponding to the given class key in the current world."
  [k]
  (let [^js/Map groups (.-groups *world*)]
    (or (.get groups k)
        (let [g (->Group 0
                         (group-counts 0 (class-index k))
                         (util/u16-array default-group-blocks -1)
                         #js [])]
          (.set groups k g)
          g))))

(defn- group-block
  [^Group group world-index]
  (let [index (.-blocks group)]
    (util/doarray [x i index]
      (when (= 0xffff x)
        (aset index i world-index)
        (util/return i)))
    (assert false "TODO group blocks full")))


;;; State Blocks (Entity batches instanced in a specific group)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- block-shared-size [init-sz ids begin end]
  (loop [n begin sz init-sz]
    (if (= n end)
      sz
      (let [mem (.-mem (component-get (aget ids n)))]
        (recur (inc n)
               (-> (component-align mem)
                   (util/align sz)
                   (+ (component-size mem))))))))

(defn- block-entity-size [shared-sz ids end capacity]
  (loop [n 0 sz shared-sz]
    (if (= n end)
      sz
      (let [mem (.-mem (component-get (aget ids n)))]
        (recur (inc n)
               (-> (component-align mem)
                   (util/align sz)
                   (+ (* (component-size mem) capacity))))))))

(defn- block-new-shared [offset ids buf shared begin end]
  (loop [n begin o offset]
    (if (= n end)
      o
      (let [c (component-get (aget ids n))
            m (.-mem c)
            o (util/align (component-align m) o)]
        (aset shared n (.ctor c buf o))
        (recur (inc n) (+ o (component-size m)))))))

(defn- block-new-arrays [offset ids buf arrays end]
  )

;; Views
;; - object: separate
;; - single: u8, u16, u32, i8, i16, i32, f32, f64
;; - vector: ^
;; - mixed :
;;
;; raw array:
;; - whole block view, need ptr + index pairs, no value types in JS
;;
;; what wrapper does:
;; - global object mutated to "slide" into view of the current component

;; Queries
;; - Everything stored here is consumed primarily by queries, optimize that
;; - queries determine frequency of access (system, group, block, entity)
;;   - per component, iterate at smallest frequency, instance others
;;   - very similar to draw call dispatch, shader stages
;; - data passed to query function, over system state, is what's important
;;   - and data coming back, handle component writes (set to same buffer or different view)
;;   - seem to indicate blocks to be made of 1 static buffer and multiple "dynamic" ones
;;   - dont want to move entire block when adding/removing common components types (esp system/tags)
;;   - support writing entire arrays to new buffers every frame (keep previous ver of destructive update)
;;     - time travel, debugging, snapshots, etc
;; - defined on component types, layout in entity blocks, consumed by queries

;; different wrappers per access frequency:
;; - per component has a sliding view (small reusable obj, typed view + offset -> BUILD MATHS AROUND THIS)
;; - per block has the raw component's view
;; - per group has the array of block IDs
;; - per system has the group handle

;; everything consumed by system functions
;; - called at highest frequency, from components in the query
;; - read-only components can have simpler wrappers (pass scalar directly, instead of wrapper with setter)

(deftype Scalar [^:mutable value])
(deftype Vector [view
                 ^:mutable offset])



;; TODO how to handle components with mixed buffer/object?
;; - disallow? simpler implementation, splits high-level components manually
;; - automatic split? component acts like two (one buffer, one object) -> screws up indexing, no longer 1:1 with class
;; - no split, wrapper access? adds an indirection (wrapper -> array + object)
;; OR
;; - wrappers for every component? control access (buffer + offset, array + offset, temp view, scalar view)
;; - already sorta need wrappers, otherwise JS is gonna get in the way
;; - so what do?

(defn- block-new [^Class class ^Group group count-hint]
  (let [capacity (max block-min-cap (min block-max-cap count-hint))
        comps    (.-components class)
        class-k  (.-key class)
        n-shared (class-num-shared class-k)
        n-empty  (class-num-empty  class-k)
        n-end    (- (alength comps) n-empty)
        n-comps  (- n-end n-shared)
        o-shared (* 4 capacity) ; lookup size, shared memory offset
        buf-sz   (-> o-shared
                     (block-shared-size comps n-comps n-end)
                     (block-entity-size comps n-comps capacity))
        buffer   (js/ArrayBuffer. buf-sz)
        lookup   (js/Uint32Array. buffer 0 capacity)
        arrays   (util/new-array n-comps)
        shared   (util/new-array n-shared)
        limits   (.-limits group)
        n-world  (world-block)
        n-group  (group-block group n-world)
        block    (->Block (block-key n-world n-group)
                          (block-cap 0 capacity)
                          lookup arrays shared)]
    (aset (.-blocks *world*) n-world block)
    (aset (.-lookup *world*) n-world group)
    (-> o-shared
        (block-new-shared comps buffer shared n-comps n-end)
        (block-new-arrays comps buffer arrays n-comps))
    (-> (group-blocks limits) (inc)
        (group-limits (group-allocs limits))
        (->> (set! (.-limits group))))
    block))

(defn- ^Block block-for [class ^Group group count-hint]
  (let [total (group-blocks      (.-limits group))
        index (group-full-blocks (.-counts group))]
    (if (not= total index)
      (block-get (aget (.-blocks group) index))
      (block-new class group count-hint))))

(defn- block-add [group block]
  )

(defn- block-del [group block]
  )

(defn- block-update [^Block block count]
  (let [cap    (.-cap block)
        allocs (block-allocs cap)
        length (block-length cap)]
    (assert (not= count length) "Nothing to update")
    (assert (<=   count allocs) "Block overflow")
    (if (zero? count)
      nil ; TODO release block
      (do (cond (= allocs count)  (block-del (.-layout block) block)
                (= allocs length) (block-add (.-layout block) block))
          (set! (.-cap block)  (block-cap count allocs))))))

;; TODO grow blocks to `block-max-cap` unless class-fixed
;; - need flag to lock blocks/entities in place anyways.
(defn- block-grow [])

(defn- block-alloc [^Block block ^Group group count]
  (let [cap    (.-cap block)
        allocs (block-allocs cap)
        length (block-length cap)]
    (assert (not= allocs length) "Full block") ; TODO grow
    (let [num (min count (- allocs length))
          new (+ length num)]
      (block-update block new)
      (when (and (= new allocs) ; Block is now full
                 (or true ; TODO class-fixed
                     (>= allocs block-max-cap)))
        (let [c (.-counts group)]
          (-> (group-full-blocks c) (inc)
              (group-counts (group-class-index c))
              (->> (set! (.-counts group))))))
      num)))


;;; Query Views
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- query-hash [components]
  (let [hash 0]
    (util/doarray [id components]
      (js* "~{} ^= ~{}" hash (bit-shift-left id (bit-and id 0xF))))
    hash))

(defn query
  [id opts components num-disabled num-required num-optional num-written]
  (let [info (query-info num-disabled num-required num-optional num-written)
        hash (bit-xor info (query-hash components))] ; TODO poor man's hash
    (if (= hash (.get id->hash id))
      (query-get (.get id->index id))
      (let [c (js/Set.)
            n (++ queries-num)
            k (bit-or n opts)
            q (->Query k info (js/Uint16Array. components) c)]
        (.set id->hash id hash)
        (.set id->index id n)
        (aset queries n q)
        (dotimes [i classes-num]
          (when (query*class q (class-get i))
            (.add c i)))
        q))))


;;; System Batches
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
  [id hash opts ctor]
  (if (= hash (.get id->hash id))
    (system-get (.get id->index id))
    (do (.set id->hash id hash)
        (let [cur (.get id->index id)
              idx (or cur (++ systems-num))]
          (if-not cur
            (.set id->index id idx)
            (let [existing (system-get idx)]
              ;; TODO reinit state, update queries
              ))
          (let [key (bit-or idx opts)
                sys (->System key hash ctor)]
            (aset systems idx sys)
            sys)))))

(defn tick
  "Lightweight system controlling the execution of other systems."
  []
  )

(defn event
  "Lightweight system dispatching pending entity events."
  [id]
  )

(defn sync
  "Lightweight system executing pending entity changes."
  []
  )

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
  (let [g (group-for (.-key class))
        o 0 ; both mutable
        n (world-alloc ids)
        l (.-limits g)]
    (+= (.-total *world*) n)
    (->> (group-allocs l) (+ n)
         (group-limits (group-blocks l))
         (set! (.-limits g)))
    (while (pos? n)
      (let [block (block-for class g n)
            index (block-length (.-cap block))
            count (block-alloc block g n)]
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

;; TODO static doesnt affect layout, get/set forward accesses to system state
(ecs/defc ^:static Name
  "Unique display name of the entity. Optional, unnamed entity if missing."
  {:type :str
   :get  name-get
   :set  name-set})

(ecs/defsys NameIndex
  {:state #js []}) ; TODO query-less systems as "world components"

(ecs/defc Tags
  "Semantic tags of the entity. Optional, assume 0 if missing."
  {:type :u32
   :init 0})

(ecs/defc Enabled
  "Marker for entities enabled and disabled regardless of component classes.")

(ecs/defc SByte {:type :i8})
(ecs/defc Short {:type :i16})
(ecs/defc Int {:type :i32})
(ecs/defc Byte {:type :u8})
(ecs/defc UShort {:type :u16})
(ecs/defc UInt {:type :u32})
(ecs/defc Float {:type :f32})
(ecs/defc Double {:type :f64})

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
