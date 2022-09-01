(ns bllm.gpu
  "Meta WebGPU. Or how I stopped worrying and learned to love the browser."
  (:require [clojure.string      :as str]
            [clojure.tools.macro :as macro]
            [bllm.util           :as util :refer [defm]]))

(defn- set-field
  ([[sym tag]]
   (set-field sym tag))
  ([sym tag]
   (if-not (symbol? tag)
     sym
     (list tag sym))))

(defn- emit-setters [desc-sym fields]
  (for [[arg tag] fields]
    `(set! (. ~desc-sym ~(util/field-name arg)) ~(set-field arg tag))))

(defn- emit-descriptor
  "Generates a reusable descriptor and a function to populate it."
  [create? ctor-name param-specs desc-sym desc-tag-fn & extra-fields]
  (let [ident  (util/kebab->pascal ctor-name)
        attrs  (meta ctor-name)
        fields (concat extra-fields param-specs)
        setter (remove (comp :static meta) fields)
        create (symbol (str (:create attrs "create") ident))]
    (util/wrap-do
     ;; Emit a reusable descriptor `js/Object`. Only used by the next fn.
     `(def ~(with-meta desc-sym
              {:private true :tag (util/js-sym (desc-tag-fn ident))})
        (cljs.core/js-obj
         ~@(flatten
            (for [[param tag & [default]] fields]
              [(str param) (if (some? default)
                             (set-field default tag)
                             'js/undefined)]))))
     ;; Emit a function to fill the descriptor and create a `gpu/Object`.
     `(defn ~ctor-name ~(mapv first setter)
        ~@(emit-setters desc-sym setter)
        ~(if-not create?
           'js/undefined
           `(. ~'device ~create ~desc-sym))))))

(defn- name->tag-str [suffix sym]
  (str "GPU" (util/kebab->pascal sym) suffix))

(defn- descriptor-symbol [s]
  (symbol (str s "-desc")))

(defm ^:private defgpu
  "Generates a constructor function for the specified WebGPU object type."
  [ctor-name & param-specs]
  (emit-descriptor true ctor-name param-specs
                   (descriptor-symbol ctor-name)
                   (partial name->tag-str "Descriptor")
                   '[label :str ""]))

(defm ^:private defstage
  "Generates a setup function for the specified WebGPU shader stage."
  [ctor-name pipeline-desc tag & param-specs]
  (let [desc-sym (descriptor-symbol ctor-name)]
    (util/wrap-do
     (emit-descriptor false ctor-name param-specs desc-sym
                      (constantly (name tag))
                      '[module ::shader-module]
                      '[entryPoint :string])
     ;; Connect the stage descriptor to the pipeline descriptor. Once.
     `(set! (. ~pipeline-desc ~(symbol (str \- ctor-name))) ~desc-sym))))

(defn- emit-object [fields]
  `(cljs.core/js-obj
    ~@(interleave (map (comp name first) fields) (map set-field fields))))

(defn- emit-bind [sym tag args set ctor]
  (let [desc (symbol (str sym "-array"))
        tmp  (with-meta (gensym "entry") {:tag tag})
        idx  (first args)]
    `(do (def ~(with-meta desc {:private true})
           (cljs.core/array))
         (defn ~sym ~args
           (if-let [~tmp (aget ~desc ~idx)]
             (do ~@(set tmp)
                 ~tmp)
             (let [~tmp ~(ctor)]
               (aset ~desc ~idx ~tmp)
               ~tmp))))))

(defm ^:private defbind
  [sym & param-specs]
  (let [index? (:index (meta sym))
        fields (cond->> param-specs
                 index? (concat '[[binding :i32]]))]
    (emit-bind sym
               (util/js-sym (name->tag-str "" sym))
               (mapv first fields)
               #(emit-setters % (next fields))
               #(emit-object ((if index? next identity) param-specs)))))

(defm ^:private deflayout
  [sym & param-specs]
  (let [extra '[[binding    :i32]
                [visibility :u32]]
        fields (concat extra param-specs)
        bind (-> (name sym)
                 (str/replace "bind-" "")
                 (util/kebab->camel))
        prop (util/field-name bind)
        bsym (symbol bind)]
    (emit-bind sym 'js/GPUBindGroupLayoutEntry (mapv first fields)
               (fn set [tmp]
                 [`(let [~bsym (. ~tmp ~prop)]
                     ~@(emit-setters tmp (next extra))
                     ~@(emit-setters bsym param-specs))])
               (fn ctor []
                 `(let [tmp# ~(emit-object extra)]
                    (set! (. tmp# ~prop) ~(emit-object param-specs))
                    tmp#)))))

(comment
  (clojure.pprint/pprint
   (macroexpand-1
    '(defgpu buffer
       "Doc String-y Thing"
       ;;{:create import}
       [size :u64]
       [usage ::buffer-usage]
       [mappedAtCreation :bool false])))
  )

(defm defres
  "Defines a GPU resource. Its value will be set when a device is acquired."
  [sym init]
  `(do (bllm.util/def1 ~sym js/undefined)
       (bllm.gpu/register
        ~(hash sym) ~(hash init)
        (fn ~'get [] ~sym) ; NOTE only required in development
        (fn ~'set [] (set! ~sym ~init)))))

;; TODO overridable compile-time "min requirements"
;; - validate only overridden values against device
;; - these defaults are guaranteed to be supported
;; - warn when emitting code breaking min specs
;;   (ie wgsl/defkernel pulling in 10 storage buffers)
;; - force authors to flag requirements above defaults
;;   provide fallbacks all the way to min specs
;; - setup alternatives at device creation, then forget
(def limits
  {:max-texture-dimension-1d 0x2000
   :max-texture-dimension-2d 0x2000
   :max-texture-dimension-3d 0x800
   :max-texture-array-layers 0x100
   :max-bind-groups 4
   :max-dynamic-uniform-buffers-per-pipeline-layout 8
   :max-dynamic-storage-buffers-per-pipeline-layout 4
   :max-sampled-textures-per-shader-stage 16
   :max-samplers-per-shader-stage 16
   :max-storage-buffers-per-shader-stage 8
   :max-storage-textures-per-shader-stage 4
   :max-uniform-buffers-per-shader-stage 12
   :max-uniform-buffer-binding-size 0x10000 ; 64Kb
   :max-storage-buffer-binding-size 0x8000000 ; 128Mb
   :min-uniform-buffer-offset-alignment 0x100
   :min-storage-buffer-offset-alignment 0x100
   :max-vertex-buffers 8
   :max-vertex-attributes 16
   :max-vertex-buffer-array-stride 0x800 ; 2Kb
   :max-inter-stage-shader-components 60
   :max-inter-stage-shader-variables 16
   :max-color-attachments 8
   :max-compute-workgroup-storage-size 0x4000 ; 16Kb
   :max-compute-invocations-per-workgroup 0x100
   :max-compute-workgroup-size-x 256
   :max-compute-workgroup-size-y 256
   :max-compute-workgroup-size-z 64
   :max-compute-workgroups-per-dimension 0xFFFF})

(defn check-limit [v k]
  (let [limit (limits k)]
    (when (> v limit)
      (throw (ex-info "Default limit exceeded"
                      {:value v :limit k :default limit})))))
