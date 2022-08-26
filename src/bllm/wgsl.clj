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
  (:require [cljs.analyzer  :as ana]
            [clojure.inspector :refer [inspect]]
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
  (map (partial ana/resolve-existing-var env) vars))

(defn- emit-field
  [{:keys [name type offset]}]
  (list 'bllm.wgsl/field
    (util/kebab->camel  name)
    (util/keyword->wgsl type)
    offset))

(defn- emit-struct
  [env sym fields]
  ;; TODO ctor -> ab? offset? -> dataview

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
    `(do (def ~sym
           (~ctor ~uuid ~hash
            ~@(when wgsl? [name])
            ~@(cond (nil?   deps) nil
                    (empty? deps) ['bllm.util/empty-array]
                    :else         [`(cljs.core/array ~@deps)])
            ~@(map (partial util/keyword->enum enum-ns) args)))
         (bllm.wgsl/register ~sym))))

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
  (let [v (:const-expr (ana/resolve-existing-var env sym))]
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
        props (when (:props attrs true)
                (take-while #(not= '& %) args))] ; Until the variadic arg marker.
    ;; Write code, that writes code, that writes code... oh hi Lisp!
    `(defm ~sym
       {:args '~(:args attrs args)}
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
             ~node  (if-not ~gmeta ~node (vary-meta ~node merge ~gmeta))]
         ;; Finally, generate the node on the ClojureScript side of things.
         (emit-node ~(:wgsl attrs true) util/ns-wgsl ~node ~kind
                    '~(util/keyword->wgsl kind)
                    ~(when (has-deps? (util/sym kind))
                       `(:deps ~gmeta []))
                    ~@props
                    ~@(when (not-empty body)
                        (if-let [ks (:keys attrs)]
                          (for [k ks] `(get ~ginit ~k))
                          [ginit])))))))

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
               (fn emit [emit-wgsl]
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
                           (map (comp ::uuid #(ana/resolve-existing-var ~'&env %)))))
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


;;; WGSL Expanders - Support for infix and macro special forms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO move to cljs compiler context? allow cljs defs to add more operators at compile time
(def precedence '{*  5
                  /  5
                  %  5
                  +  6
                  -  6
                  << 7
                  >> 7
                  <  9
                  <= 9
                  >  9
                  >= 9
                  == 10
                  &  11
                  ;; TODO xor (^ is already used by the reader to dispatch metadata literals)
                  |  13
                  && 14
                  || 15
                  =  16})

(def ops-env
  (reduce #(assoc %1 %2 {:op :op-fn :name %2}) {} (keys precedence)))

(defn- base-env []
  (-> @cljs.env/*compiler* :cljs.analyzer/namespaces
      (get ns-lib) :defs
      (merge ops-env)))

(defn- op? [x]
  (contains? precedence x))

(defn- expand-op [l [op r]]
  (if (> (precedence (first l))
         (or (precedence op)
             (throw (ex-info "Operator expected" {:op op}))))
    (concat (drop-last l) `((~op ~(last l) ~r)))
    (list op l r)))

(defn- infix-expand* [expr]
  (let [l (first expr)
        exprs (partition 2 (next expr))
        [[op r]] exprs]
    (reduce expand-op (list op l r) (rest exprs))))

(defn- infix-expand [form]
  (if-not (some op? (next form))
    form
    (let [c (count form)]
      (when-not (and (>= c 3) (odd? c))
        (throw (ex-info "Invalid operator form" {:form form})))
      (infix-expand* form))))

(comment (infix-expand '(hello world))
         (infix-expand '(1 (*) 2))
         (infix-expand '(1 * 2))
         (infix-expand '(place = 1 * expr + (2 * 3))))

(defn- macro-expand [env expr]
  (let [expr' (ana/macroexpand-1 env expr)]
    (if (= expr expr')
      expr
      (macro-expand env expr'))))


;;; WGSL Analyzer - Interpret a subset of Clojure into shader AST nodes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord ParseCtx [cljs-env deps])

(declare parse)

(defn- parse-all [ctx env forms]
  (map (partial parse ctx env) forms))

(defn- parse-dispatch [ctx env f args]
  f)

(defmulti parse* #'parse-dispatch)

(defmethod parse* :default [ctx env f args]
  {:op :call
   :fn (parse ctx env f)
   :xs (parse-all ctx env args)})

(defmethod parse* 'let* [ctx env _ [bindings & body]]
  (when-not (even? (count bindings))
    (throw (ex-info "Invalid bindings form" {:bindings bindings})))
  (loop [env   env
         out   ()
         pairs (partition 2 bindings)]
    (if-not pairs
      {:op   :let
       :bind (vec (reverse out))
       :body (parse-all ctx env body)}
      (let [[k v] (first pairs)
            node {:op    :binding
                  :name  k
                  :init  (parse ctx env v)
                  ::name (util/kebab->camel
                          (if-not (contains? env k)
                            k ; Unshadowed ident
                            (gensym (name k))))}]
        (recur (assoc env k node)
               (conj out node)
               (next pairs))))))

(defmethod parse* 'if [ctx env _ [cond then else]]
  {:op   :if
   :cond (parse ctx env cond)
   :then (parse ctx env then)
   :else (parse ctx env else)})

(defn- resolve-var [ctx sym]
  (when-let [v (ana/resolve-existing-var (:cljs-env ctx) sym)]
    (when (::uuid v)
      (.add ^java.util.HashSet (:deps ctx) v))
    v)) ; TODO validate

(defn- get-expand [s]
  (if (re-find #"^[0-9]+$" s)
    {:op :nth :elem (Integer/parseInt s)}
    {:op :field :name s ::name (util/kebab->camel s)}))

(defn- sym-expand [sym]
  (if-not (str/index-of (name sym) \.)
    sym
    (let [[s & path] (str/split (name sym) #"\.")]
      [(symbol s) (map get-expand path)])))

(def sym-expand-memoized (memoize sym-expand))

(comment (sym-expand 'foo)
         (sym-expand 'foo.bar)
         (sym-expand 'foo.3.xyz)
         (sym-expand 'foo.hello-world.bar))

(defn- parse-sym [ctx env ident]
  (let [exp  (sym-expand-memoized ident)
        vec? (vector? exp)
        root (if vec? (first exp) exp)
        node (or (env root) ; Local binding
                 (resolve-var ctx root) ; Graph node
                 (throw (ex-info "Symbol not found" {:ident ident})))]
    (if-not vec?
      node
      (loop [path (second exp)
             node node]
        (if-not path
          node
          (recur (next path)
                 (assoc (first path) :in node)))))))

(defn- parse-app [cljs env [f :as form]]
  (when-not f
    (throw (ex-info "Function expected" {})))
  (let [[f & args] (infix-expand (if (or (not (symbol? f))
                                         (contains? env f))
                                   form
                                   (macro-expand env form)))]
    (if (not= '. f)
      (parse* cljs env f args)
      (let [sym (second args)]
        {:op :field
         :in (parse cljs env (first args))
         :name sym
         ::name (util/kebab->camel sym)}))))

(defn- parse-lit [cljs env form]
  {:op :lit
   :value form
   :tag (cond
          (double?  form) :f
          (integer? form) :i
          :else (throw (ex-info "Unexpected literal type" {:type (type form)
                                                           :value form})))})

(defn- parse [cljs env form]
  (cond (symbol? form) (parse-sym cljs env form)
        (seq?    form) (parse-app cljs env form)
        :else          (parse-lit cljs env form)))


;;; WGSL Emitter - Reduce the AST down to source text
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO generate source maps
;; TODO advanced optimizations (rename vars, remove dead code, etc)

(def ^:private ^:dynamic *indent* 0)
(def ^:private ^:dynamic *return* nil) ; nil, :ret or {:op :binding} -> side-effect, fn scope, let scope

(defmulti ^:private gen* :op)

(defmethod gen* :default [node]
  (throw (ex-info (pr-str node) {:node node})))

(def ^:private block? #{:do :if :let :loop})

(def ^:private block-node? (comp block? :op))

(def ^:private needs-semicolon? (comp not block-node?))

(defn- indent []
  (print (util/spaces *indent*)))

(defn- semicolon
  ([]
   (println \;))
  ([node]
   (when (needs-semicolon? node)
     (semicolon))))

(defn- pr-name [node]
  (print (::name node)))

(defn- pr-name->camel [node]
  (print (or (::name node) (util/kebab->camel (:name node)))))

(defmethod gen* :local [node]
  (pr-name node))

(defmethod gen* :wgsl [node]
  (pr-name node))

(defmethod gen* :binding [node]
  (pr-name->camel node))

(defmethod gen* :var [node]
  (pr-name->camel node))

(defmethod gen* :nth [{:keys [elem in]}]
  (gen* in)
  (print \[)
  (print elem)
  (print \]))

(defmethod gen* :field [{:keys [name in]}]
  (gen* in)
  (print \.)
  (print (util/kebab->camel name)))

(defmethod gen* :lit [{:keys [value tag]}]
  (print value)
  (case tag
    :f (print \f)
    nil))

(defn- gen-stmt [node]
  (let [block (block-node? node)]
    (when-not block
      (indent)
      (if (= *return* :ret)
        (print "return ")
        (some-> *return* ::name (print "= "))))
    (binding [*return* (and block *return*)]
      (gen* node))
    (semicolon node)))

(defn- gen-block [stmts]
  (binding [*return* nil] ; Statements (side-effects execution)
    (doseq [node (butlast stmts)]
      (indent)
      (gen* node)
      (semicolon node)))
  (when-let [node (last stmts)] ; Terminator (control-flow evaluation)
    (gen-stmt node)))

(defmethod gen* :do [{:keys [body]}]
  (gen-block body))

(defn- gen-clause [node]
  (print " {")
  (newline)
  (binding [*indent* (inc *indent*)]
    (gen-stmt node))
  (indent)
  (print \})
  (newline))

(defmethod gen* :if [{:keys [cond then else]}]
  ;; TODO support cond being a block. (if (let [...] ...) ...) is a valid form
  (print "if (")
  (gen* cond)
  (print ")")
  (gen-clause then)
  (when else
    (indent)
    (print "else")
    (if (= :if (:op else))
      (gen* else)
      (gen-clause else))))

(defmethod gen* :let [{:keys [bind body]}]
  (doseq [{:keys [name init] :as node} bind
          :let [m (meta name)
                is-block? (block-node? init)]]
    (indent)
    (print (if is-block?
             "var"
             (cond (:const m) "const"
                   (:mut   m) "var"
                   :else      "let")))
    (print \space)
    (pr-name node)
    ;; TODO type tag
    (if is-block?
      (do (semicolon)
          (indent)
          (binding [*return* node]
            (gen* init)))
      (do (when init
            (print " = ")
            (gen* init))
          (semicolon))))
  (gen-block body))

(defmethod gen* :call [{:keys [fn xs]}]
  ;; TODO dont be so aggressive on outputting parens
  (case (:op fn)
    :op-fn ; Built-in WGSL operators, infix syntax.
    (let [fname (:name fn)]
      (print \()
      (case (count xs)
        0 (throw (ex-info "Expecting arguments" {:fn fn}))
        1 (do (print fname) ; Unary; (- operand)
              (gen* (first xs)))
        2 (do (gen* (first xs)) ; Binary; (operand + operand)
              (print \space)
              (print fname)
              (print \space)
              (gen* (second xs)))
        (do (gen* (first xs)) ; Lispy; (* operand operand operand ...)
            (loop [[x & xs] xs]
              (print \space)
              (print fname)
              (print \space)
              (gen* x)
              (some-> xs (recur)))))
      (print \)))

    (:var :wgsl) ; Named calls, fn can be user defined or built in WGSL.
    (do (gen* fn)
        (print \()
        (doseq [x (butlast xs)]
          (gen* x)
          (print ", "))
        (when-let [x (last xs)]
          (gen* x))
        (print \)))

    (throw (ex-info "Unexpected function value" {:func fn}))))

(defn- gen [body]
  (with-out-str
    (gen-block body)))

(defn- compile-fn [cljs-env body link-fn]
  (let [deps (java.util.HashSet.)
        cljs (->ParseCtx cljs-env deps)
        env  (base-env) ; TODO remember env between macros expansions? detect changes from macros in bllm.base
        code (for [expr body]
               (parse cljs env expr))
        wgsl (binding [*indent* 1]
               (gen code))]
    (link-fn deps code wgsl))) ; TODO code -> sourcemap


;;; Shader Code - Declarations of Constants, Variables, Functions & Entry Points
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- with-deps [deps x]
  (with-meta x {:deps (mapv ::uuid deps)}))

(defnode defenum
  [sym & args]) ; TODO like meta/defenum, but emits WGSL

(defnode defflag
  [sym & args]) ; TODO like meta/defflag, but emits WGSL

(defnode defconst
  "Compile-time constants."
  {:keys [:type :init] :props false}
  [sym init]
  {:type nil :init init}) ;; TODO expand init expr, infer type

(defnode defvar
  "Pipeline-overridable constants."
  {:keys [:type :init] :kind :override}
  [sym & [init]]
  {:type nil :init init}) ;; TODO same as defconst

(defn- argument [id [sym tag]]
  {:name sym
   ::name (util/kebab->camel sym)
   :binding-form? true
   :op :binding
   :env {:context :expr}
   :arg-id id
   :info {:name sym}
   :tag tag
   :local :arg})

(defnode defun
  {:props false :kind :function
   :keys [:ret :args :wgsl]
   :args [ret? [params*] & body]}
  [sym args|ret & body]
  (let [ret  (when-not (vector? args|ret) args|ret)
        args (->> (if ret (first body) args|ret)
                  (partition 2) ; ident/type pairs
                  (map-indexed argument))
        env  (->> (reduce #(assoc %1 (:name %2) %2) {} args)
                  (assoc &env :locals))] ; Make args visible to cljs' analyzer
    (->> (fn link [deps code wgsl]
                    ;; TODO type inference on let bindings -> ret
                    (with-deps deps
                      {:wgsl wgsl
                       :ret  (util/keyword->wgsl (or ret :f32)) ;; TODO default to type inference
                       :args `(cljs.core/array
                               ~@(for [arg args]
                                   `(bllm.wgsl/argument
                                     ~(::name arg)
                                     ~(util/keyword->wgsl (:tag arg)))))}))
         (compile-fn env (if ret (next body) body))
         (binding [*return* :ret]))))

(defnode defkernel
  "Defines a compute shader entry point.

  Unlike vertex and pixel shaders, there are no user-defined parameters here.
  Only builtin inputs such as the `global-invocation-id` are available, from
  which user-defined parameters can then be accessed through bound resources.

  The workgoup size must also be specified. No default value suits all cases."
  {:keys [:x :y :z :io :wgsl] :props false}
  [sym [x y z] & body]
  (compile-fn &env body
              (fn link [deps code wgsl]
                (with-deps deps {:wgsl wgsl :x x :y y :z z
                                 :io nil}))))

(defn- select-node [deps kind]
  (->> deps
       (filter #(= kind (::kind %)))
       (map ::uuid)
       (into #{})))

(defn- gen-io [ids]
  (if (empty? ids)
    `(bllm.wgsl/empty-io)
    `(bllm.wgsl/gen-io ~(hash ids) (bllm.util/array ~@ids))))

(defn- compile-io [stage env body]
  (compile-fn env body
              (fn link [deps code wgsl]
                (let [ids-stage  (select-node deps stage)
                      ids-raster (select-node deps :interpolant)]
                  (-> #(let [id (::uuid %)]
                         (or (ids-stage id) (ids-raster id)))
                      (remove deps)
                      (with-deps
                        {:wgsl  wgsl
                         :stage (gen-io ids-stage)
                         :io    (gen-io ids-raster)}))))))

(defnode defvertex
  "Defines a vertex shader entry point."
  {:keys [:stage :io :wgsl]}
  [sym & body]
  (compile-io :vertex-attr &env body))

(defnode defpixel
  "Defines a fragment shader entry point."
  {:keys [:stage :io :wgsl]}
  [sym & body]
  (compile-io :draw-buffer &env body))


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

;; TODO constructor for resource bindings
(defnode defgroup
  {:wgsl false :keys [:value]}
  [sym & binds]
  (resolve-bindings &env binds ::bind
                    #{:uniform :storage :texture :sampler}
                    emit-group))

;; TODO gpu/defres for layout object
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
  (let [{:keys [layout vertex pixel primitive depth-stencil multisample depth]}
        (group-by ::kind (resolve-vars &env layout|stages|states))]
    (emit-pipeline (required-first layout)
                   (required-first vertex)
                   (optional-first pixel)
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
