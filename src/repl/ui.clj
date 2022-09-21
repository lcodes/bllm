(ns repl.ui
  (:require [clojure.string :as str]
            [bllm.cli  :as cli]
            [bllm.util :as util :refer [defm]]))

(defmacro cb
  "Similar to `fn` but for UI event callbacks. Automatically stops propagation."
  {:arglists '([params & body] [sym params & body])}
  [& args]
  (let [sym (when (symbol? (first args))
              (first args))
        [params & body] (if sym (next args) args)
        event (or (first params) (gensym "e"))]
    `(fn ~(or sym 'cb)
       ~(case (count params)
          0 [event]
          1 params
          (throw (ex-info "cb expects zero or one arguments"
                          {:params params})))
       ~@body
       (.stopPropagation ~(with-meta event {:tag 'js/Event})))))

(defm defe
  "Similar to `defn` but wraps the event handler from `cb` instead of `fn`."
  [sym params & body]
  `(def ~sym (cb ~sym ~params ~@body)))

(def not-vector? (complement vector?))

(defn- emit-event [reg env sym args]
  (let [interceptors       (take-while not-vector? args)
        [params & handler] (drop-while not-vector? args)
        m (meta sym)
        a (gensym "args")
        f (if (and (empty? params)
                   (= 1 (count handler))
                   (symbol? (first handler)))
            (first handler) ; [] separates interceptors from user-specified fn.
            (let [[node & args] params]
              `(fn ~sym [~node ~a]
                 ~(if (empty? args)
                    `(do ~@handler)
                    `(let [[_# ~@args] ~a] ~@handler)))))]
    `(do (def ~sym ~(keyword (str *ns*)
                             (str/replace (name sym) #"^(?:handle|on)-" "")))
         ~(when (or (:cmd m) (:kbd m)) (cli/emit-cmd env sym 'repl.ui/cmd))
         ~(if-let [w (or (:with m)
                         (when (not-empty interceptors)
                           (vec interceptors)))] ; TODO warn if using both
            `(~reg ~sym ~w ~f)
            `(~reg ~sym ~f))
         ~sym)))

(defm ^:private defevent*
  [sym reg]
  `(defm ~sym
     {:args '[interceptors* params & handler]}
     [sym# & args#]
     (emit-event '~reg ~'&env sym# args#)))

(defevent* defevent re-frame.core/reg-event-db)

(defevent* defeffect re-frame.core/reg-event-fx)

(defevent* defhandler re-frame.core/reg-event-ctx)

(defn- sub [sym]
  (symbol (str sym "-sub")))

(defn- emit-schema-sub
  [sym key]
  `(do (def ~sym ~key)
       (re-frame.core/reg-sub ~sym ~'q ~sym)
       (def ~(sub sym) (repl.ui/$ [~sym]))))

;; TODO indexing needs more than this, it needs a log of delta changes to scale
;; - hook indexing into the :db effect, simple re-frame interceptor
;; - different sub graph, this one flowing transactional information ?
;;   - rethink when UI starts pulling in more data, then profile, then rethink more
(defm defschema
  "Defines a data schema as a view model of the `re-frame.db/app-db` structure.

  Similar to `bllm.data/defstore` being over the app db instead of the asset db.
  ;; TODO rename above to defschema too? share parsers -> different emits

  NOTE only specify data here, indexing happens downstream in the signal graph."
  [sym & spec+inits]
  (let [specs (partition 2 spec+inits)
        inits (map second specs)
        syms  (map first  specs)
        keys  (map util/ns-keyword syms)
        atom  (sub sym)]
    `(binding [reagent.ratom/*ratom-context* true] ; Don't warn, globals are fun
       (def ~sym ~(util/ns-keyword sym))
       (def ~atom (repl.ui/$db ~sym))
       (let [~'q (fn [] ~atom)] ; Privately reusable intermediate sub signal.
         ~@(map emit-schema-sub syms keys))
       (repl.ui/schema ~sym ~(zipmap keys inits)))))

(defm deframe
  "Defines a configurable UI node container. Pulls `re-frame` off the wall.

  The initial views are a sequence of hiccup forms, to be used when the frame
  has no existing state. Otherwise, the views configured by the user are used.

  Optional metadata is used to configure the frame:
  `:transient` disables durability of the views state.
  `:layout` is how to position and size UI nodes, defaults to `:horizontal`.
  `:class` is a string of space separated class names, only used by CSS and JS.
  `:elem` is the HTML element to use, defaults to `:div`.
  `:tags` are symbolic forms to be used by functional semantics.

  See `deflayout` and `layout` to create new layout handlers.

  NOTE that the initial views are purely template data, no signal graph subs
  should be accessed from here. Use events if more initialization is required.

  NOTE view state is only made durable when it changed from its default value."
  [sym & initial-views]
  (let [m (meta sym)]
    `(def ~sym
       (repl.ui/frame
        ~(util/ns-keyword sym)
        ~(:tags m) ~(:elem m) ~(:class m) ~(:layout m) ~(:transient? m)
        [~@initial-views]))))

(defm defview
  "Defines a configurable UI view panel."
  [sym params & view]
  `(def ~sym (repl.ui/view ~(util/ns-keyword sym) (fn ~sym ~params ~@view))))

(defm defmode
  "Defines a UI mode. Has an associated asset definition and selection."
  [sym & init]
  `(def ~sym
     (repl.ui/mode
      ~(util/unique-id sym)
      ~(or (:label (meta sym)) (util/label sym))
      (fn ~sym ~@init))))


(defm defstyle
  "Idea: CSS from CLJS -> turing complete from the start, pack tiny AST -> expand at runtime; or drive a visual style/theme editor"
  ;; would be neat if the same pattern for shader materials is reused here (both are key/vals maps in the abstract, specialized for perf)
  ;; - then materials can learn tricks from CSS -> rule based dynamic material generation? add tags to entities to match on material rules
  ;; - same underlying "algorithm", can batch through ECS; revisit when these systems have more solid foundations
  ;; - also (data/defstore Style) to persist/share content
  [sym & args]
  )
