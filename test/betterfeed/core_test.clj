(ns betterfeed.core-test
  (:require [clojure.test :refer :all]
            [betterfeed.core :refer :all]
            [rss-utils.core :as rss]
            [clojure.java.io :as io]
            [clojure.data.zip.xml :as dzx]
            [clojure.zip :as zip]))

(rss/define-atom-xmlns)

(defn string->stream
  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (java.io.ByteArrayInputStream.))))

(def example-atom-str
  (clojure.string/join "\n" [
                 "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                 "<feed xmlns=\"http://www.w3.org/2005/Atom\">"
                 "  <title>Example Feed</title>"
                 "  <link href=\"http://example.org/\"/>"
                 "  <updated>2003-12-13T18:30:02Z</updated>"
                 "  <author>"
                 "    <name>John Doe</name>"
                 "  </author>"
                 "  <id>urn:uuid:60a76c80-d399-11d9-b93C-0003939e0af6</id>"
                 "  <entry>"
                 "    <title>Atom-Powered Robots Run Amok</title>"
                 "    <link href=\"http://example.org/2003/12/13/atom03\"/>"
                 "    <id>urn:uuid:1225c695-cfb8-4ebb-aaaa-80da344efa6a</id>"
                 "    <updated>2003-12-13T18:30:02Z</updated>"
                 "    <summary>Some text.</summary>"
                 "  </entry>"
                 "</feed>"]))

(def example-rss-str
  (clojure.string/join "\n" [
                  "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
                  "<rss version=\"2.0\">"
                  "  <channel>"
                  "    <title>W3Schools Home Page</title>"
                  "    <link>https://www.w3schools.com</link>"
                  "    <description>Free web building tutorials</description>"
                  "    <item>"
                  "      <title>RSS Tutorial</title>"
                  "      <link>https://www.w3schools.com/xml/xml_rss.asp</link>"
                  "      <description>New RSS tutorial on W3Schools</description>"
                  "    </item>"
                  "    <item>"
                  "      <title>XML Tutorial</title>"
                  "      <link>https://www.w3schools.com/xml</link>"
                  "      <description>New XML tutorial on W3Schools</description>"
                  "    </item>"
                  "  </channel>"
                  "</rss>"]))

(def example-atom
  (clojure.data.xml/parse (string->stream example-atom-str)))

(def example-rss
  (clojure.data.xml/parse (string->stream example-rss-str)))

; FIXME: These two should be constructed data, otherwise they're being used to test the funcitons that produced them.
(def atom-first-item
  (rss/zip-at-first-item example-atom))

(def rss-first-item
  (rss/zip-at-first-item example-rss))

(deftest test-get-host
  (testing "returns host part of url"
    (is (= (get-host "https://google.com/blarg") "google.com"))
    (is (= (get-host "http://google.com/") "google.com"))))

(deftest test-resolve-redirect
  ; FIXME: This requires a network connection and outside behaviour
  (testing "will resolve a flaky remote redirect"
    (is (= (resolve-redirect "http://grimmwa.re") "https://www.grimmwa.re/"))))

(deftest test-get-real-url
  ; FIXME: This requires a network connection and outside behaviour
  (testing "will resolve a flaky remote redirect when the domain is in the will-3xx ref"
    (dosync (ref-set will-3xx '("grimmwa.re")))
    (is (= (get-real-url "http://grimmwa.re") "https://www.grimmwa.re/"))
    (is (= (get-real-url "http://anything-else.com") "http://anything-else.com"))))

(deftest test-get-body-method
  (testing "Returns body method for domain in the body-method ref, or get-body for anything else"
    (defn blerg [x] x)
    (dosync (ref-set body-method {"grimmwa.re" blerg}))
    (is (= (get-body-method "https://grimmwa.re") blerg))
    (is (= (get-body-method "https://google.com") get-body))))

(deftest test-fetch-local
  ; FIXME: This requires a network connection and outside behaviour
  (testing "works"
    (let [target-file "/tmp/betterfeed-test"]
      (is (= (fetch-local "https://google.com" target-file) "file:///tmp/betterfeed-test"))
      (is (= (.exists (clojure.java.io/as-file target-file)) true)))))

(deftest test-get-link-from-item
  (testing "Gets the correct link form the item"
    (is (= (get-link-from-item rss-first-item) "https://www.w3schools.com/xml/xml_rss.asp"))
    ;FIXME: Make this work for atom
    ;(is (= (get-link-from-item atom-first-item) "https://www.w3schools.com/xml/xml_rss.asp"))
    ))

(deftest test-update-item
  ; FIXME: this is a hot mess - make a convenience method for lookups
  (testing "creates a new item value"
    (is (= (-> (dzx/xml1-> rss-first-item :link) zip/node :content first) "https://www.w3schools.com/xml/xml_rss.asp"))
    (let [new-item (update-item rss-first-item :link "not here")]
      (is (= (-> (dzx/xml1-> new-item :link) zip/node :content first) "not here")))))

(deftest test-has-tag
  (testing "returns true when an item has a tag, otherwise false"
    (is (= (has-tag? :link rss-first-item) true))
    (is (= (has-tag? ::atomfeed/link atom-first-item) true))
    (is (= (has-tag? :linkogram rss-first-item) false))
    (is (= (has-tag? :linkogram atom-first-item) false))))

(deftest test-insert-tag
  (testing "correctly inserts a missing tag"
    (is (= (has-tag? :linkogram rss-first-item) false))
    (let [new-item (insert-tag rss-first-item :linkogram "bananas")]
      (is (= (has-tag? :linkogram new-item) true))
      (is (= (-> (dzx/xml1-> new-item :linkogram) zip/node :content first) "bananas")))))

(deftest test-get-all-tag
  (testing "returns all values of tag type"
    (is (= (get-all-tag :link example-rss) '("https://www.w3schools.com/xml/xml_rss.asp"
                                            "https://www.w3schools.com/xml")))))

(deftest test-get-all-links
  (testing "returns all links from a feed"
    (is (= (get-all-links example-rss) '("https://www.w3schools.com/xml/xml_rss.asp"
                                         "https://www.w3schools.com/xml")))
    ; FIXME: make work for atom
    #_(is (= (get-all-links example-atom) '("https://www.w3schools.com/xml/xml_rss.asp"
                                         "https://www.w3schools.com/xml")))
    ))
