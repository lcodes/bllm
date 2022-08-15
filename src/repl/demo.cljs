(ns repl.demo
  (:require [bllm.gpu  :as gpu]
            [bllm.disp :as disp]
            [bllm.meta :as meta]
            [bllm.time :as time]
            [bllm.util :as util :refer [def1]]
            [bllm.wgsl :as wgsl]))

;; TODO hardcoded shortest path to triangle
(def1 vbo nil)
(def1 ubo nil)
(def1 bind-grp-layout nil)
(def1 bind-grp nil)
(def1 pipe-layout nil)
(def1 shader-mod nil)
(def1 render-pipe nil)

(wgsl/defprimitive raster-default)

(wgsl/defstencil-face default-face)

(wgsl/defdepth-stencil default-ds
  :format :depth24plus-stencil8)

(wgsl/defmultisample default-msaa)

(wgsl/defblend-comp blend-default)
(wgsl/defblend-comp blend-something
  :operation  :op-reverse-subtract
  :src-factor :one-minus-src
  :dst-factor :one-minus-dst-alpha)

(wgsl/defblend blend-test
  :color blend-something
  :alpha blend-default)

(wgsl/defvertex-attr in-position 0 :vec4
  "Unpacked vertex position in model space.")

(wgsl/definterpolant io-position :builtin :vec4
  "Unpacked vertex position in clip space. Required vertex output.")

(wgsl/defdraw-target out-color 0 :vec4
  "Generic pixel color.")

(meta/defenum bind-group
  grp-frame
  grp-pass
  grp-effect
  grp-model)

(wgsl/defstruct Hello
  [world :vec2]
  [value :u32]
  [hello :f32]
  [slide :vec3])

(wgsl/defuniform ub-frame
  ""
  grp-frame 0
  [hello      Hello]
  [time       :vec4]
  [sin-time   :vec4]
  [delta-time :vec4]
  [number     :u32])

(wgsl/defstorage s-mydata grp-pass 1 :vec4 :r
  "Test storage")

(wgsl/deftexture tex-albedo grp-effect 0 :tex-2d :f32
  "Test texture")

(wgsl/defsampler sam-default grp-frame 1
  "Test sampler")

(wgsl/defgroup g-frame ub-frame)

(wgsl/defgroup g-effect tex-albedo)

(wgsl/deflayout l-demo g-frame g-effect)

(wgsl/defvertex vs-demo
  "Vertex shader in ClojureScript!"
  (set! io-position in-position))

(wgsl/defpixel ps-demo
  "Fragment/pixel shader in ClojureScript!"
  (set! out-color (vec4 0.42 0.69 0 1)))

(wgsl/defrender demo-render
  "Assemble the pipeline here, only by composition."
  vs-demo ps-demo l-demo)

;; TODO this can be generated from the above nodes, when they are implemented.
(def pass-desc
  ;; TODO this will need work, FF still uses the older API, chrome doesn't see an adapter
  #js {:colorAttachments
       #js [#js {:clearValue #js [0.92 0 0.69 1]
                 :loadOp "clear"
                 :storeOp "store"}]})

(defn scene
  "Setup the demo scene."
  []
  ;; TODO replace with buffer management
  (let [vertices (js/Float32Array. #js [-1 -1 0 1
                                        0  1 0 1
                                        1 -1 0 1])]
    (set! vbo (gpu/buffer "Demo VBO" (.-byteLength vertices)
                          (bit-or gpu/usage-vertex gpu/usage-copy-dst)
                          false))
    (.writeBuffer gpu/device.queue vbo 0 vertices))

  (set! ubo (gpu/buffer "Demo UBO" 4096
                        (bit-or gpu/usage-uniform gpu/usage-copy-dst)
                        false))

  (set! bind-grp-layout (gpu/bind-group-layout "Demo Bind Group Layout"
                                               (util/array
                                                #js {:binding 0
                                                     :visibility gpu/stage-fragment
                                                     :buffer nil})))

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
         js/undefined js/undefined))

  ;; TODO entirely generated from shader macros
  (gpu/vertex shader-mod "demo_vert"
              #js [#js {:attributes #js [#js {:shaderLocation in-position.bind
                                              :offset 0
                                              :format "float32x4"}]
                        :arrayStride 16
                        :stepMode "vertex"}])

  (gpu/fragment shader-mod "demo_frag"
                #js [#js {:format gpu/preferred-format}])

  (set! render-pipe
        (gpu/render-pipeline
         "Demo Render Pipeline"
         pipe-layout
         raster-default
         js/undefined ; depth/stencil
         js/undefined ; multisample
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

  (aset util/scratch 0 (js/Math.sin (* 0.00025 time/unscaled-now)))
  (.writeBuffer gpu/device.queue ubo 0 util/scratch 0 4)

  ;; TODO render graph, separate passes
  (let [enc (gpu/command-encoder "test")
        cmd (.beginRenderPass enc pass-desc)
        vp  (.. (aget disp/viewports 0) -canvas)]
    (.setPipeline cmd render-pipe)
    (.setVertexBuffer cmd 0 vbo)
    (.setBindGroup cmd 0 bind-grp)
    (.draw cmd 3)
    (.end cmd)
    (.submit gpu/device.queue (util/array (.finish enc)))))
