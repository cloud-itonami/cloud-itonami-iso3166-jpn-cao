;; Regenerate every measurement facts.edn's header states.
;;
;;   nbb --classpath scripts scripts/measure-host.cljs [--no-corpus]
;;
;; This exists so the header's numbers are reproducible with the SAME de-tag
;; verify-facts.cljs subtracts needles with -- see scripts/host_probe.cljs for
;; why that matters and what it cost the first time it did not.
;;
;; It measures; it does not judge. Nothing here exits non-zero on a surprise,
;; because a surprise here means the header needs updating, and the check that
;; a claim is still true is verify-facts.cljs's job. Keeping the two apart
;; stops this from becoming a second, quieter verifier that disagrees with the
;; first one.
;;
;; Three page hosts are measured, not one. That is not thoroughness -- it is
;; forced. This repository's subject is split across www.cao.go.jp,
;; www8.cao.go.jp and www.chisou.go.jp, and the three do not answer a missing
;; page alike.
;;
;; The corpus scan walks the whole e-gov law corpus in pages of 500 and is the
;; slow part. Pass --no-corpus to skip it.

(ns measure-host
  (:require [clojure.string :as str]
            [host-probe :as hp]))

(def ^:private argv (vec (drop 2 (js->clj js/process.argv))))
(def ^:private corpus? (not (some #{"--no-corpus"} argv)))

;; The phrases an author of THIS repository would reach for to prove a page is
;; the page they wanted. Whether each is usable as a needle is a property of
;; the host's chrome, not of how central the term feels -- so it is measured.
(def ^:private needles
  ["内閣府" "規制改革" "国家戦略特別区域" "国家戦略特区" "地方創生"
   "規制改革推進会議" "提案" "所管省庁" "特区" "総合調整"
   "規制のサンドボックス" "地域再生"])

(def ^:private hosts
  [{:name "www.cao.go.jp"
    :front "https://www.cao.go.jp/index.html"
    :missing "https://www.cao.go.jp/zzz-no-such-page-9f3c2a1b8e/"}
   {:name "www8.cao.go.jp"
    :front "https://www8.cao.go.jp/kisei-kaikaku/index.html"
    :missing "https://www8.cao.go.jp/zzz-no-such-page-9f3c2a1b8e/"}
   {:name "www.chisou.go.jp"
    :front "https://www.chisou.go.jp/index.html"
    :missing "https://www.chisou.go.jp/zzz-no-such-page-9f3c2a1b8e/"}])

;; Every page the register cites, so the "is any real page shorter than the
;; 404" question is answered over the actual set rather than a sample.
(def ^:private pages
  ["https://www8.cao.go.jp/kisei-kaikaku/index.html"
   "https://www8.cao.go.jp/kisei-kaikaku/kisei/hotline/h_index.html"
   "https://www8.cao.go.jp/kisei-kaikaku/kisei/publication/p_plan.html"
   "https://www8.cao.go.jp/kisei-kaikaku/kisei/publication/p_index.html"
   "https://www8.cao.go.jp/kisei-kaikaku/kisei/meeting/meeting.html"
   "https://www.chisou.go.jp/tiiki/kokusentoc/index.html"
   "https://www.chisou.go.jp/tiiki/kokusentoc/teian.html"
   "https://www.chisou.go.jp/tiiki/kokusentoc/kokkasenryakutoc.html"
   "https://www.chisou.go.jp/tiiki/toc/index.html"
   "https://www.chisou.go.jp/tiiki/toc/document.html"
   "https://www.cao.go.jp/about/pmf_index.html"
   "https://www.cao.go.jp/houan/index.html"
   "https://www.cao.go.jp/about/address.html"])

(def ^:private laws-base "https://laws.e-gov.go.jp/api/2/laws")

(defn- count-occurrences [hay needle]
  (loop [i 0 n 0]
    (let [j (str/index-of hay needle i)]
      (if j (recur (+ j (count needle)) (inc n)) n))))

(defn- sha256-hex [bytes]
  (-> (js/crypto.subtle.digest "SHA-256" bytes)
      (.then (fn [buf]
               (->> (js/Uint8Array. buf)
                    (map #(.padStart (.toString % 16) 2 "0"))
                    (apply str))))))

(defn- fetch-page
  "bytes AND characters, each labelled. Reporting one number without saying
   which unit it is in is how the first pass of this cohort's sibling register
   convinced itself a host was unstable."
  [request-url]
  (-> (hp/fetch-bytes request-url)
      (.then (fn [r]
               (if (:error r)
                 (js/Promise.resolve {:url request-url :error (:error r)})
                 (-> (sha256-hex (:bytes r))
                     (.then (fn [sha]
                              (let [txt (hp/decode-utf8-strict (:bytes r))
                                    plain (when txt (hp/de-tag txt))]
                                {:url (:url r)
                                 :requested request-url
                                 :status (:status r)
                                 :ctype (:ctype r)
                                 :redirected (:redirected r)
                                 :bytes (.-length (:bytes r))
                                 :sha (subs sha 0 12)
                                 :decodable (some? txt)
                                 :text plain
                                 :chars (when plain (count plain))
                                 :title (when txt (hp/page-title txt))})))))))))

(defn- seq-map [f xs]
  (reduce (fn [p x] (.then p (fn [acc] (.then (f x) #(conj acc %)))))
          (js/Promise.resolve []) xs))

(defn- scan-corpus
  "repeal_status and current_revision_status token counts over the WHOLE
   corpus, plus the joint distribution with remain_in_force. Counted, not
   assumed: a claim about which token means what is a claim about every row,
   and there is no way to make it from a sample."
  []
  (-> (hp/fetch-json (str laws-base "?limit=1"))
      (.then (fn [{:keys [json]}]
               (let [total (get json "total_count")]
                 (-> (js/Promise.all
                      (clj->js (map #(hp/fetch-json (str laws-base "?limit=500&offset=" %))
                                    (range 0 total 500))))
                     (.then (fn [rs]
                              (let [rows (for [r (js->clj rs :keywordize-keys true)
                                               law (get-in r [:json "laws"])]
                                           (let [rev (get law "revision_info")]
                                             {:repeal (get rev "repeal_status")
                                              :revision (get rev "current_revision_status")
                                              :remain (get rev "remain_in_force")}))]
                                {:total total
                                 :seen (count rows)
                                 :repeal (frequencies (map :repeal rows))
                                 :revision (frequencies (map :revision rows))
                                 :remain-true (count (filter :remain rows))
                                 :remain-true-and-not-repealed
                                 (count (filter #(and (:remain %) (= "None" (:repeal %))) rows))}))))))))) 

(defn- report-host [{:keys [name front missing]}]
  (-> (js/Promise.all #js [(fetch-page front) (fetch-page missing)
                           (hp/fetch-head missing) (hp/fetch-head front)
                           (hp/fetch-no-redirect missing)])
      (.then (fn [[f m hm hf nr]]
               (let [[f m hm hf nr] (map identity [f m hm hf nr])]
                 (println (str "\n-- " name))
                 (println (str "   front   " (:status f) "  " (:bytes f) " B  "
                               (:chars f) " chars  sha " (:sha f)
                               "\n           title " (pr-str (:title f))))
                 (println (str "   missing " (:status m) "  " (:bytes m) " B  "
                               (:chars m) " chars  sha " (:sha m)
                               "\n           title " (pr-str (:title m))
                               "\n           redirected-to-404? " (:redirected m)
                               "  final " (:url m)
                               "\n           unfollowed status " (:status nr)
                               " (fetch reports " (pr-str (:type nr)) ")"))
                 (println (str "   missing longer than front, in chars? "
                               (> (:chars m) (:chars f))))
                 (println (str "   HEAD missing content-length " (pr-str (:content-length hm))
                               " / GET " (:bytes m) " B"))
                 (println (str "   HEAD front   content-length " (pr-str (:content-length hf))
                               " / GET " (:bytes f) " B"))
                 (println "   needle occurrences on the MISSING body:")
                 (doseq [n needles]
                   (println (str "     " (.padEnd n 24) (count-occurrences (:text m) n))))
                 m)))))

(defn- report-pages [missing-by-host]
  (-> (seq-map fetch-page pages)
      (.then (fn [ps]
               (println "\n-- EVERY CITED PAGE, AGAINST ITS OWN HOST'S 404 (characters)")
               (doseq [p (sort-by :chars ps)]
                 (let [host (.-hostname (js/URL. (:url p)))
                       m404 (get missing-by-host host)
                       d (when m404 (- (:chars p) (:chars m404)))]
                   (println (str "   " (.padEnd (str (:chars p)) 8)
                                 (.padEnd (str (:bytes p) " B") 12)
                                 (if (and d (neg? d))
                                   (str "SHORTER than its 404 by " (- d) "  ")
                                   (str "+" d " vs 404  "))
                                 (:url p)))))))))

(println "cloud-itonami-iso3166-jpn-cao :: host measurement")
(println (str "measured " (subs (.toISOString (js/Date.)) 0 10)))

(-> (seq-map report-host hosts)
    (.then (fn [ms]
             (report-pages (into {} (map (fn [m] [(.-hostname (js/URL. (:url m))) m]) ms)))))
    (.then (fn [_]
             (when corpus?
               (-> (scan-corpus)
                   (.then (fn [c]
                            (println "\n-- e-gov CORPUS (counted, not assumed)")
                            (println (str "   total_count " (:total c) ", rows seen " (:seen c)))
                            (println (str "   repeal_status            " (pr-str (:repeal c))))
                            (println (str "   current_revision_status  " (pr-str (:revision c))))
                            (println (str "   remain_in_force true     " (:remain-true c)))
                            (println (str "   ...of those NOT repealed " (:remain-true-and-not-repealed c)
                                          "  <- if this is 0, remain_in_force is true only on dead law")))))))))
