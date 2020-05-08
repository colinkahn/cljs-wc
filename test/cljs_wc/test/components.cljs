(ns cljs-wc.test.components
  (:require [cljs-wc.component :as wc]))

(wc/defcomponent CEWithoutChildren)

(wc/defrender component-without-children-render [_]
  {}
  [:host
   [:ce-without-children {:attr/id "wc"}]])

(wc/defcomponent ComponentWithoutChildren
  :render #'component-without-children-render)

(wc/defrender ce-with-children-render [_]
  {}
  [:host
   [:h1 "Test h1"]
   [:div [:p "Test p"]]
   [:slot]])

(wc/defcomponent CEWithChildren
  :render #'ce-with-children-render)

(wc/defrender component-with-children-render [_]
  {}
  [:host
   [:ce-with-children {:attr/id "wc"}]])

(wc/defcomponent ComponentWithChildren
  :render #'component-with-children-render)

(wc/defrender component-with-children-rerender-render [state]
  {}
  [:host
   [:ce-with-children {:attr/id "wc"}
    (:count @state)]])

(wc/defcomponent ComponentWithChildrenRerender
  :render #'component-with-children-rerender-render)

(extend-type ComponentWithChildrenRerender
  wc/IInit
  (-init [state]
    (reset! state {:count 1}))

  wc/IConnected
  (-connected [state]
    (swap! state update :count inc)))

(defprotocol IToggle
  (-toggle [this]))

(wc/defrender component-with-different-views-render [state]
  {}
  [:host
   (if (:show-wc @state)
     [:ce-with-children {:attr/id "wc"}]
     [:div {:attr/id "dummy"} "Dummy view"])])

(wc/defcomponent ComponentWithDifferentViews
  :render #'component-with-different-views-render)

(extend-type ComponentWithDifferentViews
  wc/IInit
  (-init [this]
    (reset! this {:show-wc true}))

  IToggle
  (-toggle [this]
    (swap! this update :show-wc not)))

(wc/defcomponent CEWithProperties)

(wc/define-properties CEWithProperties
  (bool :get (fn [this] (.-bool__ this))
        :set (fn [this value] (set! (.-bool__ this) value)))
  (num :get (fn [this] (.-num__ this))
       :set (fn [this value] (set! (.-num__ this) value)))
  (str :get (fn [this] (.-str__ this))
       :set (fn [this value] (set! (.-str__ this) value)))
  (arr :get (fn [this] (.-arr__ this))
       :set (fn [this value] (set! (.-arr__ this) value)))
  (obj :get (fn [this] (.-obj__ this))
       :set (fn [this value] (set! (.-obj__ this) value))))

(wc/defrender component-with-properties-render [state]
  {}
  [:host
   [:ce-with-properties
    {:attr/id "wc"
     :attr/bool (:bool @state)
     :attr/num (:num @state)
     :attr/str (:str @state)
     :prop/arr (:arr @state)
     :prop/obj (:obj @state)}]])

(wc/defcomponent ComponentWithProperties
  :render #'component-with-properties-render)

(extend-type ComponentWithProperties
  wc/IInit
  (-init [this]
    (reset! this {:bool true
                  :num 42
                  :str "cljs-wc"
                  :arr #js ["c" "l" "j" "s" "-" "w" "c"]
                  :obj #js {:author "colinkahn" :repo "cljs-wc"}})))

(wc/defcomponent CEWithEvent)

(wc/define-properties CEWithEvent
  (onClick :fn (fn [this _]
                 (doseq [event ["lowercaseevent" "kebab-event" "camelEvent"
                                "CAPSevent" "PascalEvent"]]
                   (.dispatchEvent this (js/CustomEvent. event))))))

(extend-type CEWithEvent
  wc/IInit
  (-init [this]
    (.addEventListener this "click" #(.onClick this %))))

(defprotocol IAddCamelEventListener
  (-add-camel-event-listener [this f]))

(extend-type CEWithEvent
  IAddCamelEventListener
  (-add-camel-event-listener [this f]
    (when-not (.-hasCamelEvent this)
      (set! (.-hasCamelEvent this) true)
      (.addEventListener this "camelEvent" f))))

(wc/defrender component-with-imperative-event-render [state]
  {:queries [_ (IAddCamelEventListener
                 (fn [_ _ _ els]
                   {:pre [(= 1 (count els))]}
                   (-add-camel-event-listener
                     (first els)
                     (fn [e]
                       (swap! state assoc :event-handled true)))))]}
  [:host
   [:div {:attr/id "handled"} (:event-handled @state)]
   [:ce-with-event {:attr/id "wc"}]])

(wc/defcomponent ComponentWithImperativeEvent
  :render #'component-with-imperative-event-render)

(extend-type ComponentWithImperativeEvent
  wc/IInit
  (-init [this]
    (reset! this {:event-handled false})))

(wc/defrender component-with-declarative-event-render [state]
  {}
  [:host
   [:div {:attr/id "lowercase"} (:lowercase-handled @state)]
   [:div {:attr/id "kebab"} (:kebab-handled @state)]
   [:div {:attr/id "camel"} (:camel-handled @state)]
   [:div {:attr/id "caps"} (:caps-handled @state)]
   [:div {:attr/id "pascal"} (:pascal-handled @state)]
   [:ce-with-event
    {:attr/id "wc"
     :event/lowercaseevent #(swap! state assoc :lowercase-handled true)
     :event/kebab-event #(swap! state assoc :kebab-handled true)
     :event/camelEvent #(swap! state assoc :camel-handled true)
     :event/CAPSevent #(swap! state assoc :caps-handled true)
     :event/PascalEvent #(swap! state assoc :pascal-handled true)}]])

(wc/defcomponent ComponentWithDeclarativeEvent
 :render #'component-with-declarative-event-render)

(extend-type ComponentWithDeclarativeEvent
  wc/IInit
  (-init [this]
    (reset! this {:lowercase-handled false
                  :kebab-handled false
                  :camel-handled false
                  :caps-handled false
                  :pascal-handled false})))
