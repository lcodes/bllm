(ns repl.asset
  (:require [clojure.java.io :as io]
            [bllm.util :as util])
  (:import [java.io File]))

(defn split-name-ext [^String s]
  (let [idx (.lastIndexOf s ".")]
    (when (pos? idx)
      [(.substring s 0 idx)
       (.substring s (inc idx))])))

(comment (split-name-ext "hello.foo.txt"))

(defn- list-files []
  (->> (io/file "resources/public/assets")
       (file-seq)
       (drop 1) ; ignore the "assets" folder
       (map (comp split-name-ext #(.getName ^File %)))
       (group-by first)
       (filter (fn [[_ v]] (some #(= "pk3" (second %)) v)))
       (map (fn [[k vs]] [k (mapv second vs)]))
       (into {})))

(defmacro list-maps []
  (util/to-js (list-files)))
