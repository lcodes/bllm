(ns repl.app
  "Wires the initial user experience. The REPL is live, long live the REPL."
  (:require
   ;; 3rd-party
   [reagent.dom   :as dom]
   [re-frame.core :as rf]
   ;; Engine
   [bllm.core :as core]
   [bllm.disp :as disp]
   [bllm.ecs  :as ecs]
   [bllm.html :as html]
   [bllm.util :as util :refer [def1]]
   ;; Application
   [repl.dock :as dock]
   [repl.demo :as demo]
   [repl.halt :as halt]
   [repl.game :as game]
   [repl.menu :as menu]
   [repl.mini :as mini]
   [repl.nav  :as nav]
   [repl.tool :as tool]
   [repl.ui   :as ui]
   ;; Engine Plugins
   [bllm.load.cube]
   [bllm.load.gltf]
   [bllm.load.image]
   ;; Application Plugins
   [repl.browse]
   [repl.inspect]
   [repl.preview]
   [repl.project]))

(set! *warn-on-infer* true)

(def1 ^:private ^js/HTMLElement main
  "Application container element. Defined in index.html."
  (js/document.querySelector "main"))

(defn- tick
  "Consume inputs, simulate the next frame of reference, produce outputs."
  []
  (binding [ecs/*world* game/scene]
    (try
      (disp/frame tick) ; TODO frame skipping?
      (core/pre-tick)
      ;;(demo/pre-tick)
      (core/tick)
      ;;(demo/post-tick)
      (core/post-tick)
      (catch :default e
        (halt/exceptional-pause e (util/callback tick))))))

(ui/deframe window
  "Root view of the UI component tree. Covers the full client area of `main`."
  []
  menu/bar
  tool/bar
  dock/bar
  mini/bar)

(defn mount
  "Entry point for the UI component tree. Called after figwheel loads new code."
  []
  #_(dom/render [window] main))

(defn- start
  "Launch the simulation. All systems are initialized at this point."
  []
  (mount)
  (html/remove-class main "boot")
  (html/remove-class main "rotate")
  (core/start)
  (demo/scene)
  (disp/frame tick))

(defn- pre-init
  "Early initialization, before systems are initialized. Don't waste time here."
  []
  (halt/init)
  (html/add-class main "rotate"))

(defn- post-init
  "Late initialization performed while systems are initializing asynchronously."
  []
  (ui/init main)
  (nav/init)
  (let [noscript (js/document.querySelector "noscript")]
    (.removeChild (html/parent noscript) noscript)))

(defn init
  "Completely initializes all of the engine and application systems."
  [custom-init]
  (pre-init)
  (let [engine (core/init)]
    (post-init)
    (custom-init)
    (.then engine start)))

(defn -main
  "Entry point used in production builds."
  []
  (init identity))
