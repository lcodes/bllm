(ns repl.home
  (:require [repl.ui :as ui]))

;; TODO press F to pay respects (oh no, why do I think of a `meme-mode`)
;; [:img {:width 480  :height 360 :src "https://preview.redd.it/dys3wj2z9ue61.jpg?auto=webp&s=511078554dbb75c73fb0e791d4d1802b43db9801"}]

(ui/defview summary
  []
  [:div.summary.content.align-center
   [:h2 "The REPL"]
   [:p.lead "Version 0.1.prototype"]
   [:div.btn-group
    [:button "Demo Scene"]
    [:button "Examples"]
    [:button "Documentation"]
    [:button "GitHub"]]])

(ui/defview welcome
  []
  [:div.welcome.content.grow
   [:h2 "Hello, World!"]
   [:p.lead "The REPL is a simulation engine in the browser."]
   [:ul
    [:li "A portable operating system for the upcoming web."]
    [:li "Live coded in a programmable programming language."]
    [:li "Unique design merging Emacs, Vim and game engines."]
    [:li "Powered by WebGPU, WebAudio, WebRTC, IndexedDB."]]
   [:button {:data-cmd "repl.dock/split"} "Try Me"]])
