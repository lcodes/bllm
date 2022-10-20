(ns bllm.math
  (:require-macros [bllm.math]))

(set! *warn-on-infer* true)

;; define operations in terms of larger view (ie "16 floats at offset 0x440" for a mat4)
;; - not creating thousands of small Float32Array's, even over a single shared buffer
;; - naive impl doubles params to all functions (view + offset), no value types either
;; - no SIMD from here in any case, can only limit indirections and hope the VM is fast

(defn add2 []
  )

(defn add3 []
  )

(defn add4 []
  )

(defn sub2 []
  )

(defn sub3 []
  )

(defn sub4 []
  )

(defn normalize2 []
  )

(defn normalize3 []
  )

(defn normalize4 []
  )

(defn cross3 []
  )

(defn cross4 []
  )

(defn dot2 []
  )

(defn dot3 []
  )

(defn dot4 []
  )

(defn quat-identity []
  )

(defn mat3-identity []
  )

(defn mat4-identity []
  )

(defn quat->mat3 []
  )

(defn quat->mat4 []
  )

(defn mat3->quat []
  )

(defn mat4->quat []
  )

(defn mat3*vec3 []
  )

(defn mat3*vec4 []
  )

(defn mat4*vec3 []
  )

(defn mat4*vec4 []
  )
