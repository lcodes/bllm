(ns repl.menu
  "UI views for application control and navigation."
  (:require [repl.ui :as ui]))

(set! *warn-on-infer* true)

(ui/defview title
  "Application icon and label, with their associated system menu."
  []
  [:h1.title "REPL"]) ; TODO what the doc says (also let current dock/mode replace/decorate the icon)

(defn tab
  [label active?] ; TODO pass tab key, sub to tab state
  [:li (when active? {:class "active"})
   [:a {:href "#"} label
    (ui/close-btn identity)]])

(ui/defview docks
  "Main application tabs, each completely swapping the current modal dock state.

  Inspired from the workspace implementation found in Doom Emacs."
  []
  [:ul.tabs.workspaces
   ;; each workspace get its own `ui/state` replacement, instantly swapping the active views, panes, menus and modes.
   [tab "⛪ Home"]
   [tab "🌌 Editor"]  ; Default view; game viewport, world content, database browser, log console, collaboration room (from chat to VR -> REPL in REPL)
   [tab "🗔 Term"]
   [tab "⛄ Demo" true]
   [tab "🌊 Shaders"] ; Work with `bllm.wgsl` graph nodes; preview scene, visual code, node toolkit, properties, relevant assets filter
   [tab "⛳ Scripts"] ; Source editor (asset rich text, dialogue trees, visual code, text view with LSP client) -> edit The REPL itself
   [tab "🎥 Direct"]  ; Timeline focused UI, keyframe `mode` to filter asset deltas into the edited keyframe, extrapolation/replay gizmos
   [tab "🎮 Play"]    ; Almost no UI views except for the `game/viewport`.
   ;; TODO ^ this is really a `deframe` where the child views are limited to `:workspace` types, and only a partial view of their state.
   ;; - ie workspace contains full state of the UI layout, far too much to display a simple tab; but can feed another layer of re-frame subs
   ])

;; TODO these dont belong here, not sure where yet
(defn- on-minimize [])
(defn- on-maximize [])
(defn- on-quit [])

(ui/defview system
  "Controls for the current dock or the entire window when running in Electron."
  []
  ;; TODO needs to be displayed even if the menu view is disabled (but still allow power disable)
  [:ul.btns.system
   [:li (ui/btn ui/minimize-label "system-btn" on-minimize)]
   [:li (ui/btn ui/maximize-label "system-btn" on-maximize)]
   [:li (ui/btn ui/close-label "system-btn quit" on-quit)]])

;; TODO usually hidden to save screen space -> still display when pressing ALT or enabled
(ui/deframe bar
  "Application control and navigation to be displayed at the top of the window."
  {:elem :header :layout :row :class "bg-secondary"}
  [ui/node title]
  [ui/node docks]
  ui/space
  [ui/node system])
