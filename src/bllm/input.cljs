(ns bllm.input
  "Support for HTML5 keyboard, mouse and gamepad.")

(set! *warn-on-infer* true)

(defn init []
  )

(defn pre-tick []
  ;; Collect gamepad state; marshal into form more conveniently consumed during frame
  ;; - keep sequences of events (dont swallow 'press -> release' between slow frames)
  ;; - feed into deterministic frame replay buffer
  )

(defn post-tick []
  ;; Reset/swap state accums; input events trigger between animation frame requests
  )

(defn attach [target]
  )

(defn detach [target]
  )
