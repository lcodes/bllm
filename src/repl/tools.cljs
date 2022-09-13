(ns repl.tools)

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
