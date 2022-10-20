(ns bllm.load.gltf
  (:refer-clojure :exclude [byte double float int short])
  (:require [bllm.data  :as data]
            [bllm.ecs   :as ecs]
            [bllm.meta  :as meta]
            [bllm.scene :as scene]
            [bllm.util  :as util]
            [bllm.view  :as view]))

;; https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html
;; https://github.com/KhronosGroup/glTF/tree/main/specification/1.0

;; TODO https://github.com/google/draco#javascript-decoder-api

(set! *warn-on-infer* true)

(meta/defenum gl
  byte                   0x1400
  unsigned-byte          0x1401
  short                  0x1402
  unsigned-short         0x1403
  int                    0x1404
  unsigned-int           0x1405
  float                  0x1406
  double                 0x140A
  half-float             0x140B
  nearest                0x2600
  linear                 0x2601
  nearest-mipmap-nearest 0x2700
  linear-mipmap-nearest  0x2701
  nearest-mipmap-linear  0x2702
  linear-mipmap-linear   0x2703
  array-buffer           0x8892
  element-array-buffer   0x8893)

(defn- finalize [contents]
  (js/console.log contents))

(defn- load-all [task offset uris base-url]
  (when uris
    (util/doarray [desc n uris]
      (aset task (+ offset n) (data/import desc.uri)))))

(data/defimport asset
  "The main asset of glTF, whether loaded directly or embedded in a `glb` file.

  Contains references to other files, namely buffers and images. Importing such
  an `asset` effectively creates a bunch of interlinked assets in the database.

  These entries can be divided in two categories:
  - system resources provide content to their respective application domain.
  - entity components fill the simulation world and batch update each frame.

  System resources are scenes, meshes, materials, skins and animations.

  Entity components contain the models, lights, cameras, world transforms and
  parent hierarchies. Stored inside a scene, which is also a system resource."
  {:extension "gltf"
   :media-type "model/gltf+json"}
  [url json]
  (let [info json.asset]
    (if (not= "2.0" info.version)
      (js/Promise.reject "Unsupported glTF (version 1.0)") ;; TODO (non-PBR, still useful for meshes, scenes, basic materials)
      (let [bufs json.buffers
            imgs json.images
            num  (count bufs)
            task (aget util/arrays-a (+ 1 num (count imgs)))]
        (load-all task 1         bufs url) ;; TODO can be embedded base64, or reference to glb chunk
        (load-all task (+ 1 num) imgs url) ;; TODO hint with material usage -> match import preset
        (aset task 0 json) ; TODO compute as much as possible while resources are downloading
        (-> task js/Promise.all (.then finalize))))))

(data/defimport buffer
  "Loads the `bin` associated with a `glTF` asset. Contains, well, binary data:

  - Geometry: vertices and indices
  - Animation: key-frames
  - Skins: inverse-bind matrices"
  {:extensions ["bin" "glbin" "glbuf"]
   :media-types ["application/octet-stream"
                 "application/gltf-buffer"]}
  [url data]
  ;; TODO passed hints to locate packing preset
  ;; - pack & interleave vertex components
  ;; - extract skeleton & skin
  ;; - prepare animation timelines?
  data)

(comment
  (meta/defstruct Header
    [magic   :u32]
    [version :u32]
    [length  :u32])

  (meta/defstruct Chunk
    [length :u32]
    [type   :u32]
    [data   [:u32]]))

(meta/defenum chunk-type
  {:repr :fourcc}
  JSON
  BIN)

(data/defimport binary
  "Single file download containing the `glTF` and the `bin`, `jpg` and `png` files
  it references. Stored in chunks instead of base64 encoding to save storage space."
  {:extension "glb"
   :media-type "model/gltf-binary"}
  [url data]
  (js/console.log data)
  )

