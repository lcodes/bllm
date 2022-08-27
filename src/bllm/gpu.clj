(ns bllm.gpu
  "Meta WebGPU. Or how I stopped worrying and learned to love the browser."
  (:require [clojure.string      :as str]
            [clojure.tools.macro :as macro]
            [bllm.util           :as util :refer [defm]]))

;; TODO use the individual param type tags!

(defn- descriptor-symbol [s]
  (symbol (str s "-desc")))

(defn- emit-descriptor
  "Generates a reusable descriptor and a function to populate it."
  [create? ctor-name param-specs desc-sym desc-tag-fn & extra-fields]
  (let [ident    (util/kebab->pascal ctor-name)
        attrs    (meta ctor-name)
        fields   (concat extra-fields param-specs)
        create   (symbol (str (:create attrs "create") ident))
        arg-syms (mapv first (remove #(:static (meta %)) fields))
        desc-tag (symbol "js" (desc-tag-fn ident))]
    (util/wrap-do
     ;; Emit a reusable descriptor `js/Object`. Only used by the next fn.
     `(def ~(with-meta desc-sym {:tag desc-tag :private true})
        (cljs.core/js-obj
         ~@(flatten
            (for [[param tag & [default]] fields]
              ;; TODO help JS runtime, use default value of type tag
              [(str param) (if (some? default) default 'js/undefined)]))))
     ;; Emit a function to fill the descriptor and create a `gpu/Object`.
     `(defn ~ctor-name ~arg-syms
        ~@(for [arg arg-syms]
            `(set! (. ~desc-sym ~(symbol (str \- arg))) ~arg))
        ~(if-not create?
           'js/undefined
           `(. ~'device ~create ~desc-sym))))))

(defm ^:private defgpu
  "Generates a constructor function for the specified WebGPU object type."
  [ctor-name & param-specs]
  (emit-descriptor true ctor-name param-specs
                   (descriptor-symbol ctor-name)
                   #(str "GPU" % "Descriptor")
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
        (fn get [] ~sym) ; NOTE only required in development
        (fn set [] (set! ~sym ~init)))))
