(ns bllm.cli
  (:require [bllm.meta :as meta]
            [bllm.util :as util :refer [defm]]))

;; TODO `:dynamic` to collect sets of definitions per context?
;; - ie UI selections wont know *all* the `binding` vars in advance
;; - similar to emacs buffer-local variables -> dynamic dynamic vars.

(defm defgroup
  "Creates a user interaction group, named after the current `*ns*`."
  [sym]
  (let [{:keys [icon group doc tags]} (meta sym)]
    `(def ~sym (bllm.cli/group ~(keyword (str *ns*)) ~icon
                               ~(or group ::root) ~doc ~tags))))

(defn- group-or-ns
  [env g]
  (or g (-> env :ns :name name keyword)))

(defm defvar
  "Defines an interactive user variable."
  [sym default-init]
  (let [{:keys [icon group doc tags ctor get set]} (meta sym)]
    `(do (bllm.util/def1 ~sym ~default-init)
         (bllm.cli/var ~(util/ns-keyword sym) ~icon
                       ~(group-or-ns &env group) ~doc ~tags
                       ~(or get `(fn ~'get [] ~sym))
                       (fn ~'set [~'x]
                         ~(some->> set (list 'x))
                         (set! ~sym ~'x))))))

(defn emit-cmd
  [env sym ctor]
  (let [{:keys [icon group doc tags]} (meta sym)]
    `(~ctor ~(util/ns-keyword sym) ~icon
      ~(group-or-ns env group) ~doc ~tags ~sym)))

(defm defcmd
  "Defines an interactive user function."
  [sym params & body]
  `(do (defn ~sym ~params ~@body) ; TODO meta parser -> param types, return type
       ~(emit-cmd &env sym 'bllm.cli/cmd)))
