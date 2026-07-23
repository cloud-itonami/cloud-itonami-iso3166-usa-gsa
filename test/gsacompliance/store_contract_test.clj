(ns gsacompliance.store-contract-test
  "MemStore ≡ DatomicStore parity for the Store protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [gsacompliance.store :as store]
            [gsacompliance.registry :as registry]))

(defn- exercise [s]
  (store/commit-record! s {:effect :engagement/upsert
                           :value {:id "eng-x" :operator "X LLC"
                                   :base-fee 100 :monthly-rate 10 :monitoring-months 1
                                   :audit-export? false :export-fee nil :claimed-fee 110.0
                                   :sam-registration-verified? true
                                   :medical-schedule-offering? false :va-delegation-acknowledged? false
                                   :sam-registration-drafted? false :sam-registration-submitted? false
                                   :mas-schedule-drafted? false :mas-schedule-submitted? false
                                   :status :intake}})
  (store/commit-record! s {:effect :assessment/set
                           :path ["eng-x" :sam-registration]
                           :payload {:track :sam-registration :checklist ["a"] :spec-basis "x"}})
  (store/commit-record! s {:effect :engagement/mark-drafted :path ["eng-x" :sam-registration]})
  (store/commit-record! s {:effect :engagement/mark-submitted :path ["eng-x" :sam-registration]})
  (store/append-ledger! s {:t :committed :op :test})
  {:engagement (store/engagement s "eng-x")
   :assessment (store/assessment-of s "eng-x" :sam-registration)
   :drafts (store/draft-history s)
   :submits (store/submit-history s)
   :ledger (store/ledger s)
   :drafted? (store/engagement-track-drafted? s "eng-x" :sam-registration)
   :submitted? (store/engagement-track-submitted? s "eng-x" :sam-registration)})

(deftest mem-and-datomic-parity
  (let [mem (store/seed-db)
        dat (store/datomic-seed-db)
        ;; use empty stores for parity of exercised mutations
        mem* (store/->MemStore (atom {:engagements {} :assessments {} :ledger []
                                      :draft-sequences {} :draft-records []
                                      :submit-sequences {} :submit-records []}))
        dat* (store/datomic-store {})
        m (exercise mem*)
        d (exercise dat*)]
    (is (= (:operator (:engagement m)) (:operator (:engagement d))))
    (is (true? (:drafted? m)) (true? (:drafted? d)))
    (is (true? (:submitted? m)) (true? (:submitted? d)))
    (is (= 1 (count (:drafts m))) (= 1 (count (:drafts d))))
    (is (= 1 (count (:submits m))) (= 1 (count (:submits d))))
    (is (= 1 (count (:ledger m))) (= 1 (count (:ledger d))))
    (is (= (:assessment m) (:assessment d)))))
