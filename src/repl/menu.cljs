(ns repl.menu)

;; main menu (usually hidden to save screen space -> still display when pressing ALT or enabled)
;; popup menus (same logic as main menu, but on button press or context events)

(defn view []
  [:header#menu "MENU"])
