(ns repl.dock
  "Turns the browser's DOM window into an application dock."
  (:require [bllm.cli  :as cli]
            [bllm.data :as data]
            [bllm.util :as util :refer [def1]]
            [repl.mode :as mode]
            [repl.ui   :as ui]
            [repl.home :as home]
            [repl.schema :as schema]))

(set! *warn-on-infer* true)

(cli/defgroup config)

(comment (js/console.log panes))

(data/defstore Layout
  "A snapshot of the current positions, sizes and contents of every dock panel."
  )

#_
(ui/defschema state
  "Data description of the dock panels and the opened content views."
  )

#_
(ui/defview test-view []
  [:div.content
   [:h3 "Test View"]
   [:button {:data-cmd (util/fqn ::split-col)} "Split Col"]
   [:button {:data-cmd (util/fqn ::split-row)} "Split Row"]])

(ui/defevent add-tab
  {:kbd []}
  [db x]
  (js/console.log db)
  db)

(ui/defview empty-pane
  []
  (let [n (util/random-to (.-length ui/hexa))]
    [:div.empty
     [:h3 (aget ui/hexa n)]
     [:p.lead (inc n)]]))

(defn- tab
  [k]
  [:li [:a {:data-cmd ""} (name k)]])

;;  "<space> v p" `select-view-pane` or "<space> f o" `find-object` and others can navigate to everything
;;  - regardless of the current list of tabs; adds a tab if new to this panel, dont need fancy tab mgmt, most likely end up off
(ui/defview panel
  [{:keys [pane tabs]} k]
  [:div.panel {:data-panel (util/key-of k)}
   (when pane ; TODO prefs hide tabs, per panel tabs visibility
     [:header.row
      [:ul.tabs
       (for [k tabs]
         [tab ^{:key k} k])
       [:li.new [:button {:data-cmd (util/fqn add-tab)} ui/add-label]]]
      ui/space
      [:button.more ui/more-label]])
   [:section.pane
    [ui/node (or pane empty-pane)]] ; TODO configure default pane view
   (when pane ; TODO zen mode hides, not all views need one
     [mode/line])])

(ui/deframe col {:layout :col})
(ui/deframe row {:layout :row})

;; TODO how to track focus, what focus to track
;; - single focus regardless of number of input devices
;; - cmds to move focus (switch to tab, goto def, move to pane, etc)
;; - panes have focus, views alone dont (but panes contain a view, and are a view)

(ui/deframe ^:static ing
  "The main application view is a fully customizable dock."
  {:class "dock"}
  [ui/node panel])

(defn- add-view [views index view]
  (let [n (inc index)]
    (concat (take n views) (list view) (drop n views))))

(defn- replace-view [views n view]
  (concat (take n views) (list view) (drop (inc n) views)))

(def layout-hack {:row ::row :col ::col}) ; map layout keys to dock frame IDs

(defn- split [view-state new-k cur-k index view]
  (let [cur-v (-> view-state (get cur-k) (nth index))]
    (-> view-state
        (assoc  new-k (list cur-v view))
        (update cur-k replace-view index [ui/node new-k]))))

(defn- split-view [state frame-k view-k layout]
  (let [frame (ui/node-of state frame-k)
        views (ui/view-of state frame-k)
        index (ui/view-index views view-k)
        new-k (ui/gen-view-id panel)
        new-v [ui/node new-k]]
    (-> (if (= layout (:layout frame))
          ;; Split in the same direction as the container frame
          (update-in state [ui/views frame-k] add-view index new-v)
          ;; Split in a new direction needs a new container frame
          (let [node-k (layout-hack layout)
                view-k (ui/gen-view-id node-k)]
            (-> state
                (update ui/links assoc view-k node-k)
                (update ui/views split view-k frame-k index new-v))))
        ;; Insert the newly created pane
        (update ui/links assoc new-k panel)
        (update ui/views assoc new-k (:init (ui/node-of state panel))))))

(defn- split* [db x layout]
  (if-let [k (ui/frame-key x)]
    (update db ui/state split-view k (ui/panel-key x) layout)
    (do (js/console.warn "TODO no frame focus, also need user facing logs")
        db)))

(ui/defevent split-row
  {:kbd [Leader w s]}
  [db x layout]
  (split* db x (or layout :row)))

(ui/defevent split-col
  {:kbd [Leader w /]}
  [db x]
  ;; TODO reuse `split` with default argument
  (split* db x :col))

;; TODO different concerns here
;; - want command arguments (split :grid) or (split :row)
;; - not different commands for every possible argument
;; - emacs has prefix arguments, not sure how to scale directly
;; - ultimately want curried commands
;;   - ie (split :row) returns a command instead of executing it
;;   - then can bind to different keys as separate actions
;;   - while still keeping composability (ie take argument from selection, or movement, or.. vim!)
;; - doesnt matter if called from a key sequence, mouse gestures, button clicks or menu items

(comment (cli/call split nil))

;; dock containers
;; - each container is a tab view and a current pane view
;; - resize handles, add/remove containers as new areas are formed/emptied
;; - emacs/vim inspired UI -> minimal, natural navigation, self-documenting

;; actions:
;; - split panel (vertical/horizontal), delete, resize, move panels
;; - nav (change which pane has focus -> direct jump, cycle through, etc)
;; - change active pane (with per panel history)


;; what are selections?
;; - not just data store assets
;; - could be any JS object really -> so long as it has a matching mode
;;   - turns everything into different layers of potential selection
;;   - ie an UI editor might want to select which UI control it is editing
;;     - drag a control from another pane onto it -> transfer ID -> edit data model -> live changes
;;     - sprite editor -> drag button -> edit icon (or edit label if dragged onto text editor)
;;     - same for 3d scene objects, asset files, shader nodes; this is a general indirection
;;   - selection itself needs very little data (list of JS objects and their types)
;;   - selection handlers (pane views, modes, or just cut/copy/paste commands & co) dispatch on type
;;   - either default behavior or incompatible selection (ie texture dragged in text box is invalid, in address box yields asset ID)
;;
;; "nested" selections
;; select "hello.txt" -> show editor pane -> select single paragraph -> display selection in another pane
;; - unselecting with movements in the first editor will keep the selection until the other pane is closed
;;   - could be useful to go through macroexpansions, file navigation, literate subviews, live previews etc
