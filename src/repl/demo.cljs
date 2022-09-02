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



(wgsl/defconst E    2.7182818284590452)
(wgsl/defconst PI   3.1415926535897932)
(wgsl/defconst PI-2 1.5707963267948966)
(wgsl/defconst PI-4 0.7853981633974483)
(wgsl/defconst PI*2 6.2831853071795864)
(wgsl/defconst EPSILON 1e-10)

(wgsl/defstruct Test
  [hello :u32])

(meta/defenum bind-group
  FRAME
  PASS
  EFFECT
  MODEL)

(wgsl/defgroup ^:override frame-data
  "Values uploaded once per frame to the GPU."
  FRAME
  (wgsl/defbuffer frame
    [time       :vec4]
    [sin-time   :vec4]
    [delta-time :vec4]
    [number     :u32])
  (wgsl/defsampler linear)
  (wgsl/defsampler linear-mip)
  (wgsl/defsampler linear-repeat))

(wgsl/defstorage cell-out MODEL 0 :view-2d :rgba8unorm :write)

(wgsl/defgroup post-data ;; TODO simplify, compose with generic bindings
  EFFECT
  (wgsl/deftexture screen-tex  :view-2d :f32)
  (wgsl/deftexture input-tex   :view-2d :f32)
  (wgsl/deftexture bloom-tex   :view-2d :f32)
  (wgsl/deftexture grain-tex   :view-2d :f32)
  (wgsl/deftexture grading-lut :view-3d :f32)
  (wgsl/defbuffer pp
    [scatter  :f32]
    [gamma    :f32]
    [exposure :f32]
    [dist1    :vec4]
    [dist2    :vec4]
    [bloom    :vec4]
    [grain    :vec4]
    [grain-st :vec4]
    [chroma   :f32]
    [vignette-params :vec3]
    [vignette-color  :vec3]))

(wgsl/defbuffer camera
  ""
  PASS 0
  [view          :mat4]
  [proj          :mat4]
  [view-proj     :mat4]
  [view-inv      :mat4]
  [proj-inv      :mat4]
  [view-proj-inv :mat4])

(wgsl/defbuffer screen
  PASS 1
  [proj-params :vec4]
  [params      :vec4]
  [z-buffer    :vec4])

(wgsl/defgroup gbuffer-data
  PASS
  camera
  screen
  (wgsl/deftexture gbuffer-albedo     :view-2d :f32)
  (wgsl/deftexture gbuffer-normal     :view-2d :f32)
  (wgsl/deftexture gbuffer-emissive   :view-2d :f32)
  (wgsl/deftexture gbuffer-properties :view-2d :f32)
  (wgsl/deftexture gbuffer-custom     :view-2d :f32)
  (wgsl/deftexture gbuffer-depth      :view-2d :f32))

(wgsl/definterpolant io-texcoord 0 :vec2)

(wgsl/defbuffer hello EFFECT 0
  [switch :bool])

(wgsl/defun branch-test :vec3 [a :vec3 b :vec3]
  (let [d (length (a - b))
        d (cond
            (d > 10) 0.25
            (d >  5) 0.75
            :else 1)
        b (-> (cross a b)
              (vec4 1)
              (* camera.view-proj)
              (normalize))]
    (if hello.switch
      (a + b * d)
      (a * b))))

(wgsl/defun case-test :u32 [x :u32]
  (case x
    (1
     2
     3) (* x 2)
    100 (* x 3)
    101 (* x 5)
    x))

(wgsl/defun block-test :u32 [x :u32]
  (do 1 (= x 12) 3))

(wgsl/defun nested-let :i32 []
  (let [x (let [y (let [z 12]
                    (+ z 2))]
            (let [out (* y 8)]
              (1 + out * out)))]
    (/ x 2)))

(comment (js/console.log branch-test.wgsl))

(wgsl/defbuiltin vertex-index         :vertex   :in  :u32)
(wgsl/defbuiltin instance-index       :vertex   :in  :u32)
(wgsl/defbuiltin position             :vertex   :out :vec4)
(wgsl/defbuiltin frag-coord           :fragment :in  :vec4 {:name "position"})
(wgsl/defbuiltin front-facing         :fragment :in  :bool)
(wgsl/defbuiltin frag-depth           :fragment :out :f32)
(wgsl/defbuiltin local-invocation-id  :compute  :in  :u32)
(wgsl/defbuiltin global-invocation-id :compute  :in  :uvec3)
(wgsl/defbuiltin workgroup-id         :compute  :in  :uvec3)
(wgsl/defbuiltin num-workgroups       :compute  :in  :uvec3)
(wgsl/defbuiltin sample-index         :fragment :in  :u32)
(wgsl/defbuiltin sample-mask-in       :fragment :in  :u32 {:name "sample_mask"})
(wgsl/defbuiltin sample-mask          :fragment :out :u32)


(wgsl/defvertex-attr local-position 0 :vec3
  "Unpacked vertex position in model space.")

(wgsl/defdraw-target frag-color 0 :vec4
  "Generic fragment draw target.")

