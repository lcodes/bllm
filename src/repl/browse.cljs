(ns repl.browse
  "Specialized inspector for the data assets stored inside IndexedBD."
  (:require [bllm.data :as data]
            [bllm.meta :as meta]
            [repl.dock :as dock]
            [repl.ui   :as ui]))

;; Query, filter, and navigate the local IndexedDB objects
;; - got meta about every object store, can generate a ton

;; Entry
;; - Label (asset name and icons)
;; - Preview (small static media)
;; - Live preview (animated view)

;; Content
;; - list all stores
;; - browse all stores
;; - inspect all objects
;; - inspect all schemas

;; Initial view
;; - `data/File` store, `root`, `user` or `project` folders
;; - favorite locations
;; - navigation history
;; - search / advanced query -> results
;; - side treeview

;; composable browser views (single view -> treeview + fileview -> nested views -> side by side view)
;; - meaning views dont care about their inputs/outputs -> container does; view or view+view is same composition signature

;; problem; `re-frame` emits a DOM
;; - cant really project that in XR
;; - but looong way away from 3D UI -> need engine first -> which needs a browser to be practical
;;   something like a minimal hybrid imgui/retained model over ECS; functional, animated, cached
;;   ie css/html like elements as `ecs/defc` with systems to react to changes & update animations
;;   - result is always a set of "interactive" textures -> need `input` event routers & raycasters
;;   - like pixi, but even faster, for more than sprites

(ui/defevent on-folder-select
  [])

(ui/defevent on-folder-history
  #_{:with [save-selected-folder
          path-project]}
  [db [_ prev?]]
  )

(ui/defevent file-select
  [db [_ id]]
  )

(ui/defeffect file-use
  [{:keys [db]} [_ id]]
  (let [info (get-in db [:assets id])]
    ))

#_
(dock/defpane ui
  {:init {:roots {}
          :files {}
          :assets {0 {:id 0 :parent -1 :type 1 :name "Database"}}
          :project {:folder-search-q (ui/get-local ::folder-search-q identity "")
                    :file-search-q   ""
                    :folder-search   nil
                    :file-search     nil
                    :open-folders    (ui/get-local ::open-folders set #{0})
                    :folder-history  [() ()]}}}
  (folder-refresh)
  (folder-select* (ui/get-local ::selected-folder identity 0))
  (ui/emit [::file-search (ui/get-local ::file-search-q identity "")]))
