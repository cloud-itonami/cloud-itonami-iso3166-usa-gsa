(ns gsacompliance.store
  "SSoT for the USA-GSA (General Services Administration) compliance
  actor, behind a `Store` protocol so the backend is a swap, not a
  rewrite -- the same seam every prior cloud-itonami actor in this
  fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store, using `langchain-store.core` for the
                        shared EDN-blob codec + event-log helpers
                        instead of a hand-rolled `enc`/`dec*`
                        (ADR-2607141600).

  Both implement the same protocol and pass the same contract
  (test/gsacompliance/store_contract_test.clj).

  The primary entity here is an `engagement` -- one operator's
  compliance engagement carrying BOTH filing tracks:

    :sam-registration -- SAM.gov entity registration + Unique Entity
                         Identifier (UEI) -- DUNS is fully retired
                         since 2022-04-04, never a current identifier.
    :mas-schedule      -- GSA Multiple Award Schedule ('GSA Schedule')
                         offer.

  plus engagement-level (not per-track) gating facts grounded in
  `gsacompliance.facts`:
    :sam-registration-verified? -- independently-verified SAM.gov/UEI
                                   status, gates `:mas-schedule`
                                   `:filing/submit` per :mas-schedule's
                                   `:requires-sam-registration?` fact.
    :medical-schedule-offering? / :va-delegation-acknowledged? -- the
                                   VA-delegated-medical-schedule
                                   disambiguation from :mas-schedule's
                                   `:medical-schedule-delegation` fact.

  filing-draft and filing-submit actuation events apply per-TRACK to
  the SAME engagement record (draft first, submit later, independently
  for each track). Dedicated double-actuation-guard booleans per track
  (`:sam-registration-drafted?`/`:sam-registration-submitted?`/
  `:mas-schedule-drafted?`/`:mas-schedule-submitted?`, never a single
  `:status` value).

  The ledger stays append-only on every backend."
  (:require [gsacompliance.registry :as registry]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (engagement [s id])
  (all-engagements [s])
  (assessment-of [s engagement-id track] "committed track assessment, or nil")
  (ledger [s])
  (draft-history [s] "the append-only filing-draft history")
  (submit-history [s] "the append-only filing-submit history")
  (next-draft-sequence [s track])
  (next-submit-sequence [s track])
  (engagement-track-drafted? [s engagement-id track])
  (engagement-track-submitted? [s engagement-id track])
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-engagements [s engagements] "replace/seed the engagement directory"))

