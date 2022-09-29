(ns bllm.wgsl
  "Meta WebGPU. Because raw text is hard to digest, tastes like concat.

  Only a few guidelines for emitting code:
  - Don't bloat the output, emit fn calls to 'decompress' the macro outputs.
  - All macros must be pure, Clojure doesn't know about ClojureScript's env.
  - Just enough to emit WGSL source, JavaScript bindings, and shader graphs.
  - Only expose WGSL constructs using S-Expressions, layer decisions on top.

  The goal is to write GPU definitions transparently alongside the CPU ones.
  Leverage figwheel to hot-reload shader code the same way it does with JS."
  (:refer-clojure :exclude [defstruct newline])
  (:require [cljs.analyzer  :as ana]
            [clojure.string :as str]
            [bllm.gpu  :as gpu]
            [bllm.meta :as meta]
            [bllm.util :as util :refer [defm]]))


;;; Utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- emit-field
  [{:keys [name type offset]}]
  (list 'bllm.wgsl/field
    (util/kebab->camel  name)
    (util/keyword->gpu type)
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
  [& {:keys [sym expr wgsl hash kind deps args]}]
  (let [uuid (util/unique-id sym)
        hash (or  hash (util/unique-hash args))
        name (and wgsl (util/unique-name sym))
        sym  (cond-> (vary-meta sym assoc ::kind kind ::uuid uuid ::hash hash)
               wgsl (vary-meta assoc ::name name)
               expr (vary-meta expr))]
    `(do (def ~sym
           (~(util/keyword->wgsl kind) ~uuid ~hash
            ~@(when wgsl [name])
            ~@(cond (nil?   deps) nil
                    (empty? deps) ['bllm.util/empty-array]
                    :else         [`(cljs.core/array ~@deps)])
            ~@args))
         (bllm.wgsl/register ~sym))))

(defn- node-kind [sym]
  (or (:kind (meta sym))
      (keyword (str/replace-first (name sym) #"^def-?" ""))))

(def ^:private has-deps?
  "WGSL node kinds tracking their dependencies to other WGSL nodes."
  '#{buffer
     struct
     function
     depth-stencil
     blend
     kernel
     vertex
     pixel
     group
     layout
     render
     compute})

(defn- read-const [{expr :const-expr}]
  (when-not (and (= :const  (:op  expr))
                 (= 'number (:tag expr)))
    (throw (ex-info "Expected number constant." {:expr expr})))
  (:val expr))

(defn- resolve-enum [env sym]
  (read-const (ana/resolve-existing-var env sym)))

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
         (emit-node
          :sym  ~node
          :expr ~(:expr attrs)
          :wgsl ~(:wgsl attrs true)
          :hash (::hash ~gmeta)
          :kind ~kind
          :deps ~(when (has-deps? (util/sym kind))
                  `(:deps ~gmeta []))
          :args (map util/keyword->gpu
                     [~@props
                      ~@(when (not-empty body)
                          (if-let [ks (:keys attrs)]
                            (for [k ks] `(get ~ginit ~k))
                            [ginit]))]))))))

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

(defm ^:private defast
  "Defines a WGSL AST element. Encodes an expression in Clojure and decodes
  it in ClojureScript."
  [sym]
  ;; generate decoder -> really just an indexed function, pulls arguments from stream & creates node
  ;;
  ;; variants "spread" -> can be a few function calls under the entry before the changed function -> all chain affected
  )

(comment (defast if cond then else)
         (defast do [body])
         (defast let [bindings] [body])
         (defast call fn [args]))
;; encoding could be MUCH smaller than embedding tons of pre-emitted wgsl -> gzip make it kinda irrelevant, still interesting to measure
;; - ArrayBuffer[0x12345609,0x12304355,0x12301230] -> arg arg OP arg OP OP arg OP arg OP -> AST tree in JS!
;; - emit code really not hard to port to JS
;; - even generating the JS encoding doesnt look that hard
;; - decoding is trivial, its just a forth stack
;; - worst case gives us specs for AST here

;; BUT
;; - can now generate WGSL entirely from JS
;; - good to build tech and mesh types with
;; - schedule pipeline with variant sets
;;   - locate all entry permutations
;;   - trace to leaves, collect intermediates to "rename"
;;     - ie foo-cs calls both feature:on and feature:off, but through fizzbazz()
;;     - need fizzbazz and fizzbazz__feature:on -> feature default changes nothing
;;     - then all variants can coexist in single shader module
;;     - need same rename for entry points


