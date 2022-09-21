(ns bllm.cli
  "Console Log Interface. Use for direct user interactivity with engine systems.

  Contains grouped variables and commands, analouous to namespaced vars and fns.
  In fact, using `defvar` and `defcmd` is syntactic sugar over `def` and `defn`.
  These generate calls to `var` and `cmd`, automatically computing the metadata.

  Similarly, `defgroup` follows a `ns` form. With the limit of one group per ns."
  (:require-macros [bllm.cli])
  (:require [bllm.util :as util :refer [def1]]))

;; NOTE using more idiomatic ClojureScript here. This data is directly consumed
;; by the REPL UI, and doesn't need to be as high-performance as other systems.

(defrecord Group [kind name icon grp doc tags])
(defrecord CVar  [kind name icon grp doc tags get set])
(defrecord Cmd   [kind name icon grp doc tags action])

(comment (js/console.log @defs)
         (js/console.log @deps))

(def root
  ;; NOTE manually defined to bootstrap the CLI graph. Prefer `defgroup`.
  (->Group ::group ::root nil nil nil nil))

(def1 defs
  "Atomic registry of all CLI knowledge. Contains maps discriminated on `:kind`.

  All nodes also have the following keys:
  `:name` is the unique resource identifier of the node as a namespaced keyword.
  `:icon` is a Unicode string used both as a text icon and a resource file name.
  `:grp` is the parent node's key. Children are indexed into `deps` for lookups.
  `:doc` describes what the definition is for to users. String or literate text.
  `:tags` describes what the definition is for to other code. Semantic keywords.

  NOTE do not mutate directly, use the provided fns in this module to ensure the
  `deps` index is kept up to date. Also runs additional development validations."
  (atom {(:name root) root}))

(def1 deps
  "Reactive graph of all CLI nodes in `defs`. Contains key sets to child nodes.

  NOTE do not mutate directly, maintained automatically with changes to `defs`."
  (atom {}))

(def conj-set (fnil conj #{}))

(defn- index [m k old new]
  (cond-> (update m new conj-set k)
    old (update old disj k)))

(defn register [{:as node :keys [name grp]}]
  (let [old (:name (@defs name))]
    (swap! defs assoc name node)
    (when (not= old grp)
      (swap! deps index name old grp))
    node))

(declare group)

(defn upsert-group [k]
  (assert (keyword? k))
  (if-let [{:as g :keys [kind]} (@defs k)]
    (if (= ::group kind)
      (:name g)
      (throw (ex-info "Not a group" {:name k :kind kind})))
    (let [g (group k (:name root) "Generated" #{::auto})]
      (js/console.warn "Consider adding a `defgroup` for" k)
      g)))

(defn group
  [k icon grp doc tags]
  (assert (nil? (namespace k)))
  (register (->Group ::group k icon (upsert-group grp) doc tags))
  k)

(defn var
  [k icon grp doc tags get set]
  (assert (some? (namespace k)))
  (register (->CVar ::var k icon (upsert-group grp) doc tags get set)))

(defn cmd
  [k icon grp doc tags f]
  (assert (some? (namespace k)))
  (register (->Cmd ::cmd k icon (upsert-group grp) doc tags f)))

(bllm.cli/defgroup self
  "CLI configuration variables and reflection commands.")

(bllm.cli/defcmd help
  "Contextual help. Try help about help. Help yourself!"
  {:kdb F1}
  []
  (js/console.log "Hello cmd world!"))
