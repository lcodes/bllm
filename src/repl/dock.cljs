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

#_
(ui/defschema focus
  "Currently selected pane view."
  )

;; icon fonts -> fontawesome? materialicons? both? more?
;; - naively load all initially, then find ways to load only used icons on demand
;; - tower of lisp would be useful once more -> compiler should be told about this

(defn- tab
  [label key]
  [:li [:a {:href "#" :data-key (str key)} label]])

   ;; TODO zen mode just displaying the inner view
  ;; tab view (optional)
  ;; - hybrid emacs/modern style -> tabs display list associated to that panel
  ;; - but "<space> v p" `select-view-pane` or "<space> f o" `find-object` and others can navigate to everything
  ;;   - regardless of the current list of tabs; adds a tab if new to this panel, dont need fancy tab mgmt, most likely end up off
  ;; content
  ;; mode line (optional)
(ui/defview panel
  [k state]
  [:div.panel
   [:div.head.row
    [:ul.tabs
     [tab "wall.jpg"]
     [tab "hello.txt"]
     [tab "Welcome"]
     [:li.new [:button ui/add-label]]]
    ui/space
    [:button.more ui/more-label]]
   [:section.pane
    (ui/pretty state)
    [:img {:width 256 :src "https://learnopengl.com/img/textures/wall.jpg"}]]
   [ui/node mode/line k]])

(ui/deframe col {:layout :col :sep ui/split})
(ui/deframe row {:layout :row :sep ui/split})

#_
(ui/deframe temp-bar
  {:layout :row}
  [ui/node home/welcome]
  [schema/view ui/views-sub])
;; deframe -> DECIDE IF INSTANCED/SINGLETON THERE
;; -> only one of menu/tool/dock/mini, and one window
;; -> but multiple dock splitter frames
;; - all are the same thing (frame -> layout -> items)
;; EXCEPT
;; - singleton is toggled on/off globally, as a user preference (toggle independently of view state)
;; - instance is created/destroyed on user demand, usually by splitting or merging dock panels

(ui/deframe ing
  "The main application view is a fully customizable dock."
  {:class "dock" :sep ui/split}
  #_[ui/node temp-bar]
  #_[:div.row
   [ui/node home/summary]
   [ui/node panel]]
  #_[ui/node ui/sample-view]
  [ui/node panel nil]
  )

;; dock containers
;; - each container is a tab view and a current pane view
;; - resize handles, add/remove containers as new areas are formed/emptied
;; - emacs/vim inspired UI -> minimal, natural navigation, self-documenting

;; actions:
;; - split panel (vertical/horizontal), delete, resize, move panels
;; - nav (change which pane has focus -> direct jump, cycle through, etc)
;; - change active pane (with per panel history)

