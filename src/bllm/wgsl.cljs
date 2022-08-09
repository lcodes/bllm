(ns bllm.wgsl
  "One shadergraph to rule the WebGPU Shading Language.

  Specification found at https://www.w3.org/TR/WGSL/"
  (:require-macros [bllm.wgsl])
  (:require [bllm.meta :refer [defenum]]
            [bllm.util :refer [def1]]))

(set! *warn-on-infer* true)


;;; Web's Greatest Scripting Legacy
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; https://github.com/gpuweb/gpuweb/issues/566


(defn tick []
  )