(wgsl/definterpolant io-texcoord-3d 0 :vec3)

(wgsl/deftexture tex-skybox EFFECT 0 :view-cube :f32)

(wgsl/defvertex vs-demo
  (position = (vec4 local-position 1)))

(wgsl/defpixel ps-demo
  (frag-color = (vec4 0.42 0 0.69 1)))

(wgsl/defvertex vs-sky
  "Infinitely large cube projected around the camera."
  (io-texcoord-3d = (normalize local-position))
  (position = (-> camera.view-inv.3.xyz
                  (+ local-position)
                  (vec4 1)
                  (* camera.view-proj)
                  (.xyww))))

(wgsl/defpixel ps-sky
  (frag-color = (texture-sample tex-skybox linear-mip io-texcoord-3d)))

(do (wgsl/compile vs-demo)
    (wgsl/compile ps-demo)
    (wgsl/compile vs-sky)
    (wgsl/compile ps-sky))















(comment (js/console.log vs-demo.wgsl)
         (js/console.log ps-demo.wgsl)

         (js/console.log vs-sky.wgsl)
         (js/console.log ps-sky.wgsl)
         )

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
  c) ; TODO

(wgsl/defun linear->srgb :vec3 [c :vec3]
  c) ; TODO

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

(wgsl/defgroup lighting-data
  EFFECT
  (wgsl/deftexture irradiance-tex :view-cube :f32)
  (wgsl/deftexture prefilter-tex  :view-cube :f32)
  (wgsl/deftexture brdf-lut       :view-2d   :f32))

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
        f       (fresnel-schlick (clamp (dot h v) 0 1) f0)

        ks ((ndf * g * f) / (4 * n-dot-v * n-dot-l))
        kd (((vec3 1) - f) * one-minus-metallic)
        lo ((kd * data.albedo / PI * ks) * radiance * n-dot-l)

        ;; Diffuse irradiance
        irradiance (vec4 0); TODO no cube load overload? (texture-load irradiance-tex data.world-normal 0)
        diffuse    (data.albedo * one-minus-specular * one-minus-metallic)

        ;; Specular reflectance
        max-reflection-lod 12.01 ; TODO float literals
        r    (reflect (- v) data.world-normal)
        pf   (texture-sample-bias prefilter-tex linear r (data.roughness * max-reflection-lod))
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

(wgsl/defun window-depth :f32 [depth :view-2d coord :vec2]
  (.r (texture-load depth coord 0)))

(comment
  (wgsl/defkernel lighting-cs [8 8]
    ;; TODO from material function
    (let [^:mut data (Surface)
          xy global-invocation-id.xy
          albedo&specular (texture-load gbuffer-albedo (ivec2 xy) 0)]
      ;; TODO use specular
      (data.albedo = albedo&specular.rgb)
      #_(let [depth ()])
      (lighting data))))

(wgsl/defun ^:feature tonemap :vec3 [c :vec3]
  (pow (c * pp.exposure)
       (vec3 (1 / pp.gamma))))

(wgsl/defun tonemap:reinhard [c :vec3]
  (let [x (c * pp.exposure)]
    (pow (x / (1 + x))
         (vec3 (1 / pp.gamma)))))

(wgsl/defun tonemap:uncharted-2 [x :vec2]
  )

(wgsl/defun ^:feature distort-uv :vec2 [uv :vec2]
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
        ^f32
        ru  (if (ampli > 0)
              (* (tan (ru * theta)) (1 / rus))
              (* (1 / ru) theta (atan rus)))]
    (uv + ruv * (ru - 1))))

(wgsl/defun ^:feature bloom :vec3 [c :vec3 uv :vec2]
  (c + (.rgb (texture-load bloom-tex (ivec2 uv) 0)) * pp.bloom.rgb + pp.bloom.w))

(wgsl/defun ^:feature vignette :vec3 [c :vec3 uv :vec2]
  (let [intensity  pp.vignette-params.x
        roundness  pp.vignette-params.y
        smoothness pp.vignette-params.z
        color      pp.vignette-color.rgb
        center     (vec2 0.5)
        dist ((abs (uv - center)) * intensity)
        dist (vec2 (dist.x * roundness) dist.y)
        f    (pow (clamp (1 - (dot dist dist)) 0 1) smoothness)]
    (c * (mix color (vec3 1) f))))

(wgsl/defun ^:feature color-grading :vec3 [c :vec3]
  (as-> c x
    (linear->srgb x)
    (texture-load grading-lut (ivec3 x) 0)
    (.rgb x)
    (srgb->linear x)))

(wgsl/defun ^:feature chromatic-aberration :vec3 [uv :vec2]
  #_{:off (screen-color uv)
   :on _}
  (let [coord (2 * uv - 1)
        end   (uv - coord * (dot coord coord) * pp.chroma)
        delta ((end - uv) / 3)
        x (texture-load screen-tex (ivec2 uv) 0)
        y (texture-load screen-tex (ivec2 (distort-uv (uv + delta))) 0)
        z (texture-load screen-tex (ivec2 (distort-uv (uv + delta * 2))) 0)]
    (vec3 x.r y.g z.b)))

