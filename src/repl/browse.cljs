(ns repl.browse
  "Specialized inspector for the data assets stored inside IndexedBD."
  (:require [bllm.data :as data]
            [bllm.meta :as meta]
            [repl.dock :as dock]
            [repl.ui   :as ui]))

(set! *warn-on-infer* true)

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

#_
(ui/defevent on-folder-select
  [])

(ui/defevent on-folder-history
  #_{:with [save-selected-folder
          path-project]}
  [db prev?]
  )

(ui/defevent file-select
  [db id]
  )

(ui/defeffect file-use
  [{:keys [db]} id]
  (let [info (get-in db [:assets id])]
    ))

(ui/defview ui
  "Root component of the asset database browser."
  []
  [:div "Browse Asset Database"])

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

;; UI Nodes:
;; - kind (view/pane/menu/etc) is purely to select the component's entry point
;; - data is fed in, but otherwise handled outside ui/node (view-specific defevents)
;; - a `frame` is a container for a preferred view kind (ie panels prefer panes)
;; - but not all components make sense as panes, or views, or menus
;;   - also dont want a frame to be ONLY panes or ONLY menus.
;;   - menus are toolbars, the frame determine the view context (container, display opts -> through interceptors)
;;   - which means a 3D canvas could be a menu item, oh no.
;;   - a menu can be a panel element (toolbar between two panes)
;;   - changing workspace changes EVERYTHING or as little as needed (modeA -> modeA+opts doesnt change much, modeA->modeB does)
