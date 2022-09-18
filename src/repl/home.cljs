(ns repl.home
  (:require [repl.ui :as ui]))

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
    [:li "Powered by WebGPU, WebAudio, WebRTC, IndexedDB."]]])