(wgsl/defun ^:feature grain :vec3 [c :vec3 uv :vec2]
  (let [intensity pp.grain.x
        response  pp.grain.y
        g (texture-load grain-tex (ivec2 (uv * pp.grain-st.xy + pp.grain-st.zw)) 0)
        g ((g.x - 0.5) * 2.0)
        l (mix 1 (1 - (sqrt (luminance c))) response)]
    (c + c * g * intensity * l)))

(comment
  (wgsl/defkernel post-process [8 8]
    (let [uv (distort-uv (vec2 global-invocation-id.xy))]
      (-> (chromatic-aberration uv)
          (bloom uv)
          (tonemap)
          (vignette uv)
          (color-grading)
          (grain uv)
          (vec4 1)
          (->> (texture-store cell-out (ivec2 uv))))))

  (wgsl/defkernel bloom-prefilter [8 8]
    ))

;; WGSL doesnt allow user-defined const functions
;;
;; two approaches
;; - change to the WGSL pipeline: fully move codegen from clj to cljs
;; - emit packed AST, single byte array, "interpret" in js to generate wgsl
;; - emit defunc as simple `defn` (ast -> unpack -> expand defuncs -> compile -> module -> link)
;;
;; or, much simpler for now; but changes the implementation runtime of macros
;; - defunc runs in clojure, not clojurescript (really java instead of javascript)
;; - same issues as `macrolet` -> each definition starts with only the prelude
;; - later changing to other approach means refactoring existing defuncs
;; - generates more WGSL text directly in the output javascript; doesnt matter for now -> gzip it back
;; - on the other hand; can always check if a matching clojure namespace exists, and use that as a starting env to eval defunc in
(comment (wgsl/defunc my-macro [gpu unexpanded forms !]
           &env ; -> usual macroexpand environment, augmented with WGSL state
           (do ; full clojure here -> arguments can be symbols, keywords, maps, vectors, etc
             `(1 + 2)))) ; "wgsl" code emitted

(comment
  (wgsl/defkernel blur-h [8 8]
    )

  (wgsl/defkernel blur-v [8 8]
    ;; this is cool, but not elegant; and defmacro is either in another file or a cljc one
    ;; need a `wgsl/defunc` to solve this -> just need to implement `constexpr` functions
    (clojure.tools.macro/macrolet
        [(sample [uv sign y]
           `(~'texture-sample screen-tex (~uv ~sign (~'vec2 0 ~y))))]
      ;; just want to write coefficient literals and specify the filter's schema
      ;; - generate *everything* else (spec macro : schema -> implementation)
      ;; - use composable language we all understand -> just expose the mental model
      ;; - yields the very code which would be hand-crafted; every step is pure & independently tested
      ;;   - readability & simplicity more important -> communicates intent
      ;;   - "show me your flowchart and conceal your tables" -> give me your domain specs and conceal your implementations
      (let [sz (->> (texture-dimensions screen-tex 0) .y f32 (/ 1))
            uv global-invocation-id.xy
            c0 (sample uv - 3.23076923)
            c1 (sample uv - 1.38461538)
            c2 (texture-sample screen-tex uv)
            c3 (sample uv + 1.38461538)
            c4 (sample uv + 3.23076923)
            c  (+ (c0.rgb * 0.07027027)
                  (c1.rgb * 0.31621622)
                  (c2.rgb * 0.22702703)
                  (c3.rgb * 0.31621622)
                  (c4.rgb * 0.07027027))]
        (texture-store cell-out uv c))))

  (wgsl/defkernel upsample [8 8]
    (let [uv global-invocation-id.xy
          hi (texture-load screen-tex uv 0)
          lo (texture-load input-tex  uv 0)]
      (texture-store cell-out uv (vec4 (mix hi.rgb lo.rgb pp.scatter) 1)))))


(comment
  (do
    (wgsl/compile lighting-cs)
    (wgsl/compile upsample)
    (wgsl/compile blur-v)
    (wgsl/compile post-process)
    )

  (js/console.log screen-pos.wgsl)
  (js/console.log rgb->hsv.wgsl)
  (js/console.log distribution-ggx.wgsl)
  (js/console.log geometry-smith.wgsl)
  (js/console.log fresnel-schlick.wgsl)
  (js/console.log lighting.wgsl)
  (js/console.log lighting-cs.wgsl)
  (js/console.log window-depth.wgsl)
  (js/console.log distort-uv.wgsl)
  (js/console.log lighting-cs)
  )

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

  (wgsl/deftexture tex-albedo grp-effect 0 :view-2d :f32)

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
              #js [#js {:attributes #js [#js {:shaderLocation local-position.bind
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

(defn pre-tick
  "Application logic executed before each simulation tick."
  []
  )

(defn setup-sine []
  (js/Math.sin (* 0.00015 time/unscaled-now)))

(defn post-tick [])
#_
(defn post-tick
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