;;; Render States
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def state-props
  "Specification of the render state properties."
  (-> '{primitive     [topology               [:triangle-list :primitive-topology]
                       strip-index-format     [nil            :index-format]
                       front-face             [:ccw           :front-face]
                       cull-mode              [:none          :cull-mode]
                       unclipped-depth        false]
        stencil-face  [compare                [:always   :compare-function]
                       fail-op                [:keep     :stencil-operation]
                       depth-op               [:keep     :stencil-operation]
                       depth-fail-op          [:keep     :stencil-operation]
                       pass-op                [:keep     :stencil-operation]]
        depth-stencil [format                 [:required :texture-format]
                       depth-write?           false
                       depth-compare          [:always   :compare-function]
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
        blend-comp    [operation              [:add        :blend-operation]
                       src-factor             [:blend-one  :blend-factor]
                       dst-factor             [:blend-zero :blend-factor]]
        blend         [color                  :required
                       alpha                  :required]}
      (update-vals (partial partition 2))))

(defn- state-tag [s]
  (util/js-sym (str "GPU" (util/kebab->pascal s) "State")))

(defn- rename-state-prop
  "Converts a lispy `foo?` symbol into a WebGPU `foo-enabled` property name."
  [sym]
  (util/kebab->camel
   (let [s (name sym)]
     (if-not (str/ends-with? s "?")
       s
       (str (subs s 0 (dec (count s))) "Enabled")))))

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
                  (map rename-state-prop names)
                  (map configure-state props)))))

(defm ^:private defstate
  "Simpler `defnode` used to declare render states. Constructed with kw-args."
  [sym deps]
  (let [kind  (node-kind sym)
        k-sym (util/sym kind)
        props (state-props k-sym)
        names (mapv first props)]
    `(defm ~sym [~'name & {:keys ~names}]
       (emit-node
        :wgsl false
        :sym  ~'name
        :kind ~kind
        :deps ~(when (has-deps? k-sym)
                 `(->> (filter some? ~deps)
                       (map (comp ::uuid #(ana/resolve-existing-var ~'&env %)))))
        :args (map util/keyword->gpu ~names)))))

(defstate defprimitive
  "Describes how a pipeline constructs and rasterizes primitives from its vertices.")

(defstate defstencil-face)

(defstate defdepth-stencil
  "Describes how a pipeline will affect a render pass's depth-stencil attachment."
  [stencil-front stencil-back])

(defstate defmultisample
  "Describes how a pipeline interacts with a render pass's multisampled attachments.")

(defstate defblend-comp
  "Describes how the color or alpha components of a fragment are blended.")

(defstate defblend
  [color alpha])


;;; I/O - Builtin vars, vertex attributes, interpolants, fragment draw targets
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- io-expr
  ([prefix]
   (partial io-expr (constantly prefix) ""))
  ([prefix-fn name-prefix m]
   (assoc m ::expr (str \_ (prefix-fn m) \. name-prefix (::name m)))))

(defn- builtin-io [dir in out]
  (case dir
    :in  in
    :out out
    (throw (ex-info "Unexpected builtin direction" {:dir dir}))))

(defn- builtin-prefix [m]
  (let [dir (::dir m)]
    (case (::stage m)
      :vertex   (builtin-io dir "in" "io")
      :fragment (builtin-io dir "io" "out")
      :compute  (do (when (not= :in dir)
                      (throw (ex-info "Invalid compute builtin" m)))
                    "in"))))

(defnode defbuiltin
  {:keys [:name :stage :dir :type] :props false :wgsl false
   :expr (partial io-expr builtin-prefix "_")}
  [sym stage dir type & {:as opts}]
  (let [ident (or (:name opts) (util/kebab->snake sym))]
    (with-meta {:name  ident
                :stage (util/keyword->gpu stage "stage-")
                :type  (util/keyword->gpu type)
                :dir   (builtin-io dir true false)}
      {::name  ident
       ::type  type
       ::stage stage
       ::dir   dir})))

(defm defio
  "Render I/O nodes have a `bind` slot and a `type`."
  [node prefix])

(defnode definterpolant
  {:expr (io-expr "io")}
  [sym bind type])

