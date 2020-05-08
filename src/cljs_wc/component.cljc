(ns cljs-wc.component
  (:require [clojure.zip :as z]
            #?@(:clj [[camel-snake-kebab.core :as csk]
                      [clojure.core :as c]])
            #?(:cljs [cljs-wc.dom :as dom :refer [patch-element]]))
  #?(:cljs (:require-macros cljs-wc.component)))

#?(:cljs
   (do

; cljs

(def ^:dynamic *query-root* nil)
(def ^:dynamic *query-cache* nil)

(defprotocol IInit
  (-init [o]))

(defprotocol IConnected
  (-connected [o]))

(defprotocol IDisconnected
  (-disconnected [o]))

(defprotocol IRender
  (-render [o] [o target]))

(defprotocol IMatch
  (-match [o x]))

(defprotocol IQuery
  (-remove-node [o node])
  (-add-node [o node]))

(defn extend-component
  "Don't call this."
  [t]
  (extend-type t
    IRender
    (-render [this]
      (-render (.-template this) this))

    ISwap
    (-swap!
      ([this f]
       (swap! (.-state this) f))
      ([this f a]
       (swap! (.-state this) f a))
      ([this f a b]
       (swap! (.-state this) f a b))
      ([this f a b xs]
       (apply swap! (.-state this) f a b xs)))

    IReset
    (-reset! [this new-value]
      (reset! (.-state this) new-value))

    cljs.core/IWatchable
    (-add-watch [this key f]
      (add-watch (.-state this) key f))
    (-remove-watch [this key]
      (remove-watch (.-state this) key))

    IDeref
    (-deref [this]
      (deref (.-state this)))))

(deftype Template [tag-name f queries]
  IRender
  (-render [_ element]
    (when f
      (let [shadow-root (.-shadowRoot element)
            removed-nodes (atom [])
            created-nodes (atom [])]
        (binding [dom/*host-tag-name* tag-name
                  dom/*nodes-deleted* #(reset! removed-nodes %)
                  dom/*nodes-created* #(reset! created-nodes %)
                  *query-root* shadow-root
                  *query-cache* queries]
          (dom/patch-element
            shadow-root
            (fn []
              (f element))))
        (doseq [q (vals @queries)]
          (let [oldnodes @q
                rnodes (filter #(-match q %) @removed-nodes)
                cnodes (filter #(-match q %) @created-nodes)]
            (doseq [n rnodes]
              (-remove-node q n))
            (doseq [n cnodes]
              (-add-node q n))
            (when (seq (concat rnodes cnodes))
              (-notify-watches q oldnodes @q))))))))

(defn node-matches? [selector x]
  (and (instance? js/HTMLElement x)
       (.matches x selector)))

(deftype QueryRef [pred ^:mutable _nodes ^:mutable _watches]
  IMatch
  (-match [_ x] (pred x))

  IQuery
  (-remove-node [_ node]
    (set! _nodes (remove #{node} _nodes)))
  (-add-node [_ node]
    (set! _nodes (conj _nodes node)))

  IWatchable
  (-notify-watches [this oldnodes newnodes]
    (doseq [[key f] _watches]
      (f key this oldnodes newnodes)))
  (-add-watch [this key f]
    (set! _watches (assoc _watches key f)))
  (-remove-watch [this key]
    (set! _watches (dissoc _watches key)))

  IDeref
  (-deref [_] _nodes))

(defn query
  ([key pred]
   (query pred nil))
  ([key pred watch]
   (let [queries *query-cache*
         root *query-root*]
     (or (get @queries key)
         (let [r (QueryRef. pred nil {})
               _ (swap! queries assoc key r)
               _ (when watch (add-watch r ::query watch))]
           r)))))

(defn force-render!
  "Shouldn't be called in production code."
  [node]
  (doseq [node (->> (dom/dom-zip node :shadow-dom? true)
                    (iterate z/next)
                    (take-while (complement z/end?))
                    (map z/node)
                    (filter #(satisfies? IRender %)))]
    (-render node)))

))

#?(:clj
   (do

; clj

(defn ->descriptor-sym [name prop type]
  (symbol (str (csk/->kebab-case (c/name name)) "#" (c/name prop) "#" (c/name type))))

(defn ->property-descriptor [type->sym]
  `(cljs.core/js-obj
     ~@(mapcat (fn [[type sym]]
                 (case type
                   :get
                   ["get"
                    `(fn []
                       (cljs.core/this-as this# ((var ~sym) this#)))]

                   :set
                   ["set" `(fn [prop#]
                             (cljs.core/this-as this# ((var ~sym) this# prop#)))]

                   :fn
                   ["value" `(fn [& args#]
                               (cljs.core/this-as this# (apply (var ~sym) this# args#)))]))
               type->sym)))

(defn ->property-descriptor-defs [sym->value]
  `(do
     ~@(map (fn [[sym value]]
              `(def ~sym ~value))
            sym->value)))

))

; change this syntax (get [this] ...)
(defmacro define-properties [name & specs]
  `(do
     ~@(map (fn [[prop & raw-entries]]
              (let [entries (partition 2 raw-entries)
                    types (map first entries)
                    values (map second entries)
                    syms (map (fn [[type value]]
                                (->descriptor-sym name prop type))
                              entries)
                    defs (->property-descriptor-defs (map vector syms values))
                    desc (->property-descriptor (map vector types syms))
                    prop-name (c/name prop)]
                `(do
                   ~defs
                   (when-not (js/Object.getOwnPropertyDescriptor (.-prototype ~name) ~prop-name)
                     (js/Object.defineProperty (.-prototype ~name) ~prop-name ~desc)))))
            specs)))

(defmacro defrender
  [name params config dom-body]
  (let [sym-name (c/name name)
        qrefs (partition 2 (:queries config))
        qsyms (map first qrefs)
        qvals (map second qrefs)
        qargs (map (fn [[_ watch]] {:watch watch}) qvals)
        qpreds (map (fn [[protocol-or-selector]]
                      (if (string? protocol-or-selector)
                        `(fn [x#] (cljs-wc.component/node-matches? ~protocol-or-selector x#))
                        `(fn [x#] (satisfies? ~protocol-or-selector x#))))
                    qvals)
        qpred-syms (map (fn [sym]
                          (symbol (str sym-name "-" (c/name sym) "-query-match-pred")))
                        qsyms)
        qkeys (map (fn [sym]
                     (keyword (str *ns*) (str "query." (c/name sym) "." sym-name)))
                   qsyms)
        qpred-defs (map (fn [sym pred] `(def ~sym ~pred)) qpred-syms qpreds)
        qbindings (vec
                    (mapcat (fn [sym key pred-sym {:keys [watch]}]
                              [sym `(cljs-wc.component/query ~key (var ~pred-sym) ~watch)])
                            qsyms qkeys qpred-syms qargs))]
    `(do
       ~@qpred-defs

       (defn ~name ~params
         (let ~qbindings
           (cljs-wc.dom/dom ~dom-body))))))

(defmacro defcomponent
  [name & {:keys [render observed-attributes]
           :or {observed-attributes []}}]
  (let [tag-name (c/name (csk/->kebab-case name))
        factory-name (symbol (str "->" (c/name name)))
        obs-attrs (mapv c/name observed-attributes)]
    `(do
       (defonce ~factory-name
         (do
           (defn ~name []
             (let [this# (js/Reflect.construct js/HTMLElement (cljs.core/array) ~name)
                   template# (cljs-wc.component/Template. ~tag-name ~render (atom {}))]
               (goog.object/set this# "state" (atom {}))
               (goog.object/set this# "template" template#)
               (.attachShadow this# (cljs.core/js-obj "mode" "open"))
               (when (satisfies? cljs-wc.component/IInit this#)
                 (cljs-wc.component/-init this#))
               ; not sure if should be here or connectedCallback
               (add-watch this# ::render
                          (fn [_# _# _# _#]
                            (cljs-wc.component/-render template# this#)))))

           (js/Object.setPrototypeOf (.-prototype ~name) (.-prototype js/HTMLElement))
           (js/Object.setPrototypeOf ~name js/HTMLElement)

           (js/Object.defineProperty ~name "observedAttributes"
                                     (cljs.core/js-obj "get" (fn [] (to-array ~obs-attrs))))

           (set! (.. ~name -prototype -attributeChangedCallback)
                 (fn [attribute# old-value# new-value#]
                   (cljs.core/this-as
                     this#
                     (swap! this# assoc (keyword "attr" attribute#) new-value#))))

           (set! (.. ~name -prototype -connectedCallback)
                 (fn []
                   (cljs.core/this-as
                     this#
                     (when (satisfies? cljs-wc.component/IConnected this#)
                       (cljs-wc.component/-connected this#))
                     (cljs-wc.component/-render this#))))

           (set! (.. ~name -prototype -disconnectedCallback)
                 (fn []
                   (cljs.core/this-as
                     this#
                     (when (satisfies? cljs-wc.component/IDisconnected this#)
                       (cljs-wc.component/-disconnected this#)))))

           (.define js/customElements ~tag-name ~name)

           (fn ~factory-name []
             (js/document.createElement ~tag-name))))

       ; outside of defonce to sync protocols
       (cljs-wc.component/extend-component ~name))))

(defmacro defroot [name element dom-body]
  `(defn ~name []
     (cljs-wc.dom/patch-element
       ~element
       (fn []
         (cljs-wc.dom/dom ~dom-body)))))
