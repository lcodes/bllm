(ns repl.cmd
  "User interface focus navigation. From HTML5 history to interactive commands."
  (:require [reagent.core :as rc]
            [bllm.input :as input]
            [bllm.util  :as util :refer [def1]]
            [repl.ui    :as ui :refer [!>]]))

(set! *warn-on-infer* true)

;; HTML5 history -> entry point to app state deeplinks
;; - many views, each with their own nav history (like emacs windows in a frame)
;; - deeplinks will need to specify layout AND panes AND deeplink within each pane
;; - cmd to move that state to URL or copy to clipboard -> paste in another tab, browser, platform, by another user
;; - dont get megabytes of URL either; if the remote doesnt already know the referenced IDs, ignore those elements
;;   - (or provide source to query them; WebRTC connection & have the remote request the very data we referenced)
;;   - kinda like graphql, but better, without text, on top of an existing schema, protocol and database; datalog inspired

(def1 ^:private lookup (rc/atom {}))

 ;; Leader :: Input -> Leader | Handler
(def1 ^:private leaders (rc/atom {}))

;; inputs can be anything; vim started when there were only keyboards, but didn't expand into gamepads and touch inputs
;; - anything the `input` module supports is fair game here. plug a midi keyboard and bind a command to every key
;;   - use chords to change modes, (ionian commands, lydian commands, mixolydian commands, etc.)
;;   - use text to speech and NLP (combine spoken commands with keyboard, mouse, gamepad and other inputs; audible feedback)

(defn action
  "Registers a new interactive command, or replaces an existing one."
  []
  )

(defn leader
  "Registers a new command prefix, or replaces an existing one."
  []
  )

(ui/defview controls
  []
  [:div.options
   [:h3 "Controls"]
   ;; - control categories
   ;; - commands -> key chords -> leader index
   ])

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

(ui/defschema state
  stack ()
  binds {})

(def stack-cofx (ui/$cofx stack stack-sub))

(ui/defeffect on-key
  stack-cofx
  [{:as cofx :keys [db]} k]
  ;; get current input state -> leader lookup context
  ;; - either moving to a new leader context
  ;; - or executing a command
  (js/console.log k (stack cofx))
  {})

(defn- on-pop-state [e]
  (util/prevent-default e))

(defn init []
  (js/addEventListener "popstate" (util/callback on-pop-state))
  (doto ^object (.-body js/document)
    (.addEventListener "keydown" (ui/!event on-key identity))))

(comment
  (defcmd leader
    Space []
    )

  (defcmd help
    "Opens interactive documentation for the current selection."
    F1 []
    )

  (defcmd reload
    "Confirms before reloading the web application."
    F5 []
    )

  (defcmd smart
    "Focus the smart input control."
    F6 []
    )

  (defcmd fullscreen
    ""
    F11 []
    )

  (defcmd devtools
    "Meta inspectors."
    F12 []
    )

  (defcmd jump
    "Switches to a different panel."
    [index]
    )

  (defcmd split
    "Splits the active pane into two. Slices into the parent panel if the layout
    is the same, otherwise creates a new panel in place of the active pane."
    ;; no predefined key binding
    [layout] ; default horizontal, prefix vertical, otherwise directly specified
    )

  ;; shorthand commands with curried prefix argument
  (defcmd split-row leader W / (split :row))
  (defcmd split-col leader W - (split :col))

  (defcmd destroy
    "Closes the active panel window"
    leader W D
    []
    )

  )
