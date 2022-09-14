(ns repl.dock
  "Turns the browser's DOM window into an application dock."
  (:require [bllm.data :as data]
            [bllm.util :as util :refer [def1]]
            [repl.mode :as mode]
            [repl.ui   :as ui]))

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

(defn- panel
  "A container for `pane` views."
  []
  ;; panel controls (close, options, ...)
  ;; tab view (optional)
  ;; - hybrid emacs/modern style -> tabs display list associated to that panel
  ;; - but "<space> v p" `select-view-pane` or "<space> f o" `find-object` and others can navigate to everything
  ;;   - regardless of the current list of tabs; adds a tab if new to this panel, dont need fancy tab mgmt, most likely end up off
  ;; content
  ;; mode line (optional)
  )

(defn- emit-css-grid
  []
  ;; convert data definition of layout to CSS grid
  ;; - can move things around from one snapshot to the other, without having to recreate dom nodes -> lots of work avoided!
  ;; - more flexible than flex, at least for this
  )

(defn view
  "The main application view is a fully customizable dock."
  []
  ;; dock containers
  ;; - each container is a tab view and a current pane view
  ;; - resize handles, add/remove containers as new areas are formed/emptied
  ;; - emacs/vim inspired UI -> minimal, natural navigation, self-documenting
  [:div#dock "World"])
