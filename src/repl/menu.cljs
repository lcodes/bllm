(ns repl.menu
  (:require [repl.ui :as ui]))

(set! *warn-on-infer* true)

;; main menu (usually hidden to save screen space -> still display when pressing ALT or enabled)
;; popup menus (same logic as main menu, but on button press or context events)

(ui/defview title
  ;; application icon, system menu
  [:h1.title "The REPL"])

(ui/defview workspaces
  ;; main application tabs (ie going from scene view to material editor)
  ;; - distinct from dock layouts (each workspace has a current layout)
  ;;   - nothing is hardcoded (ie one workspace could be scene view AND material editor side by side)
  ;; inspired from Doom Emacs workspaces
  [:ul.workspaces
   ;; each workspace get its own `ui/state` replacement, instantly swapping the active views, panes, menus and modes.
   [:li "Editor"]  ; Default view; game viewport, world content, database browser, log console, collaboration room (from chat to VR -> REPL in REPL)
   [:li "Shaders"] ; Work with `bllm.wgsl` graph nodes; preview scene, visual code, node toolkit, properties, relevant assets filter
   [:li "Scripts"] ; Source editor (asset rich text, dialogue trees, visual code, text view with LSP client) -> edit The REPL itself
   [:li "Direct"]  ; Timeline focused UI, keyframe `mode` to filter asset deltas into the edited keyframe, extrapolation/replay gizmos
   [:li "Play"]    ; Almost no UI views except for the `game/viewport`.
   ;; TODO ^ this is really a `deframe` where the child views are limited to `:workspace` types, and only a partial view of their state.
   ;; - ie workspace contains full state of the UI layout, far too much to display a simple tab; but can feed another layer of re-frame subs
   ])

(defn- system-btn [label click]
  [:button.system-btn {:on-click click} label])

(defn- on-minimize []
  )

(defn- on-maximize []
  )

(defn- on-close []
  )

(ui/defview system
  ;; TODO needs to be displayed even if the menu view is disabled (but still allow power disable)
  [:ul.system
   [:li (system-btn "Minimize" on-minimize)]
   [:li (system-btn "Maximize" on-maximize)]
   [:li (system-btn "Close"    on-close)]])

(ui/deframe bar
  {:elem :header}
  [ui/node title]
  [ui/node workspaces]
  [ui/node system])