;; ----------------------- track-scoped field mapping -----------------------
;; No dynamic keyword construction -- each track's drafted?/submitted?/
;; draft-number/submit-number fields are explicit, named keys (mirrors
;; the rest of this fleet's explicit-boolean-field style).

(def ^:private track-fields
  {:sam-registration {:drafted? :sam-registration-drafted?   :draft-number :sam-registration-draft-number
                       :submitted? :sam-registration-submitted? :submit-number :sam-registration-submit-number}
   :mas-schedule     {:drafted? :mas-schedule-drafted?   :draft-number :mas-schedule-draft-number
                       :submitted? :mas-schedule-submitted? :submit-number :mas-schedule-submit-number}})

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained engagement set covering both actuation
  lifecycles (draft, submit) across both filing tracks, plus the
  governor's own dossier-grounded checks: a clean case (eng-1, includes
  the compliance-audit export package revenue line, SAM.gov/UEI
  already verified), a fabrication-defense case (eng-2, used for the
  no-spec-basis / legacy-DUNS-citation / FAR-sole-owner-misattribution
  request-level probes), a fee-mismatch case (eng-3), a missing-SAM-
  registration case (eng-4, `:mas-schedule` track submitted before
  `:sam-registration-verified?` is true), and an unacknowledged
  VA-medical-schedule-delegation case (eng-5, `:medical-schedule-
  offering? true` with `:va-delegation-acknowledged? false`)."
  []
  {:engagements
   {"eng-1" {:id "eng-1" :operator "Meridian Federal Solutions LLC" :portal "SAM.gov / GSA eLibrary"
             :base-fee 750000 :monthly-rate 45000 :monitoring-months 12
             :audit-export? true :export-fee 140000 :claimed-fee 1430000.0
             :sam-registration-verified? true
             :medical-schedule-offering? false :va-delegation-acknowledged? false
             :sam-registration-drafted? false :sam-registration-submitted? false
             :mas-schedule-drafted? false :mas-schedule-submitted? false
             :status :intake}
    "eng-2" {:id "eng-2" :operator "Cascadia Federal Partners LLC" :portal "SAM.gov / GSA eLibrary"
             :base-fee 750000 :monthly-rate 45000 :monitoring-months 12
             :audit-export? true :export-fee 140000 :claimed-fee 1430000.0
             :sam-registration-verified? true
             :medical-schedule-offering? false :va-delegation-acknowledged? false
             :sam-registration-drafted? false :sam-registration-submitted? false
             :mas-schedule-drafted? false :mas-schedule-submitted? false
             :status :intake}
    "eng-3" {:id "eng-3" :operator "Palisade Federal Systems LLC" :portal "SAM.gov / GSA eLibrary"
             :base-fee 750000 :monthly-rate 45000 :monitoring-months 12
             :audit-export? false :export-fee nil :claimed-fee 1700000.0
             :sam-registration-verified? true
             :medical-schedule-offering? false :va-delegation-acknowledged? false
             :sam-registration-drafted? false :sam-registration-submitted? false
             :mas-schedule-drafted? false :mas-schedule-submitted? false
             :status :intake}
    "eng-4" {:id "eng-4" :operator "Redstone Federal Advisory LLC" :portal "SAM.gov / GSA eLibrary"
             :base-fee 750000 :monthly-rate 45000 :monitoring-months 12
             :audit-export? false :export-fee nil :claimed-fee 1290000.0
             :sam-registration-verified? false
             :medical-schedule-offering? false :va-delegation-acknowledged? false
             :sam-registration-drafted? false :sam-registration-submitted? false
             :mas-schedule-drafted? false :mas-schedule-submitted? false
             :status :intake}
    "eng-5" {:id "eng-5" :operator "Beacon Federal Health Supply LLC" :portal "SAM.gov / GSA eLibrary"
             :base-fee 750000 :monthly-rate 45000 :monitoring-months 12
             :audit-export? false :export-fee nil :claimed-fee 1290000.0
             :sam-registration-verified? true
             :medical-schedule-offering? true :va-delegation-acknowledged? false
             :sam-registration-drafted? false :sam-registration-submitted? false
             :mas-schedule-drafted? false :mas-schedule-submitted? false
             :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- draft-filing!
  [s engagement-id track]
  (let [seq-n (next-draft-sequence s track)
        result (registry/register-draft engagement-id track seq-n)
        {:keys [drafted? draft-number]} (get track-fields track)]
    {:result result
     :engagement-patch {drafted? true
                        draft-number (get result "draft_number")}}))

(defn- submit-filing!
  [s engagement-id track]
  (let [seq-n (next-submit-sequence s track)
        result (registry/register-submit engagement-id track seq-n)
        {:keys [submitted? submit-number]} (get track-fields track)]
    {:result result
     :engagement-patch {submitted? true
                        submit-number (get result "submit_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (engagement [_ id] (get-in @a [:engagements id]))
  (all-engagements [_] (sort-by :id (vals (:engagements @a))))
  (assessment-of [_ engagement-id track] (get-in @a [:assessments engagement-id track]))
  (ledger [_] (:ledger @a))
  (draft-history [_] (:draft-records @a))
  (submit-history [_] (:submit-records @a))
  (next-draft-sequence [_ track] (get-in @a [:draft-sequences track] 0))
  (next-submit-sequence [_ track] (get-in @a [:submit-sequences track] 0))
  (engagement-track-drafted? [_ engagement-id track]
    (boolean (get-in @a [:engagements engagement-id (:drafted? (get track-fields track))])))
  (engagement-track-submitted? [_ engagement-id track]
    (boolean (get-in @a [:engagements engagement-id (:submitted? (get track-fields track))])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :engagement/upsert
      (swap! a update-in [:engagements (:id value)] merge value)

      :assessment/set
      (let [[engagement-id track] path]
        (swap! a assoc-in [:assessments engagement-id track] payload))

      :engagement/mark-drafted
      (let [[engagement-id track] path
            {:keys [result engagement-patch]} (draft-filing! s engagement-id track)]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:draft-sequences track] (fnil inc 0))
                       (update-in [:engagements engagement-id] merge engagement-patch)
                       (update :draft-records registry/append result))))
        result)

      :engagement/mark-submitted
      (let [[engagement-id track] path
            {:keys [result engagement-patch]} (submit-filing! s engagement-id track)]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:submit-sequences track] (fnil inc 0))
                       (update-in [:engagements engagement-id] merge engagement-patch)
                       (update :submit-records registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-engagements [s engagements] (when (seq engagements) (swap! a assoc :engagements engagements)) s))

(defn seed-db
  "A MemStore seeded with the demo engagement set."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :draft-sequences {} :draft-records []
                           :submit-sequences {} :submit-records []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  {:engagement/id                   {:db/unique :db.unique/identity}
   :assessment/key                  {:db/unique :db.unique/identity}
   :ledger/seq                      {:db/unique :db.unique/identity}
   :draft-record/seq                {:db/unique :db.unique/identity}
   :submit-record/seq               {:db/unique :db.unique/identity}
   :draft-sequence/track            {:db/unique :db.unique/identity}
   :submit-sequence/track           {:db/unique :db.unique/identity}})

(defn- engagement->tx [{:keys [id operator portal base-fee monthly-rate monitoring-months
                               audit-export? export-fee claimed-fee
                               sam-registration-verified?
                               medical-schedule-offering? va-delegation-acknowledged?
                               sam-registration-drafted? sam-registration-draft-number
                               sam-registration-submitted? sam-registration-submit-number
                               mas-schedule-drafted? mas-schedule-draft-number
                               mas-schedule-submitted? mas-schedule-submit-number
                               status]}]
  (cond-> {:engagement/id id}
    operator                                (assoc :engagement/operator operator)
    portal                                  (assoc :engagement/portal portal)
    base-fee                                (assoc :engagement/base-fee base-fee)
    monthly-rate                            (assoc :engagement/monthly-rate monthly-rate)
    monitoring-months                       (assoc :engagement/monitoring-months monitoring-months)
    (some? audit-export?)                   (assoc :engagement/audit-export? audit-export?)
    export-fee                              (assoc :engagement/export-fee export-fee)
    claimed-fee                             (assoc :engagement/claimed-fee claimed-fee)
    (some? sam-registration-verified?)      (assoc :engagement/sam-registration-verified? sam-registration-verified?)
    (some? medical-schedule-offering?)      (assoc :engagement/medical-schedule-offering? medical-schedule-offering?)
    (some? va-delegation-acknowledged?)     (assoc :engagement/va-delegation-acknowledged? va-delegation-acknowledged?)
    (some? sam-registration-drafted?)       (assoc :engagement/sam-registration-drafted? sam-registration-drafted?)
    sam-registration-draft-number           (assoc :engagement/sam-registration-draft-number sam-registration-draft-number)
    (some? sam-registration-submitted?)     (assoc :engagement/sam-registration-submitted? sam-registration-submitted?)
    sam-registration-submit-number          (assoc :engagement/sam-registration-submit-number sam-registration-submit-number)
    (some? mas-schedule-drafted?)           (assoc :engagement/mas-schedule-drafted? mas-schedule-drafted?)
    mas-schedule-draft-number               (assoc :engagement/mas-schedule-draft-number mas-schedule-draft-number)
    (some? mas-schedule-submitted?)         (assoc :engagement/mas-schedule-submitted? mas-schedule-submitted?)
    mas-schedule-submit-number              (assoc :engagement/mas-schedule-submit-number mas-schedule-submit-number)
    status                                  (assoc :engagement/status status)))

(def ^:private engagement-pull
  [:engagement/id :engagement/operator :engagement/portal :engagement/base-fee :engagement/monthly-rate
   :engagement/monitoring-months :engagement/audit-export? :engagement/export-fee :engagement/claimed-fee
   :engagement/sam-registration-verified?
   :engagement/medical-schedule-offering? :engagement/va-delegation-acknowledged?
   :engagement/sam-registration-drafted? :engagement/sam-registration-draft-number
   :engagement/sam-registration-submitted? :engagement/sam-registration-submit-number
   :engagement/mas-schedule-drafted? :engagement/mas-schedule-draft-number
   :engagement/mas-schedule-submitted? :engagement/mas-schedule-submit-number
   :engagement/status])

(defn- pull->engagement [m]
  (when (:engagement/id m)
    {:id (:engagement/id m) :operator (:engagement/operator m) :portal (:engagement/portal m)
     :base-fee (:engagement/base-fee m) :monthly-rate (:engagement/monthly-rate m)
     :monitoring-months (:engagement/monitoring-months m)
     :audit-export? (boolean (:engagement/audit-export? m)) :export-fee (:engagement/export-fee m)
     :claimed-fee (:engagement/claimed-fee m)
     :sam-registration-verified? (boolean (:engagement/sam-registration-verified? m))
     :medical-schedule-offering? (boolean (:engagement/medical-schedule-offering? m))
     :va-delegation-acknowledged? (boolean (:engagement/va-delegation-acknowledged? m))
     :sam-registration-drafted? (boolean (:engagement/sam-registration-drafted? m))
     :sam-registration-draft-number (:engagement/sam-registration-draft-number m)
     :sam-registration-submitted? (boolean (:engagement/sam-registration-submitted? m))
     :sam-registration-submit-number (:engagement/sam-registration-submit-number m)
     :mas-schedule-drafted? (boolean (:engagement/mas-schedule-drafted? m))
     :mas-schedule-draft-number (:engagement/mas-schedule-draft-number m)
     :mas-schedule-submitted? (boolean (:engagement/mas-schedule-submitted? m))
     :mas-schedule-submit-number (:engagement/mas-schedule-submit-number m)
     :status (:engagement/status m)}))

(defn- assessment-key [engagement-id track] (str engagement-id "::" (name track)))

(defrecord DatomicStore [conn]
  Store
  (engagement [_ id]
    (pull->engagement (d/pull (d/db conn) engagement-pull [:engagement/id id])))
  (all-engagements [_]
    (->> (d/q '[:find [?id ...] :where [?e :engagement/id ?id]] (d/db conn))
         (map #(pull->engagement (d/pull (d/db conn) engagement-pull [:engagement/id %])))
         (sort-by :id)))
  (assessment-of [_ engagement-id track]
    (ls/dec* (d/q '[:find ?p . :in $ ?k
                   :where [?a :assessment/key ?k] [?a :assessment/payload ?p]]
                 (d/db conn) (assessment-key engagement-id track))))
  (ledger [_] (ls/read-stream conn :ledger/seq :ledger/fact))
  (draft-history [_] (ls/read-stream conn :draft-record/seq :draft-record/record))
  (submit-history [_] (ls/read-stream conn :submit-record/seq :submit-record/record))
  (next-draft-sequence [_ track]
    (or (d/q '[:find ?n . :in $ ?t
              :where [?e :draft-sequence/track ?t] [?e :draft-sequence/next ?n]]
            (d/db conn) track)
        0))
  (next-submit-sequence [_ track]
    (or (d/q '[:find ?n . :in $ ?t
              :where [?e :submit-sequence/track ?t] [?e :submit-sequence/next ?n]]
            (d/db conn) track)
        0))
  (engagement-track-drafted? [s engagement-id track]
    (boolean (get (engagement s engagement-id) (:drafted? (get track-fields track)))))
  (engagement-track-submitted? [s engagement-id track]
    (boolean (get (engagement s engagement-id) (:submitted? (get track-fields track)))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :engagement/upsert
      (d/transact! conn [(engagement->tx value)])

      :assessment/set
      (let [[engagement-id track] path]
        (d/transact! conn [{:assessment/key (assessment-key engagement-id track)
                            :assessment/payload (ls/enc payload)}]))

      :engagement/mark-drafted
      (let [[engagement-id track] path
            {:keys [result engagement-patch]} (draft-filing! s engagement-id track)
            next-n (inc (next-draft-sequence s track))]
        (d/transact! conn
                     [(engagement->tx (assoc engagement-patch :id engagement-id))
                      {:draft-sequence/track track :draft-sequence/next next-n}
                      {:draft-record/seq (count (draft-history s)) :draft-record/record (ls/enc (get result "record"))}])
        result)

      :engagement/mark-submitted
      (let [[engagement-id track] path
            {:keys [result engagement-patch]} (submit-filing! s engagement-id track)
            next-n (inc (next-submit-sequence s track))]
        (d/transact! conn
                     [(engagement->tx (assoc engagement-patch :id engagement-id))
                      {:submit-sequence/track track :submit-sequence/next next-n}
                      {:submit-record/seq (count (submit-history s)) :submit-record/record (ls/enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (ls/append-blob! conn :ledger/seq :ledger/fact (count (ledger s)) fact)
    fact)
  (with-engagements [s engagements]
    (when (seq engagements) (d/transact! conn (mapv engagement->tx (vals engagements)))) s))

(defn datomic-store
  ([] (datomic-store {}))
  ([{:keys [engagements]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-engagements s engagements))))

(defn datomic-seed-db
  []
  (datomic-store (demo-data)))
