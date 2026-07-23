(ns gsacompliance.gsacompliancellm
  "GSACompliance-LLM client -- the *contained intelligence node* for
  the USA-GSA (General Services Administration) compliance actor.

  It normalizes engagement intake, drafts a per-track (`:sam-
  registration` / `:mas-schedule` / `:gsam-supplement` / `:far-
  council`) compliance evidence checklist, drafts the filing-draft
  action, and drafts the filing-submit action. CRITICAL: it is a
  smart-but-untrusted advisor. It returns a *proposal* (with a
  rationale + the fields it cited), never a committed record or a real
  SAM.gov/GSA filing. Every output is censored downstream by
  `gsacompliance.governor` before anything touches the SSoT, and
  `:filing/draft`/`:filing/submit` proposals NEVER auto-commit at any
  phase -- see README Actuation.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. Three request-level test-injection flags exist purely to
  exercise the governor's fabrication-defense checks against a
  deliberately bad proposal, mirroring every sibling actor's `:no-
  spec?` pattern:

    :no-spec?                 -- forces an unregistered track (no
                                  spec-basis at all).
    :cite-duns?                -- forces a proposal that cites the
                                  RETIRED DUNS identifier as current
                                  (`:cites-legacy-duns? true`).
    :misattribute-far-owner?  -- forces a proposal that claims GSA is
                                  the sole owner-authority of the FAR
                                  itself (`:owner-authority-claim`)."
  (:require [gsacompliance.facts :as facts]
            [gsacompliance.store :as store]))

(defn- normalize-intake
  [_db {:keys [patch]}]
  {:summary    (str "engagement intake record updated: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :engagement/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-track
  "Per-track (`:sam-registration` / `:mas-schedule` / `:gsam-
  supplement` / `:far-council`) compliance evidence checklist draft.
  `:no-spec?`/`:cite-duns?`/`:misattribute-far-owner?` inject the
  failure modes we must defend against."
  [_db {:keys [track no-spec? cite-duns? misattribute-far-owner?]}]
  (let [track (if no-spec? :unknown-track track)
        sb (facts/spec-basis track)]
    (cond
      (nil? sb)
      {:summary    (str (name track) " の公式spec-basisが見つかりません")
       :rationale  "gsacompliance.facts に未登録のトラック。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:track track :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}

      cite-duns?
      {:summary    (str (name track) " (" (:owner-authority sb) ") -- テスト注入: 廃止済みDUNS引用")
       :rationale  "テスト注入: DUNS(2022-04-04付で完全廃止)を現行識別子として誤って引用するケース"
       :cites      [(:provenance sb)]
       :effect     :assessment/set
       :value      {:track track :checklist (:required-evidence sb) :spec-basis (:provenance sb)
                    :cites-legacy-duns? true}
       :stake      nil
       :confidence 0.85}

      misattribute-far-owner?
      {:summary    (str (name track) " -- テスト注入: FAR単独所有権誤帰属")
       :rationale  "テスト注入: FARの所有権をGSA単独と誤って主張するケース (正: FAR Council/DoD+GSA+NASA)"
       :cites      [(:provenance sb)]
       :effect     :assessment/set
       :value      {:track track :checklist (:required-evidence sb) :spec-basis (:provenance sb)
                    :owner-authority-claim "General Services Administration (GSA)"}
       :stake      nil
       :confidence 0.85}

      :else
      {:summary    (str (name track) " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 基盤: " (:basis sb))
       :cites      [(:basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:track track
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :basis (:basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- propose-draft
  "Draft the actual FILING-DRAFT action for `track`. ALWAYS `:stake
  :actuation/draft-filing`."
  [db {:keys [subject track]}]
  (let [e (store/engagement db subject)]
    {:summary    (str subject "/" (name track) " 向け提出ドラフト提案"
                      (when e (str " (operator=" (:operator e) ")")))
     :rationale  (if e
                   (str "track=" (name track) " portal=" (:portal e))
                   "engagementが見つかりません")
     :cites      (if e [subject (name track)] [])
     :effect     :engagement/mark-drafted
     :value      {:engagement-id subject :track track}
     :stake      :actuation/draft-filing
     :confidence (if e 0.9 0.3)}))

(defn- propose-submit
  "Draft the actual FILING-SUBMIT action for `track`. ALWAYS `:stake
  :actuation/submit-filing` -- real-world SAM.gov/GSA filing
  submission. Reflects readiness across the gates the governor
  independently re-verifies: SAM.gov registration/UEI status
  (`:mas-schedule` only, per `facts/requires-sam-registration-
  prerequisite-track?`) and, for a declared medical-products/services
  offering, VA-delegation acknowledgement."
  [db {:keys [subject track]}]
  (let [e (store/engagement db subject)
        sam-registration-ok? (or (not (facts/requires-sam-registration-prerequisite-track? track))
                                  (:sam-registration-verified? e))
        medical-schedule-ok? (or (not= track :mas-schedule)
                                  (not (:medical-schedule-offering? e))
                                  (:va-delegation-acknowledged? e))]
    {:summary    (str subject "/" (name track) " 向け提出提案"
                      (when e (str " (operator=" (:operator e) ")")))
     :rationale  (if e
                   (str "sam-registration-verified?=" (:sam-registration-verified? e)
                        " medical-schedule-offering?=" (:medical-schedule-offering? e)
                        " va-delegation-acknowledged?=" (:va-delegation-acknowledged? e)
                        " claimed-fee=" (:claimed-fee e))
                   "engagementが見つかりません")
     :cites      (if e [subject (name track)] [])
     :effect     :engagement/mark-submitted
     :value      {:engagement-id subject :track track}
     :stake      :actuation/submit-filing
     :confidence (if (and e sam-registration-ok? medical-schedule-ok?)
                   0.9 0.3)}))

(defprotocol Advisor
  (-advise [this db request] "Return a proposal map for `request`."))

(defrecord MockAdvisor []
  Advisor
  (-advise [_ db {:keys [op] :as request}]
    (case op
      :engagement/intake   (normalize-intake db request)
      :compliance/assess   (assess-track db request)
      :filing/draft        (propose-draft db request)
      :filing/submit       (propose-submit db request)
      {:summary "unknown op" :rationale "unsupported" :cites []
       :effect :noop :value {} :stake nil :confidence 0.0})))

(defn mock-advisor [] (->MockAdvisor))

(defn trace [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :subject (:subject request)
   :track (:track request)
   :summary (:summary proposal)
   :confidence (:confidence proposal)
   :stake (:stake proposal)})
