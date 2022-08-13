(ns repl.asset
  (:require-macros [repl.asset :as asset])
  (:require [goog.object :as obj]))

(def maps
  "Mapping of Quake3 map names to arrays of file extensions.

  Describes all files in a map from a single identifier:
  `/assets/<name>.<ext>`

  The map are in pk3 files, the info in txt files and a preview image in jpg,
  tga or dds format. TODO will need to make this easier to consume in browser."
  (asset/list-maps))

(comment (js/console.log maps))

(defn load-map [name file-exts]
  (js/console.log name file-exts))

(comment (goto-map "dmmq3dm6"))

(defn goto-map [name]
  (if-let [exts (obj/get maps name)]
    (load-map name exts)
    (js/Promise.reject (ex-info "No such map" {:name name}))))
