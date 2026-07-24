(ns account.behaviors
  "Clojure implementations of the account module's two injected behaviors, and the binding of
   withdraw. The Souther interop -- proxying the abstract behaviors and building results through
   the generated decoders -- is handled by the reusable `souther.*` library; this namespace only
   carries the H2 queries and the domain shape."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [souther.decode :refer [construct]]
            [souther.behavior :refer [defbehavior]]
            [account.db :as db])
  (:import [example.account Balance NoAccount Withdrawn CurrentBalance UpdateBalance Withdraw]))

(defbehavior current-balance-impl [ds]
  ;; currentBalance : (account: AccountNo) -> Balance | NoAccount. Reads the current balance; a
  ;; missing row is NoAccount. A SQL exception (DB down) propagates untouched -- a platform failure.
  CurrentBalance
  (apply [account]
    (let [row (jdbc/execute-one! (db/current ds)
                                 ["select balance from account where id=?" (.value account)]
                                 {:builder-fn rs/as-unqualified-maps})]
      (if row
        (construct Balance (:BALANCE row))
        (construct NoAccount {})))))

(defbehavior update-balance-impl [ds]
  ;; updateBalance : (account: AccountNo, newBalance: Balance) -> Withdrawn. Writes and reports it.
  UpdateBalance
  (apply [account new-balance]
    (let [id (.value account)
          balance (.value new-balance)]
      (jdbc/execute! (db/current ds) ["update account set balance=? where id=?" balance id])
      (construct Withdrawn {:account id :newBalance balance}))))

(defn withdraw-behavior
  "withdraw with its two required behaviors bound to the H2 implementations (19.5 bind)."
  [ds]
  (Withdraw/bind (current-balance-impl ds) (update-balance-impl ds)))
