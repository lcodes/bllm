(ns bllm.intl
  "Internationalization & Localization support."
  (:require [bllm.util :as util :refer [def1]]))

(def1 locale nil)

;; - used by `meta` data types flagged as `localized`

;; - user locale -> resources -> tables -> queries
;; - can always access every level directly (going up = more convenience, moving from root to leaf data)

;; TODO dont wait too long to plan this, can be unpleasant to retrofit into an existing design
;; - text tables first, relatively trivial (without going heavy on replacements)
;;   - embed fn calls -> turn string to function, just call it for transform, turing complete
;; - binary assets next, also trivial (use different assets entirely) -> need asset overloads
;;   - also digs into data packaging, but doesn't concern much else than the loader (for now)

;; TODO UI will need both these (localized labels & icons)

(defn init []
  (set! locale (if-let [lang (.-languages js/navigator)]
                 (aget lang 0)
                 js/navigator.language)))
