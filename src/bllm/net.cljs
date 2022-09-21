(ns bllm.net
  (:require [bllm.cli  :as cli]
            [bllm.ecs  :as ecs]
            [bllm.meta :as meta]
            [bllm.util :as util :refer [def1]]))

;; https://developer.mozilla.org/en-US/docs/Web/API/WebRTC_API
;; https://developer.mozilla.org/en-US/docs/Web/API/Media_Capture_and_Streams_API

;; TODO development connection servers

(set! *warn-on-infer* true)

(cli/defgroup config)


;;; Collaborative Grid
;;; ----------------------------------------------------------------------------

(def1 ^:private ^js/RTCPeerConnection host  nil)
(def1 ^:private ^js/RTCPeerConnection peers (js/Map.))

;; collection of `RTCPeerConnection`s
;; - audio/video streams
;; - more importantly, data streams
;;   - live REPL is nice, live collaboration is nicer
;;   - simulating is nice, communicating is nicer
;;   - doesn't tell users how it's meant to be played
;; - maintain meta/schema mappings between hosts

(cli/defcmd connect []
  ;; open a connection to a listening peer
  )

(cli/defcmd disconnect []
  ;; disconnect from a peer
  )

(cli/defcmd listen []
  ;; accept connections from remote peers
  )

(cli/defcmd close []
  ;; stop accepting connections, disconnect peers
  )

(defn pre-tick []
  ;; collect inputs from peers into the current `ecs/*world*`
  ;; send any inputs present in the world this frame to the host
  )

(defn post-tick []
  ;; compose and send frame packet to peers
  )
