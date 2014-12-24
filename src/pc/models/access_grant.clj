(ns pc.models.access-grant
  (:require [pc.datomic :as pcd]
            [clj-time.core :as time]
            [clj-time.coerce]
            [crypto.random]
            [datomic.api :refer [db q] :as d]))

(defn read-api [grant]
  (select-keys grant [:access-grant/document
                      :access-grant/email
                      :access-grant/expiry
                      :db/id]))

(defn find-by-document [db doc]
  (->> (d/q '{:find [?t]
              :in [$ ?doc-id]
              :where [[?t :access-grant/document ?doc-id]]}
            db (:db/id doc))
    (map first)
    (map #(d/entity db %))))

(defn grant-access [doc email annotations]
  (let [txid (d/tempid :db.part/tx)
        token (crypto.random/url-part 32)
        expiry (clj-time.coerce/to-date (time/plus (time/now) (time/weeks 2)))]
    @(d/transact (pcd/conn)
                 [(assoc annotations :db/id txid)
                  [:pc.models.access-grant/create-grant (:db/id doc) email token expiry]])))
