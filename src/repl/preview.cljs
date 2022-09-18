(ns repl.preview
  "Live preview of the selection & provider of asset thumbnails."
  (:require [repl.ui :as ui]))

(set! *warn-on-infer* true)

;; small ECS scene when previewing shaders:
;; - palette of different materials at once
;; - lighting environment, camera settings

;; oscilloscope when previewing playing sounds

;; DOM view when previewing rich text

;; ...

(ui/defview selection
  "Displays a live preview of the current user selection."
  {:label "Preview"}
  []
  [:div "Hello world"])
