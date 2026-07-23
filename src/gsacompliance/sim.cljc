(ns gsacompliance.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean engagement through
  intake -> sam-registration track assessment -> filing draft
  (escalate/approve/commit) -> filing submit (escalate/approve/commit),
  same for the mas-schedule track, then shows HARD-hold scenarios
  grounded in the dossier: fabrication defense (no spec-basis), retired-
  DUNS-citation defense, FAR-sole-owner-misattribution defense, fee
  mismatch, missing SAM.gov registration/UEI, and an unacknowledged
  VA-delegated-medical-schedule offering."
  (:require [langgraph.graph :as g]
            [gsacompliance.store :as store]
            [gsacompliance.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :gsa-compliance-operator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== engagement/intake eng-1 (clean) ==")
    (println (exec-op actor "t1" {:op :engagement/intake :subject "eng-1"
                                  :patch {:id "eng-1" :operator "Meridian Federal Solutions LLC"}} operator))

    (println "== compliance/assess eng-1/sam-registration (escalates -- human approves) ==")
    (println (exec-op actor "t2" {:op :compliance/assess :subject "eng-1" :track :sam-registration} operator))
    (println (approve! actor "t2"))

    (println "== filing/draft eng-1/sam-registration (always escalates -- actuation/draft-filing) ==")
    (let [r (exec-op actor "t3" {:op :filing/draft :subject "eng-1" :track :sam-registration} operator)]
      (println r)
      (println "-- human operator approves --")
      (println (approve! actor "t3")))

    (println "== filing/submit eng-1/sam-registration (always escalates -- actuation/submit-filing) ==")
    (let [r (exec-op actor "t4" {:op :filing/submit :subject "eng-1" :track :sam-registration} operator)]
      (println r)
      (println "-- human operator approves --")
      (println (approve! actor "t4")))

    (println "== compliance/assess eng-1/mas-schedule + draft + submit (clean, escalates each time) ==")
    (println (exec-op actor "t4b" {:op :compliance/assess :subject "eng-1" :track :mas-schedule} operator))
    (println (approve! actor "t4b"))
    (println (exec-op actor "t4c" {:op :filing/draft :subject "eng-1" :track :mas-schedule} operator))
    (println (approve! actor "t4c"))
    (println (exec-op actor "t4d" {:op :filing/submit :subject "eng-1" :track :mas-schedule} operator))
    (println (approve! actor "t4d"))

    (println "== compliance/assess eng-2/sam-registration (no spec-basis -> HARD hold) ==")
    (println (exec-op actor "t5" {:op :compliance/assess :subject "eng-2" :track :sam-registration :no-spec? true} operator))

    (println "== compliance/assess eng-2/sam-registration (retired DUNS citation -> HARD hold) ==")
    (println (exec-op actor "t5b" {:op :compliance/assess :subject "eng-2" :track :sam-registration :cite-duns? true} operator))

    (println "== compliance/assess eng-2/far-council (FAR sole-owner misattribution -> HARD hold) ==")
    (println (exec-op actor "t5c" {:op :compliance/assess :subject "eng-2" :track :far-council :misattribute-far-owner? true} operator))

    (println "== compliance/assess eng-3/sam-registration (sets up fee-mismatch) ==")
    (println (exec-op actor "t6" {:op :compliance/assess :subject "eng-3" :track :sam-registration} operator))
    (println (approve! actor "t6"))
    (println (exec-op actor "t6b" {:op :filing/draft :subject "eng-3" :track :sam-registration} operator))
    (println (approve! actor "t6b"))
    (println "== filing/submit eng-3/sam-registration (fee mismatch -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :filing/submit :subject "eng-3" :track :sam-registration} operator))

    (println "== compliance/assess eng-4/mas-schedule (sets up sam-registration-missing) ==")
    (println (exec-op actor "t8" {:op :compliance/assess :subject "eng-4" :track :mas-schedule} operator))
    (println (approve! actor "t8"))
    (println (exec-op actor "t8b" {:op :filing/draft :subject "eng-4" :track :mas-schedule} operator))
    (println (approve! actor "t8b"))
    (println "== filing/submit eng-4/mas-schedule (sam-registration-missing -> HARD hold) ==")
    (println (exec-op actor "t9" {:op :filing/submit :subject "eng-4" :track :mas-schedule} operator))

    (println "== compliance/assess eng-5/mas-schedule (sets up medical-schedule-misattribution) ==")
    (println (exec-op actor "t10" {:op :compliance/assess :subject "eng-5" :track :mas-schedule} operator))
    (println (approve! actor "t10"))
    (println (exec-op actor "t10b" {:op :filing/draft :subject "eng-5" :track :mas-schedule} operator))
    (println (approve! actor "t10b"))
    (println "== filing/submit eng-5/mas-schedule (medical-schedule-misattribution -> HARD hold) ==")
    (println (exec-op actor "t11" {:op :filing/submit :subject "eng-5" :track :mas-schedule} operator))

    (println "== filing/draft eng-1/sam-registration AGAIN (double-draft -> HARD hold) ==")
    (println (exec-op actor "t12" {:op :filing/draft :subject "eng-1" :track :sam-registration} operator))

    (println "== filing/submit eng-1/sam-registration AGAIN (double-submit -> HARD hold) ==")
    (println (exec-op actor "t13" {:op :filing/submit :subject "eng-1" :track :sam-registration} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft records ==")
    (doseq [r (store/draft-history db)] (println r))

    (println "== submit records ==")
    (doseq [r (store/submit-history db)] (println r))))
