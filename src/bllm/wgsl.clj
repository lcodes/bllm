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

(defn- emit-field
  [field]
  (list 'cljs.core/array
    (util/kebab->camel (:name field))
    (util/keyword->wgsl (:type field))
    (:offset field)))

(defn- emit-struct
  [sym fields]
  ;; TODO ctor -> ab? offset? -> dataview
  ;; - attach array of fields as ctor prop, pass ctor to node ctor
  (with-meta `(util/array ~@(map emit-field fields))
    (meta fields)))

(defn- emit-node
  "Emits a WGSL node definition and its registration to the shader graph."
  [wgsl? ns sym kind ctor & args]
  (let [name (and wgsl? (gpu-name sym))
        hash (gpu-hash sym args) ; TODO diff hash for reload vs identity?
        sym  (cond-> (vary-meta sym assoc ::kind kind ::hash hash)
               wgsl? (vary-meta assoc ::name name))]
    `(bllm.wgsl/reg
      (def ~sym
        (~ctor
         ~@(when wgsl? [name])
         ~hash
         ~@(map (partial util/keyword->enum ns) args))))))

(defn- node-kind [sym]
  (or (:kind (meta sym))
      (keyword (str/replace-first (name sym) #"^def-?" ""))))

(defm ^:private defnode
  "Defines a kind of WGSL node definition. Used to define nodes of its kind."
  [sym [node & args] & body]
  (let [kind  (node-kind sym)
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
                                         [(keyword util/ns-wgsl (name p)) p])))])
             ;; Execute the compile-time logic specific to this node type.
             ~init (do ~@body)
             ~node (if-let [m# (meta ~init)]
                     (vary-meta ~node merge m#)
                     ~node)]
         ;; Finally, generate the node on the ClojureScript side of things.
         (emit-node ~(:wgsl (meta sym) true)
                    util/ns-wgsl ~node ~kind
                    '~(util/keyword->wgsl kind)
                    ~@props
                    ~@(when (not-empty body)
                        [init]))))))

