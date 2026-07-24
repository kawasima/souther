(ns account.db
  "The H2-backed store for accounts, and the connection the injected behaviors read.

   `*conn*` is the seam that lets read → check → write run in one transaction: the HTTP
   boundary rebinds it to the transaction's connection, and both behaviors query through
   `current`, so 現在残高's SELECT and 残高を更新する's UPDATE share one connection and
   commit or roll back together."
  (:require [next.jdbc :as jdbc]))

(def ^:dynamic *conn*
  "Bound to the active transaction's connection inside the boundary; nil otherwise."
  nil)

(defn make-datasource
  ([] (make-datasource "jdbc:h2:mem:account;DB_CLOSE_DELAY=-1"))
  ([jdbc-url] (jdbc/get-datasource {:jdbcUrl jdbc-url})))

(defn current
  "The connection a behavior should use: the transaction's if one is open, else the pool."
  [ds]
  (or *conn* ds))

(defn init! [ds]
  (jdbc/execute! ds ["create table if not exists account (
                        id varchar primary key,
                        balance bigint not null)"]))

(defn seed! [ds balances]
  (doseq [[id balance] balances]
    (jdbc/execute! ds ["merge into account (id, balance) values (?, ?)" id balance])))
