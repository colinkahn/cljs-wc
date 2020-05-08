(ns cljs-wc.component-test
  "tests from https://github.com/webcomponents/custom-elements-everywhere"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [cljs-wc.test.components :as tc]
            [cljs-wc.component :as wc]
            [cljs-wc.dom :as dom]
            goog.object))

(defonce app (doto (.createElement js/document "div")
               (goog.object/set "id" "app")
               (#(.appendChild (.-body js/document) %))))

(def scratch nil)

(defn setup-fixture [f]
  (set! scratch (doto (.createElement js/document "div")
                  (goog.object/set "id" "scratch")))
  (.appendChild app scratch)
  (f)
  (set! (.-innerHTML app) "")
  (set! scratch nil))

(use-fixtures :each setup-fixture)

(deftest basic-support
  (testing "no children"
    (let [root (tc/->ComponentWithoutChildren)
          _ (.appendChild scratch root)
          wc (.querySelector (.-shadowRoot root) "#wc")]
      (is wc "can display a Custom Element with no children")))

  (testing "with children"
    (let [expect-has-children!
          (fn [wc]
            (is wc)
            (let [shadow-root (.-shadowRoot wc)
                  heading (.querySelector shadow-root "h1")
                  paragraph (.querySelector shadow-root "p")]
              (is heading)
              (is (= (.-textContent heading) "Test h1"))
              (is paragraph)
              (is (= (.-textContent paragraph) "Test p"))))]
      (testing "can display a Custom Element with children in a Shadow Root"
        (let [root (tc/->ComponentWithChildren)
              _ (.appendChild scratch root)
              wc (.querySelector (.-shadowRoot root) "#wc")]
          (expect-has-children! wc)))
      (testing "can display a Custom Element with children in a Shadow Root and pass in Light DOM children"
        (let [root (tc/->ComponentWithChildrenRerender)
              _ (.appendChild scratch root)
              wc (.querySelector (.-shadowRoot root) "#wc")]
          (expect-has-children! wc)
          (is (= (.-textContent wc) "2"))))
      (testing "can display a Custom Element with children in the Shadow DOM and handle hiding and showing the element"
        (let [root (tc/->ComponentWithDifferentViews)
              _ (.appendChild scratch root)
              wc (.querySelector (.-shadowRoot root) "#wc")]
          (expect-has-children! wc)
          (tc/-toggle root)
          (let [dummy (.querySelector (.-shadowRoot root) "#dummy")]
            (is dummy)
            (is (= (.-textContent dummy) "Dummy view"))
            (tc/-toggle root)
            (let [wc (.querySelector (.-shadowRoot root) "#wc")]
              (expect-has-children! wc)))))))

  (testing "attributes and properties"
    (let [root (tc/->ComponentWithProperties)
          _ (.appendChild scratch root)
          wc (.querySelector (.-shadowRoot root) "#wc")]
      (is (true? (or (.-bool wc) (.hasAttribute wc "bool")))
          "will pass boolean data as either an attribute or a property")

      (is (= 42 (js/parseInt (or (.-num wc) (.getAttribute wc "num")) 10))
          "will pass numeric data as either an attribute or a property")

      (is (= "cljs-wc" (or (.-str wc) (.getAttribute wc "str")))
          "will pass string data as either an attribute or a property")))

  (testing "events"
    (testing "can imperatively listen to a DOM event dispatched by a Custom Element"
      (let [root (tc/->ComponentWithImperativeEvent)
            _ (.appendChild scratch root)
            wc (.querySelector (.-shadowRoot root) "#wc")
            handled (.querySelector (.-shadowRoot root) "#handled")]
        (is (= "false" (.-textContent handled)))
        (.click wc)
        (is (= "true" (.-textContent handled)))))))

(deftest advanced-support
  (testing "attributes and properties"
    (let [root (tc/->ComponentWithProperties)
          _ (.appendChild scratch root)
          wc (.querySelector (.-shadowRoot root) "#wc")]
      (is (= (js->clj (.-arr wc) ["c" "l" "j" "s" "-" "w" "c"]))
          "will pass array data as a property")

      (is (= (js->clj (.-obj wc)) {"author" "colinkahn" "repo" "cljs-wc"})
          "will pass array data as a property")))
  (testing "events"
    (testing "can declaratively listen to a lowercase DOM event dispatched by a Custom Element"
      (let [root (tc/->ComponentWithDeclarativeEvent)
            _ (.appendChild scratch root)
            wc (.querySelector (.-shadowRoot root) "#wc")
            handled (.querySelector (.-shadowRoot root) "#lowercase")]
        (is (= "false" (.-textContent handled)))
        (.click wc)
        (is (= "true" (.-textContent handled)))))

    (testing "can declaratively listen to a kebab-case DOM event dispatched by a Custom Element"
      (let [root (tc/->ComponentWithDeclarativeEvent)
            _ (.appendChild scratch root)
            wc (.querySelector (.-shadowRoot root) "#wc")
            handled (.querySelector (.-shadowRoot root) "#kebab")]
        (is (= "false" (.-textContent handled)))
        (.click wc)
        (is (= "true" (.-textContent handled)))))

    (testing "can declaratively listen to a camelCase DOM event dispatched by a Custom Element"
      (let [root (tc/->ComponentWithDeclarativeEvent)
            _ (.appendChild scratch root)
            wc (.querySelector (.-shadowRoot root) "#wc")
            handled (.querySelector (.-shadowRoot root) "#camel")]
        (is (= "false" (.-textContent handled)))
        (.click wc)
        (is (= "true" (.-textContent handled)))))

    (testing "can declaratively listen to a CAPScase DOM event dispatched by a Custom Element"
      (let [root (tc/->ComponentWithDeclarativeEvent)
            _ (.appendChild scratch root)
            wc (.querySelector (.-shadowRoot root) "#wc")
            handled (.querySelector (.-shadowRoot root) "#caps")]
        (is (= "false" (.-textContent handled)))
        (.click wc)
        (is (= "true" (.-textContent handled)))))

    (testing "can declaratively listen to a PascalCase DOM event dispatched by a Custom Element"
      (let [root (tc/->ComponentWithDeclarativeEvent)
            _ (.appendChild scratch root)
            wc (.querySelector (.-shadowRoot root) "#wc")
            handled (.querySelector (.-shadowRoot root) "#pascal")]
        (is (= "false" (.-textContent handled)))
        (.click wc)
        (is (= "true" (.-textContent handled)))))))