(defnode defattrib
  {:expr (io-expr "in") :kind :attribute}
  [sym bind type & step]
  (if step
    (util/keyword->gpu (first step))
    'bllm.gpu/step-vertex))

(defn- parse-mask* [kw]
  (->> (name kw)
       (seq)
       (map {\R 1 \G 2 \B 4 \A 8})
       (reduce bit-or 0)))

(def ^:private parse-mask (memoize parse-mask*))

(comment (parse-mask* :GA))

(defnode deftarget
  {:expr (io-expr "out") :kind :color-target :keys [:mask :blend]}
  [sym bind type & mask|blend]
  (let [elem (first mask|blend)
        mask (if (keyword? elem)
               (parse-mask* elem)
               'bllm.gpu/RGBA)
        elem (if mask (second mask|blend) elem)
        blend (when (symbol? elem)
                (let [node (ana/resolve-existing-var &env elem)]
                  (when (not= :blend (::kind node))
                    (throw (ex-info "Invalid blend state reference" {:blend elem})))
                  node))]
    {:mask mask :blend blend}))

;; ATTRIB -> location + GPU type from preferred CPU format
;; ARRAYS -> offsets, stride, ordered attrib as deps

;; ISSUE -> how to keep attribs locations matching across different arrays?
;; -> thread value throughout? how to pass mat4 -> expand to individual vertex descriptors
;;    -> just need to see :matXXX as type?

;; know the structure bottom-up
;; need top-down wirings
;; - wire pipeline to shader and state
;;
;; - pipeline -> vertex
;;            -> stream -> arrays -> attribs & step mode
;;                      -> buffers

;; vertex :: shader stage -> consumes attributes, doesnt care about arrays and stream, but still indirectly bound to them at pipeline
;; stream :: render state -> composes arrays, thats it -> single state given to pipeline
;; arrays :: buffer shape -> computes attrib offsets, array stride, interleaved, optional CPU-side TypedArray ctor -> hmm need to recompile on attrib changes
;; attrib :: source graph -> contains

;; pipeline bound to the "shape" of `setVertexBuffer`
;; - in SOME cases, these geometry can be generated on the CPU -> pack FN useful, make it OPT IN
;; - matches offsets and stride computed at `defarray`
;;   - use of multiple arrays is FREQUENCY OF CHANGE, not just step mode
;;   - static mesh data + computed skinning inputs, for example
;;
;; vertex indexing completely decoupled UNLESS using strips, then part of primitive state
;; - otherwise only specified by `setIndexBuffer` and triggered by `drawIndexed`
(defm definput
  [sym & attribs])

(defm defoutput
  [sym & targets]
  )

(defm defstream
  [sym & inputs]
  ;; build deps
  )


;;; Resources - Buffer Views, Texture Views & Samplers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- binding-type* [sym types]
  (util/keyword->gpu
   (or (first (filter (meta sym) types))
       (first types))))

(def ^:private binding-type (memoize binding-type*))

(defnode defstruct
  "Aggregate type definition."
  [sym & fields]
  (emit-struct &env sym (meta/parse-struct &env fields)))

(defnode defbuffer
  {:keys [:type :info]}
  [sym group bind & fields]
  (let [type (binding-type sym [:uniform :storage :read-only-storage])
        info (emit-struct &env sym (meta/parse-struct &env fields))]
    (with-meta {:type type :info info}
      (meta info))))

(defnode deftexture
  [sym group bind view type]
  (binding-type sym [:float :unfilterable-float :depth :sint :uint]))

(defnode defstorage
  [sym group bind view texel access]
  (binding-type sym [:write-only]))

(defnode defsampler
  [sym group bind]
  (binding-type sym [:filtering :non-filtering :comparison]))


;;; Resource Groups
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- resolve-bind-var [env x]
  (if-not (symbol? x)
    x
    (ana/resolve-existing-var env x)))

(defn- resolve-binds [env args]
  (map (partial resolve-bind-var env) args))

(defn- parse-bind [bind-key vars bind-first bind-next inline emit]
  (loop [vars vars
         deps ()
         defs ()
         used 0]
    (if vars
      ;; 1 - Run through vars, separate inline defs from refs, compute bind mask.
      (let [arg (first vars)
            def (when (seq? arg) arg)
            dep (if-not def arg {::uuid (util/unique-id (second def)) ::inline true})]
        (recur (next vars)
               (conj deps dep)
               (if def (conj defs def) defs)
               (long (if-let [bind (get dep bind-key)]
                       (do (when (bit-test used bind)
                             (throw (ex-info "Binding already in use"
                                             {:used used :dep dep :arg arg})))
                           (bit-set used bind))
                       used))))
      ;; 2 - Bind inline defs.
      (loop [bind bind-first
             used used
             defs (reverse defs)
             gen  ()
             out  ()]
        (if (not-empty defs)
          (let [used? (bit-test used bind)]
            (recur (bind-next bind)
                   (long (bit-set used bind))
                   (if used? defs (next defs))
                   (if used? gen  (conj gen bind))
                   (if used? out  (conj out (util/splice-defm (inline bind)
                                                              (first defs))))))
          ;; 3 - Code generation.
          `(do ~@(reverse out)
               ~(emit (reverse deps)
                      (reverse gen))))))))

(defn- empty-defgroup []
  (throw (Exception. "Empty defgroup")))

(defm defgroup
  [sym & binds]
  (if (empty? binds)
    (empty-defgroup)
    (let [vars (resolve-binds &env binds)
          arg  (first vars)
          grp  (cond (number? arg) arg
                     (:const  arg) (read-const arg))
          vars (if grp (next vars) vars)
          grps (filter some? (map ::group vars))
          grp  (or grp (first grps)
                   (throw (Exception. "Missing group number")))]
      (gpu/check-limit grp :max-bind-groups)
      (when (and (zero? grp) (not (:override (meta sym))))
        (throw (Exception. "Bind group 0 requires ^:override")))
      (if (empty? vars)
        (empty-defgroup)
        (parse-bind ::bind vars 0 inc #(vector grp %)
                    (fn [deps _]
                      (emit-node :sym  (vary-meta sym assoc ::group grp)
                                 :kind :group
                                 :wgsl false
                                 :hash (hash binds)
                                 :deps (map ::uuid deps)
                                 :args [grp])))))))

(defn- wrap-dec
  "Decrements x until it reaches 0, then increments from n."
  [n x]
  (cond
    (zero? x) (inc n)
    (< n x)   (inc x)
    :else     (dec x)))

;; check :max-dynamic-uniform-buffers-per-pipeline-layout
;; check :max-dynamic-storage-buffers-per-pipeline-layout

(defm deflayout
  [sym & groups]
  (if (empty? groups)
    (throw (Exception. "Empty deflayout"))
    (let [limit (:max-bind-groups gpu/limits)]
      (parse-bind ::group (resolve-binds &env groups)
                  (dec limit) (partial wrap-dec limit) vector
                  (fn [deps binds]
                    (loop [bind 0
                           out  () ; inline defs have been bound, do deps here
                           grps (->> (filter ::inline deps)
                                     (map-indexed #(assoc %2 ::group (nth binds %1)))
                                     (concat (remove ::inline deps))
                                     (sort-by ::group))]
                      (if (not-empty grps)
                        ;; Splice in `null` group slots.
                        (let [node (first grps)
                              use? (= bind (::group node))]
                          (recur (inc bind)
                                 (conj out (and use? node))
                                 (if use? (next grps) grps)))
                        ;; Emit groups if different from deps.
                        (let [uuids (map ::uuid (reverse out))
                              deps' (filter some? uuids)]
                          (emit-node :sym  sym :kind :layout :wgsl false
                                     :hash (hash groups)
                                     :deps deps'
                                     :args (if (= uuids deps')
                                             [nil]
                                             [`(cljs.core/array ~@uuids)]))))))))))


