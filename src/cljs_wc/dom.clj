(ns cljs-wc.dom
  (:require [hiccup.compiler :as hc]
            [camel-snake-kebab.core :as csk]
            [clojure.set :refer [rename-keys]]))

(def void-tags
  #{"area" "base" "br" "col" "command" "embed" "hr" "img" "input" "keygen" "link"
    "meta" "param" "source" "track" "wbr"})

(defn void-tag? [tag]
  (some? (void-tags tag)))

(defn fragment-tag? [tag]
  (= "<>" tag))

(defn static-attr? [k]
  (= (namespace k) "static"))

(defn attrs* [attrs]
  (when attrs
    (let [attrs (filter #(some? (val %)) attrs)
          static-pairs (->> attrs (filter #(static-attr? (key %))))
          dynamic-pairs (->> attrs (filter #(not (static-attr? (key %)))))
          static-vec (->> (mapcat (fn [[n v]] [(name n) v]) static-pairs) vec)]
      (concat [static-vec] (mapcat (fn [[n v]] [(name n) v]) dynamic-pairs)))))

(defn write-event
  [{:keys [kind tag attrs content id form] :as parse-event}]
  (case kind
    :expression
    {:write form}

    :open
    {:write `(cljs-wc.dom/element-open* ~tag ~id ~@(attrs* attrs))}

    :void
    {:write `(cljs-wc.dom/element-void* ~tag ~id ~@(attrs* attrs))}

    :close
    {:write `(cljs-wc.dom/element-close* ~tag)}

    :text
    {:write `(cljs-wc.dom/text* ~content)}))

(defn get-key [form]
  (-> form meta :key))

(defn normalize-tag [kw]
  (if (namespace kw)
    (keyword (str (clojure.string/replace (namespace kw) #"\." "-") "-" (name kw)))
    kw))

(defn attr->property [kw]
  (csk/->camelCase (name kw)))

(defn normalize-attrs [attrs]
  (if-some [namespaced-keys (seq (filter namespace (keys attrs)))]
    (rename-keys attrs (->> namespaced-keys
                            (map (juxt identity normalize-tag))
                            (into {})))
    attrs))

(defn binding-form [sym form]
  (let [[_ bindings & body] form]
    [{:kind :expression
      :form `(~sym ~bindings
               ~@(butlast body)
               (dom ~(last body)))}]))

(defn fragmentize*
  [form]
  (when form
    (cond
      (and (list? form) (symbol? (first form)))
      (case (name (first form))
        ("if" "if-not")
        (let [[ff test then-form else-form] form]
          [{:kind :expression
            :form `(~ff ~test
                     (dom ~then-form)
                     (dom ~else-form))}])

        ("when" "when-not")
        (let [[ff test then-form] form]
          [{:kind :expression
            :form `(~ff ~test
                     (dom ~then-form))}])

        "each"
        (binding-form 'doseq form)

        "let"
        (binding-form 'let form)

        (throw (ex-info (str "Invalid expression for form " (pr-str form)) {})))

      (vector? form)
      (let [[_ attrs children] (hc/normalize-element form)
            tag (-> form first normalize-tag name)
            attrs (normalize-attrs attrs)
            id (get-key form)
            void? (void-tag? tag)]
        (if (fragment-tag? tag)
          (lazy-seq
            (mapcat fragmentize* children))
          (lazy-seq
            (cons {:kind (if void? :void :open) :tag tag :attrs attrs :id id}
                  (concat (mapcat fragmentize* children)
                          (when-not void? [{:kind :close :tag tag}]))))))

      :else
      [{:kind :text :content form}])))

(defn fragmentize [form]
  (->> form (fragmentize*) (map write-event)))

(defmacro dom [forms]
  (let [events (fragmentize forms)]
    `(do
       ~@(for [{:keys [write] :as event} events]
          write)
       nil)))
