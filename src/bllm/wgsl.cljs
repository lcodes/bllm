(ns bllm.wgsl
  "One shadergraph to rule the WebGPU Shading Language.

  Specification found at https://www.w3.org/TR/WGSL/"
  (:require-macros [clojure.tools.macro :refer [macrolet]]
                   [bllm.wgsl :refer [defgpu defwgsl]])
  (:require [bllm.meta :refer [defenum]]
            [bllm.util :refer [defconst def1 ===]]))

(set! *warn-on-infer* true)


;;; Web's Greatest Scripting Legacy
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; https://github.com/gpuweb/gpuweb/issues/566

(defenum node-kind
  "Supported shader graph node kinds. "
  ;; Render State Definitions
  Primitive
  StencilFace
  DepthStencil
  Multisample
  BlendComp
  Blend
  ;; Render I/O Definitions
  VertexAttr
  DrawTarget
  Interpolant
  ;; Resources Definitions
  Uniform
  Storage
  Texture
  Sampler
  ;; Code Definitions
  Struct
  Enum
  Flag
  Const
  Override
  Function
  ;; Stage Definitions (stateful nodes -> preconfigured pipeline stages)
  Vertex
  Pixel
  Kernel
  ;; Pipeline Definitions (no WGSL -> high-level, stateful "glue" nodes)
  Group
  Layout
  Render
  Compute)

;; TODO encode base type, row/col counts directly in enumerated value bits
(defenum gpu-prim-type
  "Predefined types available in WGSL."
  {:repr :string}
  u32    :u32
  i32    :i32
  f32    :f32 ; max index of texture base type
  f16    :f16 ; requires "enable f16;"
  bool   :bool
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

(defenum gpu-texture-type
  {:repr :string}
  tex-1d         :texture_1d
  tex-2d         :texture_2d
  tex-2d-array   :texture_2d_array
  tex-3d         :texture_3d
  tex-cube       :texture_cube
  tex-cube-array :texture_cube_array)
;; TODO multisampled, external, storage, depth textures

(defn gpu-full-texture-type [node]
  (str (gpu-texture-type node.tex) \< (gpu-prim-type node.type) \>))

(defconst builtin
  "Special value for built-in bindings."
  -1)

(defn- io-bind [slot]
  (if (=== builtin slot)
    "builtin"
    slot))

(defn- emit-enum [node]
  "TODO")

(defn- emit-const [node]
  (str "const " node.name " : " node.type " = " node.init ";"))

(defn- emit-override [node]
  ;; TODO will need node.id to be in [0..65535]
  (str "@id(" node.id ") override " node.name " : " node.type
       (when node.init " = ")
       node.init ";"))

(defn- emit-io [node]
  (str "@location(" (io-bind node.bind) ") "
       node.name " : " (gpu-prim-type node.type)))

(defn- emit-group-binding [node address-space type]
  (str "@group(" node.group ") @binding(" node.bind ") var"
       address-space " " node.name " : " type ";"))

(defn- emit-var [node address-space type-fn]
  (emit-group-binding node address-space (type-fn node)))

(defn- emit-struct [node]
  (str "struct " node.name " {\n"
       ;; TODO fields
       "}"))

(defn- emit-fn [node]
  "TODO")

(defn- emit-entry [node]
  )


;;; Stateless Node Definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defwgsl vertex-attr [bind type] emit-io)
(defwgsl draw-target [bind type] emit-io)
(defwgsl interpolant [bind type] emit-io)

(defwgsl uniform [group bind info]
  emit-struct ; TODO support primitive uniforms?
  (emit-var "<uniform>" gpu-type))

(defwgsl storage [group bind type access]
  (emit-var (storage-address-space access) gpu-type))

(defwgsl texture [group bind tex type]
  (emit-var "" gpu-full-texture-type))

(defwgsl sampler [group bind]
  (emit-group-binding "" "sampler"))

(defwgsl struct [info] emit-struct)

(defwgsl enum [keys vals] emit-enum)
(defwgsl flag [keys vals] emit-enum)

(defwgsl const    [type init] emit-const)
(defwgsl override [type init] emit-override)

(defwgsl function [params ret body] emit-fn)


;;; Stateful Shader Nodes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment (primitive 1090 nil nil nil nil nil)
         (depth-stencil "hi" 1245 25))

(defgpu primitive)
(defgpu stencil-face)
(defgpu depth-stencil)
(defgpu multisample)
(defgpu blend-comp)
(defgpu blend)

(defwgsl vertex [body] emit-entry)
(defwgsl pixel  [body] emit-entry)
(defwgsl kernel [body] emit-entry)

(defwgsl group  [])
(defwgsl layout [])

(defwgsl render  [])
(defwgsl compute [])


;;; Shader System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 node-defs (js/Map.))

(comment (js/console.log node-defs))

(defn reg
  "Registers a shader graph node definition."
  [node]
  ;; TODO schedule reload if node definition changed
  ;;(.set node-defs node.name node)
  (js/console.log node)
  node)

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
