(ns repl.tool
  (:require [repl.ui :as ui]))

(set! *warn-on-infer* true)

;; various context-specific toolbox representations
;; - bound to a view pane, which has a type and a current asset (which also has a meta schema)
;; - editor pickers, terrain & doodad palettes, frequent assets workbench, etc

;; turned off by power users, essential to new users (otherwise its the emacs PLUS vim learning curves)
;; - power users are really developers; smooth transition from user to developer without changing envs

;; meaning visual buttons and selectors (and even XR radial selectors in 3D)
;; arent as fast as directly knowing what you want and getting there in a few characters or syllables
;; - "<spc> f f" gives me a find file dialog quicker than a tool bar or sub menu item; composable mnemonics > tool palettes

;; HOWEVER, with that being said

;; not everything can be directly mapped -> ie ending up with 200,000 sprites to select from
;; - then such a tools view can be mapped directly within a leader key command chain
;; - "<space> f a tex<tab> doom<tab>" (find asset -> texture -> demo_object_material.png)
;; - each step going through the tools view and filtering associated with its argument type
;; - <space> -> leader prefix
;; - f -> file commands
;; - a -> find asset
;; - tex<tab> -> highlight Texture in the selection popup and choose it
;; - doom<tab> -> same, listing all Textures in the object store, with fuzzy completion (also configurable)

;; just like VIM, get movements and objects;
;; - ie 3D movement can be a region within the scene, and every region-making method is valid (pick bounding box, drag view, group bounding boxes, etc)
;; - not limited to text as a context; everything is data with the means to understand it at hand

(ui/defview context
  []
  ;; similar to browser's address bar, but to select the current ECS scene instead of HTML document
  ;; - easily pull external scenes, get deeplink to share current scene, bookmark, etc
  [:div "CONTEXT HELP?"])

(ui/defview bookmark
  []
  ;; favorite deeplinks, from changing ECS scenes to dock panel views
  [:div "FAVORITES"])

(ui/defview mem-stats
  []
  [:div "MEM"])

(ui/defview cpu-stats
  []
  [:div "CPU"])

(ui/defview gpu-stats
  []
  [:div "GPU"])

;; TODO move these actions to another module.
(defn play-reverse [])
(defn play|pause [])
(defn stop [])
(defn record [])
(defn step-back [])
(defn step-forward [])
(defn goto-beginning [])
(defn goto-end [])
(defn pack [])
(defn options [])

(defn- btn [label click]
  [:li.icon [:button.tool-btn {:on-click click} label]])

(ui/defview engine-btns
  "What do we want? Faster horses! What is this car you speak of? What's a cdr?"
  []
  [:div.buttons
   [:ul.btns
    [btn "⏮" goto-beginning]
    [btn "⏪" step-back]
    [btn "◀" play-reverse]
    [btn "▶" play|pause] ; TODO "⏸" pause label dependent on engine state
    [btn "⏹" stop]
    [btn "⏺" record] ; TODO red when recording (also `recording-mode` as filter over UI)
    [btn "⏩" step-forward]
    [btn "⏭" goto-end]]])

(ui/defview editor-btns
  []
  [:ul.btns
   [btn "📦" pack]
   [btn "⚙" options]])

(ui/deframe bar
  {:elem :nav :layout :row :class "bg-secondary"}
  [ui/node bookmark]
  [ui/node context]
  ui/space
  [ui/node engine-btns]
  [ui/node editor-btns]
  ui/space
  [ui/node mem-stats]
  [ui/node cpu-stats]
  [ui/node gpu-stats])
