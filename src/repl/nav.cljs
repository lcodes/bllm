(ns repl.nav)

;; configurable vim-like controls
;; - leader key -> command groups -> command -> emacs-like function call
;; - modal -> no escaping this, except it can be done as a better focus.
;; - layouts -> not everyone will jump into vim, even if its not called vim.
;;   - gamify -> its just like a gamepad, and this is a simulation
;;     - sequences across time are the fundamental model here -> strange loops!
;;     - better to know 7 notes and how they compose, than 70 distinct chords

;; command registry & contexts in which they can be used
;; - no (interactive) but can do (defem) for def editor macro

;; commands affecting the simulation:
;; - system point where to run, part of that frame's inputs
;; - inputs -> world view -> ECS events -> normal frame flow
