(ns bllm.ecs
  (:require [bllm.meta :as meta]
            [bllm.util :as util :refer [defm]]))

(defn- component-option [next m k flag]
  (cond (not (m k)) next
        (not next)  flag
        :else (list 'bit-or flag next)))

(defn- emit-component [sym opts align size type ctor init]
  (let [m (meta sym)]
    `(def ~sym
       (bllm.ecs/component
        ~(util/unique-id   sym)
        ~(util/unique-hash sym opts type ctor init)
        ~(-> opts
             (component-option m :static 'bllm.ecs/component-static)
             (component-option m :shared 'bllm.ecs/component-shared)
             (or 0))
        ~(dec (or align 1)) ~(or size 0) ~(dec (:size m 1))
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
                    (if prim? (meta/prim-align ty) (::meta/align info))
                    (or prim?                      (::meta/size  info))
                    nil    ; type -> data access
                    nil    ; info to construct array (component size, alignment; view type)
                    nil))) ; initial value of entity elements

(defn- emit-wrapper [env sym align size vals refs]
  (emit-component (vary-meta sym assoc
                             ::meta/align align
                             ::meta/size  size)
                  (when (not refs)
                    (if (not vals)
                      'bllm.ecs/component-empty
                      'bllm.ecs/component-buffer))
                  align size
                  nil
                  nil
                  nil))

(defm defc
  "Defines a new data component type."
  [sym & fields]
  (let [m   (meta sym)
        get (:get m)
        set (:set m)
        ty  (:type m)
        ts? (not-empty fields)]
    (when (and (or get set) (not ty))
      (throw (ex-info "Component get/set requires a single :type" {:sym sym})))
    (when (and ty ts?)
      (throw (ex-info "Cannot specify both component type and fields"
                      {:sym sym :type ty :fields fields})))
    (cond ty  (emit-simple &env sym ty)
          ts? (meta/parse-struct &env sym fields emit-wrapper))))

(defm defsys
  [sym & queries] ; TODO state ctor? or queries a series of members, with ctor in them?
  (let [m (meta sym)]
    `(def ~sym
       (bllm.ecs/system
        ~(util/unique-id   sym)
        ~(util/unique-hash sym queries m)
        0 ; TODO meta -> options
        nil ; TODO state ctor
        nil ; TODO queries
        nil ; TODO code
        ))))
