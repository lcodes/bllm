(ns bllm.text
  (:require [bllm.data :as data]))

(data/defimport file
  {:extension  "txt"
   :media-type "plain/text"}
  [url text]
  (js/console.log text))
