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


;;; Signaling Service
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 ^js/WebSocket api nil)

;; Basic API
;; - announce hosts -> fairly close to matchmaking on server side
;; - list available -> same
;; - add connection -> tell host to create a RTC connection
;; - administration -> kick, ban, edit info, etc -> mostly CRUD
;; - user services  -> storage, settings, contacts, assets, etc


;;; Data Channels
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- on-data-open []
  )

(defn- on-data-close []
  )

(defn- on-data []
  ;; conn -> associated ECS world -> queue frame data -> batch process in system
  ;; systems also produce data to batch send at network tick frequency
  )


;;; Peer Connections
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def1 ^:private ^js/RTCPeerConnection host  nil)
(def1 ^:private ^js/RTCPeerConnection peers (js/Array.))

(defn- check-disconnected []
  (when host (throw "Already connected.")))

;; collection of `RTCPeerConnection`s
;; - audio/video streams
;; - more importantly, data streams
;;   - live REPL is nice, live collaboration is nicer
;;   - simulating is nice, communicating is nicer
;;   - doesn't tell users how it's meant to be played
;; - maintain meta/schema mappings between hosts

(defn- on-ice-candidate []
  )

(defn- on-ice-state-change []
  )

(cli/defcmd connect []
  (check-disconnected)
  (let [conn (js/RTCPeerConnection. nil)
        data (.createDataChannel conn "send" #js {:ordered false})]
    #_(.createOffer host ) ;
    ))

(cli/defcmd disconnect []
  ;; disconnect from a peer
  )

(cli/defcmd listen []
  (check-disconnected)
  (set! host (js/RTCPeerConnection. nil))
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
