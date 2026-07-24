(ns account.service
  "The HTTP boundary. It decodes the JSON request body into WithdrawRequest (re-checking invariants
   -- a negative amount is rejected here), runs withdraw inside one transaction, and folds its
   sealed output into an HTTP status with `souther.match/case-of` (which checks at compile time that
   every case is handled, carrying Souther's match totality across the boundary). The controller
   never constructs domain data itself: input goes through the generated decoder, output through the
   case fold. A platform failure (a SQL exception) is not a domain case: Souther passes it through
   untouched and it propagates to the framework (ordering shows the full 503 + rollback treatment)."
  (:require [cheshire.core :as json]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route :as route]
            [next.jdbc :as jdbc]
            [souther.decode :as sd]
            [souther.encode :refer [encode unwrap]]
            [souther.match :refer [case-of]]
            [account.db :as db]
            [account.behaviors :as b])
  (:import [example.account WithdrawRequest Withdrawn InsufficientFunds NoAccount AccountNo Balance
            WithdrawResult CurrentBalanceResult]))

(defn- json-response [status body]
  {:status status
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string body)})

;; Keep JSON keys as strings; souther.decode also coerces keyword keys, so either would work, but
;; leaving them as strings means the parsed body is already the decoder's external representation.
(def ^:private request-body
  (body-params/body-params (body-params/default-parser-map :json-options {:key-fn false})))

(defn- withdraw-handler [ds withdraw]
  (fn [request]
    ;; The whole request is the JSON body {"account": "...", "amount": n}; hand it straight to the
    ;; decoder, which re-checks every invariant (a negative amount is rejected here).
    (let [[tag decoded] (sd/decode (WithdrawRequest/decoder) (:json-params request))]
      (if (= tag :err)
        (json-response 400 {:error "invalid_request" :issues decoded})
        ;; read -> check -> write is one transaction: currentBalance's SELECT and updateBalance's
        ;; UPDATE share the rebound connection, so a concurrent withdrawal cannot interleave.
        (let [out (jdbc/with-transaction [tx ds]
                    (binding [db/*conn* tx]
                      (.apply withdraw decoded)))]
          (case-of WithdrawResult out
                   {Withdrawn (fn [w] (json-response 200 (encode w)))
                    InsufficientFunds (fn [i] (json-response 409 (assoc (encode i)
                                                                        :error "insufficient_funds")))
                    NoAccount (fn [_] (json-response 404 {:error "no_account"}))}))))))

(defn- balance-handler [ds]
  (let [current-balance (b/current-balance-impl ds)]
    (fn [request]
      (let [id (get-in request [:path-params :id])
            [tag account] (sd/decode (AccountNo/decoder) id)]
        (if (= tag :err)
          (json-response 404 {:error "no_account"})
          (case-of CurrentBalanceResult (.apply current-balance account)
                   {Balance (fn [b] (json-response 200 {:account id :balance (unwrap b)}))
                    NoAccount (fn [_] (json-response 404 {:error "no_account"}))}))))))

(defn routes [ds]
  (let [withdraw (b/withdraw-behavior ds)]
    (route/expand-routes
     #{["/withdrawals" :post [request-body (withdraw-handler ds withdraw)]
        :route-name :withdraw]
       ["/accounts/:id" :get (balance-handler ds)
        :route-name :balance]})))

(defn service-map [ds]
  {:io.pedestal.http/routes (routes ds)
   :io.pedestal.http/type :jetty
   :io.pedestal.http/port 8890
   :io.pedestal.http/join? false})
