(ns bllm.gpu
  "WebGPU device and related objects.

  This namespace follows the structure of the specification document, found at
  https://www.w3.org/TR/webgpu/ currently in working draft status.

  Few notes about performance:
  - Leverage the single thread, reuse a single descriptor per GPU object type.
  - Temporary glue is made from reusable arrays and sub-descriptor objects.
  - No allocations per frame is best, few during setup phases is also good.

  And some caveats:
  - WebGPU isn't finalized yet, no two browsers implement the same spec version."
  (:refer-clojure :exclude [float keep max min repeat replace])
  (:require-macros [bllm.gpu :refer [defbind deflayout defgpu defstage defres]])
  (:require [bllm.meta :refer [defenum defflag]]
            [bllm.util :as util :refer [def1 defconst]]))

(set! *warn-on-infer* true)


;;; Initialization - Adapter, Features & Device
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 ^:private ^js/GPUAdapter adapter
  "WebGPU implementation instance. Mostly used to create the `device`."
  js/undefined)

(def1 ^:private ^js/GPUDevice device
  "WebGPU logical device. Used to create all GPU objects and submit commands."
  js/undefined)

(def1 preferred-format
  "The `texture-format` detected to be most efficient for presented surfaces."
  nil)

(defenum resource-tier
  "Initialization layer of a system resource. Supersedes declaration order."
  {:prefix tier-}
  tier-group ; Bind groups layouts
  tier-entry ; Shader stages, pipeline layouts
  tier-state ; Compute & render pipelines
  tier-MAX)

(defconst tier-system tier-group)

(defn- resource-container [Tier]
  (let [res (js/Array. tier-MAX)]
    (dotimes [n tier-MAX]
      (aset res n (new Tier)))
    res))

(def1 ^:private resources
  "System resources to maintain alongside the GPU `device`.

  Provides a foundation to define new resources at runtime.
  - Auto construction; links a var or field instance to its `GPUObject`.
  - Tiered storage; resources can only refer to lower tier resources.
  - Live freedom; add or modify resources decoupled from declaration order.

  Prefer the `defres` macro to manually declare system resource vars."
  (resource-container js/Map))

(def1 ^:private dirty-resources
  (resource-container js/Set))

(comment (js/console.log resources)
         (js/console.log dirty-resources))

(defn- init-resources []
  (dotimes [n tier-MAX]
    (util/domap [res _ (aget resources n)]
      (res.set))))

(defn- try-init-resource [tier res]
  (when device
    (let [^js/Set dirty (aget dirty-resources tier)]
      (.add dirty res))))

(defn try-destroy [^js/GPUBaseObject obj]
  (when (and obj (.-destroy obj))
    (.destroy obj)))

(defn recreate [tier id]
  (let [^js/Map layer (aget resources tier)]
    (when-let [res (.get layer id)]
      (try-destroy (res.get))
      (try-init-resource tier res))))

(defn register [tier id hash get set]
  (assert (< -1 tier tier-MAX))
  (let [^js/Map layer (aget resources tier)]
    (if-let [res (.get layer id)]
      (when (not= hash res.hash) ; Re-registering with different ctor.
        (set! res.hash hash)
        (set! res.set  set)
        (try-destroy (res.get))
        (try-init-resource tier res))
      (let [res #js {:id   id
                     :hash hash
                     :get  get
                     :set  set}]
        (.set layer id res)
        (try-init-resource tier res)))))

(defn- on-uncaptured-error [e]
  ;; TODO handle errors
  (js/console.error e))

(defn- on-device-lost
  "The GPU context crashed, for one reason or another."
  [^js/GPUDeviceLostInfo info]
  ;; TODO handle loss, see comment about (.destroy device)
  (js/console.error info))

(defn- init-device []
  (assert device)
  ;; NOTE don't return this promise! Would hang init until the device crashes.
  (.then (.-lost              device) (util/callback on-device-lost))
  ;; TODO browser has better handling for now
  ;;(set!  (.-onuncapturederror device) (util/callback on-uncaptured-error))
  (init-resources))

;; TODO (.destroy device) not sure there's a case for reuse yet, equivalent to F5
(defn- set!-device [result]
  (set! device result)
  (init-device))

