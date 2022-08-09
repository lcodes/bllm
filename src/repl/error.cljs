(ns repl.error
  "Simple application-wide error handling."
  (:require [bllm.disp :as disp]
            [bllm.util :as util :refer [def1]]))

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

(def1 ^:private exceptional-resume-fn
  nil)

(defn exceptional-pause
  "Called when a thrown `Error` bubbles all the way to the app's frame handler."
  [e resume-fn]
  (js/console.log e)
  (disp/cancel-frame)
  (set! exceptional-resume-fn resume-fn)
  ;; TODO overlay error on screen, click to resume
  )

(defn exceptional-resume
  "Called as a figwheel hook, or when clicking the error overlay."
  []
  (when exceptional-resume-fn
    ;; TODO remove overlay
    (disp/request-frame exceptional-resume-fn)
    (set! exceptional-resume-fn nil)))
