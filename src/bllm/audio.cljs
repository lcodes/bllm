(ns bllm.audio
  (:require [bllm.cli   :as cli]
            [bllm.data  :as data]
            [bllm.ecs   :as ecs]
            [bllm.scene :as scene]
            [bllm.util  :as util :refer [def1]]))

;; https://webaudio.github.io/web-audio-api/

;; TODO investigate https://developer.mozilla.org/en-US/docs/Web/API/WebCodecs_API

(set! *warn-on-infer* true)

(cli/defgroup config)

;; TODO cmds & vars to control the mixer
(cli/defcmd mute [] #_TODO)


;;; System Context
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 ^:private ^js/AudioContext context nil)

(def1 ^:private master nil) ; Active mixer

(def1 ^:private sound-sources nil) ; Sound component indexes here during playback.

(def1 ^:private music-elements nil) ; From stream
(def1 ^:private music-sources  nil) ; From buffer

(defn init []
  ;; TODO start after user gesture
  #_
  (set! context (js/AudioContext. #js {:latencyHint "interactive"
                                       :sampleRate  js/undefined})))

(defn post-tick []
  )

;; https://developer.mozilla.org/en-US/docs/Web/API/Media_Capabilities_API
;; https://developer.mozilla.org/en-US/docs/Web/API/Media_Session_API

;; decodeAudioData

(data/defstore ^:singleton MixerGraph
  "An audio mixer is a hierarchy of tracks, each composed of audio filters.

  At the very minimum, each track contains filters to control its volume and
  balance, as well as toggle its solo and mute modes.

  Every component specifying a playing sound or music to stream becomes an audio
  source. These sources always send their audio data as input to a single audio
  mixer track. In turn, these audio tracks apply their filters, and output into
  their parent mixer track. The root of this hierarchy, the mixer asset itself,
  outputs into the audio device's destination."

  ;; load mixer -> set active -> connect to device destination -> accept playing sounds
  ;; - presets -> not just useful to this type of asset; presets act as snapshots while respecting the nested structure
  ;; - swap mixer -> heavy operation, prefer different presets of the same mixer -> doesnt recreate stateful AudioNodes

  ;; assets need a way to decouple their structure (this is a mixer of 4 tracks
  ;; with such nesting) and their content (use these 4 volumes and other settings
  ;; for all 4 tracks, without recreating their state or interrupting anything)

  ;; similar to tracks of animation timelines (each keyframe specify new values
  ;; over existing dynamic structures)

  ;; also similar to network synchronization (each frame sends delta changes of
  ;; existing non-deterministic components (physics-driven position/rotation))
  ;; without changing their shape; cmds from authority do that (spawn/kill entity)

  ;; filters:
  ;; - biquad
  ;; - convolver
  ;; - delay
  ;; - dynamics compressor
  ;; - gain
  ;; - IIRF
  ;; - wave shaper
  )

#_
(-> (js/navigator.mediaDevices.enumerateDevices)
    (.then #(js/console.log %)))

(data/defstore MusicStream
  ;; what about destination? (stream audio to chunks stored in database -> edit -> publish)
  ;; - play remotely, stream audio to audience, etc

  ;; can be input from `MediaDevices` -> "audioinput", store deviceId & groupId
  ;; - label only available when streams are active & after user permission check

  ;; input can also be from an `<audio>` or `<video>` element. note that video
  ;; elements will most likely also be used as dynamic textures -> shared asset somewhere.
  )

(data/defstore SoundBuffer
  ;; loads a `Blob` into an `AudioBuffer` for `AudioBufferSourceNode`

  ;; wait a sec, THIS is the `load` data, this is what `import` creates.
  ;; - all decoupled because import may create more than one load asset, and load may trigger import of outdated assets

  ;; `import` has `defimport` taking an URL, what about `load` ?
  ;; add `defload` taking in the asset key, and called with in this case `SoundBuffer` -> contains the url/blob/whatever
  ;; - dispatch to same method as import to create the device object.
  ;; - or rather, importing more or less also loads the data (always? bad when first importing large projects)
  ;; - what about searchable data? ie sample rate, texture sizes and whatnot? dont need those to just load the data

  ;; queriable data usually needs the blob data to extract from; so not all loads end up in device objects.
  ;; 3 steps, not 2;
  ;; `import` :: takes in external data, convert to internal data, split `query` and `state` stores ?
  ;; `load` :: request asset data from IDB, every transaction is a promise, batch as much as possible
  ;; `init` :: creates the device objects from constructor data (might have lifetime issues, ie same temp stateful obj used in import)

  ;; dont want things like `Camera` to be separate load units, but still able to query indexed fields
  ;; - almost no choice but to store data twice; one optimal "blob" and one denormalized query index
  ;; - either that or lose the ability to answer "which of these 200 scenes contain more than one camera?"

  ;; `query` -> purely used to power queries, otherwise inefficient for batching
  ;; `state` -> minimal data to construct device objects, ideal for fast loading
  ;; - more or less same data in both cases -> except state might be an external URL (ie images, audio, video, etc -> defer to HTTP cache)
  ;;
  ;; ie, `query` can be used to answer "give me all meshes using material X"
  ;; but `state` is what loads the GPU buffer a mesh is in, and all the other meshes in that buffer
  ;; - still 2 objects? is there a cost to storing {:blob ... :info {}} vs one blob store and one info store?
  ;; - fairly low level, can be refactored without much impact on the system -> measure both! need big load first

  ;; `defstore` review (two variants: internal data, ie `view/Camera` and external, ie `gpu/Texture` or `audio/Buffer`)
  ;; - queriable fields ::
  ;; - construct layout
  )

;; TODO audio importers (wav, mp3, etc -> delegate to browser -> same as texture, only store blob if changed from original)
;; - these are the heavy files, along with textures -> let the browser's HTTP cache store them, use service worker for more control
;; - indexeddb then stores metadata, handles queries, and delegates to service worker to get the external blobs
;; - otherwise internal blobs are more than fine, and complement the HTTP cache for data created locally until a SW is implemented

;; blurs the line between load and import, again
;; may not support all formats, browser determines support here -> defer rest to external services (write these services too)
#_
(data/defimport media
  {:extensions []
   :media-types []})

(data/defstore SoundAtlas

  )

;; component state:
;; - mixer effect
;; - music stream
;; - sound source

;; system state:
;; - listener
;; - destination

#_
(ecs/defc Music
  stream MusicStream
  source :u32)

#_
(ecs/defc Sound
  clip   SoundBuffer ; store resource index
  panner :u32 ; index to `PannerNode` to sync world position with
  ;;volume :f32  <- dont store these here, not touched every frame -> just when these values change
  ;;loop   :bool
  )

#_
(ecs/defc Panner
  {:require [scene/LocalToWorld]} ; TODO mechanism to only run system if transforms were updated (small buckets -> easier put to sleep)
  _ js/PannerNode)

;; action components for change events
;; - deterministic input, batches between ECS phases, flushed at specific points

#_
(ecs/defevent play :target Sound)
