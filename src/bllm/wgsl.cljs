(ns bllm.wgsl
  "One shadergraph to rule the WebGPU Shading Language.

  Specification found at https://www.w3.org/TR/WGSL/"
  (:require-macros [clojure.tools.macro :refer [macrolet]]
                   [bllm.wgsl :refer [defreg]])
  (:require [bllm.meta :refer [defenum]]
            [bllm.util :refer [defconst def1 ===]]))

(set! *warn-on-infer* true)


;;; Web's Greatest Scripting Legacy
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; https://github.com/gpuweb/gpuweb/issues/566

;; TODO encode base type, row/col counts directly in enumerated value bits
(defenum gpu-prim-type
  "Predefined types available in WGSL."
  {:repr :string}
  bool   :bool
  u32    :u32
  i32    :i32
  f32    :f32
  f16    :f16 ; requires "enable f16;"
  vec2   :vec2<f32>
  vec3   :vec3<f32>
  vec4   :vec4<f32>
  bvec2  :vec2<bool>
  bvec3  :vec3<bool>
  bvec4  :vec4<bool>
  uvec2  :vec2<u32>
  uvec3  :vec3<u32>
  uvec4  :vec4<u32>
  ivec2  :vec2<i32>
  ivec3  :vec3<i32>
  ivec4  :vec4<i32>
  mat2   :mat2x2<f32>
  mat3   :mat3x3<f32>
  mat4   :mat4x4<f32>
  mat2x3 :mat2x3<f32>
  mat2x4 :mat2x4<f32>
  mat3x2 :mat3x2<f32>
  mat3x4 :mat3x4<f32>
  mat4x2 :mat4x2<f32>
  mat4x3 :mat4x3<f32>
  ;; TODO f16 matrices
  )

(defn- gpu-type [node]
  (if (number? node.type)
    (gpu-prim-type node.type)
    node.name))

(defenum storage-address-space
  "Whether a storage binding is immutable (default) or mutable."
  {:repr :string}
  r  "<storage,read>"
  rw "<storage,read_write>")

(defconst builtin
  "Special value for built-in bindings."
  -1)

(defn- io-bind [slot]
  (if (=== builtin slot)
    "builtin"
    slot))


;;; Shader Graph
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defenum node-kind
  VertexAttr
  DrawTarget
  Interpolant
  Struct
  Uniform
  Storage
  Texture
  Sampler
  Group
  Layout
  Enum
  Flag
  Const
  Override
  Function
  Vertex
  Pixel
  Kernel
  Primitive
  DepthStencil
  Multisample
  Blend
  Render
  Compute)

(def1 node-defs (js/Map.))

(comment (js/console.log node-defs))

(defn reg
  "Registers a shader graph node definition."
  [node]
  ;; TODO schedule reload if node definition changed
  ;;(.set node-defs node.name node)
  (js/console.log node)
  node)


;;; Node Definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- emit-io [node]
  (str "@location(" (io-bind node.bind) ") "
       node.name " : " (gpu-prim-type node.type)))

(defn- emit-var [node address-space]
  (str "@group(" node.group ") @binding(" node.bind ") var"
       address-space " " node.name " : " (gpu-type node)))

(defn- emit-struct [node]
  (str "struct " node.name " {\n"
       ;; TODO fields
       "}"))

(defreg vertex-attr [bind type] emit-io)
(defreg draw-target [bind type] emit-io)
(defreg interpolant [bind type] emit-io)

(defreg struct [info] emit-struct)

(defreg uniform [group bind info]
  emit-struct
  (emit-var "<uniform>"))

(defreg storage [group bind type access]
  (emit-var (storage-address-space access)))

(defreg texture [group bind type])

(defreg sampler [group bind])

(defn group [& x])
(defn layout [& x])

(defn enum [& x]
  )

(defn flag [& x]
  )

(defn const [name hash type init]
  #js {:kind Const
       :name name
       :hash hash
       :type type
       :init init
       :wgsl (str "const " type " " name " = " init ";")})

(defn override
  ([name hash type]
   (override name hash type nil))
  ([name hash type init?]
   #js {:kind Override
        :name name
        :hash hash
        :type type
        :init init?
        :wgsl (str "@id(" hash ") override " type " " name
                   (when init? " = ")
                   init? ";")}))

(defn function [& x]
  )

(defn vertex [& x])
(defn pixel [& x])
(defn kernel [& x])


;;; Render Pipeline States
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO adapt emit-descriptor from gpu to create GPUPrimitiveState and others
(defn primitive
  [topology ])

(defn depth-stencil
  [])

(defn multisample
  [])

(defn blend
  [color alpha])

(defn render
  [& x])

(defn compute
  [& x])


;;; Shader System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn compile []
  ;; - entry point
  ;; - variant overrides
  ;; - schedule request
  ;; - return index? (how to get compiled module into gpu pipelines?)
  )

(defn tick []
  ;; when queue is not empty

  ;; build graph, roots are all the requests' entry points
  ;; topo-sort, generate wgsl, single gpu/shader-module call

  ;; loop through all requests, propagate shaders to pipelines, live updates
  )
