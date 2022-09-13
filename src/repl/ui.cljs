(ns repl.ui
  "User Interface convenience utilities. Simplified use of `re-frame`."
  (:require-macros [repl.ui])
  (:require [re-frame.core :as rf]))

(def sub rf/subscribe)
