(ns repl.inspect
  (:require [bllm.view :as view]))

;; like clojure's inspector, but for any `meta` data type we know about, in the browser
;; - can be a webgpu canvas, webaudio controls, etc -> really close to notebook cells -> run this in vscode!

;; object -> re-frame -> view ->
