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
  ;; I/O Definitions
  Builtin
  VertexAttr
  DrawTarget
  Interpolant
  GeneratedIO
  ;; Resources Definitions
  Buffer
  Texture
  Storage
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

(defn- gpu-type [node]
  (if (number? node.type)
    (gpu/prim-type node.type)
    node.name))

(defn- gpu-field-type [type-or-node]
  (if (number? type-or-node)
    (gpu/prim-type type-or-node)
    type-or-node.name))

(defenum storage-address-space
  "Whether a storage binding is immutable (default) or mutable."
  {:repr :string}
  r  "<storage>"
  rw "<storage,read_write>")

(defn texture-suffix [view]
  (case view
    gpu/view-1d         "1d"
    gpu/view-2d         "2d"
    gpu/view-2d-array   "2d_array"
    gpu/view-3d         "3d"
    gpu/view-cube       "cube"
    gpu/view-cube-array "cube_array"))

(defn gpu-texture-type [node]
  (let [ts (texture-suffix node.view)
        ms (when node.multisampled "multisampled_")]
    (if (= node.sample gpu/depth)
      (str "texture_depth_" ms ts)
      (str "texture_"       ms ts \< (gpu/prim-type node.type) \>))))

(defn- io-bind [slot]
  (if (neg? slot)
    ""
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
       node.name " : " (gpu/prim-type node.type)))

(defn- emit-bind [node address-space type]
  (str "@group(" node.group ") @binding(" node.bind ") var"
       address-space " " node.name " : " type ";"))

(defn- emit-var [node address-space type-fn]
  (emit-bind node address-space (type-fn node)))

(defn- emit-struct [node type-suffix]
  (let [wgsl (str "struct " node.name type-suffix " {\n")]
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

(defn- emit-kernel [node]
  (let [wgsl (str "@compute @workgroup_size(" node.x)]
    (when-let [y node.y] (str! wgsl ", " y))
    (when-let [z node.z] (str! wgsl ", " z))
    (str! wgsl ")\nfn " node.name \()
    (when node.in
      (str! wgsl "_in : " node.in.name))
    (str! wgsl ") {\n" node.wgsl \})
    wgsl))

(defn- emit-entry [stage in out node]
  (let [wgsl (str "@" stage "\nfn " node.name \()]
    (when node.in
      (str! wgsl in " : " node.in.name))
    (str! wgsl \))
    (if-not node.out
      (str! wgsl " {\n" node.wgsl \}) ; TODO can this even compile?
      (str! wgsl " -> " node.out.name " {\n  var "
            out " : " node.out.name ";\n"
            node.wgsl "  return " out ";\n}"))
    wgsl))

(def emit-vertex (partial emit-entry "vertex"   "_in" "_io"))
(def emit-pixel  (partial emit-entry "fragment" "_io" "_out"))


;;; Stateless Node Definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- emit-builtin [node]
  (str "@builtin(" node.name ") " node.name " : " (gpu/prim-type node.type)))

(defwgsl builtin [stage dir type] emit-builtin)
(defwgsl vertex-attr [bind type] emit-io)
(defwgsl draw-target [bind type] emit-io)
(defwgsl interpolant [bind type] emit-io)

(defn- uniform-type [t]
  (str t.name "_t"))

;;      dynamic offset
;;      min-binding-size -> ::size
(defwgsl buffer [group bind type info]
  (emit-struct "_t") ; TODO support primitive uniforms?
  (emit-var "<uniform>" uniform-type)) ; TODO storage

;; multisampled
(defwgsl texture [group bind view type sample]
  (emit-var "" gpu-texture-type))

;; view-dimension
;; storage: texel format, access (read, write, read_write)
(defwgsl storage [group bind type access]
  (emit-var (storage-address-space access) gpu-type))

(defwgsl sampler [group bind type]
  (emit-bind "" (if (= type gpu/comparison)
                  "sampler_comparison"
                  "sampler")))

(defwgsl struct [info] (emit-struct ""))

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

(defwgsl kernel [in x y z wgsl] emit-kernel) ; TODO workgroup components can be ref to override var
(defwgsl vertex [in out   wgsl] emit-vertex)
(defwgsl pixel  [in out   wgsl] emit-pixel)
;; TODO check limits against device, mark shader usable/unusable
;;      - fallback mechanisms

(defwgsl group  [bind])
(defwgsl layout [groups])

(defwgsl compute [pipeline])
(defwgsl render  [pipeline])


;;; Shader System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
  nil)

(defn gen-io [id deps]
  (or (.get defs id)
      (let [io #js {:kind GeneratedIO
                    :uuid id
                    :name (str "GenIO_" (bit-and 0xffff (abs id)))
                    :deps (js/Array.from deps)}]
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
  ;; Debug
  (js/console.log node)
  (when node.wgsl (js/console.log node.wgsl))
  node)

(defn- entry? [node]
  (let [kind node.kind]
    (or (=== Vertex kind) (=== Pixel kind) (=== Kernel kind))))

(defn compile [node] ; TODO from pipeline request -> gpu/defres
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
      (.add entry-ids id))))      ; - still want to ensure entry points compile at the REPL, use a dev switch?

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

(defn- collect-io [^js/Map g ^js/Set io]
  (.clear io)
  (util/domap [node _ g]
    (when (entry? node)
      (some->> node.in  (.add io))
      (some->> node.out (.add io))))
  io)

(defn tick []
  (when (pos? (.-size dirty-ids))
    (check-dirty dirty-ids)
    (.clear dirty-ids))
  (when (pos? (.-size entry-ids))
    (let [g   (-> (to-module entry-ids)
                  (topo-sort util/temp-array))
          lbl "Generated"
          src (str "// " lbl "\n")] ; TODO add version info, in case text is saved
      (util/docoll [io (collect-io g util/temp-set)]
        (str! src "\n// @wgsl " io.uuid "\nstruct " io.name " {\n")
        (util/doarray [id io.deps]
          (let [elem (.get defs id)]
            (str! src "  " elem.wgsl ", // #wgsl " elem.uuid "\n")))
        (str! src "}\n"))
      (util/doarray [node g]
        (str! src "\n// #wgsl " node.uuid "\n" node.wgsl "\n"))
      (let [mod (gpu/shader-module lbl src
                                   js/undefined   ; TODO source map
                                   js/undefined)] ; TODO hints
        (gpu/dump-errors mod)
        (js/console.log src)
        (js/console.log g)
        ;; TODO loop through all requests, propagate shaders to pipelines, live updates
        (.clear entry-ids)))))
