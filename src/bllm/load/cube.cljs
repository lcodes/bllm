(ns bllm.load.cube
  (:require [bllm.data :as data]))

;; FIXME seems to no longer exist
;; https://images2.adobe.com/content/dam/acom/en/products/speedgrade/cc/pdfs/cube-lut-specification-1.0.pdf


;; TODO port an old importer I made in C++ (<200lines)
;; - parse, arraybuffer, fill -> save data texture

(defn read [text]
  ;; cant return both file and data separately
  ;; - both at once would save data in file
  ;; - no good, importers can create more than 1 file; still want them all linked under the original
  )
