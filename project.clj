(defproject betterfeed "0.1.1"
  :description "Poorly formatted feed? That's a scrapin'"
  :url "https://github.com/oholiab/betterfeed"
  :license {:name "BSD 3 Clause"
            :url "https://opensource.org/licenses/BSD-3-Clause"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-http "3.5.0"]
                 [org.clojure/data.zip "0.1.2"]
                 [org.clojure/data.xml "0.2.0-alpha3"]
                 [enlive "1.1.6"]
                 [rss-utils "0.0.11"]])
