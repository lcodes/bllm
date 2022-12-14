(ns repl.app
  "Wires the initial user experience. The REPL is live, long live the REPL."
  (:require
   ;; 3rd-party
   [reagent.dom :as dom]
   ;; Engine
   [bllm.core :as core]
   [bllm.disp :as disp]
   [bllm.ecs  :as ecs]
   [bllm.html :as html]
   [bllm.util :as util :refer [def1]]
   ;; Editor
   [repl.cmd  :as cmd]
   [repl.dock :as dock]
   [repl.halt :as halt]
   [repl.game :as game]
   [repl.menu :as menu]
   [repl.mini :as mini]
   [repl.tool :as tool]
   [repl.ui   :as ui]
   ;; Engine Plugins
   [bllm.load.cube]
   [bllm.load.gltf]
   [bllm.load.image]
   ;; Editor Plugins
   [repl.browse]
   [repl.inspect]
   [repl.preview]
   [repl.project]
   [repl.tty]))

(set! *warn-on-infer* true)

(def1 ^:private ^js/HTMLElement main
  "Application container element. Defined in resources/public/index.html."
  (js/document.querySelector "main"))

(defn- tick
  "Consume inputs, simulate the next frame of reference, produce outputs."
  []
  (binding [ecs/*world* game/scene]
    (try
      ;; TODO move full tick to core -> hook ECS systems to extend
      (disp/frame tick) ; TODO frame skipping?
      (core/pre-tick)
      (core/tick)
      (core/post-tick)
      (catch :default e
        (halt/exceptional-pause e (util/cb tick))))))

(ui/deframe ^:static window
  "Root view of the UI component tree. Covers the full client area of `main`."
  {:class "window grow"}
  [ui/node menu/bar]
  ;;[ui/node tool/bar]
  [ui/node dock/ing]
  [ui/node mini/bar])

(defn mount
  "Entry point for the UI component tree. Called after figwheel loads new code."
  []
  (dom/render [ui/node window] main))

(defn- start
  "Launch the simulation. All systems are initialized at this point."
  []
  (mount)
  (html/remove-class main "boot")
  (html/remove-class main "init")
  (core/start)
  (disp/frame tick))

(defn- pre-init
  "Early initialization, before systems are initialized. Don't waste time here."
  []
  (halt/init)
  (html/add-class main "init"))

(defn- post-init
  "Late initialization performed while systems are initializing asynchronously."
  []
  (ui/init)
  (cmd/init)
  (let [noscript (js/document.querySelector "noscript")]
    (.removeChild (html/parent noscript) noscript)))

(defn init
  "Completely initializes all of the engine & editor systems and their plugins."
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
