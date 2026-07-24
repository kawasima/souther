(ns account.service-test
  "The Pedestal boundary: HTTP -> decode WithdrawRequest -> run withdraw in one transaction -> fold
   the sealed output cases (Withdrawn / InsufficientFunds / NoAccount) to status + JSON. A decode
   failure (e.g. a negative amount) is the boundary's own 400."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [cheshire.core :as json]
            [io.pedestal.http :as http]
            [io.pedestal.test :refer [response-for]]
            [next.jdbc :as jdbc]
            [account.db :as db]
            [account.service :as service]))

(def ^:dynamic *service* nil)

(defn- service-for [ds]
  (::http/service-fn (-> (service/service-map ds) http/create-servlet)))

(use-fixtures :each
  (fn [t]
    (let [ds (db/make-datasource "jdbc:h2:mem:service-test;DB_CLOSE_DELAY=-1")]
      (db/init! ds)
      (db/seed! ds {"acc-1" 1000 "acc-2" 500})
      (binding [*service* (service-for ds)] (t))
      (jdbc/execute! ds ["drop all objects"]))))

(defn- post-withdraw [id amount]
  (response-for *service* :post "/withdrawals"
                :headers {"Content-Type" "application/json"}
                :body (json/generate-string {:account id :amount amount})))

(defn- body-json [response]
  (json/parse-string (:body response) true))

(deftest withdraw-success-writes-new-balance
  (testing "the 200 body is the encoded Withdrawn value (souther.encode/encode)"
    (let [res (post-withdraw "acc-1" 300)]
      (is (= 200 (:status res)))
      (is (= {:account "acc-1" :newBalance 700} (body-json res))))))

(deftest withdraw-insufficient-is-409
  (let [res (post-withdraw "acc-2" 800)]
    (is (= 409 (:status res)))
    (is (= "insufficient_funds" (:error (body-json res))))
    (is (= 300 (:shortfall (body-json res))))))

(deftest withdraw-unknown-account-is-404
  (let [res (post-withdraw "acc-zzz" 100)]
    (is (= 404 (:status res)))
    (is (= "no_account" (:error (body-json res))))))

(deftest negative-amount-fails-decode-as-400
  (testing "the Amount invariant value >= 0 is rejected by the boundary decode"
    (let [res (post-withdraw "acc-1" -50)]
      (is (= 400 (:status res)))
      (is (= "invalid_request" (:error (body-json res)))))))

(deftest read-balance
  (is (= {:account "acc-1" :balance 1000}
         (body-json (response-for *service* :get "/accounts/acc-1"))))
  (is (= 404 (:status (response-for *service* :get "/accounts/acc-zzz")))))