;;; WGSL Expanders - Support for infix and macro special forms
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ns-lib (symbol util/ns-lib))

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

(def macro-expand-ignore
  "Don't `macro-expand` forms expanding into calls to `js*`."
  '#{/})

(defn- macro-expand [env expr]
  (if (and (seq? expr) (macro-expand-ignore (first expr)))
    expr
    (let [expr' (ana/macroexpand-1 env expr)]
      (if (= expr expr')
        expr
        (macro-expand env expr')))))


;;; WGSL Analyzer - Interpret a subset of Clojure into shader AST nodes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord ParseCtx [cljs-env deps])

(declare parse)

(defn- parse-all [ctx env forms]
  (map (partial parse ctx env) forms))

(defn- parse-dispatch [ctx env f args]
  f)

(defmulti parse* #'parse-dispatch)

;; TODO convert throw to discard on GPU?

(defmethod parse* :default [ctx env f args]
  {:op :call
   :fn (parse     ctx env f)
   :xs (parse-all ctx env args)})

(defmethod parse* 'do [ctx env _ body]
  {:op   :do
   :body (parse-all ctx env body)})

(defmethod parse* 'if [ctx env _ [cond then else]]
  (if (or (true? cond) (keyword? cond)) ; Commonly generated pattern, ie `cond`
    (parse ctx env then) ; Reduce (if `true` expr) to just expr
    {:op   :if
     :cond (parse ctx env cond)
     :then (parse ctx env then)
     :else (parse ctx env else)}))

(defmethod parse* 'case* [ctx env _ [expr cases clauses default]]
  ;; TODO assuming `case` -> `case*` macro expansion and valid arguments
  ;; TODO map `fallthrough` as a special form inside clauses
  (let [p (partial parse ctx env)]
    {:op       :case
     :expr     (p expr)
     :cases    (mapv #(mapv p %) cases)
     :clauses  (mapv p clauses)
     :default  (p default)}))

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
                  ::expr (util/kebab->camel
                          (if-not (contains? env k)
                            k ; Unshadowed ident
                            (gensym (str (name k) \_))))}]
        (recur (assoc env k node)
               (conj out node)
               (next pairs))))))

(defmethod parse* 'loop [ctx env _ body]
  ;; TODO support both `recur` and `continue`/`break`/`return` styles of looping.
  ;; former is idiomatic clojure, later is idiomatic WGSL; both can coexist
  ;;
  ;; can build more exotic or specific loops with the usual `defmacro` on top.
  )

(defn- resolve-var [ctx sym]
  (when-let [v (ana/resolve-existing-var (:cljs-env ctx) sym)]
    (when (::uuid v)
      (.add ^java.util.HashSet (:deps ctx) v))
    v)) ; TODO validate

(defn- get-expand [s]
  (if (re-find #"^[0-9]+$" s)
    {:op :nth :elem (Integer/parseInt s)}
    {:op :field :name s ::expr (util/kebab->camel s)}))

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
        {:op    :field
         :name  sym
         ::expr (util/kebab->camel sym)
         :in    (parse cljs env (first args))}))))

