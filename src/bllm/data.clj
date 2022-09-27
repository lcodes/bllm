(ns bllm.data
  (:require [bllm.util :as util :refer [defm]]))

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
  [sym [src fetch] & loader]
  (let [m   (meta sym)
        src (vary-meta src assoc :tag 'js/object)]
    `(do (defn ~(vary-meta sym assoc :private true)
           ~(if-not fetch
              `[~src]
              `[~src ~(vary-meta fetch assoc :tag (fetch-tag fetch))])
           ~@loader)
         (bllm.data/importer
          ~(util/unique-id sym)
          ~(name sym)
          ~(util/scalar|js-array m :extension  :extensions)
          ~(util/scalar|js-array m :media-type :media-types)
          ~(fetch-type fetch)
          ~sym))))

(comment
  (clojure.pprint/pprint
   (macroexpand-1
    '(defimport test
       {:extension "ext"
        :media-type "hello/world"}
       [src data]
       :test))))

(defn- primary-key [[s prop]]
  `(bllm.data/primary-key
    ~(name s)
    ~(= prop :auto)))

(defn- index-key [[s prop]]
  `(bllm.data/index-key
    ~(util/unique-name s)
    ~(name s)
    ~(= prop :unique)
    ~(= prop :multi)))

(defm defstore
  "Declare a new schema for an object store."
  [sym & keys]
  `(def ~sym
     (bllm.data/register
      ~(util/unique-name sym)
      ~(hash keys)
      ~(if-not keys
         'bllm.util/empty-array
         `(cljs.core/array
           ~(primary-key (first keys))
           ~@(map index-key (next keys)))))))
