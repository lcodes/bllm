(ns bllm.wgsl
  "One shadergraph to rule the WebGPU Shading Language.

  Specification found at https://www.w3.org/TR/WGSL/"
  (:require-macros [bllm.wgsl :refer [defgpu defwgsl]])
  (:require [bllm.base]
            [bllm.gpu  :as gpu]
            [bllm.meta :refer [defenum]]
            [bllm.time :as time]
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

(defn- field-type [type-or-node]
  (if (number? type-or-node)
    (gpu/prim-type type-or-node)
    type-or-node.name))


;;; Stateless Code Definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- emit-enum [node]
  "TODO")

(defwgsl enum [keys vals] emit-enum)
(defwgsl flag [keys vals] emit-enum)

(defn- emit-const [node]
  ;; FIXME `const` keyword errors on latest Brave, but part of WGSL spec?
  ;; TODO infer type from wgsl/defconst
  (str "var<private> " node.name " : f32" " = " node.init ";"))

(defn- emit-override [node]
  ;; TODO will need node.id to be in [0..65535]
  (str "@id(" node.id ") override " node.name " : " node.type
       (when node.init " = ")
       node.init ";"))

(defwgsl const    [type init] emit-const)
(defwgsl override [type init] emit-override)

(defn- emit-struct [node type-suffix]
  (let [wgsl (str "struct " node.name type-suffix " {\n")]
    (util/doarray [f node.info]
      (str! wgsl "  " f.name " : " (field-type f.type) ",\n"))
    (str! wgsl "}")
    wgsl))

(defn- emit-fn [node]
  (let [wgsl (str "fn " node.name \()]
    (util/doarray [arg i node.args]
      (when (pos? i)
        (str! wgsl ", "))
      (str! wgsl arg.name " : " (field-type arg.type)))
    (str! wgsl ") -> " (field-type node.ret) " {\n" node.wgsl \})
    wgsl))

(defwgsl struct [info] (emit-struct ""))
(defwgsl function [ret args wgsl] emit-fn)

;; TODO promote fields to first-class defwgsl. (see comment on `argument`)
(defn field [name type offset]
  #js {:name name :type type :byte offset})

;; TODO promote arguments to first-class defwgsl.
;; - idea is to have defarg and reuse decl across functions
;; - often have the same param to document over and over -> do it once
(defn argument [name type]
  #js {:name name :type type})


;;; Stateless Shader Nodes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- emit-bind [node address-space type]
  (str "@group(" node.group ") @binding(" node.bind ") var"
       address-space " " node.name " : " type ";"))

(defn- emit-var
  ([node emit-type]
   (emit-bind node "" (emit-type node)))
  ([node address-space emit-type]
   (emit-bind node (address-space node) (emit-type node))))

(defn- buffer-type [node]
  (case node.type
    gpu/uniform           "<uniform>"
    gpu/storage           "<storage,write>"
    gpu/read-only-storage "<storage,read>"))

(defn- uniform-type [node]
  (str node.name "_t"))

;;      dynamic offset
;;      min-binding-size -> ::size
(defwgsl buffer [group bind type info]
  (emit-struct "_t")
  (emit-var buffer-type uniform-type))

(defn- texture-suffix [view]
  (case view
    gpu/view-1d         "1d"
    gpu/view-2d         "2d"
    gpu/view-2d-array   "2d_array"
    gpu/view-3d         "3d"
    gpu/view-cube       "cube"
    gpu/view-cube-array "cube_array"))

(defn- texture-type [node]
  (let [ts (texture-suffix node.view)
        ms (when node.multisampled "multisampled_")]
    (if (= node.sample gpu/depth)
      (str "texture_depth_" ms ts)
      (str "texture_"       ms ts \< (gpu/prim-type node.type) \>))))

(defn- storage-type [node]
  (str "texture_storage_" (texture-suffix node.view)
       \< (gpu/texture-format node.texel)
       \, (gpu/storage-access node.access) \>))

(defn- sampler-type [node]
  (if (= node.type gpu/comparison)
    "sampler_comparison"
    "sampler"))

(defwgsl texture [group bind view type sample]
  (emit-var texture-type))

(defwgsl storage [group bind view texel access type]
  (emit-var storage-type))

(defwgsl sampler [group bind type]
  (emit-var sampler-type))

;; TODO multisampled
(defn- emit-io [node]
  (str "@location(" node.bind ") " node.name " : " (gpu/prim-type node.type)))

(defn- emit-builtin [node]
  (str "@builtin(" node.name ") _" node.name " : " (gpu/prim-type node.type)))

(defwgsl vertex-attr [bind type] emit-io)
(defwgsl draw-target [bind type] emit-io)
(defwgsl interpolant [bind type] emit-io)
(defwgsl builtin [stage dir type] emit-builtin)

(defgpu primitive)
(defgpu stencil-face)
(defgpu depth-stencil)
(defgpu multisample)
(defgpu blend-comp)
(defgpu blend)


;;; Stateful Shader Nodes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defwgsl kernel [in x y z wgsl] emit-kernel)
(defwgsl vertex [in out   wgsl] emit-vertex)
(defwgsl pixel  [in out   wgsl] emit-pixel)

(defwgsl group  [bind])
(defwgsl layout [groups])

(defwgsl compute [pipeline])
(defwgsl render  [pipeline])


;;; Shader System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 ^:private defs "Shader node definitions." (js/Map.)) ; ID -> Node
(def1 ^:private deps "Shader node dependents."  (js/Map.)) ; ID -> (js/Set ID)
(def1 ^:private mods "Shader modules." (js/Map.)) ; Handle -> js/GPUShaderModule

