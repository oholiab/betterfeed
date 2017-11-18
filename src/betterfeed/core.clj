(ns betterfeed.core
  (:require [clj-http.client :as client]
            [clojure.zip :as zip]
            [clojure.data.zip :as dz]
            [clojure.java.shell :as shell]
            [clojure.data.xml :as xml]
            [clojure.data.zip.xml :as dzx]
            [net.cgrand.enlive-html :as enl]
            [rss-utils.core :as rss]))

;; ----- BEGIN TERRIBLE HACK ----- ;;
; Stops data.xml 0.2.0-alpha3 from throwing an exception when xmlns prefix is
; correctly defined - this is a bug which has a fix out, but not in a release
; version

(in-ns 'clojure.data.xml.pu-map)
(defn assoc! [{:as put :keys [p->u u->ps]} prefix uri]
  ; Monkey patching the following bit out
  #_(when (or (core/get #{"xml" "xmlns"} prefix)
            (core/get #{name/xml-uri name/xmlns-uri} uri))
    (throw (ex-info "Mapping for xml: and xmlns: prefixes are fixed by the standard"
                    {:attempted-mapping {:prefix prefix
                                         :uri uri}})))
  (let [prev-uri (core/get p->u prefix)]
    (core/assoc! put
                 :p->u (if (str/blank? uri)
                         (core/dissoc! p->u prefix)
                         (core/assoc! p->u prefix uri))
                 :u->ps (if (str/blank? uri)
                          (dissoc-uri! u->ps prev-uri prefix)
                          (cond
                            (= uri prev-uri) u->ps
                            (not prev-uri) (assoc-uri! u->ps uri prefix)
                            :else (-> u->ps
                                      (dissoc-uri! prev-uri prefix)
                                      (assoc-uri! uri prefix)))))))

;; ----- END TERRIBLE HACK ----- ;;
(in-ns 'betterfeed.core)

(def user-agent "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/57.0.2987.133 Safari/537.36")
(def default-headers {"User-Agent" user-agent})
(def chromepath "/Applications/Google Chrome Canary.app/Contents/MacOS/Google Chrome Canary")
(def chromeargs ["--headless" "--disable-gpu" "--dump-dom"])

(def will-3xx (ref '()))

(xml/alias-uri 'content "http://purl.org/rss/1.0/modules/content/")
(def prefixes {:xmlns/content "http://purl.org/rss/1.0/modules/content/"})

(defn get-host
  "Get the host portion of a URL"
  [url]
  (.getHost (new java.net.URL url)))

(defn resolve-redirect
  [url]
  (let [response (client/head url {:headers           default-headers
                                   :follow-redirects  false
                                   :redirect-strategy :none})]
    (if (<= 300 (:status response) 400)
      (-> response
          :headers
          :Location)
      url)))


(defn get-real-url
  [url]
  (let [firsthost (get-host url)]
    (if (some #{firsthost} @will-3xx)
      (resolve-redirect url)
      url)))

(defn get-body
  "Plain old http get with a friendly user agent"
  [url]
  (:body (client/get url {:headers default-headers})))

(defn chrome-get-body
  "Use chrome to dump out the DOM after JS has rendered it"
  [url]
  (:out (apply shell/sh (concat [chromepath] chromeargs [url]))))

; A hash of hostnames to the method that should be used to fetch the message body
(def body-method (ref {}))

(defn get-body-method
  "Return the body method that matches the url or return the default one"
  [url]
  (get @body-method (get-host url) get-body))

; TO UTILS
; TODO: make this figure out filename itself so that it can just be returned
(defn fetch-local
  "Feeds like to be a dick about user-agent so grab all XML locally using a browser UA and then parse that instead"
  [url tmpfile]
  (spit tmpfile (get-body url))
  (str "file://" tmpfile))

(def c-feed (memoize rss/parse-feed))

(defn is-tag?
  [tag entry]
  (= tag (:tag entry)))

(defn filter-by
  [tag feed]
  (filter #(is-tag? tag %) (:content feed)))

(defn get-link-from-item
  [item]
  (-> (dzx/xml1-> item :link)
      zip/node
      :content
      first
      get-real-url))

; FIXME: move to rss-utils
(defn update-item
  [item field newval]
  (zip/up (zip/edit (dzx/xml1-> item field) #(assoc-in % [:content] newval))))

(defn has-tag?
  [tag item]
  (not (empty? (dzx/xml-> item tag))))

(defn insert-tag
  [item field newval]
  (zip/insert-child item (xml/element field prefixes newval)))

(defn update-or-insert-item
  [item field newval]
  (if (has-tag? field item)
    (update-item item field newval)
    (insert-tag item field newval)))

(defn get-all-tag
  [tag feed]
  (let [z (zip/xml-zip feed)]
    (map
     #(-> %
          zip/node
          :content
          first)
     (dzx/xml-> z
                :channel
                :item
                tag
                )
     )))

(defn get-all-links
  [feed]
  (get-all-tag :link feed))

(defn get-html-snippet
  [url]
  (let [get-fn (get-body-method url)]
    (-> url get-fn enl/html-snippet)))

(defn div-entry-get-content
  [url]
  (enl/select (get-html-snippet url) [:div.entry]))

(defn selector
  [sel]
  #(enl/select (get-html-snippet %) sel))

(def content-method (ref {}))

(defn get-content-method
  "Return the body method that matches the url or return the default one"
  [url]
  (get @content-method (get-host url) div-entry-get-content))


(defn emit-body-content
  [url]
  (let [get-content (get-content-method url)]
    (-> (get-content url) enl/emit* (->> (apply str)))))

; FIXME: shouldn't *need* this - try to get the code to retry with some defaults before giving up, keep this around for performance purposes.
(def rss-body-field (ref {}))

(defn get-rss-body-field
  [url]
  (get @rss-body-field (get-host url) :description))

(defn update-body-from-link-contents
  "Updates the body field of `item` with the scrape of the link field"
  ([item field]
   (let [url (get-link-from-item item)]
     (update-or-insert-item item field (xml/cdata (emit-body-content url)))))
  ([item]
   (update-body-from-link-contents item ::content/encoded)))

(defn repopulate-feed
  [feed]
  (rss/apply-to-items feed update-body-from-link-contents))

; TO UTILS
(def c-repopulate-feed (memoize #(repopulate-feed (rss/parse-feed %))))

(defn rewrite-feed
  [url dest]
  (with-open [f (clojure.java.io/writer dest)]
    (xml/emit (repopulate-feed (rss/parse-feed url)) f)))

;;; ---------------------------------------------- ;;;
;;; EXTRA SHIT THAT'S NOT ACTUALLY USED BELOW HERE ;;;
;;; ---------------------------------------------- ;;;


(defn get-read-more-paragraph
  [description]
  (filter #(re-find #"Read more on" (first (:content %))) (enl/select (enl/html-snippet description) [:p])))

(defn strip-backslash-and-space
  [text]
  (clojure.string/join (remove #(some #{%} '(\\ \")) text)))

(defn get-read-more-link
  [description]
  (let [paragraph (get-read-more-paragraph description)]
    (if (empty? paragraph)
      ""
      (strip-backslash-and-space (:href (:attrs (first (enl/select paragraph [:a]))))))))

(defn get-read-more-content
  [description]
  (let [url (get-read-more-link description)]
    (if (empty? url)
      ""
      (emit-body-content url))))
