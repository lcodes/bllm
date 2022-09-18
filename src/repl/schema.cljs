(ns repl.schema
  (:require [bllm.meta :as meta]
            [repl.ui :as ui]))

;; explore the `meta`
;; - no engine is complete without being able to run itself

(defmethod ui/node* :schema [n v]
  [:div "UI view describing another UI view, or itself, who knows"])

(ui/defview reflect
  []
  [:img {:width 480 :height 360 :src "https://preview.redd.it/dys3wj2z9ue61.jpg?auto=webp&s=511078554dbb75c73fb0e791d4d1802b43db9801"}]
  #_
  [:div "Hello"])

;; TODO debug views?
;; - browse the UI layout
;; - browse the app-db
;; - re-frame-10x inspired timelines

;; - data store schemas
;; - WGSL node definitions
;; - network grid view

;; really a generic graph browser with pluggable node and link views.
;; - ultimately XR, but same concept -> It's a UNIX system! I know this!
