(ns repl.browse
  (:require [bllm.data :as data]
            [bllm.meta :as meta]
            [repl.dock :as dock]))

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
