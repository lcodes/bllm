(ns repl.dock
  "Turns the browser's DOM window into an application dock."
  (:require [bllm.data :as data]
            [bllm.util :as util :refer [def1]]
            [repl.mode :as mode]
            [repl.ui   :as ui]
            [repl.home :as home]
            [repl.schema :as schema]))

(set! *warn-on-infer* true)


(comment (js/console.log panes))

(data/defstore Layout
  "A snapshot of the current positions, sizes and contents of every dock panel."
  )

;; icon fonts -> fontawesome? materialicons? both? more?
;; - naively load all initially, then find ways to load only used icons on demand
;; - tower of lisp would be useful once more -> compiler should be told about this

(defn- tab
  "."
  []
  )

(ui/defview panel
  [state]
  ;; panel controls (close, options, ...)
  ;; tab view (optional)
  ;; - hybrid emacs/modern style -> tabs display list associated to that panel
  ;; - but "<space> v p" `select-view-pane` or "<space> f o" `find-object` and others can navigate to everything
  ;;   - regardless of the current list of tabs; adds a tab if new to this panel, dont need fancy tab mgmt, most likely end up off
  ;; content
  ;; mode line (optional)
  [:div.panel
   [:div.head
    [:h2 "Panel"]
    ui/space
    [:button ui/more-label]]
   [:section.pane
    (ui/pretty state)
    [:img {:width 256 :src "https://learnopengl.com/img/textures/wall.jpg"}]]
   [ui/node mode/line (:name state)]])

(ui/deframe temp-bar
  {:layout :row}
  [ui/node home/welcome]
  [ui/node schema/reflect])

(ui/deframe bar
  "The main application view is a fully customizable dock."
  {:class "grow" :sep ui/split}
  ;; break down into `frame` splits, with `panel` leaves
  ;; - splitting a panel, or emacs as a window manager in the browser:
  ;;   - if same direction, add new panel to parent frame
  ;;   - otherwise, add current and new panel to new frame, splice in place of existing panel
  ;;[ui/node panel :welcome]
  ;;[ui/node panel :welcome]
  [ui/node temp-bar]
  [ui/node home/summary]
  ;; dock containers
  ;; - each container is a tab view and a current pane view
  ;; - resize handles, add/remove containers as new areas are formed/emptied
  ;; - emacs/vim inspired UI -> minimal, natural navigation, self-documenting
  )

;; actions:
;; - split panel (vertical/horizontal), delete, resize, move panels
;; - nav (change which pane has focus -> direct jump, cycle through, etc)
;; - change active pane (with per panel history)
