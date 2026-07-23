(ns gsacompliance.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [gsacompliance.registry :as registry]))

(deftest engagement-fee-recompute-no-export
  (let [e {:base-fee 750000 :monthly-rate 45000 :monitoring-months 12
           :audit-export? false :export-fee nil :claimed-fee 1290000.0}]
    (is (== 1290000.0 (registry/compute-engagement-fee e)))
    (is (true? (registry/engagement-fee-matches-claim? e))))
  (let [bad {:base-fee 750000 :monthly-rate 45000 :monitoring-months 12
             :audit-export? false :export-fee nil :claimed-fee 1700000.0}]
    (is (false? (registry/engagement-fee-matches-claim? bad)))))

(deftest engagement-fee-recompute-with-export
  (testing "the third revenue line (compliance-audit export package) only counts when :audit-export? is true"
    (let [e {:base-fee 750000 :monthly-rate 45000 :monitoring-months 12
             :audit-export? true :export-fee 140000 :claimed-fee 1430000.0}]
      (is (== 1430000.0 (registry/compute-engagement-fee e)))
      (is (true? (registry/engagement-fee-matches-claim? e))))
    (let [without-flag {:base-fee 750000 :monthly-rate 45000 :monitoring-months 12
                        :audit-export? false :export-fee 140000 :claimed-fee 1430000.0}]
      (is (== 1290000.0 (registry/compute-engagement-fee without-flag))
          "export-fee is NOT counted when :audit-export? is false, even if present"))))

(deftest register-draft-and-submit
  (let [d (registry/register-draft "eng-1" :sam-registration 0)
        s (registry/register-submit "eng-1" :mas-schedule 0)]
    (is (= "USA-GSA-SAM-REGISTRATION-DFT-000000" (get d "draft_number")))
    (is (= "USA-GSA-MAS-SCHEDULE-SUB-000000" (get s "submit_number")))
    (is (nil? (get-in d ["certificate" "proof"])))
    (is (= "draft-unsigned" (get-in s ["certificate" "status"])))))

(deftest register-requires-ids
  (is (thrown? Exception (registry/register-draft "" :sam-registration 0)))
  (is (thrown? Exception (registry/register-submit "eng-1" "" 0))))
