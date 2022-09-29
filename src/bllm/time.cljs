(ns bllm.time
  "Sandboxed time. Because of Spectre we're down to millisecond precision."
  (:require [bllm.util :as util :refer [def1]]))

(def1 frame-number 0)

(def1 unscaled-now   0)
(def1 unscaled-delta 0)

(defn pre-tick []
  (util/++ frame-number)
  (let [time (js/performance.now)]
    (set! unscaled-delta (- time unscaled-now))
    (set! unscaled-now time))
  ;; TODO derived, scaled time
  )

(defn start []
  (pre-tick)) ; Ensures the first frame doesn't have a large delta.

(defn zero!
  "Resets global time to absolute zero."
  []
  (set! unscaled-now 0)
  (set! unscaled-delta 0)
  ;; TODO
  )
