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
  (:require [cljs.analyzer :as ana]
            [clojure.string :as str]
            [bllm.meta :as meta]
            [bllm.util :as util :refer [defm]]))


;;; Utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- gpu-id [ns sym]
  (assert (string? ns))
  (assert (string? sym))
  (let [h (bit-xor (hash ns) (hash sym))] ; Poor man's deterministic ID.
    (assert (or (< h 0) (>= h 32))) ; Reserved ID range for primitive types.
    h))

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

(defn- resolve-vars [env vars]
  (map (partial ana/resolve-var env) vars))

(defn- emit-field
  [field]
  (list 'cljs.core/array
    (util/kebab->camel  (:name field))
    (util/keyword->wgsl (:type field))
    (:offset field)))

(defn- emit-struct
  [env sym fields]
  ;; TODO ctor -> ab? offset? -> dataview
  ;; - attach array of fields as ctor prop, pass ctor to node ctor

  (with-meta `(util/array ~@(map emit-field fields))
    (->> (map :type fields)
         (filter symbol?)
         (map (comp ::uuid (partial ana/resolve-var env)))
         (assoc (meta fields) :deps))))

(defn- emit-node
  "Emits a WGSL node definition and its registration to the shader graph."
  [wgsl? enum-ns sym kind ctor deps & args]
  (let [uuid (gpu-id (str *ns*) (name sym))
        hash (gpu-hash sym args) ; TODO diff hash for reload vs identity?
        name (and wgsl? (gpu-name sym))
        sym  (cond-> (vary-meta sym assoc ::kind kind ::uuid uuid ::hash hash)
               wgsl? (vary-meta assoc ::name name))]
    `(bllm.wgsl/reg
      (def ~sym
        (~ctor ~uuid ~hash
         ~@(when wgsl? [name])
         ~@(cond (nil?   deps) nil
                 (empty? deps) ['bllm.util/empty-array]
                 :else         [`(cljs.core/array ~@deps)])
         ~@(map (partial util/keyword->enum enum-ns) args))))))

(defn- node-kind [sym]
  (or (:kind (meta sym))
      (keyword (str/replace-first (name sym) #"^def-?" ""))))

(def ^:private has-deps?
  "WGSL node kinds tracking their dependencies to other WGSL nodes."
  '#{uniform
     struct
     function
     depth-stencil
     blend
     vertex
     pixel
     kernel
     group
     layout
     render
     compute})

(defn- resolve-enum [env sym]
  (let [v (:const-expr (ana/resolve-var env sym))]
    (when-not (and (= :const  (:op  v))
                   (= 'number (:tag v)))
      (throw (ex-info "Expected number constant." {:sym sym :expr v})))
    (:val v)))

(defn- invalid-bind-prop [key val]
  (throw (ex-info "Expected number or symbol." {:key key :in val})))

(def ^:private bind-group-props '#{group bind})

(defn- resolve-bind-group [prop]
  (if-not (bind-group-props prop)
    prop
    `(cond (symbol? ~prop) (resolve-enum ~'&env ~prop)
           (or (number? ~prop) (keyword? ~prop)) ~prop
           :else (invalid-bind-prop ~(name prop) ~prop))))

(defm ^:private defnode
  "Defines a kind of WGSL node definition. Used to define nodes of its kind."
  [sym [node & args] & body]
  (let [kind  (node-kind sym)
        attrs (meta sym)
        ginit (gensym "init")
        gmeta (gensym "meta")
        props (take-while #(not= '& %) args)] ; Until the variadic arg marker.
    ;; Write code, that writes code, that writes code... oh hi Lisp!
    `(defm ~sym
       {:args '~args}
       [~node & args#]
       (let [;; Allow the doc-string to be placed at the end, like Emacs-Lisp.
             doc#     (last args#)
             doc?#    (string? doc#)
             [~@args] (if doc?# (butlast args#) args#)
             ~node    (if-not doc?# ~node (vary-meta ~node assoc :doc doc#))
             ;; Copy named node properties to the meta-data of its defined var.
             ~@(when (not-empty props)
                 `[~node (vary-meta ~node assoc
                                    ~@(util/flatten1
                                       (for [p props]
                                         [(keyword util/ns-wgsl (name p))
                                          (resolve-bind-group p)])))])
             ;; Execute the compile-time logic specific to this node type.
             ~ginit (do ~@body)
             ~gmeta (meta ~ginit)
             ~node (if-not ~gmeta ~node (vary-meta ~node merge ~gmeta))]
         ;; Finally, generate the node on the ClojureScript side of things.
         (emit-node ~(:wgsl attrs true) util/ns-wgsl ~node ~kind
                    '~(util/keyword->wgsl kind)
                    ~(when (has-deps? (util/sym kind))
                       `(:deps ~gmeta []))
                    ~@props
                    ~@(when (not-empty body)
                        [(if (:wrap attrs)
                           `(:value ~ginit)
                           ginit)]))))))

(defn- emit-obj [sym emit? keys vals]
  `(cljs.core/js-obj
    "kind" ~(symbol util/ns-wgsl (util/kebab->pascal sym))
    "uuid" ~'id
    "hash" ~'hash
    ~@(when (has-deps? sym)
        '["deps" deps])
    ~@(when emit?
        '["name" name
          "wgsl" js/undefined])
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
    `(defn ~sym [~'id ~'hash
                 ~@(when emit '[name])
                 ~@(when (has-deps? sym) '[deps])
                 ~@params]
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
                       cull-mode              [:none          :cull-mode]
                       unclipped-depth        false]
        stencil-face  [compare                [:fn-always :compare-fn]
                       fail-op                [:op-keep   :stencil-op]
                       depth-op               [:op-keep   :stencil-op]
                       depth-fail-op          [:op-keep   :stencil-op]
                       pass-op                [:op-keep   :stencil-op]]
        depth-stencil [format                 [:required  :texture-format]
                       depth-write?           false
                       depth-compare          [:fn-always :compare-fn]
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
        blend-comp    [operation              [:op-add :blend-op]
                       src-factor             [:one    :blend-factor]
                       dst-factor             [:zero   :blend-factor]]
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
    `(defn ~state [~'id ~'hash
                   ~@(when (has-deps? state) '[deps])
                   ~@names]
       ~(emit-obj state nil
                  (map util/kebab->camel names)
                  (map configure-state props)))))

(defm ^:private defstate
  "Simpler `defnode` used to declare render states. Constructed with kw-args."
  [sym & deps]
  (let [kind  (node-kind sym)
        k-sym (util/sym kind)
        props (state-props k-sym)
        names (map first props)]
    `(defm ~sym [~'name & {:keys [~@names]}]
       (emit-node false util/ns-gpu ~'name ~kind
                  '~(util/keyword->wgsl kind)
                  ~(when (has-deps? k-sym)
                     `(->> (filter some? [~@deps])
                           (map (comp ::uuid #(ana/resolve-var ~'&env %)))))
                  ~@names))))

(defstate defprimitive
  "Describes how a pipeline constructs and rasterizes primitives from its vertices.")

(defstate defstencil-face)

(defstate defdepth-stencil
  "Describes how a pipeline will affect a render pass's depth-stencil attachment."
  stencil-front stencil-back)

(defstate defmultisample
  "Describes how a pipeline interacts with a render pass's multisampled attachments.")

(defstate defblend-comp
  "Describes how the color or alpha components of a fragment are blended.")

(defstate defblend
  color alpha)


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
  (emit-struct &env sym (meta/parse-struct &env fields)))

(defnode defuniform
  [sym group bind & fields]
  (emit-struct &env sym (meta/parse-struct &env fields)))

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
  {:kind :override}
  [sym & args]
  ;; ID is hash of sym
  ;; no default means required constant
  ;;
  ;; @id(0) override name : type = default;
  )

(defnode defun ; TODO like defn, but emits WGSL
  {:kind :function}
  [sym & args]
  )

(defn- emit-entry [env ]
  )

(defnode defvertex
  "Defines a vertex shader entry point."
  [sym & body]
  ;; inputs -> vertex -> interpolants
  ;;
  ;; runtime needs to collect all inputs and unpack them from the generated entry
  ;; then collect all outputs and thread them into packed interpolants
  (emit-entry &env ))

(defnode defpixel
  "Defines a fragment shader entry point."
  [sym & args]
  ;; interpolants -> fragment -> outputs
  ;;
  ;; runtime needs to collect all interpolants and unpack them
  ;; then collect all outputs and thread them into packed render targets
  (emit-entry &env))

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
  (emit-entry &env ))


;;; Resource Groups
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- resolve-bindings [env args sort-key allowed-kinds emit-fn]
  (when (empty? args)
   (throw (ex-info "Expecting binding list" {})))
  (let [vars (resolve-vars env args)]
    (doseq [v vars]
      (when-not (allowed-kinds (::kind v))
        (throw (ex-info "Unexpected binding kind"
                        {:expected allowed-kinds :found (::kind v) :in v}))))
    (let [sorted-vars (sort-by sort-key vars)]
      (emit-fn sorted-vars (map ::uuid vars)))))

(defn- emit-group [vars deps]
  (when-not (apply = (map ::group vars))
    (throw (ex-info "Group bindings must match" {:in vars})))
  (when-not (distinct? (map ::bind vars))
    (throw (ex-info "Group bindings cannot overlap" {:in vars})))
  (let [g (::group (first vars))]
    (with-meta {:value g} {:deps deps ::group g})))

(defn- emit-layout [vars deps]
  (when-not (distinct? (map ::group vars))
    (throw (ex-info "Layout groups cannot overlap" {:in vars})))
  (let [grps (loop [vars vars
                    bind 0
                    grps ()]
               (if-not vars
                 `(cljs.core/array ~@(reverse grps))
                 (let [v (first vars)
                       g (when (= bind (::group v))
                           (:name v))]
                   (recur (if g (next vars) vars)
                          (inc bind)
                          (cons g grps)))))]
    (with-meta grps {:deps deps})))

;; constructor for resource bindings
(defnode defgroup
  {:wgsl false :wrap true}
  [sym & binds]
  (resolve-bindings &env binds ::bind
                    #{:uniform :storage :texture :sampler}
                    emit-group))

;; gpu/defres for layout object
(defnode deflayout
  {:wgsl false}
  [sym & groups]
  (resolve-bindings &env groups ::group
                    #{:group}
                    emit-layout))


;;; Shader Pipelines - Runtime Execution State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- required-first [g]
  (if (= 1 (count g))
    (first g)
    (throw (ex-info "Missing required element" {}))))

(defn- optional-first [g]
  (case (count g)
    0 nil
    1 (first g)
    (throw (ex-info "Too many elements of the same kind" {:elem g}))))

(defn- emit-pipeline [& elems]
  ;; TODO where does `gpu/defres` fit in this -> implement that subsystem first
  (with-meta `(cljs.core/array ~@(map :name elems))
    {:deps (filter some? (map ::uuid elems))}))

(defnode defrender
  {:wgsl false}
  [sym & layout|stages|states]
  (let [{:keys [layout vertex fragment primitive depth-stencil multisample depth]}
        (group-by ::kind (resolve-vars &env layout|stages|states))]
    (emit-pipeline (required-first layout)
                   (required-first vertex)
                   (optional-first fragment)
                   (optional-first primitive)
                   (optional-first depth-stencil)
                   (optional-first multisample)
                   (optional-first depth))))
;; TODO fragment blends, match number of outputs in fragment stage (or 0, or 1 repeated blend)

(defnode defcompute
  {:wgsl false}
  [sym & layout|stage]
  (let [{:keys [layout compute]}
        (group-by ::kind (resolve-vars &env layout|stage))]
    (emit-pipeline (required-first layout)
                   (required-first compute))))
