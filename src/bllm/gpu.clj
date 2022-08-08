(ns bllm.gpu
  "Meta WebGPU. Or how I stopped worrying and learned to love the browser."
  (:require [clojure.string      :as str]
            [clojure.tools.macro :as macro]
            [bllm.util           :as util]))

;; TODO use the individual param type tags!

(defmacro ^:private defgpu
  "Generates a constructor function for the specified WebGPU object type."
  {:arglists '([ctor-name docstring? attrs-map? & param-specs])}
  [ctor-name & args]
  (let [[sym xs] (macro/name-with-attributes ctor-name args)
        ident    (util/kebab->pascal sym)
        attrs    (meta sym)
        create   (symbol (str (:create attrs "create") ident))
        params   (conj xs '[label :str ""])
        arg-syms (mapv first params)
        desc-tag (symbol "js" (str "GPU" ident "Descriptor"))
        desc-sym (symbol      (str sym "-desc"))]
    (util/wrap-do
     ;; Emit a reusable descriptor `js/Object`. Only used by the next fn.
     `(def ~(with-meta desc-sym {:tag desc-tag :private true})
        (cljs.core/js-obj
         ~@(flatten
            (for [[param tag & [default]] params]
              ;; TODO help JS runtime, use default value of type tag
              [(str param) (if (some? default) default 'js/undefined)]))))
     ;; Emit a function to fill the descriptor and create a `gpu/Object`.
     `(defn ~sym ~arg-syms
        ~@(for [arg arg-syms]
            `(set! (. ~desc-sym ~(symbol (str \- arg))) ~arg))
        (let [^js/GPUObjectBase obj# (. ~'device ~create ~desc-sym)]
          (set! (.-label obj#) ~'label) ; TODO compat only! dev browser doesn't take label from descriptor
          obj#)))))

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
