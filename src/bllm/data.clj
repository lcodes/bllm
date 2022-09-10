(ns bllm.data
  (:require [bllm.util :as util :refer [defm]]))

(defn- scalar|js-array [m k-scalar k-array]
  (or (get m k-scalar)
      (let [xs (get m k-array)]
        (if (empty? xs)
          'bllm.util/empty-array
          `(cljs.core/array ~@xs)))))

(defn- fetch-tag [sym]
  (case sym
    data 'js/ArrayBuffer
    blob 'js/Blob
    json 'object
    text 'string
    nil))

(defn- fetch-type* [sym]
  (if-not sym
    'bllm.data/fetch-custom
    (->> sym name (str "fetch-") (util/named->enum util/ns-data))))

(def ^:private fetch-type (memoize fetch-type*))

(defm defimport
  "Declare a new asset importer. Convenience over `importer`."
  [sym [url fetch] & loader]
  (let [m   (meta sym)
        url (vary-meta url assoc :tag 'js/URL)]
    `(do (defn ~(vary-meta sym assoc :private true)
           ~(if-not fetch
              `[~url]
              `[~url ~(vary-meta fetch assoc :tag (fetch-tag fetch))])
           ~@loader)
         (bllm.data/importer
          ~(util/unique-id sym)
          ~(name sym)
          ~(scalar|js-array m :extension  :extensions)
          ~(scalar|js-array m :media-type :media-types)
          ~(fetch-type fetch)
          ~sym))))

(comment
  (clojure.pprint/pprint
   (macroexpand-1
    '(defimport test
       {:extension "ext"
        :media-type "hello/world"}
       [url data]
       :test))))

(defm defstore
  "Declare a new schema for an object store."
  [sym & args]
  )
