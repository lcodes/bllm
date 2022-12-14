(ns bllm.util
  (:refer-clojure :exclude [array])
  (:require-macros [clojure.tools.macro :refer [macrolet]]
                   [bllm.util :as util :refer [%]]))

(set! *warn-on-infer* true)


;;; Reusable memory
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def spaces
  "Preallocated indentation spaces."
  (let [c 16
        n 2
        xs (js/Array. c)]
    (dotimes [i c]
      (aset xs i (.repeat \space (* n i)))) ; We are the parens who say.. *NI!
    xs))

;; A highly overkill method to achieve reusable typed memory in the browser.
(macrolet [(elem-dim [init] ; Initializer dimension.
             (if (vector? init) (count init) init))
           (elem-size [ty dim] ; Size of a single typed array.
             (* dim (case ty
                      (:u8)  1
                      (:u16) 2
                      (:u32
                       :f32) 4)))
           (calc-size [total-sz [_ ty init]] ; Accumulator for the buffer size.
             (+ total-sz (elem-size ty (elem-dim init))))
           (defscratch [& vars] ; Compiler for our little one-use DSL.
             (let [sz (reduce calc-size 0 vars)]
               `(let [~'buf (js/ArrayBuffer. ~sz)] ; All packed in 1 buffer.
                  ~@(loop [vars vars
                           ofs  0
                           defs ()]
                      (if-not vars
                        (reverse defs) ; Emit all definitions, buffer is hidden.
                        (let [[sym ty init doc] (first vars)
                              dim  (elem-dim init)
                              size (elem-size ty dim)
                              ctor (case ty
                                     :u8  'js/Uint8Array
                                     :u16 'js/Uint16Array
                                     :u32 'js/Uint32Array
                                     :f32 'js/Float32Array)]
                          (recur (next vars)
                                 (+ ofs ^long size)
                                 (conj defs ; Generate one def per DSL entry.
                                       `(def ~(vary-meta sym assoc :doc doc)
                                          (let [~'v (new ~ctor ~'buf ~ofs ~dim)]
                                            ~@(when (vector? init)
                                                (for [n (range (count init))]
                                                  `(aset ~'v ~n ~(nth init n))))
                                            ~'v))))))))))]
  (defscratch ; Just to make this last bit trivial to extend in the future.
    [null-u16 :u16 0  "Empty u16 array."]
    [unit-u32 :u32 1  "Singleton u32 array."]
    [temp-u8  :u8  64 "Short-lived byte-array."]
    [temp-u16 :u16 64 "Short-lived u16 array."]
    [temp-u32 :u32 64 "Short-lived u32 array."]
    [temp-a   :f32 16 "Short-lived matrix-sized float array."]
    [temp-b   :f32 16 "Short-lived matrix-sized float array."]
    [scratch  :f32 16 "Short-lived matrix-sized float array."]
    [axis-x+  :f32 [ 1  0  0 1] "Unit X+ axis."]
    [axis-y+  :f32 [ 0  1  0 1] "Unit Y+ axis."]
    [axis-z+  :f32 [ 0  0  1 1] "Unit Z+ axis."]
    [axis-x-  :f32 [-1  0  0 1] "Unit X- axis."]
    [axis-y-  :f32 [ 0 -1  0 1] "Unit Y- axis."]
    [axis-z-  :f32 [ 0  0 -1 1] "Unit Z- axis."]))

(macrolet [(gen-arrays [] (range (inc 16)))
           (defstore [sym]
             `(def ~sym
                "Reusable short-lived JavaScript arrays."
                (cljs.core/array
                 ~@(for [n (gen-arrays)]
                     `(js/Array. ~n)))))
           (defgetter [sym from]
             `(defn ~sym
                "Returns a short-lived JavaScript array holding the given arguments."
                ~@(let [ns (gen-arrays)
                        xs (map #(symbol (str "x" %)) ns)]
                    (for [n ns]
                      `([~@(take n xs)]
                        (let [~'a (aget ~from ~n)]
                          ~@(for [i (range n)]
                              `(aset ~'a ~i ~(nth xs i)))
                          ~'a))))))]
  (defstore arrays-a)
  (defstore arrays-b)
  (defgetter array arrays-a))

(def empty-array (array))
(def empty-obj   #js {})

(def temp-array #js [])
(def temp-map (js/Map.))
(def temp-set (js/Set.))

(defn new-array [n]
  (if (zero? n)
    empty-array
    (js/Array. n)))

(defn clear-array [^js/Array a]
  (.splice a 0 (.-length a)))

(defn temp-color [r g b a]
  (aset bytes 0 r)
  (aset bytes 1 g)
  (aset bytes 2 b)
  (aset bytes 3 a)
  bytes)


;;; Statically Typed Dynamic Memory
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn align [alignment-1 size]
  (assert (nat-int? size))
  (bit-and (+ alignment-1 size) (bit-not alignment-1)))

(defn array-copy [dst src]
  (util/doarray [x i src]
    (aset dst i x)))

(defn ^js/Uint16Array u16-array [length value]
  (doto (js/Uint16Array. length)
    (.fill value)))

(defn ^js/Uint32Array u32-array [length value]
  (doto (js/Uint32Array. length)
    (.fill value)))

(defn ^js/Uint32Array bit-array [length]
  (assert (nat-int? length))
  (-> length (+ 31) (/ 32) js/Math.floor js/Uint32Array.))

(defn bit-array-clear [bit-array idx]
  (let [n (/ idx 32)
        i (% idx 32)]
    (->> (aget bit-array n)
         (bit-xor (bit-shift-left 1 i))
         (aset bit-array n))))

(defn pack-array [length bits-per-element]
  (assert (nat-int? length))
  (assert (< 1 bits-per-element 32) "Use bit-array or Uint32Array")
  (assert (and (not=  8 bits-per-element)
               (not= 16 bits-per-element)) "Use Uint8Array or Uint16Array")
  (-> length (+ (dec bits-per-element)) (/ 32) js/Math.floor js/Uint32Array.))


;;; Micro Logger
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn log [x]
  (js/console.log x)
  x)


;;; Algorithms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private ansi-re
  "Matches all ANSI escape sequences."
  (js/RegExp. "\\x1b(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])" "g"))

(defn strip-ansi
  "Removes all ANSI escape sequences from a string. Firefox leaks them."
  [^js/String s]
  (.replaceAll s ansi-re ""))

(defn find-free-index
  "Returns the index of the first `nil` element, or the array's length if full."
  [^js/Array xs]
  (util/doarray [x i xs]
    (when (nil? x)
      (util/return i)))
  (.-length xs))

(defn get-or-new
  "Uses `ctor` to create `k` in `m` if doesn't exist, then return it."
  [^js/Map m k ctor]
  (if-let [v (.get m k)]
    v
    (let [v (new ctor)]
      (.set m k v)
      v)))

(defn random-to [n]
  (-> (js/Math.random)
      (* n)
      (js/Math.floor)))

(defn random-of [^js/Array coll]
  (aget coll (random-to (.-length coll))))


;;; Convenience
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn fqn [^Keyword k]
  (.-fqn k))

(defn key-of [x]
  (if (keyword? x)
    (fqn x)
    x))

(comment (keyword (fqn :hello/world)))

(defn response-test [^js/Response res]
  (if (.-ok res)
    res
    (js/Promise.reject res)))

(defn response-data [^js/Response res] (.arrayBuffer res))
(defn response-blob [^js/Response res] (.blob res))
(defn response-json [^js/Response res] (.json res))
(defn response-text [^js/Response res] (.text res))

(defn prevent-default [^js/Event e]
  (.preventDefault e)
  (.stopPropagation e))

(defn then [^js/Promise p f]
  (if (instance? js/Promise p)
    (.then p f)
    (f p)))

(defn finally [^js/Promise p f]
  (if (instance? js/Promise p)
    (.finally p f)
    (do (f) p)))
