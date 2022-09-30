(ns bllm.meta
  "Don't mind the system behind the curtain."
  (:refer-clojure :exclude [defstruct])
  (:require [clojure.string :as str]
            [cljs.analyzer  :as ana]
            [bllm.util :as util :refer [defm]]))


;;; Analysis
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO also populate cljs version of these tables.
(def prim-table
  "Primitive alignments and sizes. Used to generate lookup tables."
  {[:f16]                 [2 2]
   [:bool :i32 :u32 :f32] [4 4]
   [:vec2 :bvec2 :uvec2 :ivec2] [  8 8]
   [:vec3 :bvec3 :uvec3 :ivec3] [16 12]
   [:vec4 :bvec4 :uvec4 :ivec4] [16 16]
   [:mat2]   [ 8 16]
   [:mat3x2] [ 8 24]
   [:mat4x2] [ 8 32]
   [:mat2x3] [16 32]
   [:mat3]   [16 48]
   [:mat4x3] [16 64]
   [:mat2x4] [16 32]
   [:mat3x4] [16 48]
   [:mat4]   [16 64]})

(defmacro ^:private deftables
  "One-time macro, used below."
  [align-table size-table]
  (letfn [(table [sym selector]
            `(defn- ~sym [~'x]
               (case ~'x
                 ~@(util/flatten1
                    (for [[ks v] prim-table]
                      [(seq ks) (selector v)])))))]
    `(do ~(table align-table first)
         ~(table size-table second))))

(deftables prim-align prim-size)

(comment (prim-align :mat3)
         (prim-size  :mat3))

(defn parse-struct
  "Analyzes a data structure definition. Returns a seq of field descriptors."
  [env fields]
  (loop [fields (partition 2 fields)
         align  0
         offset 0
         output ()]
    (if (empty? fields)
      (with-meta (reverse output) {::align align ::size offset})
      (let [field   (first   fields)
            sym     (first   field)
            tag     (second  field)
            doc     (last    field)
            doc?    (string? doc)
            sym     (if-not doc? sym (vary-meta sym assoc :doc doc))
            ;;m       (cond-> (drop 2 field)
            ;;          doc? (butlast))
            prim?   (keyword? tag)
            info    (when (symbol? tag) ; User-defined types
                      (:meta (ana/resolve-existing-var env tag)))
            f-align (if prim? (prim-align tag) (::align info))
            f-size  (if prim? (prim-size  tag) (::size  info))
            offset  (util/align f-align offset)]
        (recur (next fields)
               (long (max align f-align))
               (long (+  offset f-size))
               (conj output {:name   sym
                             :meta   info
                             :type   tag
                             :size   f-size
                             :align  f-align
                             :offset offset}))))))


;;; Type Constructors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- fourcc-elem [i ch]
  (bit-shift-left (int ch) (* 8 i)))

(defn- fourcc [x]
  (let [s (name x)]
    (when (< 4 (.length s))
      (throw (ex-info "Invalid FourCC identifier." {:fourcc x})))
    (->> (map-indexed fourcc-elem s)
         (reduce bit-or))))

(comment (fourcc :JSON))

(defn- inline-vals? [elems]
  (and (<= 2 (count elems)) (not (symbol? (second elems)))))

(defn- emit-const
  ([[sym val]] (emit-const sym val))
  ([sym val] `(util/defconst ~sym ~val)))

(defm defenum
  "An enumerated type whose domain set is explicitly specified."
  [sym & elems]
  (let [m    (meta sym)
        rev? (:reverse m)
        dir  (if rev? dec inc)
        val? (inline-vals? elems)
        view (when val? (partition 2 elems))
        repr (:repr m)
        xs   (cond
               ;; Given interleaved names and values
               (and val? (number? (second elems))) view
               ;; Compute the FourCC of names for their values
               (= :fourcc repr) (map #(vector % (fourcc %)) elems)
               ;; Generate incrementing or decrementing enum values
               :else (loop [elems (if view (map first view) elems)
                            value (if rev? -1 0)
                            xs    ()]
                       (if-not elems
                         (reverse xs)
                         (let [x (first elems)
                               v (if (vector? x) (second x) value)
                               s (if (vector? x) (first  x) x)]
                           (recur (next elems)
                                  (long (dir v))
                                  (conj xs [s v]))))))
        strs (when (or (:read m) (= :string repr))
               (let [strs (if view
                            (map (comp name second) view)
                            (map (comp name first)  xs))]
                 (if-let [prefix (some-> (:prefix m) name)] ; TODO suffix
                   (let [offset (count prefix)]
                     (for [s strs]
                       (if (str/starts-with? s prefix)
                         (subs s offset)
                         s)))
                   strs)))]
    `(do
       ;; Emit each enumerated element as a separate constant definition.
       ~@(map emit-const xs)
       ;; When a reader is requested, emit a parser function.
       ~(when-let [read (:read m)]
          `(defn ~read [~'x]
             (case ~'x
               ~@(interleave strs (map second xs))
               ~(:default m))))
       ;; When a representation is requested, emit a printer function.
       ~(when (= :string repr)
          `(defn ~sym [~'x]
             (case ~'x
               ~@(interleave (map second xs) strs)))))))

(comment (defenum test-me {:repr :string :prefix hi-}
           hi-a hi-b hi-c))

(defm defflag
  "Packs one or more boolean attributes into an unsigned int."
  [sym & elems]
  ;; TODO conversion functions (bit -> name, name -> bit :: reuse defenum impl)
  `(do ~@(if (inline-vals? elems)
           (map emit-const (partition 2 elems))
           (loop [elems elems
                  bit   (if-let [n (:from (meta sym))]
                          (bit-shift-left 1 n)
                          1)
                  xs    ()]
             (if-not elems
               (reverse xs)
               (let [x (first elems)]
                 (recur (next elems)
                        (long (bit-shift-left bit 1))
                        (conj xs (emit-const x bit)))))))))

(defm defbits
  "Packs one or more integer attributes into a unsigned int."
  [sym & elems]
  (when (or (> 2  (count elems))
            (odd? (count elems)))
    (throw (Exception. "Invalid number of elements")))
  (let [bits (loop [xs elems
                    n  0
                    o  ()]
               (if-not xs
                 (if (> n 32)
                   (throw (ex-info "Too many bits" {:bits n}))
                   (reverse o))
                 (let [bits (second xs)]
                   (recur (nnext xs)
                          (long (+ n bits))
                          (conj o [(first xs) (dec (bit-shift-left 1 bits)) n])))))]
    ;; TODO meta for packed type? ie could fit in u8, u16 or u32 - no u64 in JS
    `(do ~(when-not (:transient (meta sym))
            `(defn ~sym ~(mapv first bits)
               ~@(for [[elem mask] bits]
                   `(assert (>= ~mask ~elem)))
               (bit-or ~@(for [[elem mask shift] bits]
                           `(bit-shift-left ~elem ~shift)))))
         ~@(for [[elem mask shift] bits]
             (when-not (:transient (meta elem))
               `(defn ~elem [~sym]
                  (bit-and ~mask (unsigned-bit-shift-right ~sym ~shift))))))))

(defm defstruct
  [sym & fields]
  ;; check for common field type (recursive across sub-structures)
  ;; - ie if all fields are `:u32` -> accept a `Uint32Array` as view, otherwise wrap `DataView`
  (let [ast (parse-struct &env fields)]
    `(def ~(vary-meta sym merge (meta ast))
       "TODO")))

(defmacro defvar
  "Like `def`, but also captures the place as schematic data."
  [& args]
  ;; get node
  ;; set node (!)
  )

(defmacro defun
  "Like `defn`, but also captures the body as schematic data."
  [& args]
  ;; meta flags
  ;; input nodes
  ;; output nodes
  )
