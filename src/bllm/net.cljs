(ns bllm.net
  (:require [bllm.meta :as meta]
            [bllm.ecs  :as ecs]
            [bllm.util :as util :refer [def1]]))

;; TODO development connection servers

(set! *warn-on-infer* true)


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

(defn connect []
  ;; open a connection to a listening peer
  )

(defn disconnect []
  ;; disconnect from a peer
  )

(defn listen []
  ;; accept connections from remote peers
  )

(defn close []
  ;; stop accepting connections, disconnect peers
  )

(defn pre-tick []
  ;; collect inputs from peers into the current `ecs/*world*`
  ;; send any inputs present in the world this frame to the host
  )

(defn post-tick []
  ;; compose and send frame packet to peers
  )
