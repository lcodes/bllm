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
  (:require-macros [bllm.gpu :refer [defgpu]])
  (:require [bllm.util :as util :refer [def1]]))

(set! *warn-on-infer* true)


;;; Initialization - Adapter, Features & Device
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 preferred-format nil)

(def1 ^js/GPUAdapter adapter js/undefined)
(def1 ^js/GPUDevice  device  js/undefined)

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
  ;; TODO browser has better handling for now
  ;;(set!  (.-onuncapturederror device) (util/callback on-uncaptured-error))
  (.then (.-lost              device) (util/callback on-device-lost))
  js/undefined)

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

(comment
  (util/doiter [x (.. adapter -features values)]
    (prn x))
  )


;;; Object Constructors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defgpu buffer
  [size             :u64]
  [usage            ::buffer-usage]
  [mappedAtCreation :bool false])

(defgpu texture
  [size          :ivec3]
  [mipLevelCount :i32 1]
  [sampleCount   :i32 1]
  [dimension     ::texture-dimension "2d"]
  [format        ::texture-format]
  [usage         ::texture-usage]
  [viewFormats   (:array ::texture-format) util/empty-array])

(defgpu texture-view
  [format          ::texture-format]
  [dimension       ::texture-view-dimension]
  [aspect          ::texture-aspect]
  [mipLevelCount   :u32]
  [arrayLayerCount :u32]
  [baseMipLevel    :u32 0]
  [baseArrayLayer  :u32 0])

(defgpu external-texture
  "Creates a texture from an external video object."
  {:create import}
  [source     :js/HTMLVideoElement]
  [colorSpace #{"srgb" "display-p3"} "srgb"])

(defgpu sampler
  [addressModeU  ::address-mode "clamp-to-edge"]
  [addressModeV  ::address-mode "clamp-to-edge"]
  [addressModeW  ::address-mode "clamp-to-edge"]
  [magFilter     ::filter-mode        "nearest"]
  [minFilter     ::filter-mode        "nearest"]
  [mipmapFilter  ::mipmap-filter-mode "nearest"]
  [lodMinClamp   :f32  0]
  [lodMaxClamp   :f32 32]
  [compare       ::compare-function]
  [maxAnisotropy ::u16 1])

(defgpu bind-group-layout
  [entries (:array ::bind-group-layout-entry)])

(defgpu bind-group
  [layout ::bind-group-layout]
  [entries (:array ::bind-group-entry)])

(defgpu pipeline-layout
  [bindGroupLayouts (:array ::bind-group-layout)])

(defgpu shader-module
  [code      :str]
  [sourceMap :object]
  [hints     {:str ::shader-module-compilation-hints}])

(defgpu compute-pipeline
  [compute ::programmable-stage])

(defgpu render-pipeline
  [layout       #{::pipeline-layout "auto"}]
  [vertex       ::vertex-state]
  [primitive    ::primitive-state     util/empty-obj]
  [depthStencil ::depth-stencil-state]
  [multisample  ::multisample-state   util/empty-obj]
  [fragment     ::fragment-state])

(defgpu command-encoder)

;;(defgpu render-bundle-encoder)

(defgpu query-set
  [type  ::query-type]
  [count :i32])


;;; Buffers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (def my-buf (buffer "test" 4096 (bit-or js/GPUBufferUsage.COPY_DST js/GPUBufferUsage.VERTEX) false))
  (.destroy my-buf)
  (.writeBuffer (.-queue device)
                my-buf
                0
                (js/Float32Array. #js [1 2 3]))
  (js/console.log my-buf)

  (def uni-buf (buffer "uniform" 128 (bit-or js/GPUBufferUsage.MAP_WRITE js/GPUBufferUsage.UNIFORM) true))
  (def uni-ptr (.getMappedRange uni-buf))
  (js/console.log uni-ptr)
  )


;;; Textures & Texture Views
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;; Samplers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;; Resource Binding
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;; Shader Modules
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- dump-errors* [^js/GPUCompilationInfo info]
  (util/doarray [^js/GPUCompilationMessage x (.-messages info)]
    (js/console.log (util/strip-ansi (.-message x)))))

(defn- dump-errors [^js/GPUShaderModule mod]
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
            "bgra8unorm"
            (.getPreferredFormat ctx adapter))))
  preferred-format)

(defn html-setup-target [^js/GPUCanvasContext ctx ^js/GPUCanvasConfiguration cfg]
  (util/compat-old true
    {:ff 105}
    (let [^js/HTMLCanvasElement c cfg.canvas
          sz (.-size cfg)]
      (aset sz 0 (.-width  c))
      (aset sz 1 (.-height c))))
  ;; TODO canvas disappear in FF until toggling its styles in the inspector :/
  (.configure ctx cfg))

(defn ^js/GPUCanvasConfiguration html-render-target
  [^js/HTMLCanvasElement canvas]
  (let [^js/GPUCanvasContext ctx (.getContext canvas "webgpu")]
    ;; TODO usage, viewFormats, colorSpace, alphaMode
    #js {:device device
         :format (detect-preferred-format ctx)
         :size   #js [1 1 1] ; TODO deprecated, still required by FF 105
         :canvas canvas
         :ctx    ctx}))

(defn html-destroy-target [^js/GPUCanvasContext ctx]
  (.unconfigure ctx))
