(ns bllm.pass
  "Graph of render and compute passes, built and dispatched every frame."
  (:require [bllm.gpu  :as gpu]
            [bllm.util :as util]))

;; wire together pipelines here

;; Render views:
;; - effect :: generic render-to-texture effects
;; - shadow :: what a light sees
;; - camera :: what the user sees

;; Main render passes:
;; - depth
;; - deferred
;;   - opaque
;;   - test
;;   - decals
;; - lighting
;; - forward [BEGIN HERE]
;; - sky
;; - blend
;; - post
;; - ui

(defn draw
  "Draws a camera to a render target."
  [#_scene camera target]
  ;; reset cmd enc
  ;; reset pass enc ?
  (let [enc nil]
    ;; end enc
    (gpu/submit-1 (.finish enc))))

;; pass types
;; - system -> shader
;; - effect -> tech -> shader
;; - matter -> tech -> mesh -> shader

;; realization:
;; - unless strict artistic requirement to produce same size textures
;; - hard to batch textures, as means is 2d arrays, or the slices of
;;
