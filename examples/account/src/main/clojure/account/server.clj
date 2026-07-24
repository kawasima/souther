(ns account.server
  "Runnable entry point: create the H2 store, seed a couple of accounts, start Jetty.

     clojure -M:run

   Then:
     curl localhost:8890/accounts/acc-1
     curl -X POST localhost:8890/accounts/acc-1/withdraw -H 'Content-Type: application/json' -d '{\"amount\":300}'"
  (:require [io.pedestal.http :as http]
            [account.db :as db]
            [account.service :as service])
  (:gen-class))

(defn -main [& _]
  (let [ds (db/make-datasource)]
    (db/init! ds)
    (db/seed! ds {"acc-1" 1000 "acc-2" 500})
    (-> (service/service-map ds)
        http/create-server
        http/start)))
