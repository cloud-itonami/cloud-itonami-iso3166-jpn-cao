;; -- mutation-check.cljs ------------------------------------------------
;; Breaks facts.edn one way at a time and requires verify-facts.cljs to object
;; FOR THE REASON IT NAMES. This is the fleet gate's (scripts/
;; itonami-verify-proposal.cljs) in-repo mutation suite: it prints a
;; caught=/inconclusive=/not-caught= tally that the gate parses, so the gate
;; knows cao's verifier still discriminates before it will accept a proposal
;; against this repository.
;;
;;   nbb scripts/mutation-check.cljs
;;
;; WHY THE REASON AND NOT THE COLOUR.
;; A negative test that asserts only "the run went red" counts a run that went
;; red for an unrelated cause as a discriminating one. Every mutation below
;; declares the exit code AND the reason token it must produce, and a run that
;; goes red the wrong way is a MISMATCH, not a catch.
;;
;; THE CASES ARE CAO'S OWN.
;; cao has long carried this suite as scripts/break-tests.cljs -- the same
;; mutations, the same anchors, the same asserted reasons and exit codes. This
;; file restates those cases in the gate's accounting format so the fleet gate
;; can read cao's discrimination; it does not add, remove or relax a check.
;; The control case (unmodified register, must pass) is asserted first and a
;; suite that cannot see it is broken.
;;
;; Pacing.
;; cao's hosts serve a bot challenge when asked too often (facts.edn's header
;; measures it). Each case is a full live run, so a gap is held between cases;
;; a case that comes back blocked is reported INCONCLUSIVE rather than counted
;; either way, because a blocked run establishes nothing about the mutation.
;;
;; EXIT: 0 all mutations caught / 1 some mutation not caught or the control
;; failed / 2 a refusal stood in the way (do not read as a pass).

(ns mutation-check
  (:require ["fs" :as fs]
            ["child_process" :as cp]
            ["os" :as os]
            ["path" :as path]
            [clojure.string :as str]))

(def base-text (fs/readFileSync "facts.edn" "utf8"))

(def verifier-path "scripts/verify-facts.cljs")

(defn- tmpfile [n] (path/join (os/tmpdir) (str "cao-mutation-" n ".edn")))

(defn- run-verifier
  "Returns {:exit :out} with the run against the given facts file."
  [facts-path]
  (let [r (cp/spawnSync "nbb" (clj->js [verifier-path facts-path])
                        #js {:encoding "utf8" :timeout 600000})]
    {:exit (if (nil? (.-status r)) 124 (.-status r))
     :out  (str (.-stdout r) (.-stderr r))}))

;; Textual substitution, deliberately NOT structural: the point is to produce
;; a file a person could plausibly have written, and to fail loudly if the
;; anchor is not found rather than silently testing an unmutated file.
(defn- mutate
  [from to]
  (when-not (str/includes? base-text from)
    (throw (js/Error. (str "anchor not found in facts.edn: " (pr-str from)
                           " -- this mutation would have tested nothing"))))
  (str/replace base-text from to))

;; -- the cases -----------------------------------------------------------
;; Every case is cao's existing break-tests.cljs case, restated. :want-exit is
;; the process exit code a correct verifier must produce; :want-reason is a
;; literal token that must appear in its output. Both must match.
(def cases
  [{:id "control"
    :why "the control. Without it every case below passes for a verifier that always fails."
    :facts-fn (fn [_] base-text)
    :want-exit 0
    :want-reason "OK -- all"}

   {:id "law-title-drift"
    :why "an ordinary finding about the world: exit 1."
    :facts-fn #(mutate ":law/title \"国家戦略特別区域法\"" ":law/title \"国家戦略特別区域法（旧）\"")
    :want-exit 1
    :want-reason ":law/title-mismatch"}

   {:id "repeal-drift"
    :why "the authority says Repeal and the register says None -- a live statute the day after repeal."
    :facts-fn #(mutate ":law/promulgated \"2018-05-23\"\n  :law/repeal-status \"Repeal\""
                       ":law/promulgated \"2018-05-23\"\n  :law/repeal-status \"None\"")
    :want-exit 1
    :want-reason ":law/repeal-mismatch"}

   {:id "revision-drift"
    :why "repeal_status matches; only current_revision_status sees a superseded revision."
    :facts-fn #(mutate ":law/revision-status \"PreviousEnforced\""
                       ":law/revision-status \"CurrentEnforced\"")
    :want-exit 1
    :want-reason ":law/revision-mismatch"}

   {:id "needle-on-own-host-404"
    :why "a broken check, not a changed page: exit 2, REFUSED."
    :facts-fn #(mutate ":page/needle \"規制改革関係府省庁連絡会議\"" ":page/needle \"内閣府\"")
    :want-exit 2
    :want-reason ":refused/needle-on-404"}

   {:id "chisou-needle-on-chisou-404"
    :why "needles are subtracted per host; 地方創生 is chrome on chisou but absent from the CAO 404."
    :facts-fn #(mutate ":page/needle \"構造改革特区\"" ":page/needle \"地方創生\"")
    :want-exit 2
    :want-reason ":refused/needle-on-404"}

   {:id "deleted-document"
    :why "a deleted .xlsx answers 404 with HTML; only status and magic-byte checks see it."
    :facts-fn #(mutate "https://www.chisou.go.jp/tiiki/kokusentoc/zuijiteianyoshiki.xlsx"
                       "https://www.chisou.go.jp/tiiki/kokusentoc/zuijiteianyoshiki-gone.xlsx")
    :want-exit 1
    :want-reason ":form/bad-status"}

   {:id "orphaned-citation"
    :why "the file is alive; only the link half sees that nothing cites it."
    :facts-fn #(mutate ":form/linked-from \"https://www.chisou.go.jp/tiiki/kokusentoc/teian.html\"\n  :form/basis [\"国家戦略特別区域法\"]\n  :source/verify :verify/form\n  :source/covers\n  \"The actual proposal form"
                       ":form/linked-from \"https://www.chisou.go.jp/tiiki/toc/document.html\"\n  :form/basis [\"国家戦略特別区域法\"]\n  :source/verify :verify/form\n  :source/covers\n  \"The actual proposal form")
    :want-exit 1
    :want-reason ":form/orphaned-citation"}

   {:id "probe-no-longer-404"
    :why "every needle on that host is subtracted from that body; if it is not a missing page the pages mean nothing."
    :facts-fn #(mutate ":host/missing-probe \"https://www.chisou.go.jp/zzz-no-such-page-9f3c2a1b8e/\""
                       ":host/missing-probe \"https://www.chisou.go.jp/index.html\"")
    :want-exit 2
    :want-reason ":refused/missing-probe-not-404"}

   {:id "host-stops-redirecting"
    :why "chisou answers 302 before 404; a client that stopped following would see neither."
    :facts-fn #(mutate ":host/missing-redirects? true" ":host/missing-redirects? false")
    :want-exit 1
    :want-reason ":host/redirect-behaviour-changed"}

   {:id "head-behaviour-drift"
    :why "www serves no HEAD length and www8 serves an accurate one, while sharing a 404 body."
    :facts-fn #(mutate ":host/front-page \"https://www.cao.go.jp/index.html\"\n  :host/missing-redirects? false\n  :host/missing-longer-than-front? false\n  :host/head-reports-content-length? false"
                       ":host/front-page \"https://www.cao.go.jp/index.html\"\n  :host/missing-redirects? false\n  :host/missing-longer-than-front? false\n  :host/head-reports-content-length? true")
    :want-exit 1
    :want-reason ":host/head-behaviour-changed"}

   {:id "token-uncontrolled"
    :why "dropping a control does not fail any citation; only the coverage check notices."
    :facts-fn #(mutate ":host/repeal-tokens [\"Repeal\" \"Expire\" \"LossOfEffectiveness\"]"
                       ":host/repeal-tokens [\"Repeal\" \"Expire\" \"LossOfEffectiveness\" \"Suspended\"]")
    :want-exit 1
    :want-reason ":host/token-uncontrolled"}

   {:id "corpus-size-drift"
    :why "the token frequencies in facts.edn's header are a claim about a specific corpus."
    :facts-fn #(mutate ":host/corpus-size 9551" ":host/corpus-size 9549")
    :want-exit 1
    :want-reason ":host/corpus-size-changed"}])

