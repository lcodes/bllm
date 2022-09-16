(ns bllm.html
  "Poor man's jQuery. Mostly inlinable functions to add type annotations.")

(set! *warn-on-infer* true)

(defn ^js/Node parent [^js/Node elem]
  (.-parentElement elem))

(defn ^js/Element parent-element [^js/Node elem]
  (.-parentElement elem))

(defn add-class [^js/Element elem class]
  (.add (.-classList elem) class))

(defn remove-class [^js/Element elem class]
  (.remove (.-classList elem) class))

(defn toggle-class [^js/Element elem class]
  (.toggle (.-classList elem) class))

(defn replace-children [^js/Element elem x]
  (.replaceChildren elem x))

(defn class [name-a name-b]
  (cond (nil? name-a) name-b
        (nil? name-b) name-a
        :else (str name-a \space name-b)))
