(ns frontend.components.aside
  (:require [clojure.set :as set]
            [datascript :as d]
            [frontend.async :refer [put!]]
            [frontend.components.common :as common]
            [frontend.datascript :as ds]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [frontend.utils :refer [html]])
  (:import [goog.ui IdGenerator]))

(defn chat-aside [{:keys [db chat-body client-uuid aside-menu-opened]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:listener-key (.getNextUniqueId (.getInstance IdGenerator))
       :unseen-eids #{}})
    om/IDidMount
    (did-mount [_]
      (d/listen! (om/get-shared owner :db)
                 (om/get-state owner :listener-key)
                 (fn [tx-report]
                   ;; TODO: better way to check if state changed
                   (when-let [chat-datoms (seq (filter #(= :chat/body (:a %)) (:tx-data tx-report)))]
                     (om/update-state! owner :unseen-eids (fn [eids] (set/union eids (set (map :e chat-datoms)))))
                     (om/refresh! owner)))))
    om/IWillUnmount
    (will-unmount [_]
      (d/unlisten! (om/get-shared (om/get-shared owner :db)) (om/get-state owner :listener-key)))
    om/IDidUpdate
    (did-update [_ _ _]
      ;; maybe scroll chat
      (when (and aside-menu-opened (seq (om/get-state owner :unseen-eids)))
        (om/set-state! owner :unseen-eids #{})))
    om/IRender
    (render [_]
      (let [{:keys [cast!]} (om/get-shared owner)
            chats (ds/touch-all '[:find ?t :where [?t :chat/body]] @db)]
        (html
         [:div.chat-container
          [:div.chat-messages
           (for [chat (sort-by :server/timestamp chats)
                 :let [id (apply str (take 6 (str (:session/uuid chat))))]]
             (html [:div
                    [:span {:style {:color (or (:chat/color chat) (str "#" id))}}
                     (if (= (str (:session/uuid chat))
                            client-uuid)
                       "You"
                       id)]
                    (str " " (:chat/body chat))]))]
          [:form {:on-submit #(do (cast! :chat-submitted)
                                  false)}
           [:input {:type "text"
                    :value (or chat-body "")
                    :placeholder "Write something..."
                    :on-change #(cast! :chat-body-changed {:value (.. % -target -value)})}]]
          (let [unseen-eids (seq (om/get-state owner :unseen-eids))]
            [:div.unseen-eids
             (common/icon :chat)
             (when (and (not aside-menu-opened) unseen-eids)
               [:div.count
                (str (count unseen-eids))])])])))))

(defn menu [app owner]
  (reify
    om/IRender
    (render [_]
      (let [controls-ch (om/get-shared owner [:comms :controls])
            show-grid? (get-in app state/show-grid-path)
            night-mode? (get-in app state/night-mode-path)
            client-id (:client-uuid app)]
       (html
         [:div.aside-menu
          [:a {:href "/" :target "_self"}
           (common/icon :logomark-precursor)
           [:span "Precursor"]]
          ;; hide extra buttons until there's features to back them up
          ;; [:button
          ;;  (common/icon :user)
          ;;  [:span "Login"]]
          ;; [:button
          ;;  (common/icon :download)
          ;;  [:span "Download"]]
          ;; [:button.collaborators
          ;;  (common/icon :users)
          ;;  [:span "Collaborators"]]
          (let [show-mouse? (get-in app [:subscribers client-id :show-mouse?])]
            [:button {:title "You're viewing this document. Try inviting others. Click to toggle sharing your mouse position."
                      :on-click #(put! controls-ch [:show-mouse-toggled {:client-uuid client-id :show-mouse? (not show-mouse?)}])}
             [:object
              (common/icon :user (when show-mouse? {:path-props
                                                    {:style
                                                     {:stroke (get-in app [:subscribers client-id :color])}}}))
              [:span "You"]]])
          (for [[id {:keys [show-mouse? color]}] (dissoc (:subscribers app) client-id)
                :let [id-str (apply str (take 6 id))]]
            [:button {:title "An anonymous user is viewing this document. Click to toggle showing their mouse position."
                      :on-click #(put! controls-ch [:show-mouse-toggled {:client-uuid id :show-mouse? (not show-mouse?)}])}
             (common/icon :user (when show-mouse? {:path-props {:style {:stroke color}}}))
             [:span id-str]])
          ;; XXX better name here
          [:div.aside-chat
           (om/build chat-aside {:db (:db app)
                                 :client-uuid (:client-uuid app)
                                 :chat-body (get-in app [:chat :body])
                                 :aside-menu-opened (get-in app state/aside-menu-opened-path)})]
          ; [:div.aside-settings
          ;  [:button {:disabled "true"}
          ;   (common/icon :settings)
          ;   [:span "Settings"]]
          ; [:div.settings-menu
          ;  [:button {:on-click #(put! controls-ch [:show-grid-toggled])}
          ;   [:span "Show Grid"]
          ;   (if show-grid?
          ;     (common/icon :check)
          ;     (common/icon :times))]
          ;  [:button {:on-click #(put! controls-ch [:night-mode-toggled])}
          ;   [:span "Night Mode"]
          ;   (if night-mode?
          ;     (common/icon :check)
          ;     (common/icon :times))]]]
          ])))))
