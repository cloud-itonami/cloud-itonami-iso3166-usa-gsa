(ns gsacompliance.facts
  "U.S. General Services Administration (GSA) federal-procurement /
  entity-registration compliance catalog -- the ONLY source of
  regulatory-requirement facts this actor is allowed to cite
  (`gsacompliance.governor`'s spec-basis check enforces that every
  proposal touching `:compliance/assess`, `:filing/draft`, or
  `:filing/submit` cites this catalog and nothing invented).

  Every fact below was verified via web search against `sam.gov`,
  `gsa.gov`, `acquisition.gov`, and `en.wikipedia.org` during this
  repo's research pass (2026-07-22/23). Four catalog entries, each with
  its own owner-authority and scope -- do NOT merge them into one
  undifferentiated 'GSA requirement':

    :sam-registration  -- System for Award Management (SAM.gov)
                           government-wide entity registration + the
                           Unique Entity Identifier (UEI). GSA operates
                           the platform. UEI REPLACED the legacy DUNS
                           (Dun & Bradstreet) identifier on 2022-04-04
                           -- DUNS is fully retired; FAR/2 CFR were
                           revised to remove proprietary-DUNS
                           references. This actor treats citing DUNS as
                           a current identifier (on or after that
                           transition) as a fabrication-class HARD
                           violation, not a stylistic nit.
    :mas-schedule       -- GSA Multiple Award Schedule (MAS, aka 'GSA
                           Schedule'): sellers submit an 'offer' to GSA
                           to become a MAS contractor; the solicitation
                           and required templates are posted ON SAM.gov.
                           IMPORTANT NUANCE: the Department of Veterans
                           Affairs (VA) holds authority DELEGATED FROM
                           GSA to run a parallel schedule for medical
                           products/services -- this actor never
                           attributes VA's medical-schedule authority
                           to GSA alone, and never conflates the two
                           schedules as one program.
    :gsam-supplement    -- General Services Administration Acquisition
                           Manual (GSAM): GSA-issued regulation
                           supplementing the FAR, GSA's own
                           agency-specific acquisition rules, revised
                           frequently. `acquisition.gov/gsam` is cited
                           as the living source of record -- this
                           catalog deliberately does NOT hardcode a
                           specific Change/edition number as a
                           permanent fact (one was live during
                           research, but editions turn over).
    :far-council        -- disambiguation entry, not itself a filing
                           track: the Federal Acquisition Regulation
                           (FAR) is jointly governed by the FAR Council
                           = Secretary of Defense + GSA Administrator +
                           NASA Administrator. GSA is ONE of three
                           co-owners of the FAR itself, NOT its sole
                           owner -- only GSAM (the supplement) is
                           exclusively GSA's own document. This actor
                           treats attributing sole FAR ownership to GSA
                           as a fabrication-class HARD violation,
                           symmetric with the DUNS check above.

  What this catalog deliberately does NOT claim:
    - DFARS or CMMC (DoD-specific acquisition/cybersecurity regimes --
      out of scope for this GSA-specific actor; a sibling actor covers
      DoD's own agency-specific domain);
    - SCA/DBRA/OFCCP (Department of Labor-specific labor-standards
      regimes -- out of scope for this GSA-specific actor; a sibling
      actor covers DOL's own agency-specific domain);
    - any specific SAM.gov registration processing-time SLA, MAS
      solicitation numeric pricing/discount threshold, or GSAM
      Change/edition number as a permanent fact (none was verified as
      stable enough to hardcode).")

(def catalog
  {:sam-registration
   {:name "System for Award Management (SAM.gov) Entity Registration + Unique Entity Identifier (UEI)"
    :name-en "SAM.gov entity registration + UEI"
    :owner-authority "General Services Administration (GSA) -- SAM.gov platform operator"
    :basis
    "GSA-operated, government-wide entity registration required to receive federal contracts and grants; free, requires annual renewal."
    :official-portal "https://sam.gov/content/home"
    :provenance "https://sam.gov/content/home"
    :provenance-secondary
    ["https://en.wikipedia.org/wiki/System_for_Award_Management"
     "https://en.wikipedia.org/wiki/Data_Universal_Numbering_System"]
    :process-description
    "Free, government-wide entity registration on SAM.gov required to receive federal contracts/grants; requires annual renewal. Since 2022-04-04, SAM.gov generates the Unique Entity Identifier (UEI) used government-wide; the legacy DUNS (Dun & Bradstreet proprietary) number is fully retired and FAR/2 CFR were revised to remove proprietary-DUNS references."
    :uei-replaced-duns? true
    :uei-transition-date "2022-04-04"
    :duns-retired? true
    :duns-retirement-date "2022-04-04"
    :duns-retirement-note
    "Before 2022-04-04 (Oct 2003 - Apr 2022), DUNS was required. After 2022-04-04, only the SAM-generated UEI is current -- DUNS must never be cited as a current identifier."
    :required-evidence
    ["SAM.gov entity registration confirmation (active, not expired)"
     "SAM-generated Unique Entity Identifier (UEI) assignment record -- NOT a legacy DUNS number"
     "Most recent annual SAM.gov registration renewal record"]}

   :mas-schedule
   {:name "GSA Multiple Award Schedule (MAS) -- \"GSA Schedule\""
    :name-en "GSA Multiple Award Schedule (MAS)"
    :owner-authority "General Services Administration (GSA)"
    :basis
    "Program letting eligible government buyers purchase commercial products/services at pre-negotiated prices; sellers submit an 'offer' to GSA to become a MAS contractor."
    :official-portal "https://www.gsa.gov/buy-through-us/purchasing-programs/gsa-multiple-award-schedule"
    :provenance "https://www.gsa.gov/buy-through-us/purchasing-programs/gsa-multiple-award-schedule"
    :offer-submission-portal
    "SAM.gov -- the MAS solicitation and required offer templates are posted ON SAM.gov, not a separate GSA-only site."
    :process-description
    "Sellers submit an 'offer' to GSA to become a MAS contractor; the solicitation and required templates are posted on SAM.gov, so an active SAM.gov entity registration + UEI is a practical prerequisite to submitting any MAS offer."
    :requires-sam-registration? true
    :medical-schedule-delegation
    "The Department of Veterans Affairs (VA) holds authority DELEGATED FROM GSA to run a parallel schedule for medical products/services. Do NOT attribute VA's medical-schedule authority to GSA alone, and do NOT conflate the two schedules as one program."
    :required-evidence
    ["Active SAM.gov entity registration + valid UEI on file (practical prerequisite to any MAS offer, per :sam-registration)"
     "MAS offer package prepared against the SAM.gov-posted solicitation and required templates"
     "Confirmation the offering is NOT a VA-delegated medical-products/services schedule (or, if it is, correctly routed to VA's schedule instead of GSA's)"]}

   :gsam-supplement
   {:name "General Services Administration Acquisition Manual (GSAM)"
    :name-en "GSAM"
    :owner-authority "General Services Administration (GSA)"
    :basis
    "GSA-issued regulation supplementing the Federal Acquisition Regulation (FAR) -- GSA's own agency-specific acquisition rules, revised frequently."
    :official-portal "https://www.acquisition.gov/gsam"
    :provenance "https://www.acquisition.gov/gsam"
    :process-description
    "acquisition.gov/gsam is the living source of record for the current GSAM text. A specific numbered edition ('Change N') was live during this catalog's research pass, but is NOT hardcoded here as a permanent fact -- GSAM is revised frequently and any citation must point to the current acquisition.gov/gsam text, not a cached edition number."
    :far-supplement? true
    :required-evidence
    ["Current acquisition.gov/gsam citation for the specific clause referenced (no cached Change/edition number treated as permanent)"]}

   :far-council
   {:name "Federal Acquisition Regulation (FAR) -- FAR Council joint ownership"
    :name-en "FAR Council (DoD / GSA / NASA)"
    :owner-authority "FAR Council (Secretary of Defense + GSA Administrator + NASA Administrator) -- NOT GSA alone"
    :basis
    "The FAR is jointly governed by the FAR Council. GSA is ONE of three co-owners of the FAR itself, not its sole owner -- GSAM specifically (the supplement, see :gsam-supplement) IS exclusively GSA's own document."
    :provenance "https://en.wikipedia.org/wiki/Federal_Acquisition_Regulation"
    :gsa-sole-owner? false
    :process-description
    "This entry exists to police owner-authority attribution, not to gate a filing: any proposal that cites a generic FAR requirement must record owner-authority as 'FAR Council (DoD/GSA/NASA)', never 'GSA' alone. Reserve 'GSA' as sole owner-authority for GSAM and for MAS/SAM.gov-operator facts specifically (see :gsam-supplement, :mas-schedule, :sam-registration)."
    :required-evidence
    ["Owner-authority attribution for any generic FAR citation records 'FAR Council (DoD/GSA/NASA)', never 'GSA' alone"]}})

(def valid-tracks (set (keys catalog)))

;; Tracks this actor actually drafts/submits filings for. :gsam-supplement
;; and :far-council are citation-only spec-basis entries (mirrors JPN-MOD's
;; :procurement-qualification) -- referenced for owner-authority checks,
;; never themselves the subject of a :filing/draft or :filing/submit.
(def filing-tracks #{:sam-registration :mas-schedule})

(defn spec-basis [track] (get catalog track))

(defn coverage
  ([] (coverage (keys catalog)))
  ([tracks]
   (let [have (filter catalog tracks) missing (remove catalog tracks)]
     {:requested (count tracks) :covered (count have)
      :covered-tracks (vec (sort (map name have)))
      :missing-tracks (vec (sort (map name missing)))
      :note "R0 catalog seed -- SAM.gov/UEI registration + MAS Schedule + GSAM-as-FAR-supplement + FAR-Council joint-ownership disambiguation, USA-GSA agency scope"})))

(defn required-evidence-satisfied? [track submitted]
  (when-let [{:keys [required-evidence]} (spec-basis track)]
    (= (count required-evidence) (count (filter (set submitted) required-evidence)))))

(defn evidence-checklist [track] (:required-evidence (spec-basis track) []))

(defn requires-sam-registration-prerequisite-track?
  "Does `track`'s spec-basis document an active SAM.gov registration +
  UEI as a practical prerequisite before that track's filing may be
  submitted? (Only `:mas-schedule` today.)"
  [track]
  (boolean (:requires-sam-registration? (spec-basis track))))

(defn correct-far-owner-authority
  "The single correct owner-authority string for the FAR itself (NOT
  the GSAM supplement, NOT SAM.gov/MAS which ARE GSA's own). Used by
  the governor to reject any proposal that fabricates sole-GSA
  ownership of the FAR."
  []
  (:owner-authority (spec-basis :far-council)))
