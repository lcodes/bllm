(ns bllm.wgsl
  "One shadergraph to rule the WebGPU Shading Language.

  Specification found at https://www.w3.org/TR/WGSL/"
  (:require-macros [bllm.wgsl :refer [defgpu defwgsl]])
  (:require [bllm.base]
            [bllm.gpu  :as gpu]
            [bllm.meta :refer [defenum]]
            [bllm.util :as util :refer [def1 === str!]]))

(set! *warn-on-infer* true)


;;; Web's Greatest Scripting Legacy
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; https://github.com/gpuweb/gpuweb/issues/566

(defenum node-kind
  "Supported shader graph node kinds. "
  ;; Render State Definitions
  Primitive
  StencilFace
  DepthStencil ; TODO find way to decouple depth/stencil defs -> unified view to GPU
  Multisample
  BlendComp
  Blend
  ;; Render I/O Definitions
  VertexAttr
  DrawTarget
  Interpolant
  GeneratedIO
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

(defn- gpu-field-type [type-or-node]
  (if (number? type-or-node)
    (gpu-prim-type type-or-node)
    type-or-node.name))

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

(defenum builtin
  "Special value for built-in bindings."
  {:repr :string :reverse true}
  vertex-index
  instance-index
  position
  front-facing
  frag-depth
  local-invocation-id
  local-invocation-index
  global-invocation-id
  workgroup-id
  num-workgroups
  sample-index
  sample-mask)

(defn- io-bind [slot]
  (if (neg? slot)
    (builtin slot) ; TODO this syntax is wrong
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

(defn- emit-bind [node address-space type]
  (str "@group(" node.group ") @binding(" node.bind ") var"
       address-space " " node.name " : " type ";"))

(defn- emit-var [node address-space type-fn]
  (emit-bind node address-space (type-fn node)))

(defn- emit-struct [node]
  (let [wgsl (str "struct " node.name " {\n")]
    (util/doarray [f node.info]
      (str! wgsl "  " f.name " : " (gpu-field-type f.type) ",\n"))
    (str! wgsl "}")
    wgsl))

(defn- emit-fn [node]
  (let [wgsl (str "fn " node.name \()]
    (util/doarray [arg i node.args]
      (when (pos? i)
        (str! wgsl ", "))
      (str! wgsl arg.name " : " (gpu-field-type arg.type)))
    (str! wgsl ") -> " (gpu-field-type node.ret) " {\n" node.wgsl \})
    wgsl))

(defn- emit-entry [stage node]
  (util/doarray [id node.deps]
    ;; TODO I/O, builtins
    )
  (str "@" stage "\nfn " node.name "(Input) -> Output {\n" node.wgsl \}))

(defn- emit-kernel [node]
  (let [wgsl (str "@compute @workgroup_size(" node.x)]
    (when-let [y node.y] (str! wgsl ", " y))
    (when-let [z node.z] (str! wgsl ", " z))
    (str! wgsl ")\nfn " node.name \( "INPUTS" ") {\n" node.wgsl \})))

(defn- emit-vertex [node]
  node.wgsl)

(defn- emit-pixel [node]
  node.wgsl)


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
  (emit-bind "" "sampler"))

(defwgsl struct [info] emit-struct)

;; TODO promote fields to first-class defwgsl. (see comment on `argument`)
(defn field [name type offset]
  #js {:name name :type type :byte offset})

(defwgsl enum [keys vals] emit-enum)
(defwgsl flag [keys vals] emit-enum)

(defwgsl const    [type init] emit-const)
(defwgsl override [type init] emit-override)

(defwgsl function [ret args wgsl] emit-fn)

;; TODO promote arguments to first-class defwgsl.
;; - idea is to have defarg and reuse decl across functions
;; - often have the same param to document over and over -> do it once
(defn argument [name type]
  #js {:name name :type type})


;;; Stateful Shader Nodes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment (primitive 1090 nil nil nil nil nil)
         (depth-stencil 4321 1234 25 17))

(defgpu primitive)
(defgpu stencil-face)
(defgpu depth-stencil)
(defgpu multisample)
(defgpu blend-comp)
(defgpu blend)

(defwgsl kernel [x y z   io wgsl] emit-kernel) ; TODO workgroup components can be ref to override var
(defwgsl vertex [buffers io wgsl] emit-vertex)
(defwgsl pixel  [targets io wgsl] emit-pixel)
;; TODO check limits against device, mark shader usable/unusable
;;      - fallback mechanisms

(defwgsl group  [bind])
(defwgsl layout [groups])

(defwgsl compute [pipeline])
(defwgsl render  [pipeline])


;;; Shader System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO "hidden" node kinds; autogenerated I/O structs reuse

(def1 ^:private defs "Shader node definitions." (js/Map.)) ; ID -> Node
(def1 ^:private deps "Shader node dependents."  (js/Map.)) ; ID -> (js/Set ID)

(def1 ^:private requests
  "An array of pipeline requests. Used to populate `entry-ids`.

  Only references `Compute` and `Render` nodes."
  #js [])

(def1 ^:private dirty-ids
  "A set of IDs to the shader nodes modified since the last tick.

  Used to extend the `entry-ids` of the next module generation."
  (js/Set.))

(def1 ^:private entry-ids
  "A set of IDs to the root nodes for the next shader module.

  Only references `Kernel`, `Vertex` and `Pixel` nodes."
  (js/Set.))

(comment (js/console.log defs)
         (js/console.log deps))

(defn empty-io []
  nil) ; TODO reusable "null" node?

(defn gen-io [id deps]
  (or (.get defs id)
      (let [io #js {:kind GeneratedIO
                    :uuid id
                    :deps (js/Array.from deps)
                    :wgsl "TODO gen-io"}]
        (.set defs id io)
        io)))

(defn register
  "Registers a shader graph node definition."
  [node]
  ;; Old node
  (when-let [existing (.get defs node.uuid)]
    (when (not= existing.hash node.hash)
      (.add dirty-ids node.uuid)
      (when-let [old-deps existing.deps]
        (util/docoll [id old-deps]
          (.delete (.get deps id) node.uuid)))))
  ;; New node
  (.set defs node.uuid node)
  (when-let [new-deps node.deps]
    (util/docoll [id new-deps]
      (-> (.get deps id)
          (or (let [ids (js/Set.)]
                (.set deps id ids)
                ids))
          (.add node.uuid))))
  node)

(defn- entry? [node]
  (let [kind node.kind]
    (or (=== Vertex kind) (=== Pixel kind) (=== Kernel kind))))

(defn- compile [node] ; TODO from pipeline request -> gpu/defres
  (assert (entry? node))
  (.add entry-ids node.uuid)
  ;; - variant overrides
  ;; - return index? (how to get compiled module into gpu pipelines?)
  )

(defn- build-graph [^js/Map g id]
  (let [node (.get defs id)]
    (.set g id node)
    (when-not (undefined? node.deps)
      (util/doarray [id node.deps]
        (build-graph g id)))))

(defn- to-module [ids]
  (let [g (js/Map.)]
    (util/docoll [id ids]
      (build-graph g id))
    g))

(defn- check-dirty [ids]
  (util/docoll [id ids]
    (some-> (.get deps id) (check-dirty))
    (when (entry? (.get defs id)) ; TODO collect pipelines, not entries -> unused entries dont need to exist
      (.add entry-ids id))))

(defenum topo-mark
  ^:private TopoTemp
  ^:private TopoDone)

(defn- topo-sort* [^js/Array out ^js/Map marks g node]
  (case (.get marks node.uuid)
    TopoDone js/undefined
    TopoTemp (throw (ex-info "Recursive shader graph" {:graph g :node node}))
    (do (when node.deps
          (.set marks node.uuid TopoTemp)
          (util/docoll [id node.deps]
            (topo-sort* out marks g (.get defs id))))
        (.set marks node.uuid TopoDone)
        (.push out node))))

(defn- topo-sort [^js/Map g out]
  (let [marks util/temp-map]
    (.clear marks)
    (util/clear-array out)
    (util/domap [node id g]
      (topo-sort* out marks g node))
    out))

(defn tick []
  (when (pos? (.-size dirty-ids))
    (check-dirty dirty-ids)
    (.clear dirty-ids))
  (when (pos? (.-size entry-ids))
    (let [g   (to-module entry-ids)
          s   (topo-sort g util/temp-array)
          lbl "Generated"
          src (str "// " lbl "\n")] ; TODO add version info, in case text is saved
      (util/doarray [node s]
        (str! src "\n// #wgsl " node.uuid "\n" node.wgsl "\n"))
      (let [mod (gpu/shader-module lbl src
                                   js/undefined   ; TODO source map
                                   js/undefined)] ; TODO hints
        (gpu/dump-errors mod)
        (js/console.log src)
        (js/console.log s)
        ;; TODO loop through all requests, propagate shaders to pipelines, live updates
        (.clear entry-ids)))))
