(ns gsacompliance.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [gsacompliance.facts :as facts]))

(deftest sam-registration-has-spec-basis
  (let [sb (facts/spec-basis :sam-registration)]
    (is (some? sb))
    (is (string? (:provenance sb)))
    (is (seq (:required-evidence sb)))
    (is (true? (:uei-replaced-duns? sb)))
    (is (true? (:duns-retired? sb)))
    (is (= "2022-04-04" (:uei-transition-date sb)))
    (is (= "2022-04-04" (:duns-retirement-date sb)))))

(deftest mas-schedule-has-spec-basis
  (let [sb (facts/spec-basis :mas-schedule)]
    (is (some? sb))
    (is (string? (:provenance sb)))
    (is (seq (:required-evidence sb)))
    (is (true? (:requires-sam-registration? sb)))
    (is (string? (:medical-schedule-delegation sb)))))

(deftest gsam-supplement-has-spec-basis
  (let [sb (facts/spec-basis :gsam-supplement)]
    (is (some? sb))
    (is (string? (:provenance sb)))
    (is (seq (:required-evidence sb)))
    (is (true? (:far-supplement? sb)))
    (is (= "General Services Administration (GSA)" (:owner-authority sb)))))

(deftest far-council-has-spec-basis
  (let [sb (facts/spec-basis :far-council)]
    (is (some? sb))
    (is (string? (:provenance sb)))
    (is (seq (:required-evidence sb)))
    (is (false? (:gsa-sole-owner? sb)))
    (is (= "FAR Council (Secretary of Defense + GSA Administrator + NASA Administrator) -- NOT GSA alone"
          (:owner-authority sb)))))

(deftest unknown-track-has-no-spec-basis
  (is (nil? (facts/spec-basis :unknown-track)))
  (is (nil? (facts/spec-basis :zzz))))

(deftest out-of-scope-tracks-have-no-spec-basis
  (testing "DoD-specific (DFARS/CMMC) and DOL-specific (SCA/DBRA/OFCCP) tracks are out of this actor's scope"
    (is (nil? (facts/spec-basis :dfars)))
    (is (nil? (facts/spec-basis :cmmc)))
    (is (nil? (facts/spec-basis :sca)))
    (is (nil? (facts/spec-basis :dbra)))
    (is (nil? (facts/spec-basis :ofccp)))))

(deftest required-evidence-satisfied
  (let [sb (facts/spec-basis :sam-registration)
        all (:required-evidence sb)]
    (is (true? (facts/required-evidence-satisfied? :sam-registration all)))
    (is (not (facts/required-evidence-satisfied? :sam-registration (take 1 all))))
    (is (nil? (facts/required-evidence-satisfied? :unknown-track all)))))

(deftest coverage-is-honest
  (let [c (facts/coverage [:sam-registration :mas-schedule :unknown-track])]
    (is (= 3 (:requested c)))
    (is (= 2 (:covered c)))
    (is (= ["unknown-track"] (:missing-tracks c)))))

(deftest requires-sam-registration-prerequisite-track-is-mas-schedule-only
  (is (true? (facts/requires-sam-registration-prerequisite-track? :mas-schedule)))
  (is (false? (facts/requires-sam-registration-prerequisite-track? :sam-registration)))
  (is (false? (facts/requires-sam-registration-prerequisite-track? :gsam-supplement)))
  (is (false? (facts/requires-sam-registration-prerequisite-track? :far-council))))

(deftest filing-tracks-excludes-citation-only-entries
  (is (= #{:sam-registration :mas-schedule} facts/filing-tracks))
  (is (not (contains? facts/filing-tracks :gsam-supplement)))
  (is (not (contains? facts/filing-tracks :far-council))))

(deftest correct-far-owner-authority-is-the-far-council-not-gsa-alone
  (let [owner (facts/correct-far-owner-authority)]
    (is (string? owner))
    (is (not= "General Services Administration (GSA)" owner))
    (is (not= "GSA" owner))
    (is (re-find #"FAR Council" owner))))
