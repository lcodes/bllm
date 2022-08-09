(ns repl.demo
  (:require [bllm.gpu  :as gpu]
            [bllm.disp :as disp]
            [bllm.time :as time]
            [bllm.util :as util :refer [def1]]))

;; TODO hardcoded shortest path to triangle
(def1 vbo nil)
(def1 ubo nil)
(def1 bind-grp-layout nil)
(def1 bind-grp nil)
(def1 pipe-layout nil)
(def1 shader-mod nil)
(def1 render-pipe nil)

(def pass-desc
  ;; TODO this will need work, FF still uses the older API, chrome doesn't see an adapter
  #js {:colorAttachments
       #js [#js {;:clearValue (js/Float32Array. 0.92 0 0.69 1)
                 :loadValue #js {:r 0.69 :g 0 :b 0.42 :a 1}
                 :storeOp "store"}]})

(defn scene
  "Setup the demo scene."
  []
  ;; TODO replace with buffer management
  (let [vertices (js/Float32Array. #js [-1 -1 0 1
                                        0  1 0 1
                                        1 -1 0 1])]
    (set! vbo (gpu/buffer "Demo VBO" (.-byteLength vertices)
                          (bit-or js/GPUBufferUsage.VERTEX js/GPUBufferUsage.COPY_DST)
                          false))
    (.writeBuffer gpu/device.queue vbo 0 vertices))

  (set! ubo (gpu/buffer "Demo UBO" 4096
                        (bit-or js/GPUBufferUsage.UNIFORM js/GPUBufferUsage.COPY_DST)
                        false))

  (set! bind-grp-layout (gpu/bind-group-layout "Demo Bind Group Layout"
                                               (util/array
                                                #js {:binding 0
                                                     :visibility js/GPUShaderStage.FRAGMENT
                                                     :buffer #js {}})))

  (set! bind-grp (gpu/bind-group "Demo Bind Group"
                                 bind-grp-layout
                                 (util/array
                                  #js {:binding 0
                                       :resource #js {:buffer ubo}})))

  (set! pipe-layout (gpu/pipeline-layout "Demo Pipeline Layout"
                                         (util/array bind-grp-layout)))

  ;; TODO replace with shader subsystem
  (set! shader-mod
        (gpu/shader-module
         "Demo Shaders"
         "
@vertex
fn demo_vert(@location(0) position : vec4<f32>) -> @builtin(position) vec4<f32> {
  return position;
}

@group(0) @binding(0) var<uniform> sinTime : f32;

@fragment
fn demo_frag() -> @location(0) vec4<f32> {
  return vec4<f32>(0.42f, abs(sinTime)/2.0f, 0.69f, 1f);
}
"
         js/undefined nil))

  ;; TODO entirely generated from shader macros
  (set! render-pipe
        (gpu/render-pipeline
         "Demo Render Pipeline"
         pipe-layout
         #js {:module shader-mod
              :entryPoint "demo_vert"
              :buffers #js [#js {:attributes #js [#js {:shaderLocation 0
                                                       :offset 0
                                                       :format "float32x4"}]
                                 :arrayStride 16
                                 :stepMode "vertex"}]}
         #js {:topology "triangle-list"}
         js/undefined
         js/undefined
         #js {:module shader-mod
              :entryPoint "demo_frag"
              :targets #js [#js {:format gpu/preferred-format}]}
         ))
  )

(defn pre
  "Application logic executed before each simulation tick."
  []
  )

(defn post
  "Application logic executed after each simulation tick."
  []
  ;; TODO properly thread the viewport around,
  (set! (.-view (aget pass-desc.colorAttachments 0))
        (.. (aget disp/viewports 0) -target -ctx getCurrentTexture createView))

  (aset util/scratch 0 (js/Math.sin (* 0.0025 time/unscaled-now)))
  (.writeBuffer gpu/device.queue ubo 0 util/scratch 0 4)

  ;; TODO render graph, separate passes
  (let [enc (gpu/command-encoder "test")
        cmd (.beginRenderPass enc pass-desc)
        vp (.. (aget disp/viewports 0) -canvas)]
    (.setPipeline cmd render-pipe)
    (.setVertexBuffer cmd 0 vbo)
    (.setBindGroup cmd 0 bind-grp)
    (.draw cmd 3)
    (.endPass cmd)
    (.submit gpu/device.queue (util/array (.finish enc)))))
