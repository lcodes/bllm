(ns bllm.util
  "The missing macros from ClojureScript. Highly opinionated.

  Also contains miscellaneous utility functions used by other macro modules."
  (:require [clojure.string      :as str]
            [clojure.tools.macro :as macro]))


;;; Meta utilities -> The tools your tools depend on. It's fns all the way down.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private bool-tag {:tag 'boolean})
(def ^:private func-tag {:tag 'function})

(defn- bool-t [expr]
  (with-meta expr bool-tag))

(defn- func-t [expr]
  (with-meta expr func-tag))

(defn- binop* [op args]
  (str/join op (repeat (count args) "~{}")))

(defn ^:private binop [op args]
  `(~'js* ~(binop* op args) ~@args))

(defn wrap-do [& forms] `(do ~@forms))

(def ^:private special-chars
  {\! "_NOT_"
   \$ "_NTH_"
   \% "_VAR_"
   \& "_AND_"
   \| "_OR_"
   \- "_SUB_"
   \+ "_PLUS_"
   \* "_TIMES_"
   \= "_EQ_"
   \< "_LT_"
   \> "_GT_"
   \' "_P_"
   \? "_Q_"})

(defn- kebab-capitalize [s re]
  (-> (name s)
      (str/replace re
                   (fn replacement [^String m]
                     (->> (.length m)
                          (dec)
                          (.charAt m)
                          (Character/toUpperCase)
                          (str))))
      (str/replace "->" "_2_")
      (str/escape special-chars)))

(defn kebab->camel [s]
  (kebab-capitalize s #"-\w"))

(defn kebab->pascal [s]
  (kebab-capitalize s #"^\w|-\w"))

(comment (kebab->camel  :test-hello-world)
         (kebab->camel  :foo->bar)
         (kebab->pascal :test-hello-world))

(defn kebab->snake [s]
  (str/replace (name s) \- \_))

(defn label [s]
  (-> (name s)
      (str/split #"-")
      (->> (map str/capitalize)
           (str/join " "))))

(comment (label 'hello-world))

(def flatten1 (partial apply concat))

(defn align [alignment size]
  (let [a (dec alignment)]
    (bit-and (+ a size) (bit-not a))))

(defn field-name [s]
  (symbol (str \- s)))

(defn ns-keyword [s]
  (keyword (str *ns*) (name s)))

(defn sym [^clojure.lang.Keyword k]
  (.sym k))

(defn js-sym [ident]
  (symbol "js" ident))

(defn named->enum
  ([ns x]
   (named->enum ns x nil))
  ([ns x prefix]
   (symbol (name ns) (str prefix (name x)))))

(defn keyword->enum
  "Converts keywords to the matching constants."
  ([ns x]
   (keyword->enum ns x nil))
  ([ns x prefix]
   (if-not (keyword? x)
     x
     (named->enum (or ns (namespace x)) x prefix))))

(def ns-gpu  "bllm.gpu")
(def ns-lib  "bllm.base")
(def ns-wgsl "bllm.wgsl")
(def ns-data "bllm.data")

(def keyword->gpu  (partial keyword->enum ns-gpu))
(def keyword->wgsl (partial keyword->enum ns-wgsl))

(comment (keyword->enum "hello" ::foo-bar "hi-"))

(defn to-js
  "Emits the given data form as a JavaScript literal."
  [form]
  (cond
    (map? form)
    `(cljs.core/js-obj
      ~@(flatten1
         (for [[k v] form]
           [(name k) (to-js v)])))

    (set? form)
    `(js/Set.
      (cljs.core/array
       ~@(map to-js form)))

    (or (seq? form) (vector? form))
    `(cljs.core/array
      ~@(map to-js form))

    :else form))

(comment (to-js '(hello world))
         (to-js [1 2 3])
         (to-js #{1 2 3})
         (to-js {:a "hello" :b 123})
         (to-js {:a [1 2 3]}))

(defn doc-string|attr-map? [x]
  (or (string? x) (map? x)))

(defn splice-defm [elems [ctor sym & args]]
  (let [l (take-while doc-string|attr-map? args)]
    (concat (list ctor sym) l elems
            (drop (count l) args))))

(comment (splice-defm [0 1] '(defsomething hello "world" {:key :val} [body of] args)))

(def spaces
  "Preallocated indentation spaces."
  (mapv #(.repeat " " (* 2 %)) (range 16)))

(comment (spaces 2))

(def ^:private ns-prefix
  "Converts a `Namespace` into camelCase identifier. Memoized."
  (memoize
   #(-> (str %)
        (kebab->camel)
        (str/replace \. \_)
        (str "__"))))

(defn unique-name
  "Expands a `Symbol` into a camelCase fully__qualified identifier."
  [sym]
  (->> (name sym)
       (kebab->camel)
       (str (ns-prefix *ns*))))

(defn unique-id
  ([sym]
   (unique-id (str *ns*) (name sym)))
  ([ns sym]
   (assert (string? ns))
   (assert (string? sym))
   (let [h (bit-xor (hash ns) (hash sym))] ; Poor man's deterministic ID.
     (assert (or (< h 0) (>= h 32))) ; Reserved ID range for primitive types.
     h)))


;;; Conditional Compilation -> Matching the different build profiles.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn cljs?
  "Returns `true` when evaluated in a ClojureScript compilation context."
  []
  (some? cljs.env/*compiler*))

(defn dev? []
  true) ; TODO

(defmacro debug [& expr]
  (list* 'do expr)) ; TODO conditional compilation

(defmacro debug-when [cond & expr]
  `(bllm.util/debug (when ~cond ~@expr)))

(defmacro debug-when-not [cond & expr]
  `(bllm.util/debug (when-not ~cond ~@expr)))

(defmacro compat-old [cond browsers & body]
  ;; TODO completely strip body in standard mode.
  `(when ~cond
     ~@body))

(defmacro compat-std [cond browsers & body]
  ;; TODO remove conditional in standard mode. Just run body.
  `(when ~cond
     ~@body))


;;; Prelude -> Miscellaneous macros complementing cljs.core.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro defm
  "When `defmacro` is used to write `defmacro`. I'm So Meta, Even This Acronym."
  {:arglists '([name doc-string? attr-map? [params*] body])}
  [sym & args]
  (let [[sym [[m-sym & m-args] & body]] (macro/name-with-attributes sym args)]
    `(defmacro ~sym
       ;; The inner docstring is already part of the meta. Notice the symmetry.
       {:arglists '([~'name ~'doc-string? ~'attr-map?
                     ~@(or (some-> sym meta :args eval) m-args)])}
       [sym# & args#]
       (let [[~m-sym [~@m-args]] (macro/name-with-attributes sym# args#)]
         ~@body)))) ; Here body is assumed to expand into the final `def`.

(defm defalias
  "Defines an alias to another var, while preserving its metadata."
  [sym ref]
  (let [m (if (cljs?)
            (:meta (cljs.analyzer/resolve-var &env ref))
            (meta (resolve ref)))]
    `(def ~(vary-meta sym merge m) ~ref)))

(defm defconst
  "Better, shorter `def ^:const`. Allows the defined var to be redefined."
  [sym init]
  (wrap-do
   (when (dev?) ; Constants can't be redefined, but they can be undefined.
     `(cljs.core/ns-unmap '~(.getName *ns*) '~sym))
   `(def ~(vary-meta sym assoc :const true) ; All this for a meta property.
      ~init)))

(defm def1
  "Better, shorter `defonce`. Accepts a doc-string and attributes map."
  [sym init]
  (if (dev?)
    `(defonce ~sym ~init)
    `(def ~sym ~init))) ; Advanced compilation doesn't fully remove `defonce`.

(defn pp
  "Development helper to pretty print to string."
  [form]
  (with-out-str (clojure.pprint/pprint form)))

(defn log
  "Development helper to pretty print data to the JavaScript console."
  [form]
  `(js/console.log ~(pp form)))

(defmacro dump
  "Development helper to pretty print ClojureScript's `macroexpand-1`."
  [form]
  (log (cljs.analyzer/macroexpand-1 &env form)))

(defmacro dump-env
  "Development helper to inspect ClojureScript's macro `&env`."
  []
  (log &env))

(defmacro dump-var
  "Development helper to inspect ClojureScript's `resolve-var`."
  [sym]
  `(log (cljs.analyzer/resolve-existing-var &env sym)))


;;; DWIM -> Direct access to JavaScript's good parts, or "I know what I'm doing"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro ===  [x y]  (bool-t `(~'js* "~{} === ~{}" ~x ~y)))
(defmacro !==  [x y]  (bool-t `(~'js* "~{} !== ~{}" ~x ~y)))
(defmacro &&   [& xs] (binop " && " ~xs))
(defmacro ||   [& xs] (binop " || " ~xs))
(defmacro %    [x y]  `(~'js* "~{} % ~{}"  ~x ~y))
(defmacro +=   [x y]  `(~'js* "~{} += ~{}" ~x ~y))
(defmacro inc! [x]    `(~'js* "~{}++" ~x))
(defmacro dec! [x]    `(~'js* "~{}--" ~x))

(defmacro str! [s & xs]
  `(~'js* ~(str "~{} += " (binop* " + " xs)) ~s ~@xs))

(defmacro break    [] '(js* "break"))
(defmacro continue [] '(js* "continue"))

(defmacro return
  ([]  '(  js* "return"))
  ([x] `(~'js* "return ~{}" ~x)))

(defmacro bind
  "Constructor for JavaScript's 'fat pointers'. Expands to (.bind (.-f o) o)."
  [o f]
  `(.. ~o ~(symbol (str "-" f)) (~'bind ~o)))

(defmacro cb
  "A better `#'`, for lack of a better word. Allows development builds to redefine
  `f` without having to re-register its callbacks. Does nothing in release."
  [f]
  (if-not (dev?)
    f
    `(fn ~(if-not (symbol? f)
           'cb
           (symbol (str f "-cb")))
       []
       (.apply ~(func-t f) js/undefined
               (js/Array.prototype.slice.call (cljs.core/js-arguments))))))

(defmacro defer
  [& fn-args]
  `(js/Promise. (fn ~'defer ~@fn-args)))


;;; Iterators -> What Doug Hoyte calls "duality of syntax" in Let Over Lambda.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro doarray
  "No overhead `doseq` specialized to JavaScript's `Array` type."
  {:style/indent 1
   :arglists '([[var-name array-expr] & body]
               [[var-name idx-name array-expr] & body])}
  [binding & body]
  (let [n (if (= 3 (count binding))
            (second binding)
            (gensym "n"))]
    `(let [^js/Array a# ~(last binding)
           ~n js/undefined]
       (~'js* "for (let ~{} = 0; ~{} !== ~{}.length; ~{}++) {" ~n ~n a# ~n)
       (let [~(first binding) (~'js* "~{}[~{}]" a# ~n)]
         ~@body
         (~'js* "}")
         js/undefined))))

(defmacro docoll
  "No overhead `doseq` specialized to JavaScript's `Map` and `Set`."
  {:style/indent 1
   :arglists '([[var-name coll-expr] & body]
               [[var-name idx-name array-expr] & body])}
  [binding & body]
  (let [x (first binding)
        n (when (= 3 (count binding))
            (second binding))]
    `(let [~x js/undefined
           ~@(when n [n 0])]
       (~'js* "for (let ~{} of ~{}) {" ~x ~(last binding))
       ~@body
       ~(when n `(bllm.util/inc! ~n))
       (~'js* "}")
       js/undefined)))

(defmacro domap
  [[val-name key-name map-expr] & body]
  `(let [^js/Map m# ~map-expr]
    (.forEach m# (fn ~'domap [~val-name ~key-name] ~@body))))

(defmacro doiter
  "No overhead `doseq` specialized to JavaScript's `Iterator` interface."
  {:style/indent 1}
  [[var-name iter-expr] & body]
  `(let [iter# ~iter-expr]
     (while true
       (let [result# (.next iter#)]
         (if (.-done result#)
           (~'js* "return")
           (let [~var-name (.-value result#)]
             ~@body))))))

(defn- emit-dorange
  [dir end i from to body]
  `(let [to# ~to]
     (loop [~i ~from]
       (when (~end ~i to#)
         ~@body
         (recur (~dir ~i))))))

(defmacro dorange
  [[i from to] & body]
  (emit-dorange 'inc '< i from to body))

(defmacro dorange<
  [[i from to] & body]
  (emit-dorange 'dec '>= i `(dec ~from) to body))

(defmacro dolist
  "No overhead `doseq` specialized to a DOM `List` interface."
  {:style/indent 1}
  [[var-name list-expr] & body]
  `(let [^js/List list# ~list-expr]
     (dotimes [n# (.-length list#)]
       (let [~var-name (.item list# n#)]
         ~@body))
     js/undefined))
