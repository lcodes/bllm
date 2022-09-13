(ns repl.nav
  (:require [bllm.input :as input]
            [bllm.util  :as util :refer [def1]]))

;; HTML5 history -> entry point to app state deeplinks
;; - many views, each with their own nav history (like emacs windows in a frame)
;; - deeplinks will need to specify layout AND panes AND deeplink within each pane
;; - cmd to move that state to URL or copy to clipboard -> paste in another tab, browser, platform, by another user
;; - dont get megabytes of URL either; if the remote doesnt already know the referenced IDs, ignore those elements
;;   - (or provide source to query them; WebRTC connection & have the remote request the very data we referenced)
;;   - kinda like graphql, but better, without text, on top of an existing schema, protocol and database; datalog inspired

(def1 ^:private leader-keys (js/Map.)) ;; Leader :: Input -> Leader | Handler
;; inputs can be anything; vim started when there were only keyboards, but didn't expand into gamepads and touch inputs
;; - anything the `input` module supports is fair game here. plug a midi keyboard and bind a command to every key
;;   - use chords to change modes, (ionian commands, lydian commands, mixolydian commands, etc.)
;;   - use text to speech and NLP (combine spoken commands with keyboard, mouse, gamepad and other inputs; audible feedback)

(defn command
  "Registers a new interactive command, or replaces an existing one."
  []
  )

(defn- on-pop-state [e]
  (util/prevent-default e))

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

(defn init []
  (js/addEventListener "popstate" (util/callback on-pop-state)))
