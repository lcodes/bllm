(ns bllm.wgsl
  "One shadergraph to rule the WebGPU Shading Language.

  Specification found at https://www.w3.org/TR/WGSL/"
  (:require-macros [bllm.wgsl :refer [defgpu defwgsl]])
  (:require [bllm.base]
            [bllm.cli  :as cli]
            [bllm.gpu  :as gpu]
            [bllm.meta :refer [defenum]]
            [bllm.time :as time]
            [bllm.util :as util :refer [def1 === str!]]))

(set! *warn-on-infer* true)


;;; Preferences
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(cli/defgroup config)

;; TODO use these
(cli/defvar recompile-on-eval?
  "Automatically regenerate impacted shader pipelines following REPL evals."
  true)

(cli/defvar recompile-on-load?
  "Automatically regenerate impacted shader pipelines following figwheel loads."
  true)


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
  Attribute
  ColorTarget
  Interpolant
  GeneratedIO
  ;; Resources Definitions
  Buffer
  Texture
  Storage
  Sampler
  ;; Code Definitions
  Enum
  Flag
  Const
  Struct
  Override
  Function
  ;; Stage Definitions (stateful nodes -> preconfigured pipeline stages)
  Kernel
  Vertex
  Pixel
  ;; Pipeline Definitions (no WGSL -> high-level, stateful "glue" nodes)
  Group
  Layout
  Input
  Output
  Stream
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
    (str! wgsl "}")))

(defn- emit-fn [node]
  (let [wgsl (str "fn " node.name \()]
    (util/doarray [arg i node.args]
      (when (pos? i)
        (str! wgsl ", "))
      (str! wgsl arg.name " : " (field-type arg.type)))
    (str! wgsl ") -> " (field-type node.ret) " {\n" node.wgsl \})))

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
  (emit-var texture-type)) ; TODO multisampled

(defwgsl storage [group bind view texel access type]
  (emit-var storage-type))

(defwgsl sampler [group bind type]
  (emit-var sampler-type))

(defn- emit-io [node emit-gpu-type]
  (str "@location(" node.bind ") " node.name " : " (emit-gpu-type node.type)))

(defn- emit-builtin [node]
  (str "@builtin(" node.name ") _" node.name " : " (gpu/prim-type node.type)))

;; HALF TYPES
;;

