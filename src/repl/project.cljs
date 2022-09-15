(ns repl.project
  "Specialized inspector for the ECS worlds loaded in memory."
  (:require [bllm.ecs   :as ecs]
            [bllm.scene :as scene]
            [repl.ui    :as ui]))

(set! *warn-on-infer* true)

;; - world selection (main content, editor scenes, prefabs, etc)
;; - scene graph (project organization, arbitrary unordered user defined structure -> creative mental model)
;; - hierarchy (local->world transform hierarchy -> strict vector & matrix update order -> high-performance)
;; - list entity layouts (archetypes, active queries, blocklist)
;; - filter views (all entities with a "Camera")
;; - table views (share with inspector? editing multiple entities same as editing content tables, ie the Scene store)
;;               (also similar to browser, from browsing scenes to editing their details isn't that different a view)

(ui/defpane world
  []
  [:div "ECS Scenes"])
