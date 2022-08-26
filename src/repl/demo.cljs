(ns repl.demo
  (:require [bllm.gpu  :as gpu]
            [bllm.disp :as disp]
            [bllm.meta :as meta]
            [bllm.time :as time]
            [bllm.util :as util :refer [def1]]
            [bllm.wgsl :as wgsl :refer [texture]]))

;; https://learnopengl.com/PBR/Theory
;; https://bruop.github.io/ibl/

;; TODO this is starting to get polluted, move things to gfx library
;; - right now just porting old WebGL2 code, need to push that wgsl codegen
;; - plan is to get working pipelines, gpu resources, then simplify, then move

(meta/defenum bind-group
  FRAME
  PASS
  EFFECT
  MODEL)

(wgsl/defconst E    2.7182818284590452)
(wgsl/defconst PI   3.1415926535897932)
(wgsl/defconst PI-2 1.5707963267948966)
(wgsl/defconst PI-4 0.7853981633974483)
(wgsl/defconst PI*2 6.2831853071795864)
(wgsl/defconst EPSILON 1e-10)

(wgsl/definterpolant io-texcoord    0 :vec2)
(wgsl/definterpolant io-texcoord-3d 1 :vec3)

(wgsl/defsampler linear-mip FRAME 1)

(wgsl/defuniform frame
  ""
  FRAME 0
  [time       :vec4]
  [sin-time   :vec4]
  [delta-time :vec4]
  [number     :u32])

(wgsl/defuniform screen
  PASS 3
  [proj-params :vec4]
  [params      :vec4]
  [z-buffer    :vec4])

(wgsl/defuniform camera
  ""
  PASS 0
  [view          :mat4]
  [proj          :mat4]
  [view-proj     :mat4]
  [view-inv      :mat4]
  [proj-inv      :mat4]
  [view-proj-inv :mat4])

(wgsl/defvertex-attr in-position 0 :vec3
  "Unpacked vertex position in model space.")

(wgsl/definterpolant io-position :builtin :vec4
  "Unpacked vertex position in clip space. Required vertex output.")

(wgsl/defdraw-target out-color 0 :vec4
  "Generic pixel color.")

(wgsl/defvertex vs-demo
  (io-position = in-position))

(wgsl/defpixel ps-demo
  (out-color = (vec4 0.42 0.69 0 1)))

(wgsl/deftexture tex-skybox PASS 0 :tex-cube :f32)

(wgsl/defvertex vs-sky
  (io-texcoord-3d = (normalize in-position))
  (io-position    = (-> camera.view-inv.3.xyz
                        (+ in-position)
                        (vec4 1)
                        (* camera.view-proj)
                        (.xyww))))

(comment (js/console.log vs-sky.wgsl)
         (js/console.log ps-sky.wgsl))

(wgsl/defpixel ps-sky
  (out-color = (texture-sample tex-skybox linear-mip io-texcoord-3d)))

(wgsl/defun screen-pos :vec4 [pos :vec4]
  (let [h (pos * 0.5)]
    (vec4 ((vec2 h.x (h.y * screen.proj-params.x)) * 2) pos.zw)))

(wgsl/defun linear-01-depth :f32 [z :f32]
  (1 / (screen.z-buffer.x * z + screen.z-buffer.y)))

(wgsl/defun linear-eye-depth :f32 [z :f32]
  (1 / (screen.z-buffer.z * z + screen.z-buffer.w)))

(wgsl/defun world-position-from-depth :vec3 [coord :vec2 depth :f32]
  (let [clip-pos       ((vec4 coord depth 1) * 2 - 1)
        homogenous-pos (camera.view-proj-inv * clip-pos)]
    (homogenous-pos.xyz / homogenous-pos.w)))

(wgsl/defun hsv->rgb [c :vec3]
  (let [^:const
        k (vec4 1 (2 / 3) (1 / 3) 3)
        p (abs ((fract (c.xxx + k.xyz)) * 6 - k.www))]
    (c.z * (mix k.xxx (saturate (p - k.xxx)) c.y))))

(wgsl/defun rgb->hsv :vec3 [c :vec3]
  (let [^:const
        k (vec4 0 (-1 / 3) (2 / 3) -1)
        p (mix (vec4 c.bg k.wz)
               (vec4 c.gb k.xy)
               (step c.b c.g))
        q (mix (vec4 p.xyz c.r)
               (vec4 c.r p.yzx)
               (step p.x c.r))
        d (q.x - (min q.w q.y))]
    (vec3 (abs (q.z + (q.w - q.y) / (6 * d + EPSILON)))
          (d / (q.x + EPSILON))
          q.x)))