(defn- parse-lit [cljs env form]
  {:op :lit
   :value form
   :tag (cond
          (boolean? form) :b
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
(def ^:private ^:dynamic *parens* false)
(def ^:private ^:dynamic *return* nil) ; nil, :ret or {:op :binding} -> side-effect, fn scope, let scope

(defmulti ^:private gen* :op)

(defmethod gen* :default [node]
  (throw (ex-info (pr-str node) {:node node})))

(def ^:private block? #{:case :do :if :let :loop})

(def ^:private block-node? (comp block? :op))

(def ^:private needs-semicolon? (comp not block-node?))

(def ^:private needs-indent? (comp (disj block? :let) :op))

(def ^:private needs-indent-expr? (comp not #{:let} :op))

(defn- indent []
  (print (util/spaces *indent*)))

(defn- newline []
  (print \newline))

(defn- semicolon
  ([]
   (print \;)
   (newline))
  ([node]
   (when (needs-semicolon? node)
     (semicolon))))

(defn- pr-name [node]
  (print (::expr node)))

(defmethod gen* :binding [node]
  (pr-name node))

(defmethod gen* :local [node]
  (pr-name node))

(defmethod gen* :wgsl [node]
  (pr-name node))

(defmethod gen* :var [node]
  (print (or (::expr node) (::name node)
             (throw (ex-info "Expecting expr or name" node)))))

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
        (some-> *return* ::expr (print "= "))))
    (binding [*return* (and block *return*)]
      (gen* node))
    (semicolon node)))

(defn- gen-block [stmts]
  (binding [*return* nil] ; Statements (side-effects execution)
    (doseq [node (butlast stmts)]
      (when (needs-indent-expr? node)
        (indent))
      (gen* node)
      (semicolon node)))
  (when-let [node (last stmts)] ; Terminator (control-flow evaluation)
    (gen-stmt node)))

(defmethod gen* :do [{:keys [body]}]
  (gen-block body))

(defn- enter-block []
  (print " {")
  (newline))

(defn- leave-block []
  (indent)
  (print \})
  (newline))

(defmacro with-indent [& exprs]
  `(binding [*indent* (inc *indent*)]
     ~@exprs))

(defmacro with-block [& exprs]
  `(do (enter-block)
       (with-indent ~@exprs)
       (leave-block)))

(defmethod gen* :if [{:keys [cond then else]}]
  (print "if ")
  (gen* cond)
  (with-block
    (gen-stmt then))
  (when else
    (indent)
    (print "else")
    (if (not= :if (:op else))
      (with-block
        (gen-stmt else))
      (do
        (print \space)
        (gen* else)))))

(defmethod gen* :case [{:keys [expr cases clauses default]}]
  (assert (= (count cases)
             (count clauses)))
  (print "switch ")
  (gen* expr)
  (with-block
    (dotimes [n (count cases)]
      (indent)
      (print "case ")
      (doseq [node (interpose ", " (nth cases n))]
        (if (string? node)
          (print node)
          (gen* node)))
      (with-block
        (gen-stmt (nth clauses n))))
    (indent)
    (print "default")
    (with-block
      (gen-stmt default))))

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
    (when-let [tag (:tag (meta (:name node)))]
      ;; TODO infer type
      (print " :" tag))
    (if is-block?
      (do (semicolon)
          (when (needs-indent? init)
            (indent))
          (binding [*return* node]
            (gen* init)))
      (do (when init
            (print " = ")
            (gen* init))
          (semicolon))))
  (when (needs-indent? (first body))
    (indent))
  (gen-block body))

