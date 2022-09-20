(ns repl.schema
  "UI views to explore the compiler, engine, editor and application's metadata.

  No REPL is meta complete without the ability to introspect itself."
  (:require [bllm.meta :as meta]
            [repl.ui   :as ui]))

(defprotocol ReflectUI
  (reflect* [this]))

(defn reflect [x]
  ;; TODO handle expansion point (toggle visibility)
  ;; TODO if type has matching inspector view, use it instead
  (cond (satisfies? ReflectUI x) (reflect* x)
        :else [:div (type->str x)]))

(defn- reflect-items [xs]
  (loop [xs xs
         n 0
         o ()]
    (if (empty? xs)
      (reverse o)
      (recur (next xs)
             (inc n)
             (conj o [:li {:key n} "Hi"]))))) ; TODO currently holds reactions making the whole graph recursive (add manual expand nav)

(defn reflect-seq [xs]
  ;; finite/infinite, head, count, pagination
  ;; FIXME for now assuming finite seqs, fingers crossed -> detect when it stack overflows
  [:ol.seq (reflect-items xs)])

(defn reflect-vec [xs]
  ;; index, range, pagination
  [:ol.vec (reflect-items xs)])

(defn reflect-set [xs]
  ;; pagination
  [:ul.set (reflect-items xs)])

(defn reflect-map [x]
  ;; key/val, select keys, pagination
  [:table.map
   [:tbody
    (for [[k v] x]
      [:tr {:key (str k)}
       [:td (reflect k)]
       [:td (reflect v)]])]])

(defn reflect-ident [x cls]
  [:p.ident {:class cls}
   (when-let [ns (namespace x)]
     [:span.ns (str ns)])
   [:span.name (name x)]])

(defn reflect-queue [x]
  ;; peek front
  [:div "QUEUE"])

(extend-protocol ReflectUI
  nil    (reflect* [x] [:p.nil "nil"])
  object (reflect* [x] [:p.obj (type->str x)])
  number (reflect* [x] [:p.num (str x)])
  string (reflect* [x] [:p.str \" x \"])

  function (reflect* [x] [:p.fn (.-name x)])

  js/RegExp (reflect* [x] [:p.regexp (str x)])
  js/Date   (reflect* [x] [:p.date   (str x)])
  UUID      (reflect* [x] [:p.uuid   (str x)])

  TaggedLiteral (reflect* [x] [:p.tagged (str x)])

  Var       (reflect* [x] [:p.var (str x)]) ; TODO meta (& on other IMeta types)
  Namespace (reflect* [x] [:p.namespace (str x)])

  Symbol  (reflect* [x] (reflect-ident x "symbol"))
  Keyword (reflect* [x] (reflect-ident x "keyword"))

  ;;IndexedSeq
  ;;RSeq
  ;;MetaFn

  ;;Cycle
  ;;Repeat
  ;;Iterate

  ;;Range
  ;;Delay
  ;;Eduction
  ;;IntegerRange

  ;;MultiFn

  EmptyList          (reflect* [x] [:p.list.empty "()"])
  List               (reflect* [x] (reflect-seq x))
  Cons               (reflect* [x] (reflect-seq x))
  ChunkedCons        (reflect* [x] (reflect-seq x))
  ChunkedSeq         (reflect* [x] (reflect-seq x))
  LazySeq            (reflect* [x] (reflect-seq x))
  Subvec             (reflect* [x] (reflect-vec x))
  PersistentVector   (reflect* [x] (reflect-vec x))
  TransientVector    (reflect* [x] (reflect-vec x))
  PersistentHashSet  (reflect* [x] (reflect-set x))
  PersistentTreeSet  (reflect* [x] (reflect-set x))
  TransientHashSet   (reflect* [x] (reflect-set x))
  PersistentArrayMap (reflect* [x] (reflect-map x))
  PersistentHashMap  (reflect* [x] (reflect-map x))
  PersistentTreeMap  (reflect* [x] (reflect-map x))
  TransientArrayMap  (reflect* [x] (reflect-map x))
  TransientHashMap   (reflect* [x] (reflect-map x))
  PersistentQueue    (reflect* [x] (reflect-queue x))

  Atom     (reflect* [x] [:div "Atom: "     (reflect @x) (ui/reload-btn identity #_#(swap! id inc))])
  Volatile (reflect* [x] [:div "Volatile: " (reflect @x)]) ; TODO reload

  reagent.ratom.RAtom    (reflect* [x] [:div "Reactive: " (reflect @x)])
  reagent.ratom.Reaction (reflect* [x] [:div "Reaction: " (reflect @x)]))

(defn view
  [x]
  [:div.reflect.content.scroll
   [reflect* ^{:key sub} x]])

(defmethod ui/node* :schema [n v] ; TODO use this -> forward to `reflect`
  [:div "UI view describing another UI view, or itself, who knows"])

#_[:img {:width 480 :height 360 :src "https://preview.redd.it/dys3wj2z9ue61.jpg?auto=webp&s=511078554dbb75c73fb0e791d4d1802b43db9801"}]

;; TODO debug views?
;; - re-frame-10x inspired timelines

;; - data store schemas
;; - WGSL node definitions
;; - network grid view

;; really a generic graph browser with pluggable node and link views.
;; - ultimately XR, but same concept -> It's a UNIX system! I know this!
