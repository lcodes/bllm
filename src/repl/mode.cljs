(ns repl.mode
  "A 'mode line' reflecting the current state of a UI pane."
  (:require [repl.ui :as ui]))

(set! *warn-on-infer* true)

;; status
;; - target view (type + state)
;; - list of matching modes (ie current position in text view; world coordinates in game view; current cell in data grid)
;; - every status 'mode' gets a UI component to display its state

(ui/deframe line
  {:layout :row :class "mode"}
  [:div "textures/hello-world.png"]
  [:div " 69 Kb"]
  ui/space
  [:div " rgba8unorm "]
  [:div [:strong "Texture"]])
