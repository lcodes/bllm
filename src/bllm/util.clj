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
   \? "_Q_"})

(defn- kebab-capitalize [s re]
  (-> (name s)
      (str/replace re
                   (fn [^String m]
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

(def flatten1 (partial apply concat))

(defn align [alignment size]
  (let [a (dec alignment)]
    (bit-and (+ a size) (bit-not a))))

(defn sym [^clojure.lang.Keyword k]
  (.sym k))

(defn keyword->enum
  "Converts keywords to the matching constants."
  [default-ns x]
  (if-not (keyword? x)
    x
    (-> (namespace x)
        (or default-ns)
        (name)
        (symbol (name x)))))

(def ns-gpu  "bllm.gpu")
(def ns-lib  "bllm.base")
(def ns-wgsl "bllm.wgsl")

(def keyword->gpu  (partial keyword->enum ns-gpu))
(def keyword->wgsl (partial keyword->enum ns-wgsl))

(comment (keyword->enum "hello" ::foo-bar))

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

(def spaces
  "Preallocated indentation spaces."
  (mapv #(.repeat " " (* 2 %)) (range 16)))

(comment (spaces 2))


;;; Conditional Compilation -> Matching the different build profiles.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn dev? []
  true) ; TODO

(defmacro debug [& expr]
  expr) ; TODO conditional compilation

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

(defmacro dump-env
  "Development helper to inspect ClojureScript's macro `&env`."
  []
  `(js/console.log
    ~(with-out-str
      (clojure.pprint/pprint &env))))


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

(defmacro callback
  "A better `#'`, for lack of a better word. Allows development builds to redefine
  `f` without having to re-register its callbacks. Does nothing in release."
  [f]
  (if-not (dev?)
    f
    `#(.apply ~(func-t f)
              js/undefined
              (js/Array.prototype.slice.call (cljs.core/js-arguments)))))


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
    `(let [a# ~(last binding)
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
  [binding & body]
  `(.forEach ~(last binding) (fn [~(first binding) ~(second binding)] ~@body)))

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

;; TODO dorange

(defmacro do-node-list
  "No overhead `doseq` specialized to a DOM `NodeList` interface."
  {:style/indent 1}
  [[var-name list-expr] & body]
  `(let [list# ~list-expr]
     (dotimes [n# (.-length list#)]
       (let [~var-name (.item list# n#)]
         ~@body))
     js/undefined))
