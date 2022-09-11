(ns bllm.audio
  (:require [bllm.data :as data]
            [bllm.ecs  :as ecs]
            [bllm.util :as util :refer [def1]]))

;; https://webaudio.github.io/web-audio-api/

;; TODO investigate https://developer.mozilla.org/en-US/docs/Web/API/WebCodecs_API

(set! *warn-on-infer* true)


;;; System Context
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 ^:private ^js/AudioContext context nil)

(def1 ^:private master nil) ; Active mixer

(def1 ^:private sound-sources nil) ; Sound component indexes here during playback.

(def1 ^:private music-elements nil) ; From stream
(def1 ^:private music-sources  nil) ; From buffer

(defn init []
  (set! context (js/AudioContext. #js {:latencyHint "interactive"
                                       :sampleRate  js/undefined})))

(defn post-tick []
  )

;; https://developer.mozilla.org/en-US/docs/Web/API/Media_Capabilities_API
;; https://developer.mozilla.org/en-US/docs/Web/API/Media_Session_API

;; decodeAudioData

(comment
  (data/defstore MixerGraph)

  (data/defstore MusicTrack)

  (data/defstore SoundAtlas)

  (data/defstore SoundBuffer))

;; component state:
;; - mixer effect
;; - music stream
;; - sound source

;; system state:
;; - listener
;; - destination

(comment
  (ecs/defc Music
    :require [scene/LocalToWorld]
    )

  (ecs/defc Sound
    :require [scene/LocalToWorld]
    [clip   Buffer] ; store resource index
    [volume :f32]
    [loop   :bool])

  ;; action components for change events
  ;; - deterministic input, batches between ECS phases, flushed at specific points

  (ecs/defevent play :target Sound)
  )
