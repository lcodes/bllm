(ns repl.dock
  "Turns the browser's DOM window into an application dock."
  (:require [bllm.data :as data]
            [bllm.util :as util :refer [def1]]
            [repl.mode :as mode]
            [repl.ui   :as ui]))

(set! *warn-on-infer* true)

(def1 ^:private panes (js/Map.))

(comment (js/console.log panes))

(data/defstore Layout
  "A snapshot of the current positions, sizes and contents of every dock panel."
  )

;; dock, containers, resize handles
;; window layout, tabs, snapshots
;; individual panels & their content

;; icon fonts -> fontawesome? materialicons? both? more?
;; - naively load all initially, then find ways to load only used icons on demand
;; - tower of lisp would be useful once more -> compiler should be told about this

(defn pane
  "Registers a new panel view."
  [id view label]
  (let [ui #js {:id id :view view :label label}]
    (.set panes id ui)
    ui))

(defn- tab
  "."
  []
  )

(ui/defview panel
  "A `view` container for `pane` views."
  []
  ;; panel controls (close, options, ...)
  ;; tab view (optional)
  ;; - hybrid emacs/modern style -> tabs display list associated to that panel
  ;; - but "<space> v p" `select-view-pane` or "<space> f o" `find-object` and others can navigate to everything
  ;;   - regardless of the current list of tabs; adds a tab if new to this panel, dont need fancy tab mgmt, most likely end up off
  ;; content
  ;; mode line (optional)
  [:div.panel "TODO"])

(ui/deframe bar
  "The main application view is a fully customizable dock."
  {:class "grow"}
  [ui/node panel]
  ;; dock containers
  ;; - each container is a tab view and a current pane view
  ;; - resize handles, add/remove containers as new areas are formed/emptied
  ;; - emacs/vim inspired UI -> minimal, natural navigation, self-documenting
  )

;; actions:
;; - split panel (vertical/horizontal), delete, resize, move panels
;; - nav (change which pane has focus -> direct jump, cycle through, etc)
;; - change active pane (with per panel history)
