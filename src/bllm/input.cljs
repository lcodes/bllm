(ns bllm.input
  "Support for HTML5 keyboard, mouse and gamepad."
  (:refer-clojure :exclude [divide meta])
  (:require [bllm.meta :as meta]))

(set! *warn-on-infer* true)

(defn init []
  )

(defn pre-tick []
  ;; Collect gamepad state; marshal into form more conveniently consumed during frame
  ;; - keep sequences of events (dont swallow 'press -> release' between slow frames)
  ;; - feed into deterministic frame replay buffer
  )

(defn post-tick []
  ;; Reset/swap state accums; input events trigger between animation frame requests
  )

(defn attach [target]
  )

(defn detach [target]
  )

;; TODO mouse/touch
;; TODO gamepad
;; TODO clipboard
;; TODO orientation

;;; Keyboard
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(meta/defenum keys
  backspace 8
  tab 9
  return 13
  shift 16
  ctrl 17
  alt 18
  pause 19
  capslock 20
  escape 27
  space 32
  page-up 33
  page-down 34
  end 35
  home 36
  left 37
  up 38
  right 39
  down 40
  insert 45
  delete 46
  _0 48
  _1 49
  _2 50
  _3 51
  _4 52
  _5 53
  _6 54
  _7 55
  _8 56
  _9 57
  semicolon 59
  equals 60
  a 65
  b 66
  c 67
  d 68
  e 69
  f 70
  g 71
  h 72
  i 73
  j 74
  k 75
  l 76
  m 77
  n 78
  o 79
  p 80
  q 81
  r 82
  s 83
  t 84
  u 85
  v 86
  w 87
  x 88
  y 89
  z 90
  meta-left 91
  meta-right 92
  menu 93
  num-0 96
  num-1 97
  num-2 98
  num-3 99
  num-4 100
  num-5 101
  num-6 102
  num-7 103
  num-8 104
  num-9 105
  multiply 106
  add 107
  subtract 109
  divide 111
  f1 112
  f2 113
  f3 114
  f4 115
  f5 116
  f6 117
  f7 118
  f8 119
  f9 120
  f10 121
  f11 122
  f12 123
  numlock 144
  comma 188
  period 190
  slash 191
  backquote 192
  open-bracket 219
  backslash 220
  close-bracket 221
  quote 222
  meta 224)
