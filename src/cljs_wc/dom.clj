(ns cljs-wc.dom
  (:require [hiccup.compiler :as hc]
            [camel-snake-kebab.core :as csk]
            [clojure.set :refer [rename-keys]]
            [clojure.string :as s])
  (:refer-clojure :exclude [* + cat]))

(def ^:dynamic *dom-macro* `dom)

(def void-tags
  #{"area" "base" "br" "col" "command" "embed" "hr" "img" "input" "keygen" "link"
    "meta" "param" "source" "track" "wbr"})

(defn expression? [x]
  (and (list? x)
       (symbol? (first x))))

(defn element? [x]
  (and (vector? x)
       (keyword? (first x))))

(defn void-tag? [tag]
  (some? (void-tags tag)))

(defn fragment-tag? [x]
  (cond
    (element? x)
    (recur (first x))

    (or (keyword? x) (string? x))
    (= "<>" (name x))

    :else false))

(defn host-tag? [tag-name]
  (= "host" tag-name))

(defn skip-tag? [tag-name]
  (= "skip" tag-name))

(defn skip-node-tag? [tag-name]
  (= "skip-node" tag-name))

(defn static-attr? [k]
  (and (keyword? k)
       (some-> (namespace k)
               (clojure.string/starts-with? "static."))))

(defn attrs* [attrs]
  (when attrs
    (let [attrs (filter #(some? (val %)) attrs)
          static-pairs (->> attrs (filter #(static-attr? (key %))))
          dynamic-pairs (->> attrs (filter #(not (static-attr? (key %)))))
          static-vec (->> (mapcat (fn [[n v]] [n v]) static-pairs) vec)]
      (concat [static-vec] (mapcat (fn [[n v]] [n v]) dynamic-pairs)))))

(defn write-event
  [{:keys [kind tag attrs content id form selector form-meta] :as parse-event}]
  (case kind
    :expression
    {:write form}

    (:open :void)
    {:write `(binding [cljs-wc.dom/*form-meta*
                       ~(merge form-meta {:attrs (->> (attrs* attrs)
                                                      (drop 1)
                                                      (partition 2)
                                                      (map first)
                                                      set)})]
               (~(case kind
                   :open `cljs-wc.dom/element-open*
                   :void `cljs-wc.dom/element-void*)
                       ~(if (= :auto tag) `(cljs-wc.dom/auto-open) tag)
                       ~id ~@(attrs* attrs)))
     :test (if selector
             `(fn [x#] (cljs-wc.dom/selector-matches? ~selector x#))
             `(fn [x#] (cljs-wc.dom/tag-name? ~(s/upper-case tag) x#)))}

    :close
    {:write `(cljs-wc.dom/element-close*
               ~(if (= :auto tag)
                  `(cljs-wc.dom/auto-close)
                  tag))}

    :text
    {:write `(cljs-wc.dom/text* ~content)
     :test `(fn [x#]
              (cljs-wc.dom/text? ~content x#))}))

(defn get-key [form]
  (-> form meta :key))

(defn binding-form [sym form]
  (let [[_ bindings & body] form]
    [{:kind :expression
      :form `(~sym ~bindings
               ~@(butlast body)
               (~*dom-macro* ~(last body)))}]))

(defn fragmentize*
  [form]
  (when form
    (cond
      (expression? form)
      (case (name (first form))
        ;"."
        ;[{:kind :expression
        ;  :form `(cljs-wc.dom/skip-node)}]

        ("if" "if-not")
        (let [[ff test then-form else-form] form]
          [{:kind :expression
            :form `(~ff ~test
                     (~*dom-macro* ~then-form)
                     (~*dom-macro* ~else-form))}])

        ("when" "when-not")
        (let [[ff test then-form] form]
          [{:kind :expression
            :form `(~ff ~test
                     (~*dom-macro* ~then-form))}])

        "each"
        (binding-form 'doseq form)

        "let"
        (binding-form 'let form)

        ("str" "pr-str")
        [{:kind :text :content form}]

        [{:kind :expression
          :form form}])

      (vector? form)
      (let [[tag-name attrs children] (hc/normalize-element form)
            tag (first form)
            id (get-key form)
            void? (void-tag? (name tag))
            form-meta (meta form)]
        (cond
          (string? tag)
          (lazy-seq
            (cons {:kind (if void? :void :open)
                   :tag :auto
                   :id id
                   :attrs attrs
                   :selector tag
                   :form-meta form-meta}
                  (concat (mapcat fragmentize* children)
                          (when-not void? [{:kind :close :tag :auto}]))))

          (skip-tag? tag-name)
          [{:kind :expression
            :form `(cljs-wc.dom/skip-nodes)}]

          (skip-node-tag? tag-name)
          [{:kind :expression
            :form `(cljs-wc.dom/skip-node)}]

          (fragment-tag? tag-name)
          (lazy-seq
            (mapcat fragmentize* children))

          (host-tag? tag-name)
          (cons {:kind :expression
                 :form `(cljs-wc.dom/patch-outer
                          (.-host cljs-wc.dom/*patch-node*)
                          (fn []
                            ~@(map (comp :write write-event)
                                   [{:kind :open
                                     :tag `cljs-wc.dom/*host-tag-name*
                                     :attrs attrs}
                                    {:kind :expression
                                     :form `(cljs-wc.dom/skip-nodes)}
                                    {:kind :close
                                     :tag `cljs-wc.dom/*host-tag-name*}])))}
                (mapcat fragmentize* children))

          :else
          (lazy-seq
            (cons {:kind (if void? :void :open)
                   :tag tag-name
                   :id id
                   :attrs attrs
                   :form-meta form-meta}
                  (concat (mapcat fragmentize* children)
                          (when-not void? [{:kind :close :tag tag-name}]))))))

      :else
      [{:kind :text :content form}])))

(defn fragmentize [form]
  (->> form (fragmentize*) (map write-event)))

(defmacro dom [form]
  (let [events (fragmentize form)]
     `(do
        ~@(for [{:keys [write test] :as event} events]
            write)
        nil)))

(defmacro regex-dom [form]
  (let [events (fragmentize form)]
    (binding [*dom-macro* `regex-dom]
      `(do
       ~@(for [{:keys [write test] :as event} events]
           (if test
             `(do (cljs-wc.dom/validate-loc ~test '~test) ~write)
             write))
       nil))))

(defmacro n [count form]
  `(cljs-wc.dom/rep* (fn [] (regex-dom ~form)) '~form ~count ~count))

(defmacro . []
  `(n 1 [:skip-node]))

(defmacro cat [& forms]
  `(do
     ~@(map (fn [form] (if (element? form)
                         `(n 1 ~form)
                         form))
            forms)))

(defmacro * [form]
  `(cljs-wc.dom/rep* (fn [] (regex-dom ~form)) '~form 0 nil :opt true))

(defmacro + [form]
  `(cljs-wc.dom/rep* (fn [] (regex-dom ~form)) '~form 1 nil :opt true))

(defmacro ? [form]
  `(cljs-wc.dom/rep* (fn [] (regex-dom ~form)) '~form 0 1 :opt true))

(defmacro index-as [name form]
  `(let [~name cljs-wc.dom/*index*]
     (regex-dom ~form)))

(comment
  (clojure.walk/macroexpand-all
    '(dom ^{:foo 1} [:div {:prop/something 1}])
    )
  
  )
