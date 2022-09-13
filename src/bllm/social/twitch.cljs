(ns bllm.social.twitch
  "Twitch integration supporting both IRC chat and web API."
  (:require [bllm.data :as data]
            [bllm.ecs  :as ecs]
            [bllm.gpu  :as gpu]
            [bllm.util :as util :refer [def1]]))

;; https://dev.twitch.tv/docs/api/

(set! *warn-on-infer* true)

;; TODO separate modules for FFZ and BLLM support -> supporter badges, mod badges, variable-size emotes

;; TODO persist known users, badges & emotes -> saves API & CDN roundtrips -> LRU cache with max life?
;; - original files caching handled by browser already, at HTTP layer; API responses will not, 2darray saves tons of requests

(def1 ^:private api "Authentication to the Protocols of the Internet" nil)

(def1 ^:private ^js/WebSocket irc "Live connection to the chat servers." nil)

;; dont connect on init, but on user demand
;; - store state and restore on init -> which MAY trigger connection if access-token is still valid

;; lots to cover
;; - polls, predictions, raids, clips, markers, mod actions, goals, extension reports, game reports
;; - eventsub, dont touch cli (do everything right here from the REPL)
;; - auth, oauth2 -> access token -> localstorage/sessionstorage -> periodic refresh
;; - organizations (longer term -> do last -> organization comes from experience, not planning)
;; - drops (ads for people who wouldnt their ads otherwise; modern version of carrot on a stick)
;; - embedding twitch (now we talking; yo we heard you like twitch, so we put twitch inside your twitch while you twitch on twitch)
;; - extensions (full power of whatever this is going to end up as, inside twitch; another method for the streamer to engage viewers)
;; - video broadcast -> GPU texture -> twitch stream inside a 3D world -> might need Twitch's help (CORS might be troublesome, for one)
;;   - next gen "picture in picture" is "livestreams in livestreams" 3D composited live on streamer's computers -> live audio/video coding

(comment
  (gpu/defres badges-map
    "Every 2D slice is a separate badge sprite.

  Usually displayed next to `User` identities."
    :group (gpu/texture-2d-array ))

  (gpu/defres emotes-map
    "Every 2D slice is a separate emote sprite.

  The 1x, 2x and 3x sizes are stored in the corresponding mip levels.

  Usually displayed inside `Message` entities."
    :group (gpu/texture-2d-array ))
  )

(data/defstore User
  "A unique Twitch identity. Identifies a person, an organization or a bot."
  )

(data/defstore Channel
  "A chat room associated with a live stream account."
  )

#_
(ecs/defc Badge "Badges displayed by this Entity."
  {:array 4}
  sprite :u16)

(comment
  (ecs/defc Joined "An active `Channel` receiving and sending `Message` events.")

  (ecs/defc Message "A message sent by an `User` to a `Channel` or another user.")

  ;; TODO event components -> one-frame use objects without allocations
  (ecs/defc Join "Request to join a chat `Channel`.")
  (ecs/defc Part "Request to part a chat `Channel`.")
  (ecs/defc Send "Request to send a chat `Message`."))
