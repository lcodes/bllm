(ns repl.halt
  "Simple application-wide error handling."
  (:require [bllm.disp :as disp]
            [bllm.util :as util :refer [def1]]))

(set! *warn-on-infer* true)

(defn- on-window-error [msg file line col error]
  (js/console.error msg error))

(defn- on-unhandled-error [e]
  (js/console.error e))

(defn- on-unhandled-rejection [e]
  (js/console.error e))

(defn init []
  (comment ; TODO show original stack traces in console for now
    (set! (.-onerror js/window)               (util/cb on-window-error))
    (js/addEventListener "error"              (util/cb on-unhandled-error))
    (js/addEventListener "unhandledrejection" (util/cb on-unhandled-rejection))
    ))


;;; Unhandled Frame Exception
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 ^:private exceptional-resume-fn
  nil)

(defn exceptional-pause
  "Called when a thrown `Error` bubbles all the way to the app's frame handler."
  [e resume-fn]
  (js/console.error "halt" e)
  (disp/cancel)
  (set! exceptional-resume-fn resume-fn)
  ;; TODO overlay error on screen, click to resume
  )

(defn exceptional-resume
  "Called as a figwheel hook, or when clicking the error overlay."
  []
  (when exceptional-resume-fn
    ;; TODO remove overlay
    (disp/frame exceptional-resume-fn)
    (set! exceptional-resume-fn nil)))
