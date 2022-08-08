(ns repl.error
  "Simple application-wide error handling."
  (:require [bllm.util :as util]))

(defn- on-window-error [msg file line col error]
  (js/console.error msg error))

(defn- on-unhandled-error [e]
  (js/console.error e))

(defn- on-unhandled-rejection [e]
  (js/console.error e))

(defn init []
  (comment ; TODO show original stack traces in console for now
    (set! (.-onerror js/window)               (util/callback on-window-error))
    (js/addEventListener "error"              (util/callback on-unhandled-error))
    (js/addEventListener "unhandledrejection" (util/callback on-unhandled-rejection))
    ))


;;; Unhandled Frame Exception
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn exceptional-pause [e]
  (js/console.log e)
  ;; TODO pause display, overlay error on screen, click/eval to resume
  )