(defn- emit-obj [sym emit? keys vals]
  `(cljs.core/js-obj
    "kind" ~(symbol util/ns-wgsl (util/kebab->pascal sym))
    "hash" ~'hash
    ~@(when emit? '["wgsl" js/undefined
                    "name" name])
    ~@(interleave keys vals)))

(defm ^:private defwgsl
  "Defines a WGSL node constructor. ClojureScript counterpart to `defnode`."
  [sym params & wgsl-emitters]
  (let [node (gensym "node")
        emit (when (not-empty wgsl-emitters)
               (fn [emit-wgsl]
                 (if (symbol? emit-wgsl)
                   (list emit-wgsl node)
                   (cons (first emit-wgsl)
                         (cons node (next emit-wgsl))))))]
    `(defn ~sym [~@(when emit '[name]) ~'hash ~@params]
       (let [~node ~(emit-obj sym emit (map str params) params)]
         ~(when emit
            ;; A node is just a property bag attached to a list of WGSL emitters.
            `(set! (.-wgsl ~node) (str ~@(->> (map emit wgsl-emitters)
                                              (interpose "\n\n")))))
         ~node))))


;;; Render States
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def state-props
  "Specification of the render state properties."
  (-> '{primitive     [topology               [:triangle-list :topology]
                       strip-index-format     [nil            :index-format]
                       front-face             [:ccw           :front-face]
                       cull-mode              [:cull-none     :cull-mode]
                       unclipped-depth        false]
        stencil-face  [compare                [:fn-1 :compare-fn]
                       fail-op                [:op-1 :stencil-op]
                       depth-op               [:op-1 :stencil-op]
                       depth-fail-op          [:op-1 :stencil-op]
                       pass-op                [:op-1 :stencil-op]]
        depth-stencil [format                 [:required :texture-format]
                       depth-write?           false
                       depth-compare          [:fn-1 :compare-fn]
                       stencil-front          bllm.util/empty-obj
                       stencil-back           bllm.util/empty-obj
                       stencil-read-mask      0xffffffff
                       stencil-write-mask     0xffffffff
                       depth-bias             0
                       depth-bias-slope-scale 0
                       depth-bias-clamp       0]
        multisample   [count                  1
                       mask                   0xffffffff
                       alpha-to-coverage?     false]
        blend-comp    [operation              [:op+  :blend-op]
                       src-factor             [:one  :blend-factor]
                       dst-factor             [:zero :blend-factor]]
        blend         [color                  :required
                       alpha                  :required]}
      (update-vals (partial partition 2))))

(defn- state-tag [s]
  (symbol "js" (str "GPU" (util/kebab->pascal s) "State")))

(defn- rename-state-prop
  "Converts a lispy `foo?` symbol into a WebGPU `foo-enabled` property name."
  [sym]
  (let [s (name sym)]
    (if-not (str/ends-with? s "?")
      s
      (str (subs s 0 (dec (count s))) "-enabled"))))

(defn- required-prop [sym]
  `(do (assert ~sym ~(str "Param " sym " is required"))
       ~sym))

(defn- optional-prop [sym default]
  `(or ~sym ~(util/keyword->gpu default)))

(defn- expand-prop [f val]
  (list (util/keyword->gpu f) val))

(defn- configure-state
  [[sym default]]
  (cond (= :required default)
        (required-prop sym)

        (vector? default)
        (let [[value f] default]
          (case value
            :required (expand-prop f (required-prop sym))
            nil       `(if (nil? ~sym)
                         js/undefined
                         ~(expand-prop f sym))
            (expand-prop f (optional-prop sym value))))

        :else
        (optional-prop sym default)))

(defm ^:private defgpu
  [state]
  (let [props (state-props state)
        names (map first props)]
    `(defn ~state [~'hash ~@names]
       ~(emit-obj state nil
                  (map util/kebab->camel names)
                  (map configure-state props)))))

(defm ^:private defstate
  "Simpler `defnode` used to declare render states. Constructed with kw-args."
  [sym]
  (let [kind  (node-kind sym)
        props (state-props (util/sym kind))
        names (map first props)]
    `(defm ~sym [~'name & {:keys [~@names]}]
       (emit-node false util/ns-gpu ~'name ~kind
                  '~(util/keyword->wgsl kind)
                  ~@names))))

(defstate defprimitive
  "Describes how a pipeline constructs and rasterizes primitives from its vertices.")

(defstate defstencil-face)

(defstate defdepth-stencil
  "Describes how a pipeline will affect a render pass's depth-stencil attachment.")

(defstate defmultisample
  "Describes how a pipeline interacts with a render pass's multisampled attachments.")

(defstate defblend-comp
  "Describes how the color or alpha components of a fragment are blended.")

(defstate defblend)


;;; Render I/O - Vertex attributes, interpolants, fragment draw targets
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defm defio
  "Render I/O nodes have a `bind` slot and a `type`."
  [node]
  `(defnode ~node ~'[sym bind type]))

(defio defvertex-attr "Defines an input to the vertex stage.")
(defio defdraw-target "Defines an output from the fragment stage.")
(defio definterpolant "Defines an I/O channel between render stages.")


;;; Resources - Buffer Views, Texture Views & Samplers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defnode defstruct
  "Aggregate type definition."
  [sym & fields]
  (emit-struct sym (meta/parse-struct &env fields)))

(defnode defuniform
  [sym group bind & fields]
  (emit-struct sym (meta/parse-struct &env fields)))

(defnode defstorage
  [sym group bind type access])

(defnode deftexture
  [sym group bind tex type])

(defnode defsampler
  [sym group bind])


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
  "")

(defnode defpixel
  "Defines a fragment shader entry point."
  [sym & args]
  ;; interpolants -> fragment -> outputs
  ;;
  ;; runtime needs to collect all interpolants and unpack them
  ;; then collect all outputs and thread them into packed render targets
  "")

(defnode defkernel
  "Defines a compute shader entry point.

  Unlike vertex and pixel shaders, there are no user-defined parameters here.
  Only builtin inputs such as the `global-invocation-id` are available, from
  which user-defined parameters can then be accessed through bound resources.

  The workgoup size must also be specified. No default value suits all cases."
  [sym workgroup]
  ;; inputs -> compute -> outputs
  ;;
  ;; thread counts
  "")


;;; Shader Pipelines - Runtime Execution State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defnode defgroup
  {:wgsl false}
  [sym & args]
  ;; resolve all resource, make sure groups match
  ;;
  ;; descriptor for pipeline layouts
  ;; constructor for resource bindings
  )

(defnode deflayout
  {:wgsl false}
  [sym & args]
  ;; resolve all groups, make sure order match
  ;;
  ;; gpu/defres for layout object
  )

(defnode defrender
  {:wgsl false}
  [sym & args]
  )

(defnode defcompute
  {:wgsl false}
  [sym & args]
  )
