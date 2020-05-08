(ns cljs-wc.test.generic-components.generic-accordion
  (:require [clojure.string :as s]
            [cljs-wc.component :as wc]
            [cljs-wc.dom :as dom]))

(defn ->keycode [e]
  (case (.-keyCode e)
    9  :TAB
    13 :ENTER
    16 :SHIFT
    27 :ESC
    32 :SPACE
    35 :END
    36 :HOME
    37 :LEFT
    38 :UP
    39 :RIGHT
    40 :DOWN
    nil))

(defn move-focus! [this index]
  (some-> (:buttons @this)
          (nth index)
          (.focus)))

(defn on-keydown! [e this]
  (let [button (.. e -target)
        idx (goog.object/get button "_index")
        last-idx (goog.object/get button "_lastIndex")]
    (case (->keycode e)
      :UP (do
            (swap! this update :index
                   (fn [i]
                     (if (zero? i)
                       last-idx
                       (dec i))))
            (.preventDefault e)
            (move-focus! this (:index @this)))

      :DOWN (do
              (swap! this update :index
                     (fn [i]
                       (if (= i last-idx)
                         0
                         (inc i))))
              (.preventDefault e)
              (move-focus! this (:index @this)))

      :HOME (do
              (swap! this assoc :index 0)
              (move-focus! this (:index @this)))

      :END (do
             (swap! this assoc :index last-idx)
             (move-focus! this (:index @this)))

      nil)))

(defn update-active! [this index buttons regions]
  (let [t (count buttons)]
    (dom/patch-element
      this
      (fn []
        (dom/*
          (dom/index-as i (let [selected? (= i index)]
                            (dom/cat [:button {:id (str "generic-accordion-" i)
                                               :attr/selected (when selected? "")
                                               :attr/aria-expanded (when selected? "true")
                                               :attr/aria-disabled (if selected? "true" "false")
                                               :prop/_index i
                                               :prop/_lastIndex (dec t)
                                               :event/keydown #(on-keydown! % this)
                                               :event/focus #(swap! this assoc :index i)
                                               :event/click #(.focus (.-target %))}
                                      (dom/* (dom/.))]
                                     [:div {:prop/hidden (not selected?)
                                            :attr/aria-laballedby (str "generic-accordion-" i)}
                                      (dom/* (dom/.))]))))))
    (.dispatchEvent this (js/CustomEvent.
                           "selectedchange"
                           #js {:detail (:index @this)}))))

(defn slot-changed! [e this]
  (let [buttons (vec (dom/js-slice (.querySelectorAll this "button")))
        regions (vec (dom/js-slice (.querySelectorAll this "[role=region]")))]
    (swap! this assoc :buttons buttons :regions regions)
    (update-active! this (:index @this) buttons regions)))

(wc/defrender generic-accordian-render [state]
  {}
  [:host
   [:style
    "::slotted(button) {
      display: block;
      width: 100%;
    }
    :host {
      display: block;
    }"]
   [:slot {:event/slotchange #(slot-changed! % state)}]])

(wc/defcomponent GenericAccordion
  :render #'generic-accordian-render
  :observed-attributes ["selected"])

(wc/define-properties GenericAccordion
  (selected :get (fn [this]
                   (:index @this))
            :set (fn [this v]
                   (swap! this assoc :index v)
                   (.setAttribute this "selected" v))))

(extend-type GenericAccordion
  wc/IInit
  (-init [this]
    (add-watch this ::state (fn [_ _ old-state new-state]
                              (if (not= (:index old-state) (:index new-state))
                                (update-active! this
                                                (:index new-state)
                                                (:buttons new-state)
                                                (:regions new-state))))))
  wc/IConnected
  (-connected [this]
    (let [i (or (some-> (:attr/selected @this) (js/parseInt 10)) 0)]
      (swap! this assoc :index i)
      (.setAttribute this "selected" i))))
