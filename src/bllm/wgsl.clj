(ns bllm.wgsl
  "Meta WebGPU. Because raw text is hard to digest, tastes like concat.

  Only a few guidelines for emitting code:
  - Don't bloat the output, emit fn calls to 'decompress' the macro outputs.
  - All macros must be pure, Clojure doesn't know about ClojureScript's env.
  - Just enough to emit WGSL source, JavaScript bindings, and shader graphs.
  - Only expose WGSL constructs using S-Expressions, layer decisions on top.

  The goal is to write GPU definitions transparently alongside the CPU ones.
  Leverage figwheel to hot-reload shader code the same way it does with JS."
  (:refer-clojure :exclude [defstruct])
  (:require [clojure.string :as str]
            [bllm.meta :as meta]
            [bllm.util :as util :refer [defm]]))


;;; Utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private gpu-ns
  "Converts a `Namespace` into a WGSL-compatible identifier. Memoized."
  (memoize
   #(-> (str %)
        (util/kebab->camel)
        (str/replace \. \_)
        (str \_))))

(defn- gpu-name
  "Expands a `Symbol` into a WGSL-compatible fully-qualified identifier."
  [sym]
  (->> (name sym)
       (util/kebab->camel)
       (str (gpu-ns *ns*))))

(defn- gpu-hash
  "Generates a unique hash for a WGSL shader node definition."
  [& args]
  (hash args)) ; Why is this so easy?

