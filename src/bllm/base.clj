(ns bllm.base
  (:require [bllm.util :as util :refer [defm]]))

;; TODO collect function signatures, use them in type inference/checking

(defn- emit-wgsl-def [sym]
  `(def ~(cond-> (vary-meta sym assoc :op :wgsl)
           (not (:bllm.wgsl/expr (meta sym)))
           (vary-meta assoc :bllm.wgsl/expr (util/kebab->camel sym)))))

(defm defctor [sym & args]
  (emit-wgsl-def sym))

(defm deflib [sym & args]
  (emit-wgsl-def sym))

(defm defmath [sym & args]
  (emit-wgsl-def sym))
