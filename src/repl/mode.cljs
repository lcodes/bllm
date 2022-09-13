(ns repl.mode
  "A 'mode line' reflecting the current state of a UI view.")

;; status
;; - target view (type + state)
;; - list of matching modes (ie current position in text view; world coordinates in game view; current cell in data grid)
;; - every status 'mode' gets a UI component to display its state