(comment with thanks to "https://github.com/KhronosGroup/glTF-Sample-Models"
  ;; Minimal Tests
  (data/import "glTF-Sample-Models/2.0/TriangleWithoutIndices/glTF/TriangleWithoutIndices.gltf")
  (data/import "glTF-Sample-Models/2.0/Triangle/glTF/Triangle.gltf")
  (data/import "glTF-Sample-Models/2.0/AnimatedTriangle/glTF/AnimatedTriangle.gltf")
  (data/import "glTF-Sample-Models/2.0/AnimatedMorphCube/glTF/AnimatedMorphCube.gltf")
  (data/import "glTF-Sample-Models/2.0/AnimatedMorphSphere/glTF/AnimatedMorphSphere.gltf")
  (data/import "glTF-Sample-Models/2.0/SimpleMeshes/glTF/SimpleMeshes.gltf")
  (data/import "glTF-Sample-Models/2.0/SimpleMorph/glTF/SimpleMorph.gltf")
  (data/import "glTF-Sample-Models/2.0/SimpleSparseAccessor/glTF/SimpleSparseAccessor.gltf")
  (data/import "glTF-Sample-Models/2.0/SimpleSkin/glTF/SimpleSkin.gltf")
  (data/import "glTF-Sample-Models/2.0/Cameras/glTF/Cameras.gltf")
  (data/import "glTF-Sample-Models/2.0/InterpolationTest/glTF/InterpolationTest.gltf")
  (data/import "glTF-Sample-Models/2.0/Unicode❤♻Test/glTF/Unicode❤♻Test.gltf")
  ;; Feature Tests
  (data/import "glTF-Sample-Models/2.0/AlphaBlendModeTest/glTF/AlphaBlendModeTest.gltf")
  (data/import "glTF-Sample-Models/2.0/BoomBoxWithAxes/glTF/BoomBoxWithAxes.gltf")
  (data/import "glTF-Sample-Models/2.0/MetalRoughSpheres/glTF/MetalRoughSpheres.gltf")
  (data/import "glTF-Sample-Models/2.0/MetalRoughSpheresNoTextures/glTF/MetalRoughSpheresNoTextures.gltf")
  (data/import "glTF-Sample-Models/2.0/MorphPrimitivesTest/glTF/MorphPrimitivesTest.gltf")
  (data/import "glTF-Sample-Models/2.0/MultiUVTest/glTF/MultiUVTest.gltf")
  (data/import "glTF-Sample-Models/2.0/NormalTangentTest/glTF/NormalTangentTest.gltf")
  (data/import "glTF-Sample-Models/2.0/NormalTangentMirrorTest/glTF/NormalTangentMirrorTest.gltf")
  (data/import "glTF-Sample-Models/2.0/OrientationTest/glTF/OrientationTest.gltf")
  (data/import "glTF-Sample-Models/2.0/RecursiveSkeletons/glTF/RecursiveSkeletons.gltf")
  (data/import "glTF-Sample-Models/2.0/TextureCoordinateTest/glTF/TextureCoordinateTest.gltf")
  (data/import "glTF-Sample-Models/2.0/TextureLinearInterpolationTest/glTF/TextureLinearInterpolationTest.gltf")
  (data/import "glTF-Sample-Models/2.0/TextureSettingsTest/glTF/TextureSettingsTest.gltf")
  (data/import "glTF-Sample-Models/2.0/VertexColorTest/glTF/VertexColorTest.gltf")
  ;; Standard
  (data/import "glTF-Sample-Models/2.0/Box/glTF/Box.gltf")
  (data/import "glTF-Sample-Models/2.0/BoxInterleaved/glTF/BoxInterleaved.gltf")
  (data/import "glTF-Sample-Models/2.0/BoxTextured/glTF/BoxTextured.gltf")
  (data/import "glTF-Sample-Models/2.0/BoxTexturedNonPowerOfTwo/glTF/BoxTexturedNonPowerOfTwo.gltf")
  (data/import "glTF-Sample-Models/2.0/BoxVertexColors/glTF/BoxVertexColors.gltf")
  (data/import "glTF-Sample-Models/2.0/Cube/glTF/Cube.gltf")
  (data/import "glTF-Sample-Models/2.0/AnimatedCube/glTF/AnimatedCube.gltf")
  (data/import "glTF-Sample-Models/2.0/Duck/glTF/Duck.gltf")
  (data/import "glTF-Sample-Models/2.0/2CylinderEngine/glTF/2CylinderEngine.gltf")
  (data/import "glTF-Sample-Models/2.0/ReciprocatingSaw/glTF/ReciprocatingSaw.gltf")
  (data/import "glTF-Sample-Models/2.0/GearboxAssy/glTF/GearboxAssy.gltf")
  (data/import "glTF-Sample-Models/2.0/Buggy/glTF/Buggy.gltf")
  (data/import "glTF-Sample-Models/2.0/BoxAnimated/glTF/BoxAnimated.gltf")
  (data/import "glTF-Sample-Models/2.0/CesiumMilkTruck/glTF/CesiumMilkTruck.gltf")
  (data/import "glTF-Sample-Models/2.0/RiggedSimple/glTF/RiggedSimple.gltf")
  (data/import "glTF-Sample-Models/2.0/RiggedFigure/glTF/RiggedFigure.gltf")
  (data/import "glTF-Sample-Models/2.0/CesiumMan/glTF/CesiumMan.gltf")
  (data/import "glTF-Sample-Models/2.0/BrainStem/glTF/BrainStem.gltf")
  (data/import "glTF-Sample-Models/2.0/Fox/glTF/Fox.gltf")
  (data/import "glTF-Sample-Models/2.0/VC/glTF/VC.gltf")
  (data/import "glTF-Sample-Models/2.0/Sponza/glTF/Sponza.gltf")
  (data/import "glTF-Sample-Models/2.0/TwoSidedPlane/glTF/TwoSidedPlane.gltf")
  ;; Showcase
  (data/import "glTF-Sample-Models/2.0/AntiqueCamera/glTF/AntiqueCamera.gltf")
  (data/import "glTF-Sample-Models/2.0/Avocado/glTF/Avocado.gltf")
  (data/import "glTF-Sample-Models/2.0/BarramundiFish/glTF/BarramundiFish.gltf")
  (data/import "glTF-Sample-Models/2.0/BoomBox/glTF/BoomBox.gltf")
  (data/import "glTF-Sample-Models/2.0/Corset/glTF/Corset.gltf")
  (data/import "glTF-Sample-Models/2.0/DamagedHelmet/glTF/DamagedHelmet.gltf")
  (data/import "glTF-Sample-Models/2.0/FlightHelmet/glTF/FlightHelmet.gltf")
  (data/import "glTF-Sample-Models/2.0/Lantern/glTF/Lantern.gltf")
  (data/import "glTF-Sample-Models/2.0/SciFiHelmet/glTF/SciFiHelmet.gltf")
  (data/import "glTF-Sample-Models/2.0/Suzanne/glTF/Suzanne.gltf")
  (data/import "glTF-Sample-Models/2.0/WaterBottle/glTF/WaterBottle.gltf")

  ;; No renderer is complete without a teapot.
  (data/import "https://raw.githubusercontent.com/KhronosGroup/Vulkan-Samples-Assets/master/scenes/teapot.gltf")
  )



