(ns repl.state
  "Strange loops."
  )

;; State in a simulation quickly gets confusing and overwhelming
;;
;; - Logging :: So many warnings and noise, nobody really reads it
;; - Metrics :: Profiling, basic counters, tracking what happened
;; - Errors  :: Exceptions, Conditions (hello Common Lisp!)
;; - etc     :: Anything which can happen within the boundaries of a frame.
;;
;; This module is to bind such state to ECS world frames.
;; - think re-frame-10x, but for entire simulation frames.
;; - infinite streams are intimidating, frame buckets arent.
;; - stream this state outside (kafka, etc) to graph in the large
;; - stream this state inside (datomic, etc) to visualize it back

#_(repl.ui/defcofx local
  []
  )

#_(repl.ui/defx local!
  [_event k v]
  )



;; STORES
;; - cookies (any use? stringly typed, tiny storage, sync, expiration)
;; - session (small storage, sync, cleared when closing the browser)
;; - local   (same as session, durable)
;; - fetch   (async, external, optionally through `data/import`)
;; - IDB     (async, internal, usually through `data/store`)

;; - only tradeoff is having to do the JSON serialization
;; - BUT, can debounce all writes to batches (well then, whats different from async IDB?)
;;   - more to amortize JSON serialization cost when user preferences are iterated on
;;   - all late game implementations, but useful to hook ahead of time in the design
