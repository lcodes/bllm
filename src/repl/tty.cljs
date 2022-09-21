(ns repl.tty
  "Text Terminal? Yuck! UI integration of the engine's CLI."
  (:require [reagent.core :as rc]
            [bllm.cli  :as cli]
            [bllm.util :as util :refer [def1]]
            [repl.ui   :as ui]))

(def1 defs
  "Reactive sub over the CLI definitions."
  (rc/atom @cli/defs))

(defn init []
  ;; TODO most likely there is a better way to link atom->ratom, this works now.
  (add-watch cli/defs :defs (fn link-defs [_ _ _ v] (reset! defs v))))

(ui/defview screen
  [k state]
  ;; Log output
  ;; Cmd output
  ;; User input
  ;; Data REPL inside The REPL
  [:div "TTY Screen"])

(ui/defview feedback
  [k state]
  [:div "display the current command sequence & leader group"])
