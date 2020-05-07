(ns cljs-wc.demo
  (:require [cljs-wc.dom :refer [dom patch-element]]
            [cljs-wc.component :refer [defcomponent]]))

(defn render [state]
  (let [{:keys [attributes]} @state]
    (dom [:<>
          (each [a [1 2 3 4]]
                (println a)
                (if (= (:name attributes) "me")
                  (let [b (inc a)]
                    [:div "Someone else?" a b])
                  [:div {:part "title"} "Hello, " (:name attributes) a]))
          [:slot]])))

(defcomponent CustomElement
  :render #'render
  :observed-attributes ["name"])

(patch-element (.-body js/document)
               (fn [] (dom [:custom-element {:name "Me"}])))
