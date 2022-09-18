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
  [:p.background-tasks 0 [:span.icon "📝"]])

(ui/defview log-message
  []
  [:p.log-message "Hello World"])

(ui/defview layout-select
  []
  [:select.layout ; TODO select2 or custom replacement
   [:option "Default"]])

(ui/defview user-button
  []
  [:p "Myself 👤"])

(ui/defview status-icons
  []
  ;; configure which "services" to display the status of, define notion of service
  [:p "🖧 🔌 🔋 🔊"])

;; TODO display modes; hidden, on hover, on key, disabled
(ui/deframe bar
  "The mini-bar is stocked with specialty beverages and snacks for visitors."
  {:elem :footer :layout :row :class "bg-secondary"}
  ;; TODO grab state of system frames from filtering nodes, then sorting by priority (neg to left, pos to right)
  [ui/node log-message]
  ui/space
  [ui/node background-tasks]
  ;;[ui/node layout-select]
  [ui/node user-button]
  [ui/node status-icons])
