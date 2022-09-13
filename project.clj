(def jetty-version "9.4.48.v20220622")
(def ring-version  "1.9.5")

;; Just found out The Mars Volta has new singles for the first time in a decade.
;; The prototype name of this project is therefore a mashup of two single names.
(defproject blacklight-love "0.1.0-SNAPSHOT"
  :description "REPL is for Research, Education, Production, and Lisp!"

  :min-lein-version "2.9.1"

  :pedantic? :warn
  :checksum  :fail

  :dependencies [[org.clojure/clojure       "1.11.1"]
                 [org.clojure/clojurescript "1.11.60"]
                 ;;[org.clojure/core.match    "1.0.0"]
                 ;;[org.clojure/core.logic    "1.0.1"]
                 [org.clojure/tools.macro   "0.1.5"]
                 [com.bhauman/figwheel-main "0.2.18"]]

  :managed-dependencies [[org.eclipse.jetty/jetty-server                ~jetty-version]
                         [org.eclipse.jetty.websocket/websocket-server  ~jetty-version]
                         [org.eclipse.jetty.websocket/websocket-servlet ~jetty-version]
                         [binaryage/devtools "1.0.6"]
                         [ring/ring       ~ring-version]
                         [ring/ring-core  ~ring-version]
                         [ring/ring-devel ~ring-version]
                         [commons-codec "1.15"]
                         [ns-tracker    "0.4.0"]]

  :clean-targets ^{:protect false} ["target"]

  :aliases {"build" ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "prod"]}

  :profiles
  {:dev [:base {:dependencies [[re-frame "1.3.0"]
                               [day8.re-frame/re-frame-10x "1.5.0"]]
                :resource-paths ["target"]
                :global-vars {*warn-on-reflection* true}}]
   :rel [:base {}]})
