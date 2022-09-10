(ns bllm.effect
  (:require [bllm.data :as data]
            [bllm.ecs  :as ecs]
            [bllm.gpu  :as gpu]
            [bllm.util :as util :refer [def1]]
            [bllm.wgsl :as wgsl]))


;; what is managed here?
;; - inside a pass, targets already decided, inputs either static (system/effect) or dynamic (matter)
;; - tons of uniforms, textures and overrides to control from here, however

;; not at the object level yet, but more finely grained than pass batches
;; - not all effects support batching (ie if some material props are arraytextures)
;; - for those which do, want their inputs to live where they can be read indirectly
;; - upload 2d textures to array slices
;; - upload uniforms to larger buffer
;;   - index both from effect ID in shader

;; but again, completely independent of GPU/WGSL module designs
;; - want them to enable this, without caring about this
;; - what need then? build independent parts, without batching, build on top & refactor from there

;; effect is the render pipeline
;; material is the data bindings

;; want extensible system
;; - ie further specialize with vertex streams for different kinds of surfaces
;; - partial shader code generation from high-level constructs almost needed
;;   - previously job of #defines, no longer available
;;   - can affect enabled/disabled features
;;   -


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  ;; Effect shaders
  ;; - as usual, every convenience macro builds on a runtime data API
  ;; - generate bind group & pipeline layouts in `shader`, not in macro
  ;; - live code from source; can still serialize, store & network as asset data
  ;; - extension point above system shaders, adding runtime features and variants

  ;; want to generate variants on demand
  (wgsl/defshader Standard
    ;; bindings ->
    ;; - uniform values (generates a buffer struct, not worth generating variants over)
    ;; - textures (array batching variant, saves texel sample/load with variants, but less batching -> unless materials fill the same slots!)
    ;; - samplers (if "fixed" can refer to globally bound ones, otherwise specified here)
    ;; - storage textures (TODO Figure how to specify those in material)
    albedo
    metallic
    roughness
    albedo-map
    normal-map
    occlusion-map
    emission-map
    detail-map
    uv-tiling-offset

    ;; associated entry points

    (wgsl/defvertex standard-vs
      ;; derive variants from static analysis
      ;; (feature ...) (debug ...) and (version ...) special forms wrap optional resources/define variants
      ~(if normal-map
         `(calc-normal local-normal local-tangent ; Tangent space passed to PS, another feature -> feature hierarchies
                       (texture-sample ~normal-map linear-mip texcoord))
         `local-normal)
      )

    (wgsl/defpixel standard-ps-forward
      ;; splice in shader code like this is `(lisp ~quotation)

      (hdr-out = (lighting ~shader))
      )

    (wgsl/defpixel standard-ps-deferred
      )

    ;; passes -> infer from mix & match found defs?
    ;; - doesnt matter until passes are making pipeline requests
    )

  (data/defstore Shader
    ;; NOTE dont store builtin shaders; they still cound as first-class assets

    ;; entry points IL (packed reverse polish encoding)
    ;; binding definitions
    ;; pass definitions
    ;; - feature requirements
    ;; - variant definitions
    ;; - fallback (if from another shader; matching pass on definition name + requirements)

    ;; upsert wgsl graph nodes (only bindings)
    ;; generate (& reuse) pipeline layout (only need bindings)
    ;; entry point ASTs, to generate the variants from -> no #ifdef
    ;;  - doesnt matter if coming from asset, visual graph, parsed GLSL -> so long as it resolves & infers & compiles
    ;;  - ie all texture maps optional; thats a ton of variants already
    ;;    - then quality settings
    ;;    - and different passes
    ;;    - and various vertex formats (delegate to `mesh`)
    )

  (data/defstore Material
    ;; shader asset
    ;; texture assets
    ;; uniform buffer
    )

  )
