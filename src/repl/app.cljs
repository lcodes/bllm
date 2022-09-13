(ns repl.app
  "Wires the initial user experience. The REPL is live, long live the REPL."
  (:require
   ;; 3rd-party
   [reagent.dom   :as dom]
   [re-frame.core :as rf]
   ;; Engine
   [bllm.core  :as core]
   [bllm.disp  :as disp]
   [bllm.ecs   :as ecs]
   [bllm.html  :as html]
   [bllm.util  :as util :refer [def1]]
   ;; Application
   [repl.dock  :as dock]
   [repl.demo  :as demo]
   [repl.error :as error]
   [repl.game  :as game]
   ;; Plugins
   [bllm.load.cube]
   [bllm.load.gltf]
   [bllm.load.image]))

(set! *warn-on-infer* true)

(def ^:private splash-anim-name
  "Class name for a CSS animation to entertain the user during initialization."
  "rotate")

(def1 ^:private ^js/HTMLElement main
  "Application container element. Defined in index.html."
  (js/document.querySelector "main"))

(def1 ^:private ^js/HTMLCanvasElement canvas
  "Interactive viewport; user input and gfx output."
  (js/document.createElement "canvas"))

(defn- tick
  "Consume inputs, simulate the next frame of reference, produce outputs."
  []
  (binding [ecs/*world* game/scene]
    (try
      (disp/request-frame tick) ; TODO frame skipping?
      (core/pre-tick)
      ;;(demo/pre-tick)
      (core/tick)
      ;;(demo/post-tick)
      (core/post-tick)
      (catch :default e
        (error/exceptional-pause e (util/callback tick))))))

(defn mount
  []
  (dom/render [dock/view] main))

(defn- start
  "Launch the simulation. All systems are initialized at this point."
  []
  (mount)
  (html/remove-class main splash-anim-name)
  ;;(html/replace-children main canvas)
  ;;(disp/add-viewport canvas js/devicePixelRatio)
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
