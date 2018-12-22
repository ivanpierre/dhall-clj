(ns dhall-clj.import-test
  (:require  [clojure.test :refer :all]
             [medley.core :refer [map-vals]]
             [dhall-clj.ast :refer :all]
             [dhall-clj.parse :refer [parse expr]]
             [dhall-clj.import :refer [resolve-imports]]
             [dhall-clj.typecheck :refer [typecheck]]
             [dhall-clj.beta-normalize :refer [beta-normalize]]
             [dhall-clj.state :as s]
             [dhall-clj.test-utils :refer :all]
             [clojure.java.io :as io]
             [me.raynes.fs :as fs]))


(def simple-success-cases
  {"Prelude import with hash"
   {:actual   "./../../../Prelude/package.dhall sha256:534e4a9e687ba74bfac71b30fc27aa269c0465087ef79bf483e876781602a454"
    :expected "./../../../Prelude/package.dhall"}})

(def simple-failure-cases
  {"Prelude import with hash" "./dhall-lang/Prelude/package.dhall sha256:b575f038399d47f033b63d6e29ceb8e7778b45765778026c9015ef1d28655cc3"})

(def test-folder "dhall-lang/tests/import")

(def problematic
  "Here we list all the tests that blow up, so we categorize and exclude them.
  Note: they are vectors because the path creation is platform-sensitive."
  [
   ;; Waiting on issue #26
   ["dhall-lang" "tests" "import" "success" "asText"]
   ;; Waiting for proper cycle detection
   ["dhall-lang" "tests" "import" "failure" "cycle.dhall"]])


(defn valid-testcases []
  (let [all (success-testcases (str test-folder "/success"))]
    (->> problematic
       (map #(->> % (apply io/file) str))
       (apply dissoc all))))

(deftest import-success-suite
  (let [import-cache (s/new)
        parent (str test-folder "/success")
        f (fn [e]
            (fs/with-mutable-cwd
              (fs/chdir parent)
              (-> e
                 parse
                 expr
                 (resolve-imports import-cache)
                 (beta-normalize))))]
    (doseq [[testcase {:keys [actual expected]}] (merge simple-success-cases
                                                        (valid-testcases))]
      (println "TESTCASE:" testcase)
      (testing actual
        (is (= (f actual) (f expected)))))))

(defn valid-failing-testcases []
  (let [all (failure-testcases test-folder)]
    (->> problematic
       (map #(->> % (apply io/file) str))
       (apply dissoc all))))

(deftest import-failure-suite
  (let [import-cache (s/new)
        parent (str test-folder "/failure")
        f (fn [e]
            (fs/with-mutable-cwd
              (fs/chdir parent)
              (-> e
                 parse
                 expr
                 (resolve-imports import-cache))))]
    (doseq [[testcase dhall] (merge simple-failure-cases
                                    (valid-failing-testcases))]
      (println "TESTCASE failure:" testcase)
      (testing testcase
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Import error:"
                              (f dhall)))))))