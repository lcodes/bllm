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

(ui/defschema focus
  "Information about the current selection scopes.")

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

(ui/deframe col {:layout :col})
(ui/deframe row {:layout :row})


;; deframe -> DECIDE IF INSTANCED/SINGLETON THERE
;; -> only one of menu/tool/dock/mini, and one window
;; -> but multiple dock splitter frames
;; - all are the same thing (frame -> layout -> items)
;; EXCEPT
;; - singleton is toggled on/off globally, as a user preference (toggle independently of view state)
;; - instance is created/destroyed on user demand, usually by splitting or merging dock panels

;; TODO how to track focus, what focus to track
;; - single focus regardless of number of input devices
;; - cmds to move focus (switch to tab, goto def, move to pane, etc)
;; - panes have focus, views alone dont (but panes contain a view, and are a view)

(ui/defview test-view []
  [:div.content
   [:h3 "Test View"]
   [:button {:data-cmd (util/fqn ::split)} "Split Column"]
   [:button {:data-cmd (util/fqn ::split)} "Split Row"]])

(ui/deframe ^:static ing
  "The main application view is a fully customizable dock."
  {:class "dock"}
  [ui/node :repl.home/welcome])

;; TODO different concerns here
;; - want command arguments (split :grid) or (split :row)
;; - not different commands for every possible argument
;; - emacs has prefix arguments, not sure how to scale directly
;; - ultimately want curried commands
;;   - ie (split :row) returns a command instead of executing it
;;   - then can bind to different keys as separate actions
;;   - while still keeping composability (ie take argument from selection, or movement, or.. vim!)
;; - doesnt matter if called from a key sequence, mouse gestures, button clicks or menu items

(defn- view-index [frame view-key]
  (let [cnt (count frame)]
    (loop [n 0]
      (let [[v k] (nth frame n)]
        (if (and ;;(= v ui/node) TODO arity overload breaks this
                 (= k view-key))
          n
          (let [n (inc n)]
            (assert (< n cnt) "Missing view in frame")
            (recur n)))))))

(defn- add-view [views index view]
  (let [n (inc index)]
    (concat (take n views) (list view) (drop n views))))

(defn- replace-view [views n view]
  (concat (take n views) (list view) (drop (inc n) views)))

(def layout-hack {:row ::row :col ::col}) ; map layout keys to dock frame IDs

(defn- frame [view-state new-k node-k cur-k index view]
  (let [cur-v (-> view-state cur-k (nth index))]
    (-> view-state
        (assoc  new-k (list cur-v view))
        (update cur-k replace-view index [ui/node node-k new-k]))))

(defn- split-view [state frame-k view-k layout]
  (let [frame (ui/node-of state frame-k)
        views (ui/view-of state frame-k)
        index (view-index views view-k)
        split [:div "Hello!"]]
    (if (= layout (:layout frame))
      (update-in state [ui/views frame-k] add-view index split)
      (let [new-k  (ui/gen-view-id)
            node-k (layout-hack layout)]
        (-> state
            (update ui/links assoc new-k node-k)
            (update ui/views frame new-k node-k frame-k index split))))))

(ui/defevent split
  {:kbd [Leader w s]}
  [db x layout]
  (if-let [k (ui/frame-key x)]
    (update db ui/state split-view k (ui/view-key x) (or layout :row))
    (do (js/console.warn "TODO no frame focus, also need user facing logs")
        db)))

(ui/defevent split-row
  {:kbd [Leader w /]}
  [db]
  ;; TODO reuse `split` with default argument
  db)

(comment (cli/call split nil))

;; dock containers
;; - each container is a tab view and a current pane view
;; - resize handles, add/remove containers as new areas are formed/emptied
;; - emacs/vim inspired UI -> minimal, natural navigation, self-documenting

;; actions:
;; - split panel (vertical/horizontal), delete, resize, move panels
;; - nav (change which pane has focus -> direct jump, cycle through, etc)
;; - change active pane (with per panel history)