;; MATTER IS A NODE TYPES
;; - MATTER is a partial constructor for the render pipeline's geometry (vertex stream, primitive, etc)
;; - EFFECT also a constructor for the render pipeline, (depthstencil, multisample, blend, color targets)
;;
;; - not just render pipeline; scene bindings as well; generate small render object descriptor for DoD batches (see destiny's renderer)
;;
;; think about high level view of render frame graph:
;; - viewports (1+ per swapchain)
;; - views (1+ per viewport) :: camera, lights, objects
;; - passes (1+ per view)    :: fragment outputs, classify constituents,
;; - batches (1+ per pass)   :: draw calls (all effects and objects accessible; direct singleton, indirect batch, instanced group, gpu culling, ...)
;; - effects (1+ per batch)  :: resource bindings
;; - objects (1+ per effect) :: resource bindings & vertex inputs

;; BATCHES
;; - ideally not every effect and object carries their `GPUBindGroup` or `GPUBuffer`s
;; - dont make "one at a time" shaders, rather "draw all meshes of type A using material A' stored indirectly in uniforms"
;; - different buffers/textures = different draw, even indirect
;;
;; - can merge buffers, use texture arrays
;; - need compatible shader entry then
;; - gets indices to use, performs lookups and samples
;; - then delegates to reusable function for other variants of the same

;; what does this have to do with IO?
;; - interpolants, mesh layouts, affects the vertex/fragment stages
;; - many many different variants, packings, layouts of the same few concepts
;; - where is the data fed into IO living in the ending
;; - batching changes how the stages access data, but not by much
;; -


;; BINDS
;; - UE5 declares vertex structures, always


;; REBINDS -> override-io might be useful after all? or is that link unused
;; - not all shaders will want say texcoord0 to location 4
;; - but theres a single shader module for all, must overlap all overloads without conflict
;; - _in.texcoord0 can cooexist with a definition of the same name at a different binding
;; - bindings declared inside structs, scoped; just need to ensure no two binds have the same name (simple uniq check)

;; POINT OF STREAM/TARGET
;; - simpler binding into high level domain
;; - pack/unpack of data into/from attributes, in between and out of stages
;; - matching CPU side meta "structure" to prepare data, optional (offline data or GPU generated data dont need CPU code)
;; - mostly relevant in higher level shaders -> effect and surface ones, those with pattern structure
;;   - system :: define EVERYTHING, more verbose (at first until patterns emerge,) but full control -> every shader can be 100% different
;;   - effect :: a filtering operation between two render stages; hardwired I/O but full implementation freedom (f -> effect -> t) (effect)
;;   - matter :: combines a mesh stream and a surface effect; selectable input over hardwired output (v -> mesh -> effect -> io -> f -> effect -> t)

;; EFFECT

;; MATTER
;; - Instancing
;; - Instance culling
;; - Vertex Fetch (direct, indirect)
;; - Variable # of TexCoords
;; - Tangent basis + determinant
;; - Vertex color


;; Meshes Layouts:
;; - Static
;; - Skinned
;; - Particle
;; - Water
;; - Sky
;; - Heightfield -> single
;; - Landscape -> blended layers of virtual texture
;; - Skeletal Anim
;; - Ribbon
;; - Trail

;; Framebuffers:
;;

(defn- attribute-type [type]
  (gpu/prim-type
   (case type
     (gpu/uint32)   gpu/u32
     (gpu/uint8x2
      gpu/uint16x2
      gpu/uint32x2) gpu/uvec2
     (gpu/uint32x3) gpu/uvec3
     (gpu/uint8x4
      gpu/uint16x4
      gpu/uint32x4) gpu/uvec4
     (gpu/sint32)   gpu/i32
     (gpu/sint8x2
      gpu/sint16x2
      gpu/sint32x2) gpu/ivec2
     (gpu/sint32x3) gpu/ivec3
     (gpu/sint8x4
      gpu/sint16x4
      gpu/sint32x4) gpu/ivec4
     (gpu/float32)  gpu/f32
     (gpu/unorm8x2
      gpu/unorm16x2
      gpu/snorm8x2
      gpu/snorm16x2
      gpu/float32x2) gpu/vec2
     (gpu/float32x3) gpu/vec3
     (gpu/unorm8x4
      gpu/unorm16x4
      gpu/snorm8x4
      gpu/snorm16x4
      gpu/float32x4) gpu/vec4)))

(defn- color-target-type [type]
  (gpu/prim-type
   (case type
     (gpu/r8uint
      gpu/r16uint
      gpu/r32uint)  gpu/u32
     (gpu/r8sint
      gpu/r16sint
      gpu/r32sint)  gpu/i32
     (gpu/r8unorm
      gpu/r16float
      gpu/r32float) gpu/f32
     (gpu/rg8uint
      gpu/rg16uint
      gpu/rg32uint)  gpu/uvec2
     (gpu/rg8sint
      gpu/rg16sint
      gpu/rg32sint)  gpu/ivec2
     (gpu/rg8unorm
      gpu/rg16float
      gpu/rg32float) gpu/vec2
     (gpu/rgba8uint
      gpu/rgba16uint
      gpu/rgba32uint) gpu/uvec4
     (gpu/rgba8sint
      gpu/rgba16sint
      gpu/rgba32sint) gpu/ivec4
     (gpu/rgba8unorm
      gpu/rgba8unorm-srgb
      gpu/bgra8unorm
      gpu/bgra8unorm-srgb
      gpu/rgba16float
      gpu/rgba32float
      gpu/rgb10a2unorm
      gpu/rg11b10ufloat) gpu/vec4)))

(defwgsl builtin [stage dir type] emit-builtin)

(defwgsl attribute    [bind type step]       (emit-io attribute-type))
(defwgsl interpolant  [bind type]            (emit-io gpu/prim-type))
(defwgsl color-target [bind type mask blend] (emit-io color-target-type))

;; TODO override nodes? don't need WGSL on these
;;(defwgsl vertex-attr* [node type step])
;;(defwgsl draw-buffer* [node type mask blend])

(defgpu primitive)
(defgpu stencil-face)
(defgpu depth-stencil)
(defgpu multisample)
(defgpu blend-comp)
(defgpu blend)

;; OK SO BACK TO DECOUPLING EVERYTHING ->
;; defattrib direct GPU prim type (expand matrix? -> later!)
;; definput from existing attribs -> specify CPU side step mode & types, compute offsets + stride
;; defvertex deps on attrib collection from GenIO, regardless of inputs
;; - vertex reused in many pipelines, input layout changes less frequently

;; FREQUENCY OF CHANGE
;; - attribute def -> rarely if ever; dont push in packing here, but pass it through here instead; simple layers not complex system
;; - input layout -> to match vertex buffers
;; - stream -> array of inputs -> default on vertex, override on pipeline


;; VERTEX FACTORY -> one thing to have vertex streams, most often needs unpacking
;; - no more stream; vertex.in is seq of vertex arrays
;; - define data meant for fragment
;;   - or could read input from fragment, generate the interpolant and link to input in vertex
;;   - even simpler;

;; dont need to generate everything at once
;; - emit meta, meta, meta
;; - start from the end result

;; RENDER PIPELINE
;; - layout (RESOURCES)
;; - vertex (INPUTS)
;; - fragment (OUTPUT)
;; - primitive (STATE)
;; - depthstencil (STATE)
;; - multisample (STATE)

;; VERTEX
;; - inputs (BUFFER LAYOUTS) -> .setVertexBuffer
;; - outputs (INTERPOLANTS) -> include inputs pulled from FRAGMENT

;; FRAGMENT
;; - inputs (attributes, interpolants) -> generate interpolants from attributes, passthru in vertex
;; - outputs (color targets)

;; TODO vertex arrays as ctors of their associated buffers? again, need to plug into another system -> dont know who what when where data generated
;; TODO color targets as ctors of their associated resources? -> input to such ctor, dont generate tons of functions when data suffices
;; - color targets come from the render graph, which is built from interlinking render passes
;; - vertex arrays come from vertex buffers, which come from meshes and render systems

;; OKAY -> DONT SOLVE EVERYTHING HERE
;; - layer up, then reduce as relations emerge
;; - dont want magical I/O, just composable bits

;; systems are made of elements, interconnections and functions;
;; but can also be seen as queries, relations and goals

;; DO, then simplify

;;; Stateful Shader Nodes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- emit-kernel [node]
  (let [wgsl (str "@compute @workgroup_size(" node.x)]
    (some->> node.y (str! wgsl ", "))
    (some->> node.z (str! wgsl ", "))
    (str! wgsl ")\nfn " node.name \()
    (when node.in
      (str! wgsl "_in : " node.in.name))
    (str! wgsl ") {\n" node.wgsl \})))

