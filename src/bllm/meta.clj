(ns bllm.meta
  "Don't mind the system behind the curtain."
  (:refer-clojure :exclude [defstruct]))

;; Names without a space are reserved for meta attributes and schematics.

(defmacro defenum
  "An enumerated type whose domain set is explicitly specified."
  [& args]
  ; also prefix-MAX
  )

(defmacro defflag
  "Packs one or more boolean attributes into an unsigned int."
  [& args]
  )

(defmacro defbits
  "Packs one or more integer attributes into a unsigned int."
  [& args]
  ;; how many bits total
  ;; how many bits per elem
  ;; pack function (elem... -> num)
  ;; unpack functions (num -> elem)
  )

(defmacro defstruct
  "Aggregates one or more attributes into a named structure.

  Elements of the structure are re-ordered to limit padding.
  This can be disabled using the `:unordered` meta property.
  "
  [& args]
  )

(defmacro defvar
  "Like `def`, but also captures the place as schematic data."
  [& args]
  ;; get node
  ;; set node (!)
  )

(defmacro defun
  "Like `defn`, but also captures the body as schematic data."
  [& args]
  ;; meta flags
  ;; input nodes
  ;; output nodes
  )
