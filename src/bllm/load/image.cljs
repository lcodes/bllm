(ns bllm.load.image
  (:require [bllm.data :as data]
            [bllm.gpu  :as gpu]
            [bllm.meta :as meta]
            [bllm.util :as util]))

;; Images and Meshes might be the two main file types where deferring work to external libraries/processes is worth it.
;; - fully encoding compressed formats is outside the (initial, and v2, and possibly v3) scopes of this library
;; - consuming them, however, is always worthwhile; hard to scale performance without, profiling worth less if inputs arent optimal
;; - can generate inputs to anything from here, at least (native structs for IO, tools requests as part of import pipeline)
;; - so long as the (source -> import -> asset) process is respected, importers are free to do anything they want.

;; NOTE that also means cancellable requests; promise alone isnt enough, cancellation tokens is too.. OOP

;; Import vs Load, Save vs Export
;; ------------------------------
;; `import` is the process of converting external sources into internal assets for `load` to consume.
;; `export` is the process of converting internal assets into external sources, for `save` to produce.
;;
;;                    ECS World
;;                     `save`
;; Real                                       Real
;; World -> `import` -> App Db -> `export` -> World
;;
;;                     `load`
;;                    ECS World

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

(data/defimport element
  {:extensions ["bmp" "gif" "jpg" "jpeg" "png"]
   :media-types ["image/jpeg"
                 "image/png"]}
  [src]
  ;; few options here
  ;; - blob url -> gotta pull the data and save Blob, otherwise its lost
  ;; - standard url -> just save the file description, loading from db will then load from URL
  ;;
  ;; in both cases, we're not doing this here, but when loading it to GPU later on (when asset is opened; right now is being imported)
  ;; - next importer version will match a texture preset, and compress the texture in its final format (even if it needs to be deferred)
  (let [img (js/document.createElement "img")]
    (set! (.-src img) src.url)
    (-> (.decode img)
        (.then #(js/createImageBitmap img))
        (.then #(js/console.log %)))))

(data/defimport tga
  {:extensions ["tga"]}
  [src data]
  ;; https://github.com/toji/webgl-texture-utils/blob/master/texture-util/tga.js
  )

;; TODO TIF/EXR/PSD

;; https://docs.microsoft.com/en-us/windows/win32/direct3ddds/dx-graphics-dds-pguide

(meta/defenum DXGI_FORMAT
  DXGI-FORMAT-UNKNOWN
  R32G32B32A32_TYPELESS
  R32G32B32A32_FLOAT
  R32G32B32A32_UINT
  R32G32B32A32_SINT
  R32G32B32_TYPELESS
  R32G32B32_FLOAT
  R32G32B32_UINT
  R32G32B32_SINT
  R16G16B16A16_TYPELESS
  R16G16B16A16_FLOAT
  R16G16B16A16_UNORM
  R16G16B16A16_UINT
  R16G16B16A16_SNORM
  R16G16B16A16_SINT
  R32G32_TYPELESS
  R32G32_FLOAT
  R32G32_UINT
  R32G32_SINT
  R32G8X24_TYPELESS
  D32_FLOAT_S8X24_UINT
  R32_FLOAT_X8X24_TYPELESS
  X32_TYPELESS_G8X24_UINT
  R10G10B10A2_TYPELESS
  R10G10B10A2_UNORM
  R10G10B10A2_UINT
  R11G11B10_FLOAT
  R8G8B8A8_TYPELESS
  R8G8B8A8_UNORM
  R8G8B8A8_UNORM_SRGB
  R8G8B8A8_UINT
  R8G8B8A8_SNORM
  R8G8B8A8_SINT
  R16G16_TYPELESS
  R16G16_FLOAT
  R16G16_UNORM
  R16G16_UINT
  R16G16_SNORM
  R16G16_SINT
  R32_TYPELESS
  D32_FLOAT
  R32_FLOAT
  R32_UINT
  R32_SINT
  R24G8_TYPELESS
  D24_UNORM_S8_UINT
  R24_UNORM_X8_TYPELESS
  X24_TYPELESS_G8_UINT
  R8G8_TYPELESS
  R8G8_UNORM
  R8G8_UINT
  R8G8_SNORM
  R8G8_SINT
  R16_TYPELESS
  R16_FLOAT
  D16_UNORM
  R16_UNORM
  R16_UINT
  R16_SNORM
  R16_SINT
  R8_TYPELESS
  R8_UNORM
  R8_UINT
  R8_SNORM
  R8_SINT
  A8_UNORM
  R1_UNORM
  R9G9B9E5_SHAREDEXP
  R8G8_B8G8_UNORM
  G8R8_G8B8_UNORM
  BC1_TYPELESS
  BC1_UNORM
  BC1_UNORM_SRGB
  BC2_TYPELESS
  BC2_UNORM
  BC2_UNORM_SRGB
  BC3_TYPELESS
  BC3_UNORM
  BC3_UNORM_SRGB
  BC4_TYPELESS
  BC4_UNORM
  BC4_SNORM
  BC5_TYPELESS
  BC5_UNORM
  BC5_SNORM
  B5G6R5_UNORM
  B5G5R5A1_UNORM
  B8G8R8A8_UNORM
  B8G8R8X8_UNORM
  R10G10B10_XR_BIAS_A2_UNORM
  B8G8R8A8_TYPELESS
  B8G8R8A8_UNORM_SRGB
  B8G8R8X8_TYPELESS
  B8G8R8X8_UNORM_SRGB
  BC6H_TYPELESS
  BC6H_UF16
  BC6H_SF16
  BC7_TYPELESS
  BC7_UNORM
  BC7_UNORM_SRGB
  AYUV
  Y410
  Y416
  NV12
  P010
  P016
  _420_OPAQUE
  YUY2
  Y210
  Y216
  NV11
  AI44
  IA44
  P8
  A8P8
  B4G4R4A4_UNORM
  #_P208 #_130
  #_V208
  #_V408)

(meta/defenum D3D10_RESOURCE_DIMENSION
  dimension-unknown
  buffer
  texture-1d
  texture-2d
  texture-3d)

(util/defconst DDS 0x20534444) ; "DDS "

(meta/defflag DDPF
  ALPHAPIXELS 0x1
  ALPHA       0x2
  FOURCC      0x4
  RGB         0x40
  YUV         0x200
  LUMINANCE   0x20000)

(meta/defstruct DDS_PIXELFORMAT
  size          :u32
  flags         :u32
  fourcc        :u32
  rgb-bit-count :u32
  r-bit-mask    :u32
  g-bit-mask    :u32
  b-bit-mask    :u32
  a-bit-mask    :u32)

#_
(meta/defstruct DDS_HEADER ; singleton instance, wrapper object; (usage: DDS_Header.set(array, offset), DDS_Header.width -> 512)
  size         :u32
  flags        :u32
  height       :u32
  pitch        :u32
  depth        :u32
  mipmap-count :u32
  reserved-1   [:u32 11]
  pixel-format DDS_PIXELFORMAT
  caps         :u32
  caps-2       :u32
  caps-3       :u32
  caps-4       :u32
  reserved-2   :u32)

#_
(meta/defstruct DDS_HEADER_DXT10
  dxgi-format  ::DXGI_FORMAT
  dimension    ::D3D10_RESOURCE_DIMENSION
  misc-flags   :u32
  array-size   :u32
  misc-flags-2 :u32)

(data/defimport dds
  {:extensions ["dds"]
   :media-types []}
  [src data]
  (let [u32 (js/Uint32Array. data)]
    ;; check min size (4 + dds_header.sizeof)
    ;; check signature
    ;; ()
    (js/console.log u32)))

(comment (data/import "https://github.com/toji/webgl-texture-utils/blob/master/sample/img/test-dxt5.dds"))

;; https://github.com/BinomialLLC/crunch
(data/defimport crn
  {:extensions ["crn"]
   :media-types []}
  [src data]
  )

(data/defimport basis
  {:extensions ["basis"]
   :media-types []}
  [src data]
  )

(meta/defstruct KtxHeader
  endianness               :u32
  gl-type                  :u32
  gl-type-size             :u32
  gl-format                :u32
  gl-internal-format       :u32
  gl-base-internal-format  :u32
  pixel-width              :u32
  pixel-height             :u32
  pixel-depth              :u32
  number-of-array-elements :u32
  number-of-faces          :u32
  number-of-mipmap-levels  :u32
  bytes-of-key-value-data  :u32)

(meta/defenum ktx-signature
  ktx-0 0x58544BAB
  ktx-1 0xBB313120
  ktx-2 0x0A1A0A0D
  ktx-endian 0x04030201)

;; https://registry.khronos.org/KTX/specs/1.0/ktxspec_v1.html
(data/defimport ktx
  {:extensions ["ktx"]
   :media-types []}
  [src data]
  (let [u32 (js/Uint32Array. data)]
    (js/console.log u32)
    ))

;; https://github.khronos.org/KTX-Specification/
(data/defimport ktx2
  {:extensions ["ktx2"]}
  [src data]
  )

;; first version directly imports the <image>
;; - what to store in that case? there is no "imported" version at this point; the HTTP cache is effectively the same data, no need saving that

;; start from load pipeline then:
;; - something refers to a texture file (material, spritesheet, direct ref)
;; - `Texture` store request (at minimum; map its ID to the request's promise -> prevent duplicate loads)
;; - dispatch load path based on asset data; could be `Blob`->compressed, `Blob`->image, or `URL`->fetch->dispatch
;; - `GPUTexture` creation & upload, assoc to asset's refcount

;; Speed things up? Lots of texture requests means lots of small asset requests to IDB -> all of these async, meaning they quickly spread over frames
;; - ideally want an in-memory index (load on boot, maintain afterwards; shared worker & webrtc will help towards that)
;;   - hmm, that still implies multiple live indices if multiple tabs opened
;;   - moving index to shared worker is same as querying idb directly, in terms of async
;;   - can leverage simulation again, dont need to send query the moment it is created -> create during frame, batch at the end
;;   - but IDB doesnt support that; its "query one" or "scan index", with no inbetween (which would just be convenience over these anyways)
