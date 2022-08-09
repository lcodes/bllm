(ns bllm.wgsl
  "Meta WebGPU. Because raw text is hard to digest, tastes like concat.")

;; TODO need CPU side of vertex definition (match what WebGPU wants)

(defmacro def-vertex-in
  [& args]
  ;; specialized attribute describing a vertex element (GPU side of)
  )

(defmacro def-pixel-out
  [& args]
  ;; specialized attribute describing a render target (GPU side of)
  )

(defmacro defuniform
  [& args]
  )

(defmacro defstorage
  [& args]
  )

(defmacro deftexture
  [& args]
  ;; type (format is not part of the texture definition, but the texture object itself)
  ;; multisampled
  ;; depth
  ;; storage (TODO vs defstorage? one is structured the other just a repeated scalar type)
  )

(defmacro defsampler
  [& args]
  )

(defmacro defvertex
  "Defines a vertex shader entry point."
  [& args]
  ;; inputs -> vertex -> interpolants
  ;;
  ;; runtime needs to collect all inputs and unpack them from the generated entry
  ;; then collect all outputs and thread them into packed interpolants
  )

(defmacro defpixel
  "Defines a fragment shader entry point."
  [& args]
  ;; interpolants -> fragment -> outputs
  ;;
  ;; runtime needs to collect all interpolants and unpack them
  ;; then collect all outputs and thread them into packed render targets
  )

(defmacro defkernel
  "Defines a compute shader entry point."
  [& args]
  ;; inputs -> compute -> outputs
  ;;
  ;; thread counts
  )
