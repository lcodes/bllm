(ns repl.app
  "Wires the high-level user application."
  (:require [bllm.core  :as core]
            [bllm.disp  :as disp]
            [bllm.html  :as html]
            [bllm.util  :as util :refer [def1]]
            [repl.asset :as asset]
            [repl.demo  :as demo]
            [repl.error :as error]))

(set! *warn-on-infer* true)

(def ^:private splash-anim-name
  "Class name for a CSS animation to entertain the user during initialization."
  "rotate")

(def1 ^js/HTMLElement main
  "Application container element. Defined in index.html."
  (js/document.querySelector "main"))

(def1 ^js/HTMLCanvasElement canvas
  "Interactive viewport; user input and gfx output."
  (js/document.createElement "canvas"))

(defn- tick
  "Consume inputs, simulate the next frame of reference, produce outputs."
  []
  (disp/request-frame tick) ; TODO frame skipping?
  (try
    (core/pre)
    (demo/pre)
    (core/tick)
    (demo/post)
    (core/post)
    (catch :default e
      (error/exceptional-pause e (util/callback tick)))))

(defn- start
  "Launch the simulation. All systems are initialized at this point."
  []
  (html/remove-class main splash-anim-name)
  (html/replace-children main canvas)
  (disp/add-viewport canvas js/devicePixelRatio)
  (disp/request-frame tick)
  (core/start)
  (demo/scene))

(defn- pre-init
  "Early initialization, before systems. Don't waste any time here."
  []
  (error/init)
  (html/add-class main splash-anim-name))

(defn- post-init
  "Late initialization, after systems."
  []
  (let [noscript (js/document.querySelector "noscript")]
    (.removeChild (html/parent noscript) noscript)))

(defn init
  "Completely initializes all sub-systems."
  [init-fn]
  (pre-init)
  (let [engine (core/init)]
    (init-fn)
    (post-init)
    (.then engine start)))

(defn -main
  "Entry point used in production builds."
  []
  (init identity))