(wgsl/defun srgb->linear :vec3 [c :vec3]
  )

(wgsl/defun linear->srgb :vec3 [c :vec3]
  )

(wgsl/defun luminance :f32 [c :vec3]
  (dot c (vec3 0.2126729 0.7151522 0.0721750)))

(wgsl/defun distribution-ggx :f32 [n-dot-h :f32 roughness :f32]
  (let [a  (roughness * roughness)
        a2 (a * a)
        d  (n-dot-h * n-dot-h * (a2 - 1) + 1)]
    (a2 / (max (PI * d * d) EPSILON))))

(wgsl/defun geometry-schlick-ggx :f32 [n-dot-v :f32 k :f32]
  (n-dot-v / (n-dot-v * (1 - k) + k)))

(wgsl/defun geometry-smith :f32 [n-dot-v :f32 n-dot-l :f32 roughness :f32 bias :f32 scale :f32]
  (let [r (roughness + bias)
        k ((r * r) / scale)]
    (* (geometry-schlick-ggx n-dot-v k)
       (geometry-schlick-ggx n-dot-l k))))

(wgsl/defun fresnel-schlick :vec3 [cos-theta :f32 f0 :vec3]
  (f0 + (1 - f0) * (pow (1 - cos-theta) 5)))

(wgsl/defun fresnel-schlick-roughness :vec3 [cos-theta :f32 f0 :vec3 roughness :f32]
  (f0 + ((max (vec3 (1 - roughness)) f0) - f0) * (pow (1 - cos-theta) 5)))

(wgsl/defstruct Surface
  [world-position :vec3]
  [world-normal   :vec3]
  [albedo         :vec3]
  [emissive       :vec3]
  [metallic       :f32]
  [roughness      :f32]
  [ao             :f32]
  [occlusion      :f32])

(wgsl/deftexture irradiance-tex PASS 1 :tex-cube :f32)
(wgsl/deftexture prefilter-tex  PASS 2 :tex-cube :f32)
(wgsl/deftexture brdf-lut       PASS 3 :tex-3d   :f32)

(wgsl/defun lighting :vec3 [data Surface]
  (let [light-pos (vec3 0 0 0)
        light-color (vec3 )

        n (normalize data.world-normal)
        v (normalize (camera.view-inv.3.xyz - data.world-position))

        specular           0.04
        f0                 (mix (vec3 specular) data.albedo data.metallic)
        one-minus-specular (1 - specular)
        n-dot-v            (clamp (dot data.world-normal v) EPSILON 1)
        one-minus-metallic (1 - data.metallic)

        ;; Light radiance
        l  (normalize (light-pos - data.world-position))
        vl (v + l)
        h  (normalize vl)
        distance     (length vl)
        attenuation  (1 / (distance * distance))
        radiance     (light-color * attenuation)

        ;; Cook-Torrance BRDF
        n-dot-h (max (dot n h) EPSILON)
        n-dot-l (max (dot n l) EPSILON)
        ndf     (distribution-ggx n-dot-h data.roughness)
        g       (geometry-smith n-dot-v n-dot-l data.roughness 1 8)
        f       (fresnel-schlick (saturate (dot h v)) f0)

        ks ((ndf * g * f) / (4 * n-dot-v * n-dot-l))
        kd (((vec3 1) - f) * one-minus-metallic)
        lo ((kd * data.albedo / PI * ks) * radiance * n-dot-l)

        ;; Diffuse irradiance
        irradiance (texture-load irradiance-tex data.world-normal 0)
        diffuse    (data.albedo * one-minus-specular * one-minus-metallic)

        ;; Specular reflectance
        max-reflection-lod 12
        r    (reflect (- v) data.world-normal)
        pf   (texture-load prefilter-tex r (data.roughness * max-reflection-lod))
        brdf (texture-sample brdf-lut linear-mip (vec2 n-dot-v data.roughness))
        fr   ((max (vec3 (1 - data.roughness)) f0) - f0)
        ss   ((f0 + fr * (pow (1 - n-dot-v) 5)) * brdf.x + brdf.y)

        ;; Multiple scattering
        ems     (1 - (brdf.x + brdf.y))
        favg    (f0 + (1 - f0) / 21)
        ms      (ems * ss * favg / (1 - favg * ems))
        diffuse (diffuse * (1 - ss - ms))

        ;; Final mix
        ambient (ss * pf.rgb + (ms + diffuse) * irradiance.rgb)]
    (lo + ambient)))

