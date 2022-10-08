(ns ^:figwheel-hooks cljs.user
  "Development entry point. Attaches a few strings before launching the app."
  (:require [npm.stats.js  :as Stats]
            [devtools.core :as devtools]
            [re-frame.core :as rf]
            [bllm.util     :as util :refer [def1]]
            [repl.app      :as app]
            [repl.halt     :as halt]
            [game.demo     :as demo]))

(defn upsert-html-node
  "Locate the matching 'tag#id' HTML element, or create it."
  [tag id]
  (if-let [elem (js/document.querySelector (str tag "#" id))]
    elem
    (let [elem (js/document.createElement tag)]
      (set! (.-id elem) id)
      (js/document.body.appendChild elem)
      elem)))

(defn fix-scripts
  "Development expands the one <script> into hundreds. Clean up the inspector."
  []
  (let [container (upsert-html-node "div" "scripts")]
    (util/dolist [script (js/document.querySelectorAll "script")]
      (.appendChild container script))))

;; Launch the application right away. TODO setup dev plugins
(def1 app-ctx
  (do #_(devtools/install! [:formatters :hints]) ; TODO chrome devtools only
      (app/init fix-scripts)
      :ok))

;; TODO hook Statsjs into the tick loop

(defn ^:before-load on-before-load []
  (rf/clear-subscription-cache!))

(defn ^:after-load on-after-load []
  (app/mount)
  (halt/exceptional-resume))
