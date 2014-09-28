(ns birdwatch.communicator.component
  (:gen-class)
  (:require
   [clojure.pprint :as pp]
   [clojure.tools.logging :as log]
   [birdwatch.communicator.websockets :as ws]
   [taoensso.sente :as sente]
   [taoensso.sente.packers.transit :as sente-transit]
   [com.stuartsierra.component :as component]
   [clojure.core.async :as async :refer [chan]]))

(def packer (sente-transit/get-flexi-packer :json)) ;; serialization format for client<->server comm

(defrecord Communicator [channels chsk-router]
  component/Lifecycle
  (start [component] (log/info "Starting Communicator Component")
         (let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
               (sente/make-channel-socket! {:packer packer :user-id-fn ws/user-id-fn})
               event-handler (ws/make-event-handler (:query channels) (:tweet-missing channels) (:register-percolation channels))
               chsk-router (sente/start-chsk-router! ch-recv event-handler)]
           (ws/run-percolation-matches-loop (:percolation-matches channels) send-fn connected-uids)
           (ws/run-users-count-loop send-fn connected-uids)
           (ws/run-tweet-stats-loop send-fn connected-uids (:tweet-count channels))
           (ws/run-missing-tweet-loop (:missing-tweet-found channels) send-fn)
           (ws/run-query-results-loop (:query-results channels) send-fn)
           (assoc component :ajax-post-fn ajax-post-fn
                            :ajax-get-or-ws-handshake-fn ajax-get-or-ws-handshake-fn
                            :chsk-router chsk-router)))
  (stop [component] (log/info "Stopping Communicator Component")
        (chsk-router) ;; stops router loop
        (assoc component :chsk-router nil)))

(defn new-communicator [] (map->Communicator {}))

(defrecord Communicator-Channels []
  component/Lifecycle
  (start [component] (log/info "Starting Communicator Channels Component")
         (assoc component
           :query (chan)
           :query-results (chan)
           :tweet-missing (chan)
           :missing-tweet-found (chan)
           :persistence (chan)
           :rt-persistence (chan)
           :tweet-count (chan)
           :register-percolation (chan)
           :percolation-matches (chan)))
  (stop [component] (log/info "Stop Communicator Channels Component")
        (assoc component :query nil :query-results nil :tweet-missing nil :missing-tweet-found nil
                         :persistence nil :rt-persistence nil :tweet-count nil)))

(defn new-communicator-channels [] (map->Communicator-Channels {}))