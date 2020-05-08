(ns cljs-wc.demo
  (:require [cljs-wc.dom :as dom]
            [cljs-wc.component :as wc]
            ["clipboard-copy-element"]
            [cljs-wc.test.generic-components.generic-accordion]))

(wc/defrender render-list-item [state]
  {}
  (let [snapshot @state]
    [:<>
     (str "this " (pr-str snapshot))
     [:generic-accordion
      [:button "One"]
      [:div {:attr/role "region"}
       "Region 1"]

      [:button "Two"]
      [:div {:attr/role "region"}
       "Region 2"]

      [:button "Three"]
      [:div {:attr/role "region"}
       "Region 3"]
      
      ]
     [:slot {:event/slotchange (fn [e]
                                  (println "CHANGED!"
                                           (.. e -target -textContent)))}]]))

(wc/defcomponent ListItemElement
  :render #'render-list-item)

(wc/define-properties ListItemElement
  (itemdata :get (fn [this]
                   (get @this :prop/itemdata))
            :set (fn [this v]
                   (swap! this assoc :prop/itemdata v))))

(defprotocol IAlert
  (alert [self]))

(extend-protocol IAlert
  ListItemElement
  (alert [this]
    (js/alert (:prop/itemdata @this)))

  js/ClipboardCopyElement
  (alert [this]
    (js/alert (.-value this)))

  nil
  (alert [_]))

(wc/defrender render [state]
  {:queries [alerters (IAlert (fn [_ _ _ nodes]
                                (println "in watch" @state nodes)))]}
  (let [{:keys [:attr/name no-li swap-fns]} @state]
    [:host {:class name}
     [:div
      (when-not no-li
        [:list-item-element {:prop/itemdata (:items @state)}
         (if (= name "me")
           ; NB: required so dom lib removes/adds distinct nodes
           ^{:key 1}
           [:span "Try someone else!"]
           ^{:key 2}
           [:span (str name)])])]
     [:div
      [:button {:event/click (if swap-fns
                               (fn [_] (js/alert "swapped!"))
                               (fn [_]
                                 (doseq [n @alerters]
                                   (alert n))))}
       "Announce Items"]
      [:button {:event/click (fn [_]
                               (swap! state assoc :items [1 2 3]))}
       "Add Items"]
      [:button {:event/click (fn [_]
                               (swap! state update :no-li not))}
       "Toggle LI"]
      [:button {:event/click (fn [_]
                           (swap! state update :swap-fns not))}
       "Toggle FNS"]

      [:clipboard-copy {:value "hi there"
                        :event/clipboard-copy
                        (fn [e]
                          (println "DID COPY!"
                                   (.. e -target -value))) }
       "Copy Note"]

      (each [a [1 2 3 4]]
            ; side effecting OK
            (println a)
            (if (= name "me")
              (let [b (inc a)]
                [:div "Someone else?" a b])
              [:div {:part "title"} "Helloz, " name a]))
      [:slot]]]))

(wc/defcomponent CustomElement
  :render #'render
  :observed-attributes [:name])

(wc/defroot root (.-body js/document)
  [:<>
   [:h1 "Testing" ]
   [:custom-element {:name "Me"}
    "Hello world!"]])

(root)

(defn ^:dev/after-load reloaded []
  (println "Reloaded")
  (wc/force-render! (.-body js/document)))
