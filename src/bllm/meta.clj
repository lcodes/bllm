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
    (if-not fields
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
        repr (when val? (partition 2 elems))
        view (:repr m)
        xs (cond
             (and val? (number? (second elems))) repr
             (= :fourcc view) (map #(vector % (fourcc %)) elems)
             :else (loop [elems (if repr (map first repr) elems)
                          value (if rev? -1 0)
                          xs    ()]
                     (if-not elems
                       (reverse xs)
                       (let [x (first elems)
                             v (if (vector? x) (second x) value)
                             s (if (vector? x) (first  x) x)]
                         (recur (next elems)
                                (long (dir v))
                                (conj xs [s v]))))))]
    `(do
       ;; Emit each enumerated element as a separate constant definition.
       ~@(map emit-const xs)
       ;; When a representation is requested, emit a conversion function.
       ~(when (and view (not= :fourcc view))
          `(defn ~sym [~'x]
             (case ~'x
               ~@(case view
                   ;; TODO more views?
                   :string (let [prefix (some-> (:prefix m) name)
                                 offset (some-> prefix count)]
                             (util/flatten1
                              (for [n (range (count xs))
                                    :let [x (nth xs n)
                                          s (name (if repr
                                                    (second (nth repr n))
                                                    (first x)))]]
                                [(second x)
                                 (if (and prefix (str/starts-with? s prefix))
                                   (subs s offset)
                                   s)]))))))))))

(comment (defenum test-me {:repr :string :prefix hi-}
           hi-a hi-b hi-c))

(defm defflag
  "Packs one or more boolean attributes into an unsigned int."
  [sym & elems]
  `(do ~@(if (inline-vals? elems)
           (map emit-const (partition 2 elems))
           (loop [elems elems
                  bit   1
                  xs    ()]
             (if-not elems
               (reverse xs)
               (let [x (first elems)]
                 (recur (next elems)
                        (long (bit-shift-left bit 1))
                        (conj xs (emit-const x bit)))))))))

(defmacro defbits
  "Packs one or more integer attributes into a unsigned int."
  [& args]
  ;; how many bits total
  ;; how many bits per elem
  ;; pack function (elem... -> num)
  ;; unpack functions (num -> elem)
  )

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