(def1 ^:private dirty-ids
  "A set of IDs to the shader nodes modified since the last tick.

  Used to extend the `entry-ids` of the next module generation."
  (js/Set.))

(def1 ^:private entry-ids
  "A set of IDs to the root nodes for the next shader module.

  Only references `Kernel`, `Vertex` and `Pixel` nodes."
  (js/Set.))

(comment (js/console.log defs)
         (js/console.log deps)
         (js/console.log mods))

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

(defn- entry? [node]
  (case node.kind (Kernel Vertex Pixel) true false))

(defn- state? [node]
  (case node.kind (Group Layout Compute Render) true false))

(defn- gpu-tier [kind]
  (case kind
    (Group) gpu/tier-group
    (Layout
     Kernel
     Vertex
     Pixel) gpu/tier-entry
    (Compute
     Render) gpu/tier-state
    nil))

(defn- release-module [mod-id]
  (let [mod (.get mods mod-id)]
    (util/dec! mod.refs)
    (when (zero? mod.refs)
      (.delete mods mod-id))))

;; TODO change wgsl def* to allow reusing unchanged nodes?
;; - right now it always sets the new node as the var; adds complexity to state mgmt
;; - only relevant in dev (or live code), prod build registers everything once
(defn register
  "Registers a shader graph node definition."
  [^object node]
  ;; Old node (dev only)
  (if-let [existing (.get defs node.uuid)]
    (if (= existing.hash node.hash)
      (cond (state? node) (set! (.-gpu node) existing.gpu)
            (entry? node) (set! (.-mod node) existing.mod))
      (do (.add dirty-ids node.uuid) ; Will end up recreating GPU states.
          (cond (state? node) (release-module  node.mod)
                (entry? node) (gpu/try-destroy node.gpu))
          (when-let [old-deps existing.deps]
            (util/docoll [id old-deps]
              (.delete (.get deps id) node.uuid)))))
    (.add dirty-ids node.uuid)) ; Will create GPU states for the first time.
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

(defn- bind-group-layout [node]
  (assert (= Group node.kind))
  (let [^js/Array ids node.deps
        entries (aget util/arrays (.-length ids))]
    (util/doarray [id n ids]
      (let [e (.get defs id)
            b e.bind
            v (bit-or gpu/stage-compute gpu/stage-vertex gpu/stage-fragment)] ; TODO visibility
        (aset entries n
              (case e.kind
                Buffer  (gpu/bind-buffer  b v e.type false e.size) ; TODO dynamic
                Texture (gpu/bind-texture b v e.sample e.view false) ; TODO multisample
                Storage (gpu/bind-storage-texture b v e.access e.format e.view)
                Sampler (gpu/bind-sampler b v e.type)))))
    (gpu/bind-group-layout node.uuid entries)))

(defn- pipeline-layout [node]
  (assert (= Layout node.kind))
  (let [^js/Array ids (or node.groups node.deps)
        groups (aget util/arrays (.-length ids))]
    (util/doarray [id n ids]
      (aset groups n
            (if (nil? id)
              gpu/empty-bind-group
              (let [grp (.get defs id)] grp.gpu))))
    (gpu/pipeline-layout node.uuid groups)))

(defn- compute-pipeline [node]
  #_(gpu/compute )
  #_(gpu/compute-pipeline ))

(defn- render-pipeline [node]
  #_(gpu/vertex )
  #_(gpu/fragment )
  #_(gpu/render-pipeline ))

(defn- reg-gpu [^object node ctor]
  (assert (nil? node.gpu))
  (gpu/register (gpu-tier node.kind) node.uuid node.hash
                (fn get []       (.-gpu node))
                (fn set [] (set! (.-gpu node) (ctor node)))))

(defn- reg-mod [^object node]
  (.add entry-ids node.uuid)
  (set! (.-mod node) time/frame-number))

(defn- check-dirty [ids]
  (util/docoll [id ids]
    (some-> (.get deps id) (check-dirty))
    (let [node (.get defs id)]
      (case node.kind
        Group   (reg-gpu node bind-group-layout)
        Layout  (reg-gpu node pipeline-layout)
        Compute (reg-gpu node compute-pipeline)
        Render  (reg-gpu node render-pipeline)
        (Kernel
         Vertex
         Pixel) (reg-mod node)
        nil))))

;; TODO feature sets -> generated variants -> fit it all in single module
(defn compile [node]
  (assert (entry? node))
  (reg-mod node))

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

(defn pre-tick []
  (when (pos? (.-size dirty-ids))
    (check-dirty dirty-ids)
    (.clear dirty-ids))
  (when (pos? (.-size entry-ids))
    (let [g   (-> (to-module entry-ids)
                  (topo-sort util/temp-array))
          idx time/frame-number
          lbl (str idx)
          src (str "// " lbl "\n")] ; TODO add version info, in case text is saved
      ;; Emit constants.
      (util/doarray [node g]
        (when (= Const node.kind)
          (str! src "\n" node.wgsl "\n")))
      ;; Emit GenIO nodes.
      (util/docoll [io (collect-io g util/temp-set)]
        (str! src "\n// @wgsl " io.uuid "\nstruct " io.name " {\n")
        (util/doarray [id io.deps]
          (let [elem (.get defs id)]
            (str! src "  " elem.wgsl ", // #wgsl " elem.uuid "\n")))
        (str! src "}\n"))
      ;; Emit generic sorted nodes.
      (util/doarray [node g]
        (when (not= Const node.kind)
          (str! src "\n// #wgsl " node.uuid "\n" node.wgsl "\n")))
      (js/console.log src)
      (js/console.log g)
      ;; Compile, validate & dispatch.
      (let [^object mod
            (gpu/shader-module lbl src
                               js/undefined   ; TODO source map
                               js/undefined)] ; TODO hints
        (set! (.-refs mod) (.-size entry-ids))
        (util/debug (set! (.-src mod) src))
        (aset mods time/frame-number mod)
        (gpu/dump-errors mod) ; TODO async, check result here
        (.clear entry-ids)))))
