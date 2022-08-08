(ns bllm.util
  (:refer-clojure :exclude [array])
  (:require-macros [clojure.tools.macro :refer [macrolet]]
                   [bllm.util :as util]))

(set! *warn-on-infer* true)


;;; Reusable memory
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(macrolet [(gen-arrays [] (range (inc 16)))
           (defstore [sym]
             `(def ~sym
                "Reusable short-lived JavaScript arrays."
                (cljs.core/array
                 ~@(for [n (gen-arrays)]
                     `(js/Array. ~n)))))
           (defgetter [sym]
             `(defn ~sym
                "Returns a short-lived JavaScript array holding the given arguments."
                ~@(let [ns (gen-arrays)
                        xs (map #(symbol (str "x" %)) ns)]
                    (for [n ns]
                      `([~@(take n xs)]
                        (let [~'a (aget ~'arrays ~n)]
                          ~@(for [i (range n)]
                              `(aset ~'a ~i ~(nth xs i)))
                          ~'a))))))]
  (defstore arrays)
  (defgetter array))

(def empty-array (array))
(def empty-obj   #js {})


;;; Micro Logger
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn log [x]
  (js/console.log x)
  x)


;;; Algorithms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private ansi-re
  "Match all ANSI escape sequences."
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
