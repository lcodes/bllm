(ns bllm.util
  (:refer-clojure :exclude [array bytes])
  (:require-macros [clojure.tools.macro :refer [macrolet]]
                   [bllm.util :as util]))

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
                      :u8  1
                      :f32 4)))
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
    [unit-x+ :f32 [ 1  0  0 1] "Unit X+ axis."]
    [unit-y+ :f32 [ 0  1  0 1] "Unit Y+ axis."]
    [unit-z+ :f32 [ 0  0  1 1] "Unit Z+ axis."]
    [unit-x- :f32 [-1  0  0 1] "Unit X- axis."]
    [unit-y- :f32 [ 0 -1  0 1] "Unit Y- axis."]
    [unit-z- :f32 [ 0  0 -1 1] "Unit Z- axis."]
    [bytes   :u8  64 "Short-lived byte-array."]
    [scratch :f32 16 "Short-lived matrix-sized float array."]
    [temp-a  :f32 16 "Register-like matrix-sized float array."]
    [temp-b  :f32 16 "Register-like matrix-sized float array."]))

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

(defn clear-array [^js/Array a]
  (.splice a 0 (.-length a)))

(defn color [r g b a]
  (aset bytes 0 r)
  (aset bytes 1 g)
  (aset bytes 2 b)
  (aset bytes 3 a)
  bytes)


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


;;; Convenience
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
