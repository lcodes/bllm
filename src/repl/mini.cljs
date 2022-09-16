(ns repl.mini
  "Similar to mode lines, but for the entire application frame. Tracks focus."
  (:require [repl.ui :as ui]))

(set! *warn-on-infer* true)

;; - background tasks info (load queue, import queue, misc long running promises)
;; - quick controls (play/stop; pause; step; etc) depends on global engine mode (editor, running, paused, debugging, idle, etc)
;; - user account (auth providers & active session tokens, permissions & roles, )
;; - layout selection (dock snapshots, control schemes, options menu)
;; - status icons (indexeddb activity, webrtc activity; connection state, network health, etc)
;; - last log message (log level notice and up, otherwise this can change way too fast) (click for full log details broken down by frame)
;; - NOTE on clicks -> just like emacs, everything triggers an interactive function;
;; - always layer on top of programmatic data api, then code, then meta, then user convenience

;; NOTE on application views
;; - dont limit which subviews they display (ie this isnt a statusbar containing statusbaritems)
;; - likewise, the tool view is not a toolbar containing toolbuttons or whatever specific control
;; - and finally, the menu view isn't a menu bar, and not limited to displaying menu bar items

;; `ui/defview` defines generic ui -> can be displayed in any application view
;; `ui/defpane` defines dock panes -> can be displayed in any dock panels view
;; - whats the difference? UIs are enabled/disabled, panes are added/removed
;;   - makes no sense having two user buttons, or background task progresses
;;   - Some panel views will end up singletons too (welcome screen) doesnt matter
;;   - app views accumulate UI subviews, all displayed at once in the view's layout
;;   - dock panels display a single pane at any time, with their own navigation history
;;   - both are completely placeable, drag & droppable and configurable by end users
;; - meaning: far less component types to implement, small registry; views and panes cover the full foundations

;; NOTE on view focus and data selections
;; - popups (modal or not) always get focused when displayed, unless acting as a notification (but avoid those)
;; - otherwise current view (dock panes are also views) receives input events
;;   - vim-style command language is the foundation everywhere, a composable command language with leader keys
;;   - command input take focus priority, can still navigate mid-command (ie drag object from any view as arg)

(ui/defview background-tasks
  []
  [:div.background-tasks
   [:span.icon "📝"]
   "0"])

(ui/defview log-message
  []
  [:div.grow.log-message "Hello World"])

;; TODO move these actions to another module.
(defn play-reverse [])
(defn play|pause [])
(defn stop [])
(defn record [])
(defn step-back [])
(defn step-forward [])
(defn goto-beginning [])
(defn goto-end [])
(defn pack [])
(defn options [])

(defn- engine-btn [label click]
  [:li.icon [:button.engine-btn {:on-click click} label]])

(ui/defview engine-controls
  "What do we want? Faster horses! What is this car you speak of? What's a cdr?"
  []
  [:div.flex.engine-controls
   [:ul.btns
    [engine-btn "⏮" goto-beginning]
    [engine-btn "⏪" step-back]
    [engine-btn "◀" play-reverse]
    [engine-btn "▶" play|pause] ; TODO "⏸" pause label dependent on engine state
    [engine-btn "⏹" stop]
    [engine-btn "⏺" record] ; TODO red when recording (also `recording-mode` as filter over UI)
    [engine-btn "⏩" step-forward]
    [engine-btn "⏭" goto-end]]
   [:ul.btns
    [engine-btn "📦" pack]
    [engine-btn "⚙" options]]])

(ui/defview layout-select
  []
  [:select.layout ; TODO select2 or custom replacement
   [:option "Default"]])

(ui/defview user-button
  []
  [:div.user-button]
  [:div "Myself 👤"])

(ui/defview status-icons
  []
  ;; configure which "services" to display the status of, define notion of service
  [:div "🖧 🔌 🔋 🔊"])

(ui/deframe bar
  "The mini-bar is stocked with specialty beverages and snacks for visitors."
  {:elem :footer}
  ;; TODO grab state of system frames from filtering nodes, then sorting by priority (neg to left, pos to right)
  [ui/node engine-controls]
  [ui/node background-tasks]
  [ui/node log-message]
  [ui/node layout-select]
  [ui/node user-button]
  [ui/node status-icons])