;; TODO bc-texture-compression & co
(defn- init-device-descriptor []
  #js {:requiredFeatures []})

(defn- init-adapter []
  (assert adapter)
  (util/compat-std (.-requestAdapterInfo adapter)
    {:ff 105}
    ) ; TODO this is a promise, need to await; no test browser right now
  (util/compat-std (.-getPreferredCanvasFormat js/navigator.gpu)
    {:ff 105}
    (set! preferred-format (.getPreferredCanvasFormat js/navigator.gpu)))
  (when-not device
    (-> (.requestDevice adapter (init-device-descriptor)) ^js/Promise
        (.then set!-device))))

(defn- set!-adapter [result]
  (if-not result
    (js/Promise.reject "WebGPU adapter not found")
    (do (set! adapter result)
        (init-adapter))))

(defn init []
  (when-not adapter
    (if (undefined? js/navigator.gpu)
      (js/Promise.reject "WebGPU is not supported")
      (-> (js/navigator.gpu.requestAdapter
           #js {:powerPreference "high-performance"}) ^js/Promise
          (.then set!-adapter)))))

(defn pre-tick []
  ;; TODO reuse this to auto-size render targets on viewport resize?
  ;; canvas events -> viewport -> attached targets -> scale factor -> schedule dirty reinit
  (dotimes [n tier-MAX]
    (let [^js/Set dirty (aget dirty-resources n)]
      (util/docoll [res dirty]
        (res.set))
      (.clear dirty))))

(comment
  (util/doiter [x (.. adapter -features values)]
    (prn x))
  )

(defflag feature
  {:repr :string :suffix ?}
  bgra8unorm-storage?
  depth-clip-control?
  depth24unorm-stencil8?
  depth32float-stencil8?
  indirect-first-instance?
  rg11b10ufloat-renderable?
  shader-f16?
  texture-compression-bc?
  texture-compression-etc2?
  texture-compression-astc?
  timestamp-query?)
;; TODO build bitfield of feature support by calls to
;; (feature "detected-feature-name")
;; - returns 0 if no matching flag, flag bit otherwise, fold over bit-or
;; - tests then simple bit maths instead of testing strings inside a set or distinct vars


;;; Specification - Generated API mirroring the WebGPU interface definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO encode base type, row/col counts directly in enumerated value bits
(defenum prim-type
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

(defenum storage-access
  "Whether a storage binding is immutable (default) or mutable."
  {:repr :string}
  read       :read
  write      :write
  read-write :read_write)

(defflag buffer-usage
  "Compile-time constants equivalent to the runtime `js/GPUBufferUsage`."
  {:suffix -buffer}
  map-read-buffer
  map-write-buffer
  copy-src-buffer
  copy-dst-buffer
  index-buffer
  vertex-buffer
  uniform-buffer
  storage-buffer
  indirect-buffer
  query-resolve-buffer)

(defgpu buffer
  [size             :u64]
  [usage            ::buffer-usage]
  [mappedAtCreation :bool false])

(defenum texture-dimension
  {:repr :string :prefix dim-}
  dim-1d
  dim-2d
  dim-3d)

(defenum texture-format
  "Memory layout of a texture pixel."
  {:repr :string}
  r8unorm
  r8snorm
  r8uint
  r8sint
  r16uint
  r16sint
  r16float
  rg8unorm
  rg8snorm
  rg8uint
  rg8sint
  r32uint
  r32sint
  r32float
  rg16uint
  rg16sint
  rg16float
  rgba8unorm
  rgba8unorm-srgb
  rgba8snorm
  rgba8uint
  rgba8sint
  bgra8unorm
  bgra8unorm-srgb
  rgb9e5ufloat
  rgb10a2unorm
  rg11b10ufloat
  rg32uint
  rg32sint
  rg32float
  rgba16uint
  rgba16sint
  rgba16float
  rgba32uint
  rgba32sint
  rgba32float
  stencil8
  depth16unorm
  depth24plus
  depth24plus-stencil8
  depth32float
  depth32float-stencil8 ; With feature of same name
  ;; texture-compression-bc feature
  bc1-rgba-unorm
  bc1-rgba-unorm-srgb
  bc2-rgba-unorm
  bc2-rgba-unorm-srgb
  bc3-rgba-unorm
  bc3-rgba-unorm-srgb
  bc4-r-unorm
  bc4-r-snorm
  bc5-rg-unorm
  bc5-rg-snorm
  bc6h-rgb-ufloat
  bc6h-rgb-float
  bc7-rgba-unorm
  bc7-rgba-unorm-srgb
  ;; texture-compression-etc2
  etc2-rgb8unorm
  etc2-rgb8unorm-srgb
  etc2-rgb8a1unorm
  etc2-rgb8a1unorm-srgb
  etc2-rgba8unorm
  etc2-rgba8unorm-srgb
  eac-r11unorm
  eac-r11snorm
  eac-rg11unorm
  eac-rg11snorm
  ;; texture-compression-astc
  astc-4x4-unorm
  astc-4x4-unorm-srgb
  astc-5x4-unorm
  astc-5x4-unorm-srgb
  astc-5x5-unorm
  astc-5x5-unorm-srgb
  astc-6x5-unorm
  astc-6x5-unorm-srgb
  astc-6x6-unorm
  astc-6x6-unorm-srgb
  astc-8x5-unorm
  astc-8x5-unorm-srgb
  astc-8x6-unorm
  astc-8x6-unorm-srgb
  astc-8x8-unorm
  astc-8x8-unorm-srgb
  astc-10x5-unorm
  astc-10x5-unorm-srgb
  astc-10x6-unorm
  astc-10x6-unorm-srgb
  astc-10x8-unorm
  astc-10x8-unorm-srgb
  astc-10x10-unorm
  astc-10x10-unorm-srgb
  astc-12x10-unorm
  astc-12x10-unorm-srgb
  astc-12x12-unorm
  astc-12x12-unorm-srgb)

(defflag texture-usage
  {:suffix -texture}
  copy-src-texture
  copy-dst-texture
  texture-binding
  storage-binding
  render-attachment)

(defgpu texture
  [size          :ivec3]
  [mipLevelCount :i32 1]
  [sampleCount   :i32 1]
  [dimension     texture-dimension dim-2d]
  [format        texture-format]
  [usage         ::texture-usage]
  [viewFormats   [texture-format] util/empty-array])

(defenum texture-view-dimension
  {:repr :string :prefix view-}
  view-1d
  view-2d
  view-2d-array
  view-3d
  view-cube
  view-cube-array)

(defenum texture-aspect
  {:repr :string}
  all
  stencil-only
  depth-only)

(defgpu texture-view
  [format          texture-format]
  [dimension       texture-view-dimension]
  [aspect          texture-aspect]
  [mipLevelCount   :u32]
  [arrayLayerCount :u32]
  [baseMipLevel    :u32 0]
  [baseArrayLayer  :u32 0])

(defenum predefined-color-space
  {:repr :string}
  srgb
  display-p3)

(defgpu external-texture
  "Creates a texture from an external video object."
  {:create import}
  [source     :js/HTMLVideoElement]
  [colorSpace predefined-color-space srgb])

(defenum address-mode
  {:repr :string}
  clamp-to-edge
  repeat
  mirror-repeat)

(defenum filter-mode
  {:repr :string}
  nearest
  linear)

(def mipmap-filter-mode filter-mode)

(defenum compare-function
  "Predefined functions available to the depth/stencil tests and samplers."
  {:repr :string}
  never
  less
  equal
  less-equal
  greater
  not-equal
  greater-equal
  always)

(defgpu sampler
  [addressModeU  address-mode clamp-to-edge]
  [addressModeV  address-mode clamp-to-edge]
  [addressModeW  address-mode clamp-to-edge]
  [magFilter     filter-mode        nearest]
  [minFilter     filter-mode        nearest]
  [mipmapFilter  mipmap-filter-mode nearest]
  [lodMinClamp   :f32                     0]
  [lodMaxClamp   :f32                    32]
  [compare       compare-function    always]
  [maxAnisotropy :u16                     1])

(defflag shader-stage
  "Compile-time constants equivalent to the runtime `js/GPUShaderStage`."
  {:prefix stage-}
  stage-vertex
  stage-fragment
  stage-compute)

(defenum buffer-binding-type
  {:repr :string}
  uniform
  storage
  read-only-storage)

(deflayout bind-buffer
  [type             buffer-binding-type uniform]
  [hasDynamicOffset :bool               false]
  [minBindingSize   :u64                0])

(defenum sampler-binding-type
  {:repr :string}
  filtering
  non-filtering
  comparison)

(deflayout bind-sampler
  [type sampler-binding-type filtering])

(defenum texture-sample-type
  {:repr :string}
  float
  unfilterable-float
  depth
  sint
  uint)

(deflayout bind-texture
  [sampleType    texture-sample-type    float]
  [viewDimension texture-view-dimension view-2d]
  [multisampled  :bool                  false])

(defenum storage-texture-access
  {:repr :string}
  write-only)

(deflayout bind-storage-texture
  [access        storage-texture-access write-only]
  [format        texture-format]
  [viewDimension texture-view-dimension view-2d])

(deflayout bind-external-texture)

(defgpu bind-group-layout
  [entries [::bind-group-layout-entry]])

(defbind bind-group-entry
  [binding  :i32]
  [resource ::binding-resource])

(defbind buffer-binding
  {:index true}
  [buffer ::buffer]
  [offset :u64 0]
  [size   :u64 0])

(defgpu bind-group
  [layout ::bind-group-layout]
  [entries [::bind-group-entry]])

(defgpu pipeline-layout
  [bindGroupLayouts [::bind-group-layout]])

(defgpu shader-module
  [code      :str]
  [sourceMap :object]
  [hints     {:str ::shader-module-compilation-hints}])

(defenum compilation-message-type
  {:repr :string} ; TODO need to reverse direction, this one mostly (string -> enum), not (enum -> string)
  error
  warning
  info)

(defgpu compute-pipeline
  "Low-level pipeline creation, called from `bllm.wgsl`.

  Note: set the `compute` stage *before* calling this."
  [layout  ::pipeline-layout]
  ^:static
  [compute ::programmable-stage])

(defstage compute
  "Setups the compute stage before calling `compute-pipeline`."
  compute-pipeline-desc GPUProgrammableStage)

(defgpu render-pipeline
  "Low-level pipeline creation, called from `bllm.wgsl`.

  Note: set the `vertex` and `fragment` stages *before* calling this."
  [layout       ::pipeline-layout] ; TODO should auto layouts even be supported? can't guarantee group-compatible layouts
  ^:static
  [vertex       ::vertex-state]
  [primitive    ::primitive-state     util/empty-obj]
  [depthStencil ::depth-stencil-state]
  [multisample  ::multisample-state   util/empty-obj]
  ^:static
  [fragment     ::fragment-state])

(defstage vertex
  "Setups the vertex stage before calling `render-pipeline`."
  render-pipeline-desc GPUVertexState
  [buffers [::gpu-vertex-buffer-layout]])

(defstage fragment
  "Setups the fragment stage before calling `render-pipeline`."
  render-pipeline-desc GPUFragmentState
  [targets [::gpu-color-target-state]])

(defenum primitive-topology
  {:repr :string}
  point-list
  line-list
  line-strip
  triangle-list
  triangle-strip)

(defenum front-face
  {:repr :string}
  ccw
  cw)

(defenum cull-mode
  {:repr :string}
  none
  front
  back)

(defenum blend-factor
  {:repr :string :prefix blend-}
  blend-zero
  blend-one
  src
  src-alpha
  dst
  dst-alpha
  one-minus-src
  one-minus-src-alpha
  one-minus-dst
  one-minus-dst-alpha
  src-alpha-saturated
  constant
  one-minus-constant)

(defenum blend-operation
  {:repr :string}
  add
  subtract
  reverse-subtract
  min
  max)

(defflag color-write
  "Values for the write-mask of a color target state."
  red
  green
  blue
  alpha)

(defconst RGBA (bit-or red green blue alpha))

(defenum stencil-operation
  {:repr :string}
  keep
  zero
  replace
  invert
  increment-clamp
  decrement-clamp
  increment-wrap
  decrement-wrap)

(defenum index-format
  "Memory layout of an index element."
  {:repr :string :prefix index-}
  index-uint16
  index-uint32)

(defenum vertex-format
  "Memory layout of a vertex attribute."
  {:repr :string}
  uint8x2
  uint8x4
  sint8x2
  sint8x4
  unorm8x2
  unorm8x4
  snorm8x2
  snorm8x4
  uint16x2
  uint16x4
  sint16x2
  sint16x4
  unorm16x2
  unorm16x4
  snorm16x2
  snorm16x4
  float16x2
  float16x4
  float32
  float32x2
  float32x3
  float32x4
  uint32
  uint32x2
  uint32x3
  uint32x4
  sint32
  sint32x2
  sint32x3
  sint32x4)

(defenum vertex-step-mode
  {:repr :string :prefix step-}
  step-vertex
  step-instance)

(defbind vertex-buffer-layout
  {:index true}
  [arrayStride :u64]
  [stepMode    vertex-step-mode]
  [attributes  [::vertex-attribute]])

(defbind vertex-attribute
  [shaderLocation :i32]
  [format         vertex-format]
  [offset         :u64])

(defbind color-target
  {:index true}
  [format    texture-format]
  [blend     ::blend-state]
  [writeMask ::color-write])

(defgpu command-encoder)

;;(defgpu render-bundle-encoder)

(defgpu query-set
  [type  ::query-type]
  [count :i32])


;;; Buffers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (def my-buf (buffer "test" 4096 (bit-or copy-dst-buffer vertex-buffer) false))
  (.destroy my-buf)
  (.writeBuffer (.-queue device)
                my-buf
                0
                (js/Float32Array. #js [1 2 3]))
  (js/console.log my-buf)

  (def uni-buf (buffer "uniform" 128 (bit-or map-write-buffer uniform-buffer) true))
  (def uni-ptr (.getMappedRange uni-buf))
  (js/console.log uni-ptr)
  )


;;; Textures & Texture Views
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;; Samplers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- make-sampler
  ([label address-mode filter]
   (make-sampler label address-mode filter nearest))
  ([label address-mode filter mip-filter]
   (sampler label
            address-mode address-mode address-mode
            filter filter mip-filter
            nil nil always 1))) ; TODO nils shouldnt break enum expansion -> use default


;;; Shader Modules
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- dump-errors* [^js/GPUCompilationInfo info]
  (util/doarray [^js/GPUCompilationMessage x (.-messages info)]
    (js/console.log (util/strip-ansi (.-message x)))))

(defn dump-errors [^js/GPUShaderModule mod]
  (-> (.compilationInfo mod) ^js/Promise
      (.then dump-errors*)))

(comment
  (def shader-test
    (shader-module "test"
                   "
@fragment
fn vert() -> @location(0) vec4<f32> {
  return vec4<f32>(1.0, 1.0, 1.0, 1.0);
}

@fragment
fn frag() -> @location(0) vec4<f32> {
  return vec4<f32>(1.0, 1.0, 1.0, 1.0);
}
"
                   js/undefined nil))

  (dump-errors shader-test)
  (dump-errors repl.demo/shader-mod)
  )


;;; Pipelines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;; Command Buffers, Command Encoders
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;; Debug Markers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO also track CPU time between push/pop & at marker, basis for perf graphs

(defn push-dbg [label]
  (.pushDebugGroup device label))

(defn pop-dbg []
  (.popDebugGroup device))

(defn marker [label]
  (.insertDebugMarker device label))


;;; Queues
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn submit [cmd-bufs]
  (.submit (.-queue device) cmd-bufs))

(defn submit-1 [cmd-buf]
  (submit (util/array cmd-buf)))


;;; Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO


;;; Canvas Context
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- detect-preferred-format
  "Support of legacy WebGPU APIs to detect the preferred canvas pixel format."
  [^js/GPUCanvasContext ctx]
  (when-not preferred-format
    ;; TODO: get full range of versions this relates to. This is on FF 105.0a1.
    (set! preferred-format
          (if (undefined? (.-getPreferredFormat ctx))
            (texture-format bgra8unorm)
            (.getPreferredFormat ctx adapter))))
  preferred-format)

