(ns repl.dock
  (:require [bllm.util :as util :refer [def1]]
            [repl.ui   :as ui]))

;; dock, containers, resize handles
;; window layout, tabs, snapshots
;; individual panels & their content

;; icon fonts -> fontawesome? materialicons? both? more?
;; - naively load all initially, then find ways to load only used icons on demand
;; - tower of lisp would be useful once more -> compiler should be told about this

(defn view []
  ;; dock containers
  ;; - each container is a tab view and a current pane view
  ;; - resize handles, add/remove containers as new areas are formed/emptied
  ;; - emacs/vim inspired UI -> minimal, natural navigation, self-documenting
  [:div "World"])