(defn- emit-entry [stage in out node]
  (let [wgsl (str "@" stage "\nfn " node.name \()]
    (when node.in
      (str! wgsl in " : " node.in.name))
    (str! wgsl \))
    (if-not node.out
      (str! wgsl " {\n" node.wgsl \}) ; TODO can this even compile?
      (str! wgsl " -> " node.out.name " {\n  var " out " : " node.out.name ";\n"
            node.wgsl
            "  return " out ";\n}"))))

(def emit-vertex (partial emit-entry "vertex"   "_in" "_io"))
(def emit-pixel  (partial emit-entry "fragment" "_io" "_out"))

(defwgsl kernel [in x y z wgsl] emit-kernel)
(defwgsl vertex [in out   wgsl] emit-vertex) ; TODO in.deps -> lists vertex attribs -> generate vertex state here!
(defwgsl pixel  [in out   wgsl] emit-pixel)  ; TODO same

(defwgsl group  [bind])
(defwgsl layout [groups])

(defwgsl input  [])
(defwgsl stream [])
(defwgsl output [])

(defwgsl compute [layout kernel])
(defwgsl render  [layout vertex primitive depth-stencil multisample fragment
                  stream target])


;;; Shader System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 ^:private defs "Shader node definitions." (js/Map.)) ; ID -> Node
(def1 ^:private deps "Shader node dependents."  (js/Map.)) ; ID -> (js/Set ID)
(def1 ^:private mods "Shader modules." (js/Map.)) ; Handle -> js/GPUShaderModule

(comment (js/console.log defs)
         (js/console.log deps)
         (js/console.log mods))

(def1 ^:private dirty-ids
  "A set of IDs to the shader nodes modified since the last tick.

  Used to extend the `entry-ids` of the next module generation."
  (js/Set.))