(defmethod gen* :call [{:keys [fn xs]}]
  (case (:op fn)
    :op-fn ; Built-in WGSL operators, infix syntax.
    (let [fname (:name fn)]
      (when *parens* (print \())
      (binding [*parens* true] ; Simple rule: parens all the nested operators.
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
              (loop [[x & xs] (next xs)]
                (print \space)
                (print fname)
                (print \space)
                (gen* x)
                (some-> xs (recur))))))
      (when *parens* (print \))))

    (:var :wgsl) ; Named calls, fn can be user defined or built in WGSL.
    (do (gen* fn)
        (print \()
        (binding [*parens* false] ; Comma has highest precedence, can omit parens.
          (doseq [x (butlast xs)]
            (gen* x)
            (print ", "))
          (when-let [x (last xs)]
            (gen* x)))
        (print \)))

    (throw (ex-info "Unexpected function value" {:func fn}))))


;;; Shader Code - Declarations of Constants, Variables, Functions & Entry Points
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- with-deps [deps hash x]
  (with-meta x {::hash hash :deps (mapv ::uuid deps)}))

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
   ::expr (util/kebab->camel sym)
   :binding-form? true
   :op :binding
   :env {:context :expr}
   :arg-id id
   :info {:name sym}
   :tag tag
   :local :arg})

(defn- compile-fn [cljs-env body link-fn]
  (let [deps (java.util.HashSet.)
        cljs (->ParseCtx cljs-env deps)
        env  (merge (base-env) ; TODO remember env between macros expansions? detect changes from macros in bllm.base
                    (:locals cljs-env)) ; TODO warn when locals shadow base-env?
        code (for [expr body]
               (parse cljs env expr))
        wgsl (binding [*indent* 1]
               ;; TODO refactor code before gen, WGSL more restrictive than CLJS
               ;; - lift stmts outside expressions, introduce temporary locals
               ;; - remove pure expressions from nonterminal stmts (do 1 2 3) -> 3
               (with-out-str
                 (gen-block code)))]
    (link-fn deps code wgsl))) ; TODO code -> sourcemap

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
                    (with-deps deps (util/unique-hash args|ret body)
                      {:wgsl wgsl
                       :ret  (util/keyword->gpu (or ret :f32)) ;; TODO default to type inference
                       :args `(cljs.core/array
                               ~@(for [arg args]
                                   `(bllm.wgsl/argument
                                     ~(::expr arg)
                                     ~(util/keyword->gpu (:tag arg)))))}))
         (compile-fn env (if ret (next body) body))
         (binding [*return* :ret]))))

