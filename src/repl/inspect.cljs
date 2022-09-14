(ns repl.inspect
  (:require [bllm.view :as view]
            [repl.ui :as ui]))

;; like clojure's inspector, but for any `meta` data type we know about, in the browser
;; - can be a webgpu canvas, webaudio controls, etc -> really close to notebook cells -> run this in vscode!

;; object -> re-frame -> view ->

(defn- label []
  "Inspect") ; TODO customize label to reflect selection

(ui/defpane selection
  {:label label} ; TODO
  []
  [:div "Inspect Hello"])