(comment (gpu-ns "hello-world.foo-bar.deep-nest")
         (gpu-name 'in-position)
         (gpu-hash 1 "hi" :key 'sym))

(defn- keyword->enum
  "Converts keywords to the matching constants."
  [x]
  (if-not (keyword? x)
    x
    (-> (namespace x)
        (or "bllm.wgsl")
        (name)
        (symbol (name x)))))

(comment (keyword->enum :foo-bar))

(defn- emit-field
  [field]
  (list 'cljs.core/array
    (util/kebab->camel (:name field))
    (keyword->enum (:type field))
    (:offset field)))

(defn- emit-struct
  [sym fields]
  ;; TODO ctor -> ab? offset? -> dataview
  ;; - attach array of fields as ctor prop, pass ctor to node ctor
  (with-meta `(util/array ~@(map emit-field fields))
    (meta fields)))

(defn- emit-node
  "Emits a WGSL node definition and its registration to the shader graph."
  [kind ctor sym & args]
  (let [name (gpu-name  sym)
        hash (gpu-hash  sym args) ; TODO diff hash for reload vs identity?
        sym  (vary-meta sym assoc ::kind kind ::name name ::hash hash)]
    `(bllm.wgsl/reg
      (def ~sym
        (~ctor ~name ~hash ~@(map keyword->enum args))))))

(defm ^:private defnode
  "Defines a kind of WGSL node definition. Used to define nodes of its kind."
  [sym [node & args] & body]
  (let [kind  (or (:kind (meta sym))
                  (keyword (str/replace-first (name sym) #"^def-?" "")))
        ctor  (keyword->enum kind)
        init  (gensym "init")
        props (take-while #(not= '& %) args)] ; Until the variadic arg marker.
    ;; Write code, that writes code, that writes code... oh hi Lisp!
    `(defm ~sym
       {:args '~args}
       [~node & args#]
       (let [;; Allow the doc-string to be placed at the end, like Emacs-Lisp.
             doc#     (last args#)
             doc?#    (string? doc#)
             [~@args] (if doc?# (butlast args#) args#)
             ~node    (if-not doc?#
                        ~node
                        (vary-meta ~node assoc :doc doc#))
             ;; Copy named node properties to the meta-data of its defined var.
             ~@(when (not-empty props)
                 `[~node (vary-meta ~node assoc
                                    ~@(util/flatten1
                                       (for [p props]
                                         [(keyword "bllm.wgsl" (name p)) p])))])
             ;; Execute the compile-time logic specific to this node type.
             ~init (do ~@body)
             ~node (if-let [m# (meta ~init)]
                     (vary-meta ~node merge m#)
                     ~node)]
         ;; Finally, generate the node on the ClojureScript side of things.
         (emit-node ~kind '~ctor ~node ~@props
                    ~@(when (not-empty body)
                        [init]))))))

(defm ^:private defreg
  ""
  [sym params & wgsl-emitters]
  (let [node (gensym "node")
        emit (fn [emit-wgsl]
               (if (symbol? emit-wgsl)
                 (list emit-wgsl node)
                 (cons (first emit-wgsl)
                       (cons node (next emit-wgsl)))))]
    `(defn ~sym [~'name ~'hash ~@params]
       (let [~node (cljs.core/js-obj
                    "kind" ~(symbol "bllm.wgsl" (util/kebab->pascal sym))
                    "name" ~'name
                    "hash" ~'hash
                    ~@(util/flatten1 (for [p params] [(str p) p]))
                    "wgsl" js/undefined)]
         ;; A node is just a property bag attached to a list of WGSL emitters.
         (set! (.-wgsl ~node) (str ~@(->> (map emit wgsl-emitters)
                                          (interpose "\n\n"))))
         ~node))))


;;; I/O - Vertex attributes, interpolants, fragment draw targets
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defm defio
  "Render I/O nodes have a `bind` slot and a `type`."
  [node]
  `(defnode ~node ~'[sym bind type]))

(defio def-vertex-attr "Defines an input to the vertex stage.")
(defio def-draw-target "Defines an output from the fragment stage.")
(defio def-interpolant "Defines an I/O channel between render stages.")


;;; Resource Groups - Buffer Views, Texture Views & Samplers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defnode defstruct
  "Aggregate type definition."
  [sym & fields]
  (emit-struct sym (meta/parse-struct &env fields)))

(defnode defuniform
  [sym group binding & fields]
  (emit-struct sym (meta/parse-struct &env fields)))

(defnode defstorage
  [sym group binding type access])

(defnode deftexture
  [sym group bind type])

(defnode defsampler
  [sym group bind])

(defnode defgroup
  [sym & args]
  ;; resolve all resource, make sure groups match
  ;;
  ;; descriptor for pipeline layouts
  ;; constructor for resource bindings
  )

(defnode deflayout
  [sym & args]
  ;; resolve all groups, make sure order match
  ;;
  ;; gpu/defres for layout object
  )


;;; Shader Code - Constants, Variables, Functions & Entry Points
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defnode defenum
  [sym & args]) ; TODO like meta/defenum, but emits WGSL

(defnode defflag
  ""
  [sym & args]) ; TODO like meta/defflag, but emits WGSL

(defnode defconst ; TODO like util/defconst, but emits WGSL
  "Compile-time constants."
  [sym & args]
  ;; const name = value;
  )

(defnode defvar ; TODO like def, but emits WGSL
  "Pipeline-overridable constants."
  {:kind 'override}
  [sym & args]
  ;; ID is hash of sym
  ;; no default means required constant
  ;;
  ;; @id(0) override name : type = default;
  )

(defnode defun ; TODO like defn, but emits WGSL
  {:kind 'function}
  [sym & args]
  )

(defnode defvertex
  "Defines a vertex shader entry point."
  [sym & args]
  ;; inputs -> vertex -> interpolants
  ;;
  ;; runtime needs to collect all inputs and unpack them from the generated entry
  ;; then collect all outputs and thread them into packed interpolants
  )

(defnode defpixel
  "Defines a fragment shader entry point."
  [sym & args]
  ;; interpolants -> fragment -> outputs
  ;;
  ;; runtime needs to collect all interpolants and unpack them
  ;; then collect all outputs and thread them into packed render targets
  )

(defnode defkernel
  "Defines a compute shader entry point.

  Unlike vertex and pixel shaders, there are no user-defined parameters here.
  Only builtin inputs such as the `global-invocation-id` are available, from
  which user-defined parameters can then be accessed through bound resources.

  The workgoup size must also be specified. No default value suits all cases."
  [sym & args]
  ;; inputs -> compute -> outputs
  ;;
  ;; thread counts
  )


;;; Shader Pipelines - Runtime Execution State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defm ^:private defstate [sym & args]
  )

(defstate def-primitive
  [sym & args]
  ())

(defstate def-depth-stencil
  [sym & args]
  )

(defstate def-multisample
  [sym & args]
  )

(defstate defblend
  [sym & args]
  )

(defm defrender
  [sym & args]
  )

(defm defcompute
  [sym & args]
  )
