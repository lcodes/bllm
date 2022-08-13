(ns bllm.meta
  "We heard you like data, so we used data to describe your data.
  Provides the foundations to compose modern interactive systems.

  The idea is to simplify development in which multiple languages communicating
  using different data formats is a core requirement. For example, JS <-> WGSL.

  What is this structure? A high-level description of dataflow propagation.

  | Meta      | Type   | Object   | Role                           |
  |-----------+--------+----------|--------------------------------|
  | Attribute | Member | Property | Reusable property definitions. |
  | Component | Struct |          |                                |
  | Schematic | Entity | Entity   | Relation graph, of attributes. |
  | Archetype | System | Identity |

  A datomic-like schema in the browser. A database of attributes & schematics.
  Its content is populated either directly from the `reg-*` functions here, or
  the macros defined in the Clojure counterpart of this file for convenience."
  (:require-macros [bllm.meta :refer [defenum defflag]])
  (:require [bllm.util :refer [def1]]))

(set! *warn-on-infer* true)


;;; Database of Schematics
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 ^:private attributes (js/Map.)) ; name -> symbols (defined as schematic!)
(def1 ^:private schematics (js/Map.)) ; name -> diagram (with named relations!)

(defn reg-attribute
  "Upserts the entity for the definition of a single attribute."
  []
  )

(defn reg-schematic
  "Upserts the entity for the definition of a single schematic."
  []
  )

(defflag AttributeFlag
  ""
  ; packed attribute infos
   ;; WGSL | JS | CLJS (where can it live)
   ;;
   )

(defenum AttributeKind
  "Built-in attribute schematics."
  ;; prim
  ;; enum / flag
  ;;
  )

(defenum SchematicKind
  "Built-in schematic schematics."
  )

(defenum PrimitiveKind
  "Built-in primitive types. More than JavaScript, to also model WGSL & binary."
  {:suffix -t}
  #_
  [;; Atoms.
   never any void bool ; Logical bottom, top, unit and bit types.
   uni str sym key     ; Textual character, string, symbol and keyword.

   ;; Scalars.
   i8 i16 i32 i64      ; Signed integers. For anything which can be counted.
   u8 u16 u32 u64      ; Unsigned integers. When the extra bit is important.
   f16 f32 f64         ; Real numbers. Or the subset known as floating points.

   ;; Vectors and matrices.
    vec2  vec3  vec4   ; GPU float vectors.
   dvec2 dvec3 dvec4   ; GPU double vectors.
   ivec2 ivec3 ivec4   ; GPU int vectors.
   uvec2 uvec3 uvec4   ; GPU uint vectors.
   bvec2 bvec3 bvec4   ; GPU bool vectors.
    mat2  mat3  mat4   ; GPU square matrices.
   mat2x3 mat2x4       ; GPU 2 by N matrices.
   mat3x2 mat3x4       ; GPU 3 by N matrices.
   mat4x2 mat4x3       ; GPU 4 by N matrices.

   i32-atomic          ; GPU signed atomic.
   u32-atomic          ; GPU unsigned atomic.

   ;; Conveniences.
   quat                ; Quaternions.
   inst                ; Instant in time.
   uuid                ; 128-bit universally unique identifier.
   blob                ; Opaque array of bytes.

   ;; Constructors
   ref                 ; Reference to another property.
   ptr                 ; Reference implemented as a memory location.
   mut                 ; Immutable default, used for non-constant variables.
   const               ; Dynamic constant, set at runtime before evaluation.
   alias               ; Associates a name to a schematic expression.

   ;; Aggregates.
   object              ; JavaScript's property bags with prototype inheritance.
   array tuple struct  ; Homogenous, heterogenous linear sequences.
   list set map        ; Logical lists, sets and mappings of attributes.
                       ; These allow for the arbitrary nesting of patterns.
   ])


;;; Bootstrap Schematics
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init []
  ;; built-in attributes & schematics -> those used to describe attributes & schematics!
  )
