(ns repl.tty
  "Text Terminal? Yuck! Interactive editor view over the engine's CLI."
  (:require [bllm.util :as util]
            [repl.ui   :as ui]))

(set! *warn-on-infer* true)

;; emacs eshell with literate output
;; interactive org-mode
;; notebook foundations?

(ui/defview screen
  [k state]
  ;; Log output
  ;; Cmd output
  ;; User input
  ;; Data REPL inside The REPL
  [:div "TTY Screen"])
