(ns repl.state)

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