(def1 ^:private entry-ids
  "A set of IDs to the root nodes for the next shader module.

  Only references `Kernel`, `Vertex` and `Pixel` nodes."
  (js/Set.))

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

;; defpixel
;; - refers DRAW BUFFERS, not TARGET; but draw buffers CAN have a default target
;; - node refers TARGET, specialized GENIO

;; EITHER CALL THAT OR STREAM REFERENCE
(defn gen-stream [id deps]
  (let [^object io (gen-io id deps)]
    ;; find matching `stream` node, or generate one
    io))

(defn gen-target [id deps]
  )

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
  (when-let [mod (.get mods mod-id)]
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
  #_(js/console.log node)
  #_(when node.wgsl (js/console.log node.wgsl))
  node)

(defn- bind-group-layout [node]
  (assert (= Group node.kind))
  (let [^js/Array ids node.deps
        entries (aget util/arrays-a (.-length ids))]
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
        groups (aget util/arrays-a (.-length ids))]
    (util/doarray [id n ids]
      (aset groups n
            (if (nil? id)
              gpu/empty-bind-group
              (let [grp (.get defs id)] grp.gpu))))
    (gpu/pipeline-layout node.uuid groups)))

(defn- shader [stage id arg]
  (if-let [node (.get defs id)]
    (stage (.get mods node.mod) node.name (arg node))
    (stage js/undefined "" (util/array))))

(defn- auto-layout [node]
  (if-let [layout-id node.layout]
    (let [layout (.get defs layout-id)]
      layout.gpu)
    ;; TODO can do better than "auto"
    ;; - tracking bindings dependencies and their dependent groups
    ;; - slower, wont work if binding used in multiple groups, but
    gpu/empty-pipeline-layout))

(defn- compute-pipeline [node]
  (assert (= Compute node.kind))
  (shader gpu/compute   node.kernel identity)
  (gpu/compute-pipeline node.name (auto-layout node)))

(defn- attr-buffers [node]
  (let [io-node (.get defs node.id)
        ^js/Array
        deps io-node.deps ; TODO need count WITHOUT builtins (def* -> push builtins last, emit count without them)
        out  (aget util/arrays-a (.-length deps))]
    ;; TODO reassemble stream here?
    (util/doarray [id n deps]
      )
    out))

(defn- draw-targets [node]
  (let [io-node (.get defs node.id)
        ^js/Array
        deps io-node.deps
        out (aget util/arrays-b (.-length deps))]
    (util/doarray [id n deps]
      ;; TODO need to loop, not every location might be bound: need to splice in `undefined`
      (let [d (.get defs id)]
        (aset out n (gpu/color-target d.bind d.format d.blend d.write-mask))))
    out))

(defn- primitive-state [id]
  )

(defn- depth-stencil-state [id]
  )

(defn- multisample-state [id]
  )

(defn- render-pipeline [node]
  (assert (= Render node.kind))
  (shader gpu/vertex   node.vertex   attr-buffers)
  (shader gpu/fragment node.fragment draw-targets)
  (gpu/render-pipeline node.name
                       (auto-layout         node)
                       (primitive-state     node.primitive)
                       (depth-stencil-state node.depth-stencil)
                       (multisample-state   node.multisample)))

(defn- reg-gpu [^object node ctor]
  (assert (nil? node.gpu))
  (gpu/register (gpu-tier node.kind) node.uuid node.hash
                (fn get []       (.-gpu node))
                (fn set [] (set! (.-gpu node) (ctor node)))))

(defn- reg-mod [^object node]
  (assert (nil? node.mod))
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
  ;; TODO pipelines are the true entry points -> determine variants of entry points -> lazy init variants (or opt-in main module compile)

  #_
  (when (pos? (.-size dirty-ids))
    (check-dirty dirty-ids)
    (.clear dirty-ids))
  #_
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
      (let [^object mod (gpu/shader-module lbl src
                                           js/undefined   ; TODO source map
                                           js/undefined)] ; TODO hints
        (set! (.-refs mod) (.-size entry-ids))
        (util/debug (set! (.-src mod) src))
        (.set mods time/frame-number mod)
        (gpu/dump-errors mod) ; TODO async, check result here
        (.clear entry-ids)))))
