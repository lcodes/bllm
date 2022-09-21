(ns bllm.disp
  "The display sub-system. Controls the `requestAnimationFrame` cycle.

  Also integrates the browser's page visibility and fullscreen APIs."
  (:require [bllm.cli :as cli]
            [bllm.gpu  :as gpu]
            [bllm.util :as util :refer [def1 === !==]]
            [bllm.html :as html]))

(set! *warn-on-infer* true)

(cli/defgroup config)

(cli/defvar target-fps 60) ; TODO frame skip / throttle / based on context (ie editor/idle/bg values)

(cli/defvar pixel-ratio js/devicePixelRatio)


;;; Page Visibility
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; https://developer.mozilla.org/en-US/docs/Web/API/Page_Visibility_API

(def1 visible? true)

(defn- set-visible? [state]
  (set! visible? (=== "visible" state)))

(defn- on-visibility-change []
  (set-visible? js/document.visibilityState))

(defn- on-xr-visibility-change []
  ;; TODO
  )


;;; Fullscreen
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; https://developer.mozilla.org/en-US/docs/Web/API/Fullscreen_API

(defn- on-fullscreen-change []
  )

(defn request-fullscreen [^js/Element target]
  )

(defn exit-fullscreen []
  )


;;; Request Animation Frame
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; https://developer.mozilla.org/en-US/docs/Web/API/window/requestAnimationFrame

;; TODO prepare for this future API, or help push its adoption, or revisit it:
;;      https://github.com/WICG/request-post-animation-frame/blob/main/explainer.md

(def1 ^:private caf js/undefined)
(def1 ^:private raf js/undefined)
(def1 ^:private reg nil)

;; TODO WebXR will replace these for higher FPS in HMD mode.
(defn- setup []
  (set! caf window.cancelAnimationFrame)
  (set! raf window.requestAnimationFrame))

(defn frame [tick-fn]
  (set! reg (raf tick-fn)))

(defn cancel []
  (when reg
    (caf reg)
    (set! reg nil)))


;;; WebXR HMD
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;; Viewports
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; https://developer.mozilla.org/en-US/docs/Web/API/Resize_Observer_API
;; https://developer.mozilla.org/en-US/docs/Web/API/Window/resize_event

;; TODO:
;; https://developer.mozilla.org/en-US/docs/Web/API/Intersection_Observer_API
;; - mark invisible viewports as such; render pipeline ignoring disabled views

(def1 viewports #js [])

(defn find-viewport [canvas]
  (.find viewports (fn [vp] (=== canvas (html/parent vp.canvas)))))

(defn- resize-viewport [vp]
  ;; TODO sizing is currently hardcoded to work around weird firefox issues.
  (let [^js/HTMLCanvasElement
        c vp.canvas
        w (int (* (.-innerWidth  js/window) vp.pixel-ratio))
        h (int (* (.-innerHeight js/window) vp.pixel-ratio))]
    (when (or (!== (.-width  c) w)
              (!== (.-height c) h))
      (set! (.-width  c) w)
      (set! (.-height c) h)
      (gpu/html-setup-target vp.target.ctx vp.target)
      ;; TODO run through associated resources too
      )))

(defn- on-resize
  "Called on window resize events."
  []
  ;; TODO still needed?
  #_(util/doarray [vp viewports]
    (resize-viewport vp)))

(defn- on-resize-observed [entries]
  (util/doarray [^js/ResizeObserverEntry e entries]
    (resize-viewport (find-viewport (.-target e)))))

(def1 ^:private resize-observer
  (js/ResizeObserver. (util/callback on-resize-observed)))

(defn add-viewport
  "Registers a canvas as a WebGPU viewport. If pixel-ratio is positive, the
  canvas and its associated resources will automatically be resized."
  ([canvas]
   (add-viewport canvas pixel-ratio))
  ([canvas pixel-ratio]
   (let [vp #js {:canvas canvas
                 :target (gpu/html-render-target canvas)
                 :index  (util/find-free-index viewports)
                 :resize (js/Set.) ; Resources to propagate resize events to.
                 :pixel_ratio pixel-ratio}]
     (aset viewports vp.index vp)
     (when (pos? pixel-ratio)
       (.observe resize-observer (html/parent canvas)))
     vp)))

(defn remove-viewport [vp]
  (aset viewports vp.index nil)
  (gpu/html-destroy-target vp.target.cfg)
  (when (pos? vp.pixel-ratio)
    (.unobserve resize-observer vp.canvas)))


;;; Display System
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init []
  (setup)
  (js/addEventListener "resize"           (util/callback on-resize))
  (js/addEventListener "visibilitychange" (util/callback on-visibility-change))
  (js/addEventListener "fullscreenchange" (util/callback on-fullscreen-change)))

(defn start []
  (on-visibility-change)
  (util/doarray [vp viewports]
    (resize-viewport vp)))

(defn pre-tick []
  ;; schedule resource creation on resizes from here
  ;; - throttle resize requests to frame boundaries
  )
