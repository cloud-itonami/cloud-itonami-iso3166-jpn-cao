;; The measuring instruments, in ONE place.
;;
;; facts.edn's header states character counts, and verify-facts.cljs subtracts
;; needles from a body it measures the same way. If those two used different
;; text extractors, the register and its verifier would disagree about their
;; own evidence -- and the disagreement would be invisible, because both would
;; look internally consistent.
;;
;; So there is one de-tag, here, and scripts/measure-host.cljs regenerates
;; every number the register's header states by calling it. If you change
;; de-tag, the header's numbers change with it -- rerun measure-host and
;; update them.
;;
;; -- WHY THE TAG-STRIPPING REGEXES ARE BUILT WITH js/RegExp AND NOT #"(?is)"
;;
;; This is the defect that shaped this file. The obvious spelling is
;;
;;   (str/replace html #"(?is)<style.*?</style>" " ")
;;
;; and it is wrong in ClojureScript, in a way that leaves no trace. The reader
;; does translate (?is) into JS flags -- (.-flags #"(?is)x") really is "is" --
;; but clojure.string/replace does not use the compiled regex. It rebuilds one
;; to force a global match, and it carries over only g, i, m and u. The s flag
;; (dotAll) is DROPPED. Measured in this runtime:
;;
;;   (str/replace "<style>body{}</style>"     #"(?is)<style.*?</style>" "")  => ""
;;   (str/replace "<style>\nbody{}\n</style>" #"(?is)<style.*?</style>" "")  => unchanged
;;   (str/replace "<STYLE>x</STYLE>"          #"(?is)<style.*?</style>" "")  => ""
;;
;; Case-insensitivity survives, so the regex looks like it works. What fails is
;; only the multi-line case -- which is every real <style> and <script> block
;; on every host in this register. The stylesheet is then left in the document,
;; the next rule strips its angle brackets but not its text, and CSS source
;; ends up counted as page text.
;;
;; That is not a rounding error here. www.chisou.go.jp's missing-page body is
;; 682 characters measured with the broken extractor and 148 with this one:
;; nearly THREE QUARTERS of what a needle would have been subtracted from was
;; stylesheet. A needle like "margin" or "container" would have been reported
;; as appearing on the 404 page, and refused, for a reason that has nothing to
;; do with this host's chrome.
;;
;; [\s\S] rather than . would also work and needs no flag. js/RegExp with an
;; explicit "gis" is used instead because it states the intent, and because a
;; later editor who reaches for . inside these patterns is then not silently
;; wrong.

(ns host-probe
  (:require [clojure.string :as str]))

(def ua "cloud-itonami-iso3166-jpn-cao facts verifier")

(defn decode-utf8-strict
  "nil when the bytes are not valid UTF-8. Callers must treat nil as REFUSED,
   never as an empty page: the difference between a dead citation and an
   unreadable one is the whole reason exit 2 exists.

   All three page hosts in this register send a bare text/html with no charset
   parameter and declare UTF-8 only inside the document, so a decoder that
   substitutes replacement characters would turn an encoding change into a
   missing needle -- a finding about the wrong thing."
  [bytes]
  (try (.decode (js/TextDecoder. "utf-8" #js {:fatal true}) bytes)
       (catch :default _ nil)))

(defn fetch-bytes
  "Bytes, not text. Decoding is a decision each check makes for itself: the
   published documents are not text at all, and getting that wrong is silent.

   redirect follow is REQUIRED, not incidental. www.chisou.go.jp answers a
   missing page with 302 to <path>/index.html and a zero-length body of
   content-type application/xml; only after following it does a 404 appear.
   A client that does not follow sees status 302 and no body, which is neither
   a live page nor a recognisable missing one."
  [url]
  (-> (js/fetch url #js {:redirect "follow" :headers #js {"User-Agent" ua}})
      (.then (fn [r]
               (.then (.arrayBuffer r)
                      (fn [ab] {:status (.-status r)
                                :url (.-url r)
                                :redirected (.-redirected r)
                                :ctype (or (.get (.-headers r) "content-type") "")
                                :bytes (js/Uint8Array. ab)}))))
      (.catch (fn [e] {:error (str e)}))))

(defn fetch-no-redirect
  "The same request with redirects left unfollowed, for the one entry that
   asserts chisou's missing-page redirect is real. Nothing else uses it."
  [url]
  (-> (js/fetch url #js {:redirect "manual" :headers #js {"User-Agent" ua}})
      (.then (fn [r] {:status (.-status r)
                      :type (.-type r)}))
      (.catch (fn [e] {:error (str e)}))))

(defn fetch-head
  "HEAD, for the entries that assert what HEAD can and cannot tell you on each
   host. No liveness check uses it."
  [url]
  (-> (js/fetch url #js {:method "HEAD" :redirect "follow"
                         :headers #js {"User-Agent" ua}})
      (.then (fn [r] {:status (.-status r)
                      :content-length (.get (.-headers r) "content-length")}))
      (.catch (fn [e] {:error (str e)}))))

(defn fetch-json
  "Parsed JSON with the status alongside it. The status is returned but the
   statute judge must not branch on it -- the listing endpoint answers a
   fabricated law id with 200."
  [url]
  (-> (js/fetch url #js {:redirect "follow" :headers #js {"User-Agent" ua}})
      (.then (fn [r]
               (.then (.text r)
                      (fn [t]
                        (try {:status (.-status r) :json (js->clj (js/JSON.parse t))}
                             (catch :default _ {:status (.-status r) :bad-json true}))))))
      (.catch (fn [e] {:error (str e)}))))

(defn page-title
  "The title element's contents, VERBATIM. Not trimmed and entities NOT
   decoded: every www8.cao.go.jp title in this register carries literal
   &nbsp; sequences, and a check that decoded them would be asserting a string
   the document does not contain."
  [html]
  (when-let [m (re-find #"<title>([\s\S]*?)</title>" html)]
    (second m)))

;; Built with js/RegExp so the dotAll flag actually reaches the match. See the
;; header -- #"(?is)" loses it inside clojure.string/replace.
(def ^:private re-script (js/RegExp. "<script[\\s\\S]*?</script>" "gis"))
(def ^:private re-style  (js/RegExp. "<style[\\s\\S]*?</style>" "gis"))
(def ^:private re-comment (js/RegExp. "<!--[\\s\\S]*?-->" "gis"))
(def ^:private re-tag    (js/RegExp. "<[^>]+>" "gis"))

(defn de-tag
  "The one text extractor. Every character count in facts.edn's header and
   every needle subtraction in verify-facts.cljs goes through this.

   Script, style and comment CONTENTS are removed before tags are, because
   stripping angle brackets alone leaves their source behind as prose."
  [html]
  (-> html
      (str/replace re-script " ")
      (str/replace re-style " ")
      (str/replace re-comment " ")
      (str/replace re-tag " ")
      (str/replace #"\s+" " ")
      str/trim))
