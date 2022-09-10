(ns bllm.vertex
  (:require [bllm.util :as util :refer [def1]]
            [bllm.wgsl :as wgsl]))

;; feature support flags
;; - effect
;; - lighting
;; - batching

;; manage vertex and index buffers and their contents
;; - create large pages (4mb?) and allocate resources within
;; - intermediate ring buffers for upload
;; - grow buffers instead of splitting same layout into multiple ones?

;; fairly similar to ECS, except manager/layout is on the CPU and system/memory is GPU
;; - similar to effect management -> with resource bindings instead of vertex streams

;; START FROM DATA, ALWAYS START FROM DATA
;; meshes

;; more and more looks like streams are to pipeline what attribs are to stage

;; so, vertex component is
;; - GPUBuffer, start, offset,
;;




;; usage
;; - batching
;; - instance
;; - indirect


;; ECS design, dont need most of usual structure
;; - entities decoupled from archetypes, decoupled from blocks of storage
;; -

;; wait, use ECS to design renderer?
;; - no different really, 1 "entity" per GPU resource

;; go from high level data to low level specifics
;; - everything flexible -> let "renderer" decide where data comes from and in what shape
;; - essentially creating the vertex STATE here



;; vertex array declaration -> struct

(comment
  ;; few things; struct structure, array of them, optional fields -> variants
  (wgsl/definput local-vertex
    ;; THIS ONE IS REALLY HIGH LEVEL

    ;; issues
    ;; - data can come from multiple vertex buffers (ie position separate)
    ;; - ue has lots of special case handling for separate position stream
    ;; -
    )

  ;; BUFFER DESCRIPTORS
  ;; - dont just create VBO, match it to a `definput`, so it has to be flexible
  ;;   - how to handle "variadic" elements? like color/texcoord
  ;;     - most of the time these will go straight to fragment
  ;;     - but vertex stage also runs part of effect graph
  ;;  - no stream index -> distinct arrays required
  ;;  - no independent attribs really -> always end up in genio
  ;;    - BUT ends up in MANY genios
  ;;    - keep CPU side types in decl -> gen GPU, would end up different binaries anyways at the pipeline level
  ;;    - normalize data to what shader wants -> less pipeline variants in the end anyways
  ;;    - meaning go by semantics; matches format, documentation, binding and etc in one spot
  ;;    - if need another use of similar semantic, leverage namespaces (ie texcoord bound elsewhere, but prefer reusable bindings)

  ;; how to get reusable bindings, if they all need to be declared once and then used in context
  ;; - dont attach bind # to attrib definition -> dont know that yet

  ;; main idea is to still construct in genio-style, cause thats emerging from more fundamental design
  ;; -> but so attached to input data (meshes -> VBOs)
  ;; -> in reality genio is GPU side, stream is CPU side
  ;; -> interpolants are also genio, with no CPU counterpart
  ;; -> CPU has no builtins (ie vertex-index or instance-index)
  ;; -> genio doesnt hold WGSL, just thin dependencies wrapper
  ;; -> will still match the CPU layout by filtering builtins away
  ;; -> dont waste too much time on this, just do
  ;; -> really the center part now is RENDER PIPELINE

  ;; RENDER PIPELINE VERTEX STATE
  ;; -> ideally want to just refer vertex shader and get pipeline (at least for system; effect/matter will also add to the pipeline key)
  ;; -> meaning actual render pipeline at this level cannot easily be referenced globally, but with a material/mesh/pass triplet it can!

  ;; PASS IS UNIT OF GPU WORK
  ;; - heaviest state change, not that many of them (<100; even <1000 wouldnt change design, just optimize it)
  ;; - sets the draw buffer state in `beginRenderPass`, or is a `beginComputePass`; compute is trivial (compared to render)
  ;; - doesnt say anything about tech or mesh types; although their fragment needs to be pass compatible (match outputs)
  ;; - most visuals belong in render passes -> except culling but thats visibility; use-mention!
  ;; -

  ;; FRAGMENT IS FIRST MATCHED STAGE IN RESOLVING RENDER -> directly coupled to PASS as it writes it
  ;; - effect tech contributes to entry points -> but already got the shader graph AST thingy!
  ;; - same for mesh type ->


  ;; are entries more than a glorified defun?
  ;; - vary with the tech and mesh types
  ;; - they both contribute to them (expept mesh cant contribute to kernels)
  ;; - system is fine -> no system mesh/effect shaders tho! defrender convenience, but wgsl/render real entry
  ;; - need to support both -> system can still specify variants manually (ie 2 pipelines over same kernel, changing impl of fn_01)

  ;; THEN DONT COMPILE AT ENTRYPOINTS, BUT PIPELINES
  ;; - ah but something changes now
  ;; - emitting WGSL function BODIES at runtime now
  ;; - change wgsl emitter to be an IR emitter
  ;;   - an encoding of the AST in reverse polish notation
  ;;
  ;; good for next iteration, right now want to get VBOs on screen
  ;; - no good going more abstract if no weight to pull with it
  ;; - tangents problematic -> in that either precalculated or need to implement calculation -> also needs to match normal map
  ;;

  ;; ie basic mesh can have 1+ UVs, 1+ colors, to match given effect
  ;; - leave that for AST refactor -> needs to generate wgsl later than now


  ;; TODO need underlying API clean too - ie GPU particle systems will generate vertex layout from combining emitter props

  (wgsl/defvertex slate-element-vs
    )

  (wgsl/defpixel sleet-element-ps
    )

  (wgsl/defarray sleet
    [texcoords :vec4]
    []))
