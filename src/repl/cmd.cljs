(ns repl.cmd
  "User interface focus navigation. From HTML5 history to interactive commands.

  NOTE built on top of engine CLI commands. Using definition groups as leaders."
  (:require [bllm.cli   :as cli]
            [bllm.html  :as html]
            [bllm.input :as input]
            [bllm.util  :as util :refer [def1]]
            [repl.ui    :as ui   :refer [!>]]))

(set! *warn-on-infer* true)

;; histories -> all pretty much the same thing, can `undo-tree` them all?
;; - undo history
;; - browse history
;; - terminal history
;; - command history
;; - panel nav history
;; - HTML5 history

;; entry point to app state deeplinks
;; - many views, each with their own nav history (like emacs windows in a frame)
;; - deeplinks will need to specify layout AND panes AND deeplink within each pane
;; - cmd to move that state to URL or copy to clipboard -> paste in another tab, browser, platform, by another user
;; - dont get megabytes of URL either; if the remote doesnt already know the referenced IDs, ignore those elements
;;   - (or provide source to query them; WebRTC connection & have the remote request the very data we referenced)
;;   - kinda like graphql, but better, without text, on top of an existing schema, protocol and database; datalog inspired
;;
;; OR, just resolve deeplinks to an asset or a pane, and either create or show it in a panel.
;; - one app DB convenient now, can deelink *everything*, send someone a link to focus the arcane command sequence you're talking about
;; - no different than sharing WoW item links in chat, or twitch/FFZ/BTTV emotes, or github commits in issues, everything is relative


;; inputs can be anything; vim started when there were only keyboards, but didn't expand into gamepads and touch inputs
;; - anything the `input` module supports is fair game here. plug a midi keyboard and bind a command to every key
;;   - use chords to change modes, (ionian commands, lydian commands, mixolydian commands, etc.)
;;   - use text to speech and NLP (combine spoken commands with keyboard, mouse, gamepad and other inputs; audible feedback)

;; configurable vim-like controls
;; - leader key -> command groups -> command -> emacs-like function call
;; - modal -> no escaping this, except it can be done as a better focus.
;; - layouts -> not everyone will jump into vim, even if its not called vim.
;;   - gamify -> its just like a gamepad, and this is a simulation
;;     - sequences across time are the fundamental model here -> strange loops!
;;     - better to know 7 notes and how they compose, than 70 distinct chords


(ui/defschema state
  focus {} ; TODO focus history?
  stack ()
  binds {}) ; TODO user-customizable keybinds, init is :kbd on cmds

#_
(ui/defeffect ^:private on-key
  [{:keys [db]} ^js/KeyboardEvent e]
  (let [value (state db)
        input (stack value)
        table (or (first input) (binds value))
        cmd   (get table (.-code e))]
    ;; resolve modifiers on cmd? or resolve modifiers first, then cmd in that? (later)
    (if-not cmd
      (js/console.warn (.-code e) "is undefined" e)
    ;; - execute command (effect! -> rswap! result back into app-db -> UI render -> repeat with next `on-key`)
    ;;   - either moving to a new leader context
    ;;   - async load UI when given a promise
    ;;   - close feedback pane if nil
      )))

(defn on-key [k]
  (js/console.log (input/keys k))
  true)

(defn- on-click [_ e]
  ;; TODO modifiers for filtering, different selection modes, meta selection.
  (when-let [cmd (html/find-attr-key e "data-cmd")]
    (let [ret (cli/call cmd e)]
      ;; TODO dispatch ret
      true)
    ;; TODO fallback behaviors - try to give something focus
    ))

(comment
  (.pushState js/history nil nil "#/ohhi2"))

(ui/defevent ^:private on-pop-state
  [db pop]
  ;; check input state, delegate to:
  ;; - ignore if just removing history noise from cmd input stack
  ;; - `cmd-back` if a cmd stack exists, and `pop` state is leaf input key
  ;; - cancel cmd stack if one exists -> user is navigating somewhere else (fixup history accordingly)
  ;; - dispatch to custom cmd if state is {"cmd" "name"}
  ;; - otherwise dispatch to default behavior -> use hash as a dock space key
  (js/console.log "POP" pop)
  db)

(def ^:private dispatch
  "Input handler forwarding to UI focus and HTML elements."
  (input/handler-simple ::dispatch
                        (util/cb on-key)
                        (util/cb on-click)))

(defn init []
  (input/enable! dispatch)
  (ui/!event js/window "popstate" on-pop-state
             (fn get-state [^js/PopStateEvent e]
               (or (.-state e) :empty))))

(ui/defview controls
  []
  [:div.options
   [:h3 "Controls"]
   ;; - control categories
   ;; - commands -> key chords -> leader index
   ])

(ui/defview feedback
  [k state]
  [:div "display the current command sequence & leader group"])


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
  (defcmd split-row :- W / (split :row))
  (defcmd split-col :- W - (split :col))

  (defcmd destroy
    "Closes the active panel window"
    :- W D
    []
    )

  )
