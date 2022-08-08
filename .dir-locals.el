((clojure-mode . ((eval . (progn
                            (define-clojure-indent
                              (macrolet '(1 ((:defn)) nil))
                              (clojure.tools.macro/macrolet '(1 ((:defn)) nil))
                              (bllm.util/compat-old 1)
                              (bllm.util/compat-std 1)
                              (bllm.util/doarray 1)
                              (bllm.util/docoll  1)
                              (bllm.util/doiter  1)
                              (bllm.util/dorange 1)
                              (bllm.util/do-node-list 1))
                            (put 'def1 'clojure-doc-string-elt 2)
                            (put 'defgpu 'clojure-doc-string-elt 2)
                            )))))
