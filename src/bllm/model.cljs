(ns bllm.model
  ""
  (:require [bllm.cull :as cull]
            [bllm.data :as data]
            [bllm.ecs  :as ecs]
            [bllm.gpu  :as gpu]
            [bllm.skin :as skin]
            [bllm.wgsl :as wgsl]))

;; TODO pool GPU buffers to store meshes in (ie alloc 4mb pages & upload mesh buffers there)

;; TODO mesh asset -> prepared batches

;; TODO cull results -> packed batches

;; TODO pass -> draw calls

(data/defstore Mesh
;; - list of materials -> uniform data, GPU resource bindings
;; - list of primitives -> vertex data, material bindings
  )

;; Mesh NODE
;; - "baked" to quickly emit efficient draw calls
;;   - draw (for `draw`, `drawIndexed` or pushing in indirect buffer)
;;   - batch ID (contains resources to bind)
;;   - effect ID (index of material inside batch)

(ecs/defc StaticMesh
  :require [cull/Bounds scene/LocalToWorld]
  ;; TODO rough design, better packing
  [batch :u32]  ; Index to GPU resources (vertex, index, uniform) shared by multiple meshes
  [index :u32]) ; Index of this mesh (draw params, pass node)

(ecs/defc SkinnedMesh
  :require [cull/Bounds scene/LocalToWorld skin/Pose]
  [batch :u32])