;; LOTS will happen from here
;; - lazy vertex formats
;; - lazy effect layouts

;; seeing many things already: different vertex formats, interleaved vs not (most are, it seems)
;; - separate buffer and mesh definitions -> should also be "packed" together
;;   - model already allow every file to have children, filesystem doesnt know if *.foobar is a container

;; raw binary data as a file type then, useless by itself
;; - but can load file info without having that blob see memory
;; - files are JS objects, easy to ETL;
;;
;; one store per resource type? useful for structure, not so much for avoiding extra data -> file info & file data together
;; - ie meshes are tiny, but still searchable individually -> referenced in scene from entities -> points to larger GPU buffer
;;   - meshes also have multiple primitives, each a static draw element (ie loaded to readonly gpu memory)
;;   - in case of same-size textures (OR NO TEXTURES) -> batching "trivial"
;;   - still batching when multiple meshes use the same material & gpu buffer


;; check avg size of these buffers -> upload to even larger GPU vertex/index buffers?
;; - less render state changes = more batches

;; RECAP
;; - source files :: glb, gltf, bin, png, jpg
;;   (and even those form a graph, rooted at gltf or glb -> which could also be parented to whoever requested it -> up to the tx)
;; - container files :: scenes (treat imported root as scene if `scene` is present, otherwise container, otherwise skip if no scenes)
;; - embedded "files" :: ECS (imported nodes have transforms, cameras, mesh references and hierarchies, among others -> ecs layout)
;; - content files :: meshes, animations, skins (point into binary file; loading one mesh loads the data of all others in that blob)
;; - binary files :: buffers, textures, ECS quick save (contains data for batches of files)

;; all of this modifiable at runtime (can even decouple loaders & editor-like systems -> lazy load the lazy loaders)
;; - add/remove data from binary files along with content (quick staging area -> slower persistent & durable commits)
;; - modify ECS scene (fairly basic editor behavior -> load scene, edit, store back -> ECS serialization leveraged)
;; - container files (purely high-level meta-data to view the database as a filesystem "It's a unix system! I know this!")
;; - source files (information about where the stored data came from - to handle reimports)

;; another directed acyclic graph (files can have more than one child, and be referenced by more than one file)
;; - folders are just the most basic form of container, purely organizational (a scene is a container AND a load unit)
;;   - meaning its useful to also index who uses what -> find entities using a certain mesh, for example
;; - prefabs are small scenes with property overrides -> indirect load, but never executed directly
