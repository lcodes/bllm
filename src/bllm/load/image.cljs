(ns bllm.load.image
  (:require [bllm.data :as data]))

(defn import*
  "Loads the `png` or `jpg` image associated with one or more `glTF` texture.

  TODO: first pass loads raw data directly, if format supported by browser
  - wont scale without BC7 encoders and the likes
  - BUT those are also GPU driven now, and open source.. hmm
    - translate them into system shaders here, store binary in IndexedDB -> available from service worker then
    - licensing issues to check, copyright notices to add, people to thank -> multiple implementations to pick from too
    - also troublesome if browser adds compute time limits; dont want long running shaders on slower mobile GPUs
      - see if iterations can be decoupled across progressive passes -> better approach, spread workload over frames
      - everything will load progressively anyways; unless it comes in prepackaged scene-sized ready-to-wear blobs
      - ECS needs to be designed alongside this loader, and the renderer -> or rather, just-in-time to support them
      - the dynamic structure of materials is entity components; batching from the systems -> leaves of strange loops

  TODO: also add service worker
  TODO: basis good idea to support too (wasm worker trivial to integrate), but:
        - already know the specific formats supported
        - either get them directly (no time spent converting; naive load to GPU)
        - or create them durably (same effect)
        - basis then a convenience to consume external *.basis files -> GPU ultimately needs its native compressed formats
        - producing archives for every target format at cloud scale is trivial to solve; no basis needed either -> specialized packs -> less CPU
        - unless basis produces smaller output than compressed+gzip would, then bandwidth savings worthwhile

  TODO: thats also a lot of formats to add encoders for, even if they all already exists
        - BC7 encoder might be fastest for live coding right now, dev is on win64+RTX2070
        - no need for external tools (even basis needs offline tools to create its inputs)
        - can just load a new texture at the REPL and have it in optimal format right away
  "
  [info]
  )

(data/defimport image
  {:extensions ["jpg" "jpeg" "png"]
   :mime-types ["image/jpeg"
                "image/png"]}
  [url]
  ;; no fetch, load through <image>
  )

;; TODO consume https://github.com/KhronosGroup/KTX-Software
