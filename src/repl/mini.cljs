(ns repl.mini
  "Similar to mode lines, but for the entire application frame. Tracks focus.")

;; - background tasks info (load queue, import queue, misc long running promises)
;; - quick controls (play/stop; pause; step; etc) depends on global engine mode (editor, running, paused, debugging, idle, etc)
;; - user account (auth providers & active session tokens, permissions & roles, )
;; - layout selection (dock snapshots, control schemes, options menu)
;; - status icons (indexeddb activity, webrtc activity; connection state, network health, etc)
;; - last log message (log level notice and up, otherwise this can change way too fast) (click for full log details broken down by frame)
;; - NOTE on clicks -> just like emacs, everything triggers an interactive function;
;; - always layer on top of programmatic data api, then code, then meta, then user convenience

(defn view []
  [:footer#mini "MINI BUFFER"])
