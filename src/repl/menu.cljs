(ns repl.menu
  (:require [repl.ui :as ui]))

(set! *warn-on-infer* true)

;; main menu (usually hidden to save screen space -> still display when pressing ALT or enabled)
;; popup menus (same logic as main menu, but on button press or context events)

(ui/defview title
  ;; application icon, system menu
  )

(ui/defview workspaces
  ;; main application tabs (ie going from scene view to material editor)
  ;; - distinct from dock layouts (each workspace has a current layout)
  ;;   - nothing is hardcoded (ie one workspace could be scene view AND material editor side by side)
  ;; inspired from Doom Emacs workspaces
  )

(ui/defview system
  ;; if running in electron, displays the minimize, maximize and close buttons
  ;; NOTE needs to be displayed even if the menu view is disabled (but still allow power disable)
  )

(ui/deframe bar
  {:elem :header}
  []
  title
  workspaces
  system)