(defn- select-node [deps pred]
  (->> deps
       (filter pred)
       (map ::uuid)
       (into #{})))

(defn- match-builtin [node kind stage dir]
  (and (= :builtin kind)
       (= (::stage node) stage)
       (= (::dir   node) dir)))

(defn- select-kind [deps kind stage dir]
  (select-node deps #(let [k (::kind %)]
                       (or (= kind k)
                           (match-builtin % k stage dir)))))

(defn- gen-io [ids]
  (if (empty? ids)
    `(bllm.wgsl/empty-io)
    `(bllm.wgsl/gen-io ~(hash ids) (bllm.util/array ~@ids))))

(defn- check-workgroup-size [v k]
  (when-not (or (nil? v) (symbol? v))
    (if-not (nat-int? v)
      (throw (ex-info "Invalid workgroup value" {:value v}))
      (gpu/check-limit v k))))

(comment (check-workgroup-size 8 :max-compute-workgroup-size-x))

;; TODO wrap link-fn of stages:
;; check :max-sampled-textures-per-shader-stage
;; check :max-samplers-per-shader-stage
;; check :max-storage-buffers-per-shader-stage
;; check :max-storage-textures-per-shader-stage
;; check :max-uniform-buffers-per-shader-stage

(defnode defkernel
  "Defines a compute shader entry point.

  Unlike vertex and pixel shaders, there are no user-defined parameters here.
  Only builtin inputs such as the `global-invocation-id` are available, from
  which user-defined parameters can then be accessed through bound resources.

  The workgoup size must also be specified. No default value suits all cases."
  {:keys [:in :x :y :z :wgsl] :props false}
  [sym [x y z] & body]
  ;; TODO no check if fallback specified -> only validate min-specs
  (check-workgroup-size x :max-compute-workgroup-size-x)
  (check-workgroup-size y :max-compute-workgroup-size-y)
  (check-workgroup-size z :max-compute-workgroup-size-z)
  (when (and (number? x) (number? y) (number? z))
    (gpu/check-limit (* x y z) :max-compute-invocations-per-workgroup))
  (compile-fn &env body
              (fn link [deps code wgsl]
                (let [ids (select-node deps #(match-builtin % (::kind %)
                                                            :compute :in))]
                  (-> #(ids (::uuid %))
                      (remove deps)
                      (with-deps (util/unique-hash x y z body)
                        {:in (gen-io ids) :x x :y y :z z :wgsl wgsl}))))))

;; TODO step between compile and link -> validation!
(defn- compile-io [stage in out env body]
  ;; TODO check :max-inter-stage-shader-components/variables
  (compile-fn env body
              (fn link [deps code wgsl]
                (let [ids-i (select-kind deps in  stage :in)
                      ids-o (select-kind deps out stage :out)]
                  (-> #(let [id (::uuid %)]
                         (or (ids-i id)
                             (ids-o id)))
                      (remove deps)
                      (with-deps (util/unique-hash body)
                        {:wgsl wgsl
                         :in   (gen-io ids-i)
                         :out  (gen-io ids-o)}))))))

(defnode defvertex
  "Defines a vertex shader entry point."
  {:keys [:in :out :wgsl]}
  [sym & body]
  ;; TODO check :max-vertex-buffers, :max-vertex-attributes
  (compile-io :vertex :vertex-attr :interpolant &env body))

(defnode defpixel
  "Defines a fragment shader entry point."
  {:keys [:in :out :wgsl]}
  [sym & body]
  ;; TODO check :max-color-attachments
  (compile-io :fragment :interpolant :draw-buffer &env body))


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

(defn- wrap-deps [m]
  (with-meta m {:deps (filter some? (vals m))}))

(defn- emit-pipeline [env vars args]
  (let [g (group-by ::kind (map (partial ana/resolve-existing-var env) vars))]
    (-> (fn [m k]
          (->> (if (:required (meta k))
                   (required-first (g k))
                   (optional-first (g k)))
               ::uuid
               (assoc m k)))
        (reduce {} args)
        (wrap-deps))))

(defm defpipeline [sym elems]
  (let [args (mapv keyword elems)]
    `(defnode ~sym
       {:wgsl false :props false
        :keys ~args
        :args ~['& (set elems)]}
       [sym# & vars#]
       (emit-pipeline ~'&env vars# ~args))))

(defpipeline defcompute
  [layout ^:required kernel])

(defpipeline defrender
  [layout ^:required vertex primitive depth-stencil multisample pixel])
