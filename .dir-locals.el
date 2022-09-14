;; TODO I'm starting to want an emacs function to auto-generate this file.
((clojure-mode . ((eval . (progn
                            (define-clojure-indent
                              (macrolet '(1 ((:defn)) nil))
                              (clojure.tools.macro/macrolet '(1 ((:defn)) nil))
                              (bllm.util/compat-old 1)
                              (bllm.util/compat-std 1)
                              (bllm.util/doarray    1)
                              (bllm.util/docoll     1)
                              (bllm.util/domap      1)
                              (bllm.util/doiter     1)
                              (bllm.util/dorange    1)
                              (bllm.util/dolist     1))
                            ;; bllm.util
                            (put 'defm                  'clojure-doc-string-elt 2)
                            (put 'defconst              'clojure-doc-string-elt 2)
                            (put 'def1                  'clojure-doc-string-elt 2)
                            ;; bllm.meta
                            (put 'defenum               'clojure-doc-string-elt 2)
                            (put 'defflag               'clojure-doc-string-elt 2)
                            ;; bllm.gpu
                            (put 'defgpu                'clojure-doc-string-elt 2)
                            (put 'defstage              'clojure-doc-string-elt 2)
                            (put 'defbind               'clojure-doc-string-elt 2)
                            (put 'deflayout             'clojure-doc-string-elt 2)
                            ;; bllm.wgsl
                            (put 'defnode               'clojure-doc-string-elt 2)
                            (put 'defwgsl               'clojure-doc-string-elt 2)
                            (put 'defstate              'clojure-doc-string-elt 2)
                            (put 'defio                 'clojure-doc-string-elt 2)
                            (put 'defstate              'clojure-doc-string-elt 2)
                            ;; namespaced
                            (put 'data/defimport        'clojure-doc-string-elt 2)
                            (put 'data/defstore         'clojure-doc-string-elt 2)
                            (put 'ecs/defc              'clojure-doc-string-elt 2)
                            (put 'ecs/defsys            'clojure-doc-string-elt 2)
                            (put 'gpu/defres            'clojure-doc-string-elt 2)
                            (put 'wgsl/defprimitive     'clojure-doc-string-elt 2)
                            (put 'wgsl/defstencil-face  'clojure-doc-string-elt 2)
                            (put 'wgsl/defdepth-stencil 'clojure-doc-string-elt 2)
                            (put 'wgsl/defmultisample   'clojure-doc-string-elt 2)
                            (put 'wgsl/defblend-comp    'clojure-doc-string-elt 2)
                            (put 'wgsl/defblend         'clojure-doc-string-elt 2)
                            (put 'wgsl/defbuiltin       'clojure-doc-string-elt 4)
                            (put 'wgsl/defvertex-attr   'clojure-doc-string-elt 4)
                            (put 'wgsl/defdraw-buffer   'clojure-doc-string-elt 4)
                            (put 'wgsl/definterpolant   'clojure-doc-string-elt 4)
                            (put 'wgsl/defstruct        'clojure-doc-string-elt 2)
                            (put 'wgsl/defbuffer        'clojure-doc-string-elt 2)
                            (put 'wgsl/deftexture       'clojure-doc-string-elt 6)
                            (put 'wgsl/defstorage       'clojure-doc-string-elt 6)
                            (put 'wgsl/defsampler       'clojure-doc-string-elt 4)
                            (put 'wgsl/defgroup         'clojure-doc-string-elt 2)
                            (put 'wgsl/deflayout        'clojure-doc-string-elt 2)
                            (put 'wgsl/defenum          'clojure-doc-string-elt 2)
                            (put 'wgsl/defflag          'clojure-doc-string-elt 2)
                            (put 'wgsl/defconst         'clojure-doc-string-elt 2)
                            (put 'wgsl/defvar           'clojure-doc-string-elt 2)
                            (put 'wgsl/defun            'clojure-doc-string-elt 2)
                            (put 'wgsl/defkernel        'clojure-doc-string-elt 2)
                            (put 'wgsl/defvertex        'clojure-doc-string-elt 2)
                            (put 'wgsl/defpixel         'clojure-doc-string-elt 2)
                            (put 'wgsl/defcompute       'clojure-doc-string-elt 2)
                            (put 'wgsl/defrender        'clojure-doc-string-elt 2)
                            (put 'ui/defpane            'clojure-doc-string-elt 2)
                            )))))
