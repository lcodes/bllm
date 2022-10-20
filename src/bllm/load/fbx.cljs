(ns bllm.load.fbx
  (:require [bllm.data :as data]
            [bllm.ecs  :as ecs]
            [bllm.meta :as meta]))

;; https://docs.fileformat.com/3d/fbx/

(set! *warn-on-infer* true)

(meta/defstruct Header
  {:size 27}
  file-magic (:u8 21 "Kaydara FBX Binary  \u0000")
  unknown    [:u8 0x1a 0x00]
  version    :u32)

(meta/defstruct Node
  end-offset :u32
  num-props  :u32
  props-len  :u32
  name-len   :u8)
;; name
;; property 0 .. props-len

(meta/defstruct Prop
  type-code :u8
  data      :?) ; `Prop` wrapper has buffer ref, offset, & knows data is offset+1 -> blob extract fn
;; TODO can we encode (type-code -> size) in field spec? auto populate prop with bounded data view

(meta/defstruct Array
  length     :u32
  encoding   :u32
  compressed :u32
  contents   :?)

(meta/defstruct Data
  "Special data types include strings and raw binary data."
  length   :u32
  contents :?)

(data/defimport scene
  "Importer for the FilmBox file format."
  {:extension "fbx"
   :media-type "application/octet-stream"}
  [url data]
  ;; TODO check if text or binary format
  )
