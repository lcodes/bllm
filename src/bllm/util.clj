(ns bllm.util
  "The missing macros from ClojureScript. Highly opinionated."
  (:require [clojure.string :as str]
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

(defn kebab->pascal [s]
  (str/replace (name s) #"^\w|-\w"
               (fn [^String m]
                 (->> (.length m)
                      (dec)
                      (.charAt m)
                      (Character/toUpperCase)
                      (str)))))

(comment
  (kebab->pascal :test-hello-world)
  )


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


;;; Prelude -> Miscellaneous functions complementing cljs.core.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro def1
  "Better, shorter `defonce`."
  ([sym init]
   (if (dev?)
     `(defonce ~sym ~init)
     `(def ~sym ~init))) ; Advanced compilation doesn't fully remove `defonce`.
  ([sym docstring init]
   `(bllm.util/def1 ~(with-meta sym {:doc docstring})
      ~init)))


;;; DWIM -> Direct access to JavaScript's good parts, or "I know what I'm doing"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro ===  [x y]  (bool-t `(~'js* "~{} === ~{}" ~x ~y)))
(defmacro !==  [x y]  (bool-t `(~'js* "~{} !== ~{}" ~x ~y)))
(defmacro &&   [& xs] (binop " && " ~xs))
(defmacro ||   [& xs] (binop " || " ~xs))
(defmacro %    [x y]  `(~'js* "~{} % ~{}"  ~x ~y))
(defmacro add! [x y]  `(~'js* "~{} += ~{}" ~x ~y))
(defmacro inc! [x]    `(~'js* "~{}++" ~x))
(defmacro dec! [x]    `(~'js* "~{}--" ~x))

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
