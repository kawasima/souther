(ns account.behaviors-test
  "The Souther<->Clojure seam over a real H2 database: the two injected behaviors (currentBalance /
   updateBalance) implemented as `proxy`, bound into withdraw, and driven through its three output
   cases."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [account.db :as db]
            [account.behaviors :as b])
  (:import [example.account WithdrawRequest Withdrawn InsufficientFunds NoAccount]))

(def ^:dynamic *ds* nil)

(use-fixtures :each
  (fn [t]
    (let [ds (db/make-datasource "jdbc:h2:mem:behaviors-test;DB_CLOSE_DELAY=-1")]
      (db/init! ds)
      (db/seed! ds {"acc-1" 1000 "acc-2" 500})
      (binding [*ds* ds] (t))
      (jdbc/execute! ds ["drop all objects"]))))

(defn- req [id amount]
  (.value (.decode (WithdrawRequest/decoder) {"account" id "amount" amount})))

(defn- withdraw [id amount]
  (.apply (b/withdraw-behavior *ds*) (req id amount)))

(defn- balance-of [id]
  (:BALANCE (jdbc/execute-one! *ds*
                               ["select balance from account where id=?" id]
                               {:builder-fn rs/as-unqualified-maps})))

(deftest writes-new-balance-when-funds-are-enough
  (let [out (withdraw "acc-1" 300)]
    (is (instance? Withdrawn out))
    (is (= "acc-1" (.value (.account out))))
    (is (= 700 (.value (.newBalance out))))
    (testing "the write is reflected in the DB"
      (is (= 700 (balance-of "acc-1"))))))

(deftest stops-and-does-not-write-when-funds-are-short
  (let [out (withdraw "acc-2" 800)]
    (is (instance? InsufficientFunds out))
    (is (= 300 (.shortfall out)))
    (testing "the balance is unchanged"
      (is (= 500 (balance-of "acc-2"))))))

(deftest no-account-yields-no-account
  (is (instance? NoAccount (withdraw "acc-zzz" 100))))