;; -- driving -------------------------------------------------------------
(defn- verdict [m {:keys [exit out]}]
  (let [reason-seen (str/includes? out (:want-reason m))
        refused-any (str/includes? out "REFUSED --")]
    (cond
      (= exit (:want-exit m)) (and reason-seen
                                   {:state :caught
                                    :note (str "exit " exit ", reason " (pr-str (:want-reason m)))})

      refused-any
      {:state :inconclusive
       :note (str "the verifier could not answer (REFUSED, exit " exit
                  ") -- a blocked run establishes nothing about the mutation")}

      (or (= 124 exit) (= 137 exit))
      {:state :inconclusive
       :note (str "the verifier timed out (exit " exit
                  ") -- a run that did not finish establishes nothing")}

      (= exit 0)
      {:state :missed :note "the verifier reported OK on a register that is wrong"}

      (not reason-seen)
      {:state :wrong-reason
       :note (str "went red (exit " exit ") but never named " (pr-str (:want-reason m))
                  " -- red for some other cause is not a discriminating run")}

      :else
      {:state :wrong-exit
       :note (str "named the reason but exited " exit ", wanted " (:want-exit m))})))

(defn- report [rs]
  (doseq [r rs]
    (println (str "  " (case (:state r)
                           :caught "CAUGHT      "
                           :missed "MISSED      "
                           :wrong-reason "WRONG-REASON"
                           :wrong-exit "WRONG-EXIT  "
                           :inconclusive "INCONCLUSIVE")
                  "\t" (:id r) "\t" (:note r))))
  rs)

(defn- run-one
  "Run one mutation, clean its temp file, return its verdict map with :id and :why."
  [n m]
  (let [f (tmpfile n)]
    (fs/writeFileSync f ((:facts-fn m) m))
    (let [r (run-verifier f)]
      (fs/unlinkSync f)
      (assoc (verdict m r) :id (:id m) :why (:why m)))))

(defn- main []
  (println (str "Running " (count cases) " end-to-end mutations against the live authorities."))
  (println "Each is a full run of verify-facts.cljs as a subprocess; pacing between cases.")
  (println)
  (let [rs (mapv run-one (range (count cases)) cases)]
    (report rs)
    (let [caught (filterv #(= :caught (:state %)) rs)
          incon  (filterv #(= :inconclusive (:state %)) rs)
          bad    (filterv #(#{:missed :wrong-reason :wrong-exit} (:state %)) rs)]
      (println)
      (println (str "caught=" (count caught) " inconclusive=" (count incon)
                    " not-caught=" (count bad) " of " (count rs)))
      (when (seq incon)
        (println (str "\u26a0 " (count incon) " mutation(s) were never actually tested. "
                      "Re-run them; do not read this as a pass.")))
      (cond
        (seq bad) (do (println (str "FAIL\tthe verifier does not discriminate these"))
                      (js/process.exit 1))
        (seq incon) (do (println (str "REFUSED\tsome mutations could not be tested"))
                        (js/process.exit 2))
        :else (do (println (str "OK\t" (count caught) " mutation(s), each caught by its own reason"))
                  (js/process.exit 0))))))

(main)
