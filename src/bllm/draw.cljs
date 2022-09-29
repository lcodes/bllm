(ns bllm.draw
  "Frame drawing script."
  (:require [bllm.cull :as cull]
            [bllm.gpu  :as gpu]
            [bllm.pass :as pass]
            [bllm.view :as view]))

(gpu/defres ^:dynamic *enc*
  :group (gpu/command-encoder "draw"))

(defn post-tick []
  ;; Locate active viewports
  ;; - collect render views (one per viewport + shadows + render textures + effect textures)
  ;; - scene culling, render graph creation, pass execution

  ;; Pass (system, effect, matter)
  ;; - system :: fully delegated to pass implementation
  ;; - effect :: predefined outputs; customizable material; no vertex inputs
  ;; - matter :: predefined outputs; customizable material and vertex stream

  ;; examples
  ;; - system :: Data generation, GPU particles, water systems, etc (standard effect/matter model breaks down)
  ;; - effect :: Deferred lighting, post-process, render-to-texture (spliced in larger compute shader pipeline)
  ;; - matter :: Depth prepass, base pass, translucents, shadowcasters (3d objects rasterized and materialized)

  ;; Flush `*enc*` & recreate if non-empty
  ;; - get uploads & misc compute work into the frame encoder, decoupled from this draw pipeline
  ;; - no-op if no GPU work, *enc* untouched
  )
