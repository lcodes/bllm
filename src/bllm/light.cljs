(ns bllm.light
  (:require [bllm.ecs    :as ecs]
            [bllm.effect :as effect]
            [bllm.gpu    :as gpu]
            [bllm.model  :as model]
            [bllm.pass   :as pass]
            [bllm.wgsl   :as wgsl]))

(comment
  (ecs/defc Point
    )

  (ecs/defc Spot
    )

  (ecs/defc Directional
    ))

;; systems to collect culled light data -> upload to GPU
;; systems to load, update and batch probe data
;; systems to compute final lighting environment

;; lookups:
;; - brdf LUT (stupid idea: 3D lut to parameterize an hardcoded value; 128x128x128 -> lower on mobile)
;; - light/reflection probes (spread cube faces over frames, update cache when fully refreshed)

;; forward lighting pass
;; deferred lighting pass
;; translucent pass (OIT on web? at least bucket by depth)
;; unlit pass is just not using any of this (draw from `mesh` directly)
