(ns bllm.base
  "Base shader library. Describes the functions built into WGSL.

  Provides information to feed the WGSL source analyzer.

  TODO types for inference, including overloads
  TODO JavaScript implementations, to run shaders on the CPU (ie unit tests)"
  (:refer-clojure :exclude [abs max min])
  (:require-macros [bllm.base :refer [defctor deflib defmath]]))

(set! *warn-on-infer* true)


(defctor f32 []) ; really a cast

;; TODO infer vec2<f32> from (vec2 (f32 expr) (f32 expr)) or (vec2 (vec2<f32> expr))
;; TODO replace hardcoded expr with type inference -> value in single generic `vec4` over `vec4`+`uvec4`+`ivec4`+...
(defctor vec2 {:bllm.wgsl/expr "vec2<f32>"} [])
(defctor vec3 {:bllm.wgsl/expr "vec3<f32>"} [])
(defctor vec4 {:bllm.wgsl/expr "vec4<f32>"} [])

(defctor ivec2 {:bllm.wgsl/expr "vec2<i32>"} [])
(defctor ivec3 {:bllm.wgsl/expr "vec3<i32>"} [])

(defctor mat2 {:bllm.wgsl/expr "mat2<f32>"} [])
(defctor mat3 {:bllm.wgsl/expr "mat3<f32>"} [])
(defctor mat4 {:bllm.wgsl/expr "mat4<f32>"} [])

(defctor mat2x3 [])
(defctor mat2x4 [])
(defctor mat3x2 [])
(defctor mat3x4 [])
(defctor mat4x2 [])
(defctor mat4x3 [])

(deflib all
  ([e :bvec] :bool)
  ([e :bool] :bool))

(deflib any
  ([e :bvec] :bool)
  ([e :bool] :bool))

(deflib select
  ([f 'T t 'T cond :bool] 'T)
  ([f (:vec 'T) t 'T cond :bool] 'T))

(deflib array-length
  [p :array] :u32)

(defmath abs [e])

(defmath acos [e])

(defmath acosh)
(defmath asin)
(defmath asinh)
(defmath atan)
(defmath atanh)
(defmath atan2)
(defmath ceil)
(defmath clamp)
(defmath cos)
(defmath cosh)
(defmath count-leading-zeros)
(defmath count-one-bits)
(defmath count-trailing-zeros)
(defmath cross)
(defmath degrees)
(defmath determinant)
(defmath distance)
(defmath dot)
(defmath exp)
(defmath exp2)
(defmath extract-bits)
(defmath face-forward)
(defmath first-leading-bit)
(defmath first-trailing-bit)
(defmath floor)
(defmath fma)
(defmath fract)
(defmath frexp)
(defmath insert-bits)
(defmath inverse-sqrt)
(defmath ldexp)
(defmath length)
(defmath log)
(defmath log2)
(defmath max)
(defmath min)
(defmath mix)
(defmath modf)
(defmath normalize)
(defmath pow)
(defmath quantize-to-f16)
(defmath radians)
(defmath reflect)
(defmath refract)
(defmath reverse-bits)
(defmath round)
(defmath saturate) ; FIXME present in WGSL spec but unspecified in Brave?
(defmath sign)
(defmath sin)
(defmath sinh)
(defmath smoothstep)
(defmath sqrt)
(defmath step)
(defmath tan)
(defmath tanh)
(defmath transpose)
(defmath trunc)

(deflib dpdx)
(deflib dpdx-coarse)
(deflib dpdx-fine)
(deflib dpdy)
(deflib dpdy-coarse)
(deflib dpdy-fine)
(deflib fwidth)
(deflib fwidth-coarse)
(deflib fwidth-fine)

(deflib texture-dimensions)
(deflib texture-gather)
(deflib texture-gather-compare)
(deflib texture-load)
(deflib texture-num-layers)
(deflib texture-num-levels)
(deflib texture-num-samples)
(deflib texture-sample)
(deflib texture-sample-bias)
(deflib texture-sample-compare)
(deflib texture-sample-compare-level)
(deflib texture-sample-grad)
(deflib texture-sample-level)
(deflib texture-store)

(deflib atomic-load)
(deflib atomic-store)
(deflib atomic-add)
(deflib atomic-sub)
(deflib atomic-max)
(deflib atomic-min)
(deflib atomic-and)
(deflib atomic-or)
(deflib atomic-xor)
(deflib atomic-exchange)
(deflib atomic-compare-exchange-weak)

(deflib pack4x8snorm)
(deflib pack4x8unorm)
(deflib pack2x16snorm)
(deflib pack2x16unorm)
(deflib pack2x16float)

(deflib storage-barrier)
(deflib workgroup-barrier)
