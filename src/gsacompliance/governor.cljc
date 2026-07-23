(ns gsacompliance.governor
  "GSA Federal-Procurement Compliance Governor -- the independent
  compliance layer that earns the GSACompliance-LLM the right to
  commit. The LLM has no notion of whether SAM.gov entity registration
  + the Unique Entity Identifier (UEI) are actually on file (and
  whether it just cited the RETIRED DUNS number instead -- DUNS was
  replaced by the UEI on 2022-04-04 and must never be cited as
  current), whether an operator has an active SAM.gov/UEI status
  before a GSA Multiple Award Schedule (MAS) offer may be submitted,
  whether an offering is actually a VA-delegated medical-products/
  services schedule being wrongly claimed under GSA's own MAS,
  whether the Federal Acquisition Regulation (FAR) itself is being
  misattributed to GSA alone (it is jointly governed by the FAR
  Council -- DoD + GSA + NASA -- GSA is only the sole owner of the
  GSAM supplement, not the FAR itself), whether a claimed engagement
  fee actually equals base + months x rate (+ optional export
  package), or when a draft stops being a draft and becomes a real
  SAM.gov/GSA filing, so this MUST be a separate system able to
  *reject* a proposal and fall back to HOLD.

  `:itonami.blueprint/governor` is `:gsa-federal-procurement-compliance-governor`
  (blueprint.edn).

  Eight checks, in priority order, ALL HARD violations except the
  confidence/actuation gate: a human approver CANNOT override the hard
  ones. The confidence/actuation gate is SOFT: it asks a human to look
  (low confidence / actuation), and the human may approve -- but see
  `gsacompliance.phase`: for `:stake :actuation/draft-filing`/
  `:actuation/submit-filing` NO phase ever allows auto-commit either.
  Two independent layers agree that actuation is always a human call.

    1. Spec-basis                       -- did the compliance-track
                                            proposal cite an OFFICIAL
                                            source
                                            (`gsacompliance.facts`), or
                                            invent one?
    2. Legacy-DUNS-citation             -- did the proposal cite DUNS
                                            (Dun & Bradstreet) as a
                                            CURRENT identifier? DUNS
                                            was fully retired
                                            2022-04-04 -- the SAM-
                                            generated UEI is the only
                                            current identifier. This is
                                            a fabrication-class HARD
                                            violation, not a stylistic
                                            nit.
    3. FAR-owner-misattribution         -- did the proposal claim GSA
                                            is the SOLE owner-authority
                                            of the Federal Acquisition
                                            Regulation itself? The FAR
                                            is jointly governed by the
                                            FAR Council (DoD/GSA/NASA);
                                            only the GSAM supplement is
                                            exclusively GSA's own
                                            document.
    4. Evidence incomplete              -- for `:filing/draft`/
                                            `:filing/submit`, has the
                                            track actually been
                                            assessed with a full
                                            evidence checklist on file?
    5. SAM-registration missing         -- for `:filing/submit` on the
                                            `:mas-schedule` track,
                                            INDEPENDENTLY verify
                                            `:sam-registration-
                                            verified?` is true -- an
                                            active SAM.gov entity
                                            registration + UEI is a
                                            practical prerequisite to
                                            any MAS offer
                                            (gsa.gov/buy-through-us).
    6. Medical-schedule misattribution  -- for `:filing/submit` on the
                                            `:mas-schedule` track, when
                                            the engagement declares
                                            `:medical-schedule-
                                            offering? true`,
                                            INDEPENDENTLY verify
                                            `:va-delegation-
                                            acknowledged?` is true --
                                            the VA holds authority
                                            DELEGATED FROM GSA for
                                            medical products/services;
                                            GSA's own MAS never covers
                                            that offering silently.
    7. Engagement fee mismatch          -- for `:filing/submit`,
                                            INDEPENDENTLY recompute
                                            whether the engagement's
                                            own `:claimed-fee` equals
                                            `base-fee + monthly-rate x
                                            monitoring-months` (+
                                            optional export-fee when
                                            `:audit-export?` is true)
                                            -- honest reapplication of
                                            the ground-truth-recompute
                                            discipline sibling actors
                                            use, matched against this
                                            repo's own three revenue
                                            lines.
    8. Confidence floor / actuation
       gate                              -- LLM confidence below
                                            threshold, OR the op is
                                            `:filing/draft`/`:filing/
                                            submit` (REAL acts) ->
                                            escalate.

  Two more guards, double-draft/double-submit prevention, are enforced
  off dedicated per-track `:sam-registration-drafted?`/
  `:sam-registration-submitted?`/`:mas-schedule-drafted?`/
  `:mas-schedule-submitted?` facts (never a single `:status` value)."
  (:require [gsacompliance.facts :as facts]
            [gsacompliance.registry :as registry]
            [gsacompliance.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Drafting a real GSA/SAM.gov compliance filing package and submitting
  a real SAM.gov/GSA filing are the two real-world actuation events
  this actor performs."
  #{:actuation/draft-filing :actuation/submit-filing})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:compliance/assess` (or `:filing/draft`/`:filing/submit`)
  proposal with no spec-basis citation is a HARD violation -- never
  invent SAM.gov's or GSA's requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:compliance/assess :filing/draft :filing/submit} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案はコンプライアンス要件として扱えない"}]))))

(defn- legacy-duns-citation-violations
  "A proposal that cites DUNS (Dun & Bradstreet) as a CURRENT
  identifier is a HARD violation, unconditional. DUNS was fully
  retired 2022-04-04 -- the SAM-generated Unique Entity Identifier
  (UEI) is the only current identifier."
  [_request proposal]
  (when (true? (:cites-legacy-duns? (:value proposal)))
    [{:rule :legacy-duns-citation
      :detail "DUNSは2022-04-04付で完全に廃止済み -- 現行識別子として引用不可。SAM生成のUEIのみが現行"}]))

(defn- far-owner-misattribution-violations
  "A proposal that claims GSA is the SOLE owner-authority of the
  Federal Acquisition Regulation itself is a HARD violation. The FAR
  is jointly governed by the FAR Council (DoD/GSA/NASA) -- only the
  GSAM supplement is exclusively GSA's own document."
  [_request proposal]
  (let [claim (:owner-authority-claim (:value proposal))]
    (when (and claim (not= claim (facts/correct-far-owner-authority)))
      [{:rule :far-owner-misattribution
        :detail (str "FARの唯一の所有権者としてGSA単独を主張する提案は不可 -- 正しい owner-authority は \""
                    (facts/correct-far-owner-authority) "\" (claim=\"" claim "\")")}])))

(defn- evidence-incomplete-violations
  "For `:filing/draft`/`:filing/submit`, the track's required
  evidence checklist must actually be satisfied."
  [{:keys [op subject track]} st]
  (when (contains? #{:filing/draft :filing/submit} op)
    (let [assessment (store/assessment-of st subject track)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      track (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail (str subject "/" (name track) " の必要書類が充足していない状態での提案")}]))))

(defn- sam-registration-missing-violations
  "For `:filing/submit` on `:mas-schedule`, INDEPENDENTLY verify
  `:sam-registration-verified?` is true -- an active SAM.gov entity
  registration + UEI is a practical prerequisite to any MAS offer.
  CONDITIONAL on the track's own `:requires-sam-registration?` ground
  truth (`gsacompliance.facts`)."
  [{:keys [op subject track]} st]
  (when (and (= op :filing/submit)
             (facts/requires-sam-registration-prerequisite-track? track))
    (let [e (store/engagement st subject)]
      (when-not (true? (:sam-registration-verified? e))
        [{:rule :sam-registration-missing
          :detail (str subject " はSAM.gov登録/UEIが未確認 -- MASオファー提出提案は進められない")}]))))

(defn- medical-schedule-misattribution-violations
  "For `:filing/submit` on `:mas-schedule`, when the engagement
  declares `:medical-schedule-offering? true`, INDEPENDENTLY verify
  `:va-delegation-acknowledged?` is true -- VA holds authority
  DELEGATED FROM GSA for medical products/services; GSA's own MAS
  never silently covers that offering."
  [{:keys [op subject track]} st]
  (when (and (= op :filing/submit) (= track :mas-schedule))
    (let [e (store/engagement st subject)]
      (when (and (true? (:medical-schedule-offering? e))
                 (not (true? (:va-delegation-acknowledged? e))))
        [{:rule :medical-schedule-misattribution
          :detail (str subject " は医療品/サービスのオファリング -- VA委任スケジュールの確認 (va-delegation-acknowledged?) 無しにGSA MASとして提出できない")}]))))

(defn- engagement-fee-mismatch-violations
  "For `:filing/submit`, INDEPENDENTLY recompute whether the
  engagement's own claimed fee equals base + months x rate (+ optional
  export-fee)."
  [{:keys [op subject]} st]
  (when (= op :filing/submit)
    (let [e (store/engagement st subject)]
      (when-not (registry/engagement-fee-matches-claim? e)
        [{:rule :engagement-fee-mismatch
          :detail (str subject " の申告手数料(" (:claimed-fee e)
                      ")が独立再計算値(" (registry/compute-engagement-fee e) ")と一致しない")}]))))

(defn- already-drafted-violations
  "For `:filing/draft`, refuses to draft the SAME engagement/track
  twice."
  [{:keys [op subject track]} st]
  (when (= op :filing/draft)
    (when (store/engagement-track-drafted? st subject track)
      [{:rule :already-drafted
        :detail (str subject "/" (name track) " は既にドラフト済み")}])))

(defn- already-submitted-violations
  "For `:filing/submit`, refuses to submit the SAME engagement/track
  twice."
  [{:keys [op subject track]} st]
  (when (= op :filing/submit)
    (when (store/engagement-track-submitted? st subject track)
      [{:rule :already-submitted
        :detail (str subject "/" (name track) " は既に提出済み")}])))

(defn check
  "Censors a GSACompliance-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (legacy-duns-citation-violations request proposal)
                           (far-owner-misattribution-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (sam-registration-missing-violations request st)
                           (medical-schedule-misattribution-violations request st)
                           (engagement-fee-mismatch-violations request st)
                           (already-drafted-violations request st)
                           (already-submitted-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :track      (:track request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
