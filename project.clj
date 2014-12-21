(defproject xmas "0.1.0-SNAPSHOT"
  :description "Xmas greetings"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2498"]

                 [figwheel "0.1.7-SNAPSHOT"]     
                 ]
  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-figwheel "0.1.7-SNAPSHOT"]
            ]
  :hooks [leiningen.cljsbuild]

  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src"]
                        :compiler {
                                   :output-to "dev-resources/public/js/main.js"
                                   :output-dir "dev-resources/public/js"
                                   :optimizations :none
                                   :source-map true}}

                       {:id "prod"
                        :source-paths ["src"]
                        :compiler {
                                   :output-to "xmas.js"
                                   :output-dir "prod"
                                   :optimizations :advanced
                                   }}

                       ]}




)
