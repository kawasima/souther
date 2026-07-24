(ns account.souther-lib-test
  "Exercises the reusable souther.* interop library against the account module's real
   Souther-generated types (used here only as fixtures; the library source itself refers to no
   domain type)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.stacktrace :as st]
            [souther.decode :as d]
            [souther.encode :as e]
            [souther.match :as m]
            [souther.behavior :as b])
  (:import [example.account WithdrawRequest Withdrawn InsufficientFunds NoAccount Balance
            AccountNo CurrentBalance CurrentBalanceResult WithdrawResult]))

;; --- souther.decode ---

(deftest decode-ok-accepts-keyword-keys
  (let [[tag v] (d/decode (WithdrawRequest/decoder) {:account "acc-1" :amount 300})]
    (is (= :ok tag))
    (is (instance? WithdrawRequest v))))

(deftest decode-err-carries-issues-as-data
  (let [[tag issues] (d/decode (WithdrawRequest/decoder) {:account "acc-1" :amount -5})]
    (is (= :err tag))
    (is (seq issues))
    (testing "each issue is Clojure data with a code"
      (is (every? :code issues)))))

(deftest construct-builds-domain-values
  (testing "newtype from a bare value"
    (is (= 700 (.value (d/construct Balance 700)))))
  (testing "unit case from an empty map"
    (is (instance? NoAccount (d/construct NoAccount {}))))
  (testing "record case from a keyword-keyed map"
    (let [w (d/construct Withdrawn {:account "acc-1" :newBalance 700})]
      (is (= "acc-1" (.value (.account w))))
      (is (= 700 (.value (.newBalance w)))))))

(deftest construct-throws-on-invariant-violation
  (is (thrown? clojure.lang.ExceptionInfo (d/construct Balance -1))))

;; --- souther.encode ---

(deftest encode-unwraps-a-newtype-to-its-bare-value
  (is (= 700 (e/encode (d/construct Balance 700))))
  (is (= "acc-1" (e/encode (d/construct AccountNo "acc-1")))))

(deftest encode-a-record-to-a-keyword-map-with-nested-newtypes-unwrapped
  (is (= {:account "acc-1" :newBalance 700}
         (e/encode (d/construct Withdrawn {:account "acc-1" :newBalance 700})))))

(deftest unwrap-returns-the-inside-of-a-wrapper
  (is (= 700 (e/unwrap (d/construct Balance 700)))))

(deftest unwrap-rejects-a-non-wrapper
  (is (thrown? clojure.lang.ExceptionInfo
               (e/unwrap (d/construct Withdrawn {:account "acc-1" :newBalance 700})))))

;; --- souther.match ---

(deftest case-of-dispatches-on-the-sealed-case
  (let [w (d/construct Withdrawn {:account "acc-1" :newBalance 700})]
    (is (= :won (m/case-of WithdrawResult w
                           {Withdrawn (fn [_] :won)
                            InsufficientFunds (fn [_] :short)
                            NoAccount (fn [_] :none)})))))

(deftest case-of-rejects-a-non-exhaustive-fold-at-macroexpansion
  (testing "omitting InsufficientFunds / NoAccount is a macro-time error"
    (let [ex (try (macroexpand
                   '(souther.match/case-of example.account.WithdrawResult x
                                           {example.account.Withdrawn (fn [_] :won)}))
                  nil
                  (catch Throwable t t))
          cause (st/root-cause ex)]
      (is (instance? clojure.lang.ExceptionInfo cause))
      (is (re-find #"exhaustive" (.getMessage cause)))
      (testing "the error names the missing cases"
        (is (= #{"example.account.InsufficientFunds" "example.account.NoAccount"}
               (set (:missing (ex-data cause)))))))))

;; --- souther.behavior ---

(b/defbehavior fixed-balance [n]
  CurrentBalance
  (apply [_account] (d/construct Balance n)))

(deftest defbehavior-yields-a-working-proxy
  (let [impl (fixed-balance 500)]
    (is (instance? CurrentBalance impl))
    (is (= 500 (.value (.apply impl (d/construct AccountNo "acc-1")))))))
