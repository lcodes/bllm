(ns bllm.ecs
  (:require [clojure.tools.macro :as macro]
            [bllm.meta :as meta]
            [bllm.util :as util :refer [defm]]))


;;; Component Definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- component-option [next m k flag]
  (cond (not (m k)) next
        (not next)  flag
        :else (list 'bit-or flag next)))

(defn- emit-component [sym opts align size type ctor init]
  (let [m (meta sym)]
    `(def ~(if (zero? size)
             sym
             (vary-meta sym assoc
                        ::meta/align align
                        ::meta/size  size))
       (bllm.ecs/component
        ~(util/unique-id   sym)
        ~(util/unique-hash sym opts type ctor init)
        ~(-> opts
             (component-option m :static 'bllm.ecs/component-static)
             (component-option m :shared 'bllm.ecs/component-shared)
             (component-option m :system 'bllm.ecs/component-system)
             (or 0))
        ~(dec align) ~size ~(dec (:size m 1))
        nil ; TODO type
        nil ; TODO ctor
        nil ; TODO init
        ~(util/u16-array (:in  m))
        ~(util/u16-array (:out m))))))

(defn- emit-simple [env sym ty]
  (let [prim? (meta/prim-size ty)
        info  (meta/resolve-type env ty)]
    (emit-component sym
                    (when prim? 'bllm.ecs/component-buffer) ; TODO from info too
                    (if prim? (meta/prim-align ty) (::meta/align info 1))
                    (or prim?                      (::meta/size  info 0))
                    nil    ; type -> data access
                    nil    ; info to construct array (component size, alignment; view type)
                    nil))) ; initial value of entity elements

(defn- emit-wrapper [env sym align size vals refs]
  (emit-component sym
                  (when-not refs 'bllm.ecs/component-buffer)
                  align size
                  nil
                  nil
                  nil))

(defn- emit-marker [sym]
  (emit-component sym 'bllm.ecs/component-empty 0 0 nil nil nil))

(defm defc
  "Defines a new data component type."
  [sym & fields]
  (let [m   (meta sym)
        get (:get m) ; TODO use get/set
        set (:set m)
        ty  (:type m)
        ts? (not-empty fields)]
    (when (and (or get set) (not ty))
      (throw (ex-info "Component get/set requires a single :type" {:sym sym})))
    (when (and ty ts?)
      (throw (ex-info "Cannot specify both component type and fields"
                      {:sym sym :type ty :fields fields})))
    (cond ty    (emit-simple &env sym ty)
          ts?   (meta/parse-struct &env sym fields emit-wrapper)
          :else (emit-marker sym))))


;;; System Definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; query buildup:
;; - parse -> from EDN pattern to query AST
;; - emit  -> from query AST to cljs builder forms
;; -------
;; - reg   -> index query type; assoc matching classes; update views in all worlds -> through versioning at beginning of frame
;; - use   -> on world create, or world run with different version; map query->class to world->group and map comp-id to group's array-idx
;; - run   -> on manual/system execution, wire the view selection of block data to invocation of result function at requested frequency

(defn- parse-query-map [q]
  ;; selection (defines the I/O view, inputs to the query function, output is also an input)
  ;; negation (prevents matches if certain components are present)
  ;; filter (runtime conditions to match; per block on shared components, per entity otherwise)
  ;; source (defaults to entire world; can add functional parameters)
  )

(defn- parse-query-vec [q v]
  (parse-query-map (assoc q :select v)))

(defn- emit-system [sym body]
  (let [m (meta sym)
        p (first body)
        b (if (vector? p) (next body) body)
        q (if (vector? p)
            (parse-query-vec m p)
            (parse-query-map m))]
    ;; query -> system state type -> constructor
    `(def ~sym
       (bllm.ecs/system
        ~(util/unique-id   sym)
        ~(util/unique-hash sym body m)
        0 ; TODO meta -> options
        nil ; TODO ctor
        ))))

(defm defsys
  "Defines a new entity system type."
  [sym & body]
  (emit-system sym body))

(defn- parse-system [tick prev [func & form]]
  (if (keyword? func)
    (list* (util/keyword->enum "bllm.ecs" func) form)
    (let [[func form] (macro/name-with-attributes func form)]
      (emit-system (vary-meta func assoc :group tick :after prev) form))))

(defm deftick
  "Defines a new entity system tick group."
  [sym & systems]
  `(do (def ~sym
         (bllm.ecs/tick)) ; TODO merge into existing tick group (use direct symbols to position existing systems)
       ~@(loop [sys systems
                arg nil
                out ()]
           (if (empty? sys)
             (reverse out)
             (let [s (parse-system sym arg (first sys))]
               (recur (next sys) (second s) (conj out s)))))))


;;; Query Definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defm def?
  [sym & args]
  `(def ~sym nil))