(defn html-setup-target [^js/GPUCanvasContext ctx ^js/GPUCanvasConfiguration cfg]
  (.configure ctx #js {:device device
                       :format preferred-format
                       :usage render-attachment
                       :colorSpace "srgb"
                       :alphaMode "opaque"}))

(defn ^js/GPUCanvasConfiguration html-render-target
  [^js/HTMLCanvasElement canvas]
  (let [^js/GPUCanvasContext ctx (.getContext canvas "webgpu")]
    ;; TODO usage, viewFormats, colorSpace, alphaMode
    #js {:device device
         :format (detect-preferred-format ctx)
         :alphaMode "opaque"
         :canvas canvas
         :ctx    ctx}))

(defn html-destroy-target [^js/GPUCanvasContext ctx]
  (.unconfigure ctx))


;;; Generic System Resources
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defres nearest-clamp
  :system (make-sampler label clamp-to-edge nearest))

(defres linear-clamp
  :system (make-sampler label clamp-to-edge linear))

(defres nearest-mip-clamp
  :system (make-sampler label clamp-to-edge nearest linear))

(defres linear-mip-clamp
  :system (make-sampler label clamp-to-edge linear linear))

(defres linear-repeat
  :system (make-sampler label repeat linear))

(defres empty-bind-group
  "Used to fill unused group bindings in a pipeline layouts."
  :group (bind-group-layout label (util/array)))

(defres empty-pipeline-layout
  :entry (pipeline-layout label (util/array)))

(comment
  (defres default-tex
    (texture-2d label rgba8unorm 1 1 (util/color 0x7f 0x7f 0x7f 0xff)))

  (defres black-tex
    (texture-2d label rgba8unorm 1 1 (util/color 0x00 0x00 0x00 0xff)))

  (defres white-tex
    (texture-2d label rgba8unorm 1 1 (util/color 0xff 0xff 0xff 0xff)))

  )
