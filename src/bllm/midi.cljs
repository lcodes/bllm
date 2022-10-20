(ns bllm.midi
  (:require [bllm.util :as util :refer [def1]]))

;; https://developer.mozilla.org/en-US/docs/Web/API/Web_MIDI_API

#_(set! *warn-on-infer* true) ; TODO WebMIDI externs missing? What?

;; TODO need a MIDI device anyways to develop and test this.
;; - drum triggers, keyboard controller; MIDI input to anim track
;; - synth; anim tracks to output voices

(def1 ^js/MIDIAccess access nil)

(comment (js/console.log access))

(defn- on-state-change []
  )

(defn- on-request-success [midi]
  (set! access midi)
  (set! (.-onstatechange midi) (util/cb on-state-change)))

(defn- on-request-failure [msg]
  (js/console.error "MIDI access failed:" msg))

(defn- ^js/Promise request-access [^object navigator] ; HACK why isnt requestMIDIAccess resolved?
  (.requestMIDIAccess navigator))

(defn init []
  (-> (request-access js/navigator)
      (.then on-request-success on-request-failure)))
