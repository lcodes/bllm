(ns bllm.sky
  (:require [bllm.ecs :as ecs]))

;; few options (all related to how color targets are cleared)
;; - none (no clear)
;; - plain color (render pass clear)
;; - effect pass (gradients, side-scrolling, shader effects; anything without geometry)
;; - matter pass (skybox, sky dome, sky sphere or other custom geometry)

;; advanced effects
;; - rayleigh scattering
;; - mie scattering

#_
(ecs/defc Light
  )
