(ns bllm.task)

;; TODO web workers, shared worker, service worker (registration)

;; uses like the cells on PS3; dispatch commands with transferable/shared data.
;; - need to break down entire app into output modules -> closure already supports
;; - need to find or make (or already exists?) figwheel loader for worker contexts
;;   - send live evals to workers? at least get figwheel live refresh
;;   - ideally select which REPL to send evals to, need to investigate nREPL sessions in editors & document them
