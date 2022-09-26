(ns bllm.input
  "Support for various HTML5 input devices: keyboards, mice, gamepads and more."
  (:refer-clojure :exclude [keys])
  (:require [bllm.meta :as meta]
            [bllm.util :as util :refer [def1]]))

(set! *warn-on-infer* true)


;; 0=keyboard, 1=mouse, 2+ = connected gamepads, remote controls, playback timelines, etc

;; DEVICE ID + KEY = discrete input
;; DEVICE ID + VAR = continuous input

;; TODO input here is tick based
;; high level often wants single events

;; capture inputs -> routing -> engine / editor handlers -> tick batches / event dispatch

;; tick loop can be paused (ie on error), still want inputs coming through

;; what about using commands directly?
;; - simulation inputs get batched every frame (command just writes to a chunk)


;;; Input Context
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype Handler [id ^:mutable enabled-at
                  key-down   key-up
                  mouse-down mouse-up
                  mouse-move mouse-wheel])

(def1 ^:private stack     #js [])
(def1 ^:private stack-top 0)

(defn register!
  "Development only. Replaces a redefined handler in the current `stack`."
  [^Handler h]
  (assert (neg? (.-enabled-at h)))
  (util/doarray [^Handler x n stack]
    (when (= (.-id h) (.-id x))
      (set! (.-enabled-at h) n)
      (aset stack n h)
      (util/return))))

(defn handler-up [id key-up mouse-up]
  (doto (->Handler id -1 nil key-up nil mouse-up nil nil)
    (register!)))

(defn enable! [^Handler h]
  (when (neg? (.-enabled-at h))
    (set! (.-enabled-at h) stack-top)
    (aset stack stack-top h)
    (util/inc! stack-top)))

(defn disable! [^Handler h]
  (let [n (.-enabled-at h)]
    (when (nat-int? n)
      (set! (.-enabled-at h) -1)
      (util/dec! stack-top)
      (when (< n stack-top)
        (.splice stack n 1)))))

(defn dispatch [get-fn e]
  (try
    (util/dorange< [n stack-top 0]
      (when-let [f (get-fn (aget stack n))]
        (when (f e)
          (util/prevent-default e)
          (util/return))))
    (catch :default e
      (js/console.error e)))) ; TODO


;;; Keyboard
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; https://developer.mozilla.org/en-US/docs/Web/API/UI_Events/Keyboard_event_code_values
(meta/defenum keys
  {:repr :string :read ->key :default Unidentified}
  Unidentified Escape
  Digit1 Digit2 Digit3 Digit4 Digit5 Digit6 Digit7 Digit8 Digit9 Digit0
  Minus Equal Backspace Tab
  KeyQ KeyW KeyE KeyR KeyT KeyY KeyU KeyI KeyO KeyP
  BracketLeft BracketRight Enter ControlLeft
  KeyA KeyS KeyD KeyF KeyG KeyH KeyJ KeyK KeyL
  Semicolon Quote Backquote ShiftLeft Backslash
  KeyZ KeyX KeyC KeyV KeyB KeyN KeyM
  Comma Period Slash ShiftRight NumpadMultiply AltLeft Space CapsLock
  F1 F2 F3 F4 F5 F6 F7 F8 F9 F10 Pause ScrollLock
  Numpad7 Numpad8 Numpad9 NumpadSubtract
  Numpad4 Numpad5 Numpad6 NumpadAdd
  Numpad1 Numpad2 Numpad3 Numpad0
  NumpadDecimal PrintScreen IntlBackslash F11 F12 NumpadEqual
  F13 F14 F15 F16 F17 F18 F19 F20 F21 F22 F23 KanaMode Lang2 Lang1 IntlRo F24
  Convert NonConvert IntlYen NumpadComma MediaTrackPrevious NumpadEnter
  ControlRight AudioVolumeMute LaunchApp2 MediaPlayPause MediaStop
  VolumeDown VolumeUp BrowserHome NumpadDivide AltRight NumLock
  Home ArrowUp PageUp ArrowLeft ArrowRight End ArrowDown PageDown Insert Delete
  OSLeft MetaLeft OSRight MetaRight
  ContextMenu Power Sleep BrowserSearch BrowserFavorites BrowserRefresh
  BrowserStop BrowserForward BrowserBack LaunchApp1 LaunchMail MediaSelect
  ;; Translated from mouse inputs, to dispatch again as keys.
  ClickLeft ClickMiddle ClickRight WheelUp WheelDown WheelLeft WheelRight
  ClickPrev ClickNext
  ;; Translated from gamepad inputs, to dispatch as keys.
  key-max)

(def ^:private on-key-down (partial dispatch (fn [^Handler h] (.-key-down h))))
(def ^:private on-key-up   (partial dispatch (fn [^Handler h] (.-key-up   h))))


;;; Mouse & Touch
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private on-mouse-wheel (partial dispatch (fn [^Handler h] (.-mouse-wheel h))))
(def ^:private on-mouse-move  (partial dispatch (fn [^Handler h] (.-mouse-move  h))))
(def ^:private on-mouse-down  (partial dispatch (fn [^Handler h] (.-mouse-down  h))))
(def ^:private on-mouse-up    (partial dispatch (fn [^Handler h] (.-mouse-up    h))))


;;; Gamepads
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- on-gamepad-connected [^js/GamepadEvent e]
  (js/console.log e))

(defn- on-gamepad-disconnected [^js/GamepadEvent e]
  (js/console.log e))


;;; Input System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init []
  (js/addEventListener "keydown"   (util/cb on-key-down))
  (js/addEventListener "keyup"     (util/cb on-key-up))
  (js/addEventListener "wheel"     (util/cb on-mouse-wheel))
  (js/addEventListener "mousemove" (util/cb on-mouse-move))
  (js/addEventListener "mousedown" (util/cb on-mouse-down))
  (js/addEventListener "mouseup"   (util/cb on-mouse-up))
  (js/addEventListener "gamepadconnected"    (util/cb on-gamepad-connected))
  (js/addEventListener "gamepaddisconnected" (util/cb on-gamepad-disconnected)))

(defn pre-tick []
  ;; Collect gamepad state; marshal into form more conveniently consumed during frame
  ;; - keep sequences of events (dont swallow 'press -> release' between slow frames)
  ;; - feed into deterministic frame replay buffer
  )

(defn post-tick []
  ;; Reset/swap state accums; input events trigger between animation frame requests
  )
