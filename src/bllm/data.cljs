(ns bllm.data
  (:refer-clojure :exclude [import load])
  (:require-macros [bllm.data :as data])
  (:require [bllm.util :as util :refer [def1]]
            [bllm.meta :as meta]))

(set! *warn-on-infer* true)

;; TODO LZ4 wasm worker to pack/unpack blobs -> limit blobs to transferable types
;; - loading data in worker then passing around would deep clone it, bad
;; - anything loaded into shared array buffers is also fair game
;;   - ideally build systems upon these, modular closure output to lazy load same code in workers
;;   - getting a figwheel client into workers will be fun, all in one emacs cider REPL session


;;; IndexedDB
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 ^:private version
  "Bumped when a release includes schema changes.

  Incremented at runtime on schema changes at the REPL."
  0)

(def1 ^:private ^js/IDBDatabase conn nil)

(comment (js/console.log conn))

(defn- on-abort [e]
  (js/console.error e))

(defn- on-close [e]
  (js/console.log "close" e)
  )

(defn- on-error [e]
  (js/console.error e))

(defn- on-version-change [e]
  (js/console.log e))

(defn init []
  (some-> conn .close) ; TODO reinit pipeline to live code schema upgrade
  (js/Promise.
   (fn defer [resolve reject]
     (let [^object req (.open js/indexedDB "data" version)]
       (set! (.-onupgradeneeded req)
             #(let [^js/IDBDatabase db req.result]
                ;; TODO this will grow, wont be fun to manage
                (doto (.createObjectStore db "thumbnail"))
                (doto (.createObjectStore db "import" #js {:keyPath "source"}))
                (doto (.createObjectStore db "blob"))
                (doto (.createObjectStore db "file" #js {:keyPath "uuid"})
                  (.createIndex "name"   "name")
                  (.createIndex "kind"   "kind")
                  (.createIndex "size"   "size")
                  (.createIndex "deps"   "deps" #js {:multiEntry true})
                  (.createIndex "parent" "parent")
                  (.createIndex "import" "import" #js {:multiEntry true}))))
       (set! (.-onerror   req) reject)
       (set! (.-onsuccess req)
             (fn success []
               (set! conn req.result)
               (set! (.-onabort conn) (util/callback on-abort))
               (set! (.-onclose conn) (util/callback on-close))
               (set! (.-onerror conn) (util/callback on-error))
               (set! (.-onversionchange conn) (util/callback on-version-change))
               (resolve)))))))

(defn pre-tick []
  ;; schema dirty check -> `defstore` to specify indices and props
  )

;; dont care about scenes or specific file formats here
;; - just storing file entries, and data blobs

;; async CRUD for both files and blobs
;; - load external files, bake into internal formats, store here
;; - load/save ECS scenes
;; - load/save simulation states


;; loading operation -> both async
;; - stream from index -> filter -> results
;; - direct entry from key -> result (1)


;;; File System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Blob is unit of loading
;;

(data/defstore File
  "An indexed descriptor for a `Blob` of content."
  :key :auto-increment ;; TODO auto increment with keypath?
  name
  kind
  size
  )

;; case:
;; - got 200+ meshes indexing one buffer (med size model)
;; - got 2000+ entities indexing one scene (med sized world)
;; - dont want to load all these things individually (but still index their fields)
;; - IndexedDB is ECS-like; structs of arrays can be indexed, arrays of structs cant
;;   - can have cake and eat it too; blob can be {:bin ... :prop1 [...] :prop2 [...]}
;;   - defstore handles all that, plus CRUD interface, plus ...

;; neat:
;; - also drives/helps the design of both loader, renderer and the ECS in between
;; - also fairly close to gltf structure; textures always separate but thats fine, they large
;;   - not entirely true, textures can be in array blobs -> load multiple slices of multiple textures in 1 async op
;;   - again, one gpu upload -> compute shader to place things in proper place? in "theory" is faster

(data/defstore Blob
  "Unit of content loading."
  :key File)

(data/defstore Preview
  :key File)

;; TODO handlers to generate previews (texture thumbnail, material sphere, mesh scene, audio waveform)


;;; Importing Data
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 ^:private importers   (js/Map.)) ; imp-id -> importer | set<importer>
(def1 ^:private extensions  (js/Map.)) ; string -> imp-id
(def1 ^:private media-types (js/Map.)) ; string -> imp-id

;; TODO importer versioning -> got db of data, URLs to originals; detect what to reimport on changes
;; - reimport opened assets instantly, otherwise on load or batch on user demand -> expensive operation!

(meta/defenum fetch-type
  fetch-res
  fetch-blob
  fetch-data
  fetch-json
  fetch-text
  fetch-custom)

(defn- add-imp [^js/Map m k v]
  (if-not (array? k)
    (.set m k v)
    (util/doarray [x k]
      (.set m x v))))

(defn- del-imp [^js/Map m k]
  (if-not (array? k)
    (.delete m k)
    (util/doarray [x k]
      (.delete m x))))

(defn importer
  "Registers a new asset importer."
  [uuid name exts types fetch loader]
  ;; Old importer.
  (when-let [^object existing (.get importers uuid)]
    (del-imp extensions  existing.exts)
    (del-imp media-types existing.types))
  ;; New importer.
  (let [imp #js {:uuid   uuid
                 :name   name
                 :exts   exts
                 :types  types
                 :fetch  fetch
                 :loader loader}]
    (.set importers uuid imp)
    (add-imp extensions  exts  imp)
    (add-imp media-types types imp)
    imp))

(defn import
  "Imports a new asset."
  ([^string url]
   (if-let [m (.match url #"/([^/]+)\.([^.]+)(?:\?|#|$)")]
     (import url (aget m 1) (aget m 2))
     (js/Promise.reject url)))
  ([url name ext]
   ;; TODO not found
   (let [imp (.get extensions ext)
         load imp.loader]
     (if (= imp.fetch fetch-custom)
       (load url)
       (-> (js/fetch url)
           (.then util/response-test)
           (.then (case imp.fetch
                    fetch-res  identity
                    fetch-data util/response-data
                    fetch-blob util/response-blob
                    fetch-json util/response-json
                    fetch-text util/response-text)) ^js/Promise
           (.then #(load url % name ext)))))))


;;; Load Queue
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 ^:private requests (js/Map.)) ; uuid -> Promise

;; dont want to load the same asset more than once -> centralized requests
;; - refcount -> unload (potentially after a short delay, sometimes quick reuse)

;; TODO track import requests here too (`import` is really lazy from `load`)
;; - first time import, source changed, importer changed, etc

(defn load [url]
  ;; check if already loaded
  )

(defn unload [res]
  ;; decrement ref-count
  ;; - schedule actual unload at 0 (might get ref'd again before that)
  )

;; TODO what to load directly?
;; - things like meshes and entity/components are sub-resources (part of buffers and scenes)
;; - can still be queried and referenced (component has to refer to mesh, mesh to material, material to texture)
;;   - entities in one scene can refer to entities in another scene (scenes are composable parts of a world)
;;   - in short; anything can refer to anything, its a large graph with specific sections delimited for loading
;; - still want to decouple everything (dont keep references or relations directly in data -> encapsulate here)

;; load any store object as an asset (`defstore` creates new types, specify their layout/indices & relations)
;; - files != asset; foo.gltf is a file, so is bar.png; scenes, meshes, skins, anims and textures are assets

;; `File` tracks these importers (future: baked *.live data format -> skip importers -> fast distribution)
;; `Blob` contains all-at-once load units (temp, large store; LRU evict here first, can always reimport)
;; `store` registers a new asset kind (packed struct-of-array inside `Blob`, queryable here)
