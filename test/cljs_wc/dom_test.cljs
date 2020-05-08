(ns cljs-wc.dom-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as s]
            [cljs-wc.dom :as dom]))

(defn regex-match [f html-string & {:keys [check-equality?] :or {check-equality? true}}]
  (let [el (.createElement js/document "div")
        _ (set! (.-innerHTML el) html-string)]
    (dom/patch-element el f)
    (if check-equality?
      (is (= html-string (.-innerHTML el)))
      true)))

(deftest dom-regex
  (testing "regex-dom"
    (is (regex-match (fn [] (dom/regex-dom ^{} [:div "a"])) "<div>a</div>"))
    ; FIXME: should by default only change attributes defined in elements attrs,
    ; vs per-attribute exclusion
    (is (regex-match (fn [] (dom/regex-dom ^{:ignore #{"role"}} ["[role=region]"])) "<div role=\"region\"></div>"))
    (is (thrown? js/Error (regex-match (fn [] (dom/regex-dom [:div "a"])) "<div></div>")))

    ; TODO: should this throw? currently doesn't, clobbers the text in the div
    #_(is (thrown? js/Error (regex-match (fn [] (dom/regex-dom [:div])) "<div>a</div>"))))

  (testing "+"
    (is (regex-match (fn [] (dom/+ [:div])) "<div></div>"))
    (is (regex-match (fn [] (dom/+ [:div])) "<div></div><div></div><div></div>"))
    (is (thrown? js/Error (regex-match (fn [] (dom/+ [:div])) "")))
    (is (thrown? js/Error (regex-match (fn [] (dom/+ [:span])) "<div></div>"))))

  (testing "*"
    (is (regex-match (fn [] (dom/* [:div])) ""))
    (is (regex-match (fn [] (dom/* [:div])) "<div></div>"))
    (is (regex-match (fn [] (dom/* [:div])) "<div></div><div></div><div></div>")))

  (testing "?"
    (is (regex-match (fn [] (dom/? [:div])) ""))
    (is (regex-match (fn [] (dom/? [:div])) "<div></div>")))

  (testing "n"
    (is (regex-match (fn [] (dom/n 1 [:div])) "<div></div><div></div>" :check-equality? false))
    (is (regex-match (fn [] (dom/n 2 [:div])) "<div></div><div></div>"))
    (is (thrown? js/Error (regex-match (fn [] (dom/n 3 [:div])) "<div></div><div></div>"))))

  (testing "."
    (is (regex-match (fn [] (dom/.)) "<div></div>"))
    (is (thrown? js/Error (regex-match (fn [] (dom/.)) ""))))

  (testing "cat"
    (is (regex-match (fn [] (dom/cat)) ""))
    (is (regex-match (fn [] (dom/cat [:button] [:div])) "<button></button><div></div>"))
    (is (thrown? js/Error (regex-match (fn [] (dom/cat [:div])) "")))
    (is (thrown? js/Error (regex-match (fn [] (dom/cat [:button] [:div])) "<button></button>"))))

  (testing "collaboration"
    (is (regex-match (fn [] (dom/cat (dom/? [:div]))) "<div></div>"))
    (is (regex-match (fn [] (dom/cat (dom/? [:div]))) ""))
    (is (regex-match (fn [] (dom/cat (dom/? [:div]) (dom/? [:span]))) "<span></span>"))
    (is (regex-match (fn [] (dom/cat (dom/cat [:div] [:span]) [:small])) "<div></div><span></span><small></small>"))
    (is (thrown? js/Error (regex-match (fn [] (dom/? [:div (dom/+ [:p])])) "<div></div>")))
    (let [f (fn [] (dom/cat [:main
                             (dom/cat [:h1]
                                      (dom/? [:small])
                                      (dom/+ [:p])
                                      [:footer]
                                      (dom/* [:small]))]))]
      (is (regex-match f "<main><h1></h1><small></small><p></p><p></p><footer></footer><small></small></main>"))
      (is (regex-match f "<main><h1></h1><p></p><p></p><footer></footer><small></small></main>"))
      (is (regex-match f "<main><h1></h1><p></p><footer></footer></main>"))
      (is (regex-match f "<main><h1></h1><p></p><footer></footer><small></small><small></small></main>"))
      (is (thrown? js/Error (regex-match f "")))
      (is (thrown? js/Error (regex-match f "<main></main>")))
      (is (thrown? js/Error (regex-match f "<main><h1></h1><footer></footer><small></small><small></small></main>")))
      (is (thrown? js/Error (regex-match f "<main><h1></h1><p></p></main>"))))))