(wgsl/defun window-depth :f32 [depth :tex-2d coord :vec2]
  (.r (texture-load depth coord 0)))

(wgsl/deftexture gbuffer-albedo     PASS 4  :tex-2d :f32)
(wgsl/deftexture gbuffer-normal     PASS 6  :tex-2d :f32)
(wgsl/deftexture gbuffer-emissive   PASS 7  :tex-2d :f32)
(wgsl/deftexture gbuffer-properties PASS 8  :tex-2d :f32)
(wgsl/deftexture gbuffer-custom     PASS 9  :tex-2d :f32)
(wgsl/deftexture gbuffer-depth      PASS 10 :tex-2d :f32)

(wgsl/defkernel lighting-cs [8 8]
  ;; TODO from material function
  (let [^:mut data (Surface)
        albedo&specular (texture-load gbuffer-albedo io-texcoord 0)] ; TODO texcoord from invocation
    ;; TODO use specular
    (data.albedo = albedo&specular.rgb))
  #_(let [depth ()]))

(wgsl/defuniform pp
  EFFECT 0
  [gamma    :f32]
  [exposure :f32]
  [dist1    :vec4]
  [dist2    :vec4]
  [vignette-params :vec3]
  [vignette-color  :vec3])

(wgsl/defun ^:feature tonemap [c :vec3])

(wgsl/defun tonemap:linear [c :vec3]
  (pow (c * pp.exposure)
       (vec3 (1 / pp.gamma))))

(wgsl/defun tonemap:reinhard [c :vec3]
  (let [x (c * pp.exposure)]
    (pow (x / (1 + x))
         (vec3 (1 / pp.gamma)))))

(wgsl/defun tonemap:uncharted-2 [x :vec2]
  )

(wgsl/deftexture bloom-tex   EFFECT 1 :tex-2d :f32)
(wgsl/deftexture grain-tex   EFFECT 2 :tex-2d :f32)
(wgsl/deftexture grading-lut EFFECT 3 :tex-3d :f32)

(wgsl/defun ^:feature distort-uv [uv :vec2]
  uv
  #_
  (let [center pp.dist1.xy ; TODO don't want to manually pack/unpack such things
        axis   pp.dist1.zw
        theta  pp.dist2.x
        sigma  pp.dist2.y
        scale  pp.dist2.z
        ampli  pp.dist2.w
        uv  ((uv - 0.5) * (scale + 0.5))
        ruv (axis * (uv - 0.5 - center))
        ru  (length ruv)
        rus (ru * sigma)
        ru  (if (intensity > 0)
              ((tan (ru * theta)) * (1 / rus))
              ((1 / ru) * theta * (atan rus)))]
    (uv + ruv * (ru - 1))))

(wgsl/defun ^:feature bloom [c :vec3 uv :vec2]
  (c + (.rgb (texture-load bloom-tex uv 0)) * pp.bloom.rgb + pp.bloom.w))

(wgsl/defun ^:feature vignette [c :vec3 uv :vec2]
  (let [intensity  pp.vignette-params.x
        roundness  pp.vignette-params.y
        smoothness pp.vignette-params.z
        color      pp.vignette-color.rgb
        center     (vec2 0.5)
        dist ((abs (uv - center)) * intensity)
        dist (vec2 (dist.x * roundness) dist.y)
        f    (pow (saturate (1 - (dot dist dist))) smoothness)]
    (c * (mix color (vec3 1) f))))

(wgsl/defun ^:feature color-grading [c :vec3]
  (as-> c x
    (linear->srgb x)
    (texture-load grading-lut x 0)
    (srgb->linear x)))

#_
(wgsl/defun ^:feature chromatic-aberration [uv :vec2]
  {:off (screen-color uv)
   :on  (let [coord (2 * uv - 1)
              end   (uv - coord * (dot coord coord) * pp.chroma)
              delta ((end - uv) / 3)
              x (texture screen-tex uv)
              y (texture screen-tex (distort-uv (uv + delta)))
              z (texture screen-tex (distort-uv (uv + delta * 2)))]
          (vec3 x.r y.g z.b))})

(wgsl/defun ^:feature grain [c :vec3 uv :vec2]
  (let [intensity pp.grain.x
        response  pp.grain.y
        g (texture-load grain-tex (uv * pp.grain-st.xy + pp.grain-st.zw) 0)
        g ((g.x - 0.5) * 2.0)
        l (mix 1 (1 - (sqrt (luminance c))) response)]
    (c + c * g * intensity * l)))

(wgsl/defkernel post-process [8 8]
  (let [xy (vec2) ; TODO global invocation id
        uv (distort-uv xy)]
    (-> (vec3) #_(chromatic-aberration uv)
        (bloom uv)
        (tonemap)
        (vignette uv)
        (color-grading)
        (grain uv)
        (vec4 1)
        #_
        (->> (texture-store out-color xy)))))

(wgsl/defkernel bloom-prefilter [8 8]
  )

(wgsl/defkernel blur-h [8 8]
  )

(wgsl/defkernel blur-v [8 8]
  )

#_
(wgsl/defkernel upsample [xy 8 8]
  (let [hi (texture screen-tex   xy)
        lo (texture upsample-tex xy)] ; TODO generic storage input
    (texture-store out-color xy (vec4 (mix hi.rgb lo.rgb pp.scatter) 1))))

(comment (wgsl/compile lighting-cs))

(js/console.log screen-pos.wgsl)
(js/console.log rgb->hsv.wgsl)
(js/console.log distribution-ggx.wgsl)
(js/console.log geometry-smith.wgsl)
(js/console.log fresnel-schlick.wgsl)
(js/console.log lighting.wgsl)
(js/console.log lighting-cs.wgsl)
(js/console.log window-depth.wgsl)

#_
(defn test [pos b]
  (util/dump-env))
#_(test 1 2)

(comment


  (wgsl/defprimitive raster-default)

  (wgsl/defprimitive raster-cw
    :cull-mode  :front
    :front-face :cw)

  (wgsl/defstencil-face default-face)

  (wgsl/defdepth-stencil default-ds
    :format :depth24plus-stencil8)

  (wgsl/defdepth-stencil test-ds
    :format :depth16unorm
    :stencil-front default-face)

  (wgsl/defmultisample default-msaa)

  (wgsl/defblend-comp blend-default)
  (wgsl/defblend-comp blend-something
    :operation  :op-reverse-subtract
    :src-factor :one-minus-src
    :dst-factor :one-minus-dst-alpha)

  (wgsl/defblend blend-test
    :color blend-something
    :alpha blend-default)

  (wgsl/defstruct Hello
    [world :vec2]
    [value :u32]
    [hello :f32]
    [slide :vec3])

  (wgsl/defconst log-base-10
    (1 / (log2 10)))

  (wgsl/defun log10 [n #{:float :vec2 :vec3 :vec4}] ; TODO union type -> overload sets
    ((log2 n) * log-base-10))

  (wgsl/defun attenuation [light :vec3]
    (let [dist (length light)]
      (1 / (dist * dist))))

  (wgsl/defun point-light [view-pos :vec3 world-pos :vec3 ^:out h :vec3]
    (let [light-dir (normalize (light-pos - world-pos))
          vl (view-pos + light-dir)
          h (normalize vl)]
      (light-color * (attenuation vl))))

  (wgsl/defun spot-light []
    )

  (wgsl/defun directional-light [view-pos :vec3 world-pos :vec3 ^:out h :vec3]
    )

  (wgsl/defstorage s-mydata grp-pass 1 :vec4 :r)

  (wgsl/deftexture tex-albedo grp-effect 0 :tex-2d :f32)

  (wgsl/defsampler sam-default grp-frame 1)
  (wgsl/defsampler sam-linear-mip grp-frame 2)

  ;; TODO gpu/defres
  (wgsl/defgroup g-frame frame)

  (wgsl/defgroup g-effect tex-albedo)

  (wgsl/deflayout l-demo g-frame g-effect)

  (wgsl/defrender demo-render
    "Assemble the pipeline here, only by composition."
    vs-demo
    ps-demo
    l-demo
    raster-cw
    default-ds)

  )

(comment (.-deps demo-render))

(comment
  (cljs.pprint/pprint
   (macroexpand-1
    '(wgsl/defprimitive raster-test
       :front-face :ccw
       :cull-mode  :back
       :topology   :line-list
       )))
  )

(comment
  (cljs.pprint/pprint
   (macroexpand-1
    '(wgsl/defrender demo-render
       "Assemble the pipeline here, only by composition."
       ps-demo
default-ds
       l-demo
       raster-cw
       vs-demo
       )))
  )

















