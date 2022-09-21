(ns bllm.data
  (:refer-clojure :exclude [import load])
  (:require-macros [bllm.data :as data])
  (:require [clojure.string :as str]
            [bllm.cli  :as cli]
            [bllm.meta :as meta]
            [bllm.util :as util :refer [def1]]))

(set! *warn-on-infer* true)

(cli/defgroup config)

;; TODO LZ4 wasm worker to pack/unpack blobs -> limit blobs to transferable types
;; - loading data in worker then passing around would deep clone it, bad
;; - anything loaded into shared array buffers is also fair game
;;   - ideally build systems upon these, modular closure output to lazy load same code in workers
;;   - getting a figwheel client into workers will be fun, all in one emacs cider REPL session


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

(defn- parse-src [^js/object src]
  (if-let [m (.match src.url #"(?:^|/)([^/]+)\.([^.]+)(?:\?|#|$)")]
    (do (set! (.-name src) (aget m 1))
        (set! (.-ext  src) (str/lower-case (aget m 2)))
        src)
    (throw src))) ; TODO reason why

(defn- create-json [blob]
  (-> (util/response-text blob) ^js/Promise (.then js/JSON.parse)))

(cli/defcmd create
  "Creates a new asset from a `js/Blob`."
  [url-or-src blob]
  (let [src (parse-src (if (string? url-or-src)
                         #js {:url url-or-src}
                         url-or-src))
        imp (.get extensions src.ext)
        load imp.loader]
    (case imp.fetch
      (fetch-blob) (load src blob)
      (fetch-custom fetch-res)
      (let [url (js/URL.createObjectURL blob)]
        (set! (.-url src) url)
        (util/finally (load src) #(js/URL.revokeObjectURL url)))
      ;; else
      (-> ^js/Promise
          ((case imp.fetch
             fetch-data util/response-data
             fetch-text util/response-text
             fetch-json create-json) blob)
          (.then #(load src %))))))

(cli/defcmd import
  "Imports a new asset from a `js/URL` or a string."
  [url]
  (let [src (parse-src #js {:url url})
        imp (.get extensions src.ext)
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
          (.then #(load src %))))))


;;; IndexedDB
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO project management (one db per project, multi-db projects -> all one context)
#_(cli/defcmd build [])

(defn- on-change-project [])

(cli/defvar project
  "Name of the IndexedDB database."
  {:set on-change-project}
  "user")

(def1 ^:private version
  "Bumped when a release includes schema changes.

  Incremented at runtime on schema changes at the REPL."
  1) ; TODO this means release version will often be lower than local version. git might have answers

(def1 ^:private ^js/IDBDatabase conn nil)

(def1 ^:private stores (js/Map.))

(def1 ^:private dirty-stores (js/Set.))

(comment (js/console.log conn)
         (js/console.log stores))

(defn- on-close []
  (set! conn nil))

(defn- on-abort [^js/Event e]
  (js/console.error "abort" e.target)) ; transaction aborted -> global handler?

(defn- on-error [^js/Event e]
  (js/console.error "error" e.target)) ; transaction error

(defn- on-version-change [^js/IDBVersionChangeEvent e]
  (js/console.log "version" e)) ; another connection upgraded the database

;; TODO doesnt work if the object store already exists; need to access from a transaction
(defn- upgrade [^js/IDBDatabase db desc]
  (let [store (.createObjectStore db desc.name (aget desc.keys 0))]
    (util/dorange [n 1 desc.keys.length]
      (let [k (aget desc.keys n)]
        (.createIndex store k.name k.path k)))))

(defn- on-upgrade-needed [^js/object #_js/IDBVersionChangeEvent e]
  (let [db (.. e -target -result)]
    (if (zero? (.-size dirty-stores))
      (util/domap [store _ stores]
        (upgrade db store))
      (util/docoll [name dirty-stores]
        (upgrade db (.get stores name))))))

(defn- on-blocked [^js/IDBVersionChangeEvent e]
  (js/console.log "blocked" e))

(defn- valid-drop? [^js/DragEvent e]
  (util/doarray [item (.. e -dataTransfer -items)]
    #_(when-not (.contains ))))

;; TODO check if valid drag source
(defn- on-drag-enter [e]

  ;; TODO add drag class
  (util/prevent-default e))

(defn- on-drag-end [e]
  ;; TODO remove drag class
  (util/prevent-default e))

(defn- on-drag-over [e]
  (util/prevent-default e))

(defn- on-drop [e]
  (try
    ;; TODO temporary "fs" with all dropped files -> can still link gltf + buffers + images in single drop
    ;; - otherwise keep import state -> prompt user for missing files (or create with defaults, then fill later)
    (util/dolist [f e.dataTransfer.files]
      (create f.name f))
    (catch :default e
      (js/console.log "drop error" e)))
  (util/prevent-default e)) ; TODO error UI -> catch & eat -> wrapper macro

(defn- init-version [dbs]
  (util/doarray [db dbs]
    (when (= project db.name)
      (set! version db.version)
      (util/return))))

(defn- open []
  (some-> conn .close)
  (util/defer [resolve reject]
    (let [^object req (.open js/indexedDB project version)]
      (set! (.-onupgradeneeded req) on-upgrade-needed)
      (set! (.-onblocked req) on-blocked)
      (set! (.-onerror   req) reject)
      (set! (.-onsuccess req)
            (fn success []
              (set! conn req.result)
              (set! (.-onabort conn) (util/callback on-abort))
              (set! (.-onclose conn) (util/callback on-close))
              (set! (.-onerror conn) (util/callback on-error))
              (set! (.-onversionchange conn) (util/callback on-version-change))
              (resolve))))))

(defn init []
  (doto ^js/Node (.-body js/document)
    (.addEventListener "dragenter" (util/callback on-drag-enter))
    (.addEventListener "dragend"   (util/callback on-drag-end))
    (.addEventListener "dragover"  (util/callback on-drag-over))
    (.addEventListener "drop"      (util/callback on-drop)))
  (-> (.databases js/indexedDB)
      (.then init-version)
      (.then open)))

(defn destroy-db []
  (set! version 1)
  (.deleteDatabase js/indexedDB project)) ; NOTE Evaluate this at your own risk!

(defn primary-key [path auto]
  #js {:keyPath path :autoIncrement auto})

(defn index-key [name path unique multi]
  #js {:name name :path path :unique unique :multiEntry multi})

(defn register [name hash keys]
  (let [store #js {:name name :hash hash :keys keys}
        dirty (if-let [existing (.get stores name)]
                (not= hash existing.hash) ; TODO check for indices to delete
                (boolean conn))]
    (.set stores name store)
    (when dirty
      (.add dirty-stores name))
    store))

(defn- finish-refresh []
  (.clear dirty-stores)
  ;; TODO kick off pending requests in the fresh queue -> resume normal queuing
  )

(defn pre-tick []
  (when (and conn (pos? (.-size dirty-stores)))
    ;; TODO move new requests to fresh queue, wait for existing requests to complete
    (util/inc! version)
    (-> (open) (.then finish-refresh)))) ; TODO (.catch generic-error-handler)

;; dont care about scenes or specific file formats here
;; - just storing file entries, and data blobs

;; async CRUD for both files and blobs
;; - load external files, bake into internal formats, store here
;; - load/save ECS scenes
;; - load/save simulation states


;; loading operation -> both async
;; - stream from index -> filter -> results
;; - direct entry from key -> result (1)

;; TODO later add all fields to defstore (split index hash; dont bump version if unindexed fields change?)
;; - specs -> runtime validators, meta-data for UI, etc


;;; File System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; create -> add
;; read -> get, getKey, getAll, getAllKeys, openCursor, openKeyCursor, count
;; update -> put
;; delete -> delete, clear

;; CRUD -> like server side REST, also async, also maps to FS ops (well, this is a FS)
;; - as part of existing transaction -> dont pollute this part with transaction mgmt
;; - one transaction per tick, accum during frame, flush at end, results later

(data/defstore File ; It's a UNIX system! I know this!
  "An entry in the local filesystem.

  See `Asset` for the file's relations, `Source` if the file has been imported,
  `Preview` to get a thumbnail, and the matching object store for its contents.

  NOTE Use `defstore` to create new kinds of assets."
  [id :auto] ; Auto-generated primary asset key.
  [kind]     ; Value generated from `defstore`.
  [entry]    ; Primary key in the asset's store.
  [parent])  ; `id` of the owner `Asset`. Always rooted at a project or library.

(data/defstore Asset
  "An asset is really the list of requirements to open this file."
  [id   :unique]
  [deps :multi])

(data/defstore Source
  "Stores information about an imported file. Actions are dispatched by kind."
  [id  :unique]
  [url :unique]
  [type]
  [generator]
  [copyright]
  [license])

(data/defstore Preview
  "Small media file to preview the asset without creating it inside the world.")

(data/defstore Blob
  "Stores binary data. Does nothing by itself, referenced by other assets.")

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


;; TODO handlers to generate previews (texture thumbnail, material sphere, mesh scene, audio waveform)


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