(def1 vbo nil)
(def1 vbo2 nil)
(def1 ubo nil)
(def1 bind-grp-layout nil)
(def1 bind-grp nil)
(def1 pipe-layout nil)
(def1 shader-mod nil)
(def1 render-pipe nil)

(defn setup-target [pass]
  (set! (.-view (aget pass.colorAttachments 0))
        (.. (aget disp/viewports 0) -target -ctx getCurrentTexture createView)))

(defn- upload-ubo [v]
  (aset util/scratch 0 v)
  (.writeBuffer gpu/device.queue ubo 0 util/scratch 0 4))

(defn scene
  "Setup the demo scene."
  []
  #_
  (let [vertices (js/Float32Array. #js [-1 -1 0 1
                                        0  1 0 1
                                        1 -1 0 1])]
    (set! vbo (gpu/buffer "Demo VBO" (.-byteLength vertices)
                          (bit-or gpu/usage-vertex gpu/usage-copy-dst)
                          false))
    (.writeBuffer gpu/device.queue vbo 0 vertices))

  #_
  (let [vertices (js/Float32Array. #js [-0.5 -0.5 0 1
                                        0  0.5 0 1
                                        0.5 -0.5 0 1])]
    (set! vbo2 (gpu/buffer "Demo VBO" (.-byteLength vertices)
                          (bit-or gpu/usage-vertex gpu/usage-copy-dst)
                          false))
    (.writeBuffer gpu/device.queue vbo2 0 vertices))

  #_
  (set! ubo (gpu/buffer "Demo UBO" 4096
                        (bit-or gpu/usage-uniform gpu/usage-copy-dst)
                        false))

  #_
  (set! bind-grp-layout (gpu/bind-group-layout "Demo Bind Group Layout"
                                               (util/array
                                                #js {:binding 0
                                                     :visibility gpu/stage-fragment
                                                     :buffer nil})))

  #_
  (set! bind-grp (gpu/bind-group "Demo Bind Group"
                                 bind-grp-layout
                                 (util/array
                                  #js {:binding 0
                                       :resource #js {:buffer ubo}})))

  #_
  (set! pipe-layout (gpu/pipeline-layout "Demo Pipeline Layout"
                                         (util/array bind-grp-layout)))

  #_
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
  return vec4<f32>(abs(sinTime)/2.0f, 0.69f, 0.42f, 1f);
}
"
         js/undefined js/undefined))

  #_
  (gpu/vertex shader-mod "demo_vert"
              #js [#js {:attributes #js [#js {:shaderLocation in-position.bind
                                              :offset 0
                                              :format "float32x4"}]
                        :arrayStride 16
                        :stepMode "vertex"}])

  #_
  (gpu/fragment shader-mod "demo_frag"
                #js [#js {:format gpu/preferred-format}])

  #_
  (set! render-pipe
        (gpu/render-pipeline
         "Demo Render Pipeline"
         pipe-layout
         #_raster-cw
         raster-default
         js/undefined ; depth/stencil
         js/undefined ; multisample
         ))
  )

























(def pass-desc
  #js {:colorAttachments #js [#js {:clearValue #js [0.42 0 0.69 1]
                                   :loadOp  "clear"
                                   :storeOp "store"}]})

(defn pre
  "Application logic executed before each simulation tick."
  []
  )

(defn setup-sine []
  (js/Math.sin (* 0.00015 time/unscaled-now)))

(defn post [])
#_
(defn post
  "Application logic executed after each simulation tick."
  []

  (setup-target pass-desc)
  (upload-ubo (setup-sine))

  (let [enc (gpu/command-encoder "Demo Frame")
        cmd (.beginRenderPass enc pass-desc)]
    (.setPipeline cmd render-pipe)
    (.setVertexBuffer cmd 0 vbo)
    (.setBindGroup cmd 0 bind-grp)
    (.draw cmd 3)
    (.end cmd)
    (gpu/submit1 (.finish enc))))

(comment (js/console.log gpu/device)
         (js/console.log wgsl/node-defs))
