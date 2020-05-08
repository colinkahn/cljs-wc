(ns cljs-wc.dom
  (:require ["incremental-dom" :refer [elementOpen elementClose elementVoid
                                       text patch patchOuter attributes notifications
                                       symbols applyProp applyAttr
                                       currentElement currentPointer skip skipNode]]
            [clojure.string :as s]
            [clojure.zip :as z]
            [clojure.core :as c]
            goog.object)
  (:refer-clojure :exclude [*])
  (:require-macros [cljs-wc.dom :refer [dom]]))

(defn element-open*
  ([tag id sattrs]
   (elementOpen tag id (js->clj sattrs)))
  ([tag id sattrs pk1 pv1]
   (elementOpen tag id (js->clj sattrs) pk1 pv1))
  ([tag id sattrs pk1 pv1 pk2 pv2]
   (elementOpen tag id (js->clj sattrs) pk1 pv1 pk2 pv2))
  ([tag id sattrs pk1 pv1 pk2 pv2 pk3 pv3]
   (elementOpen tag id (js->clj sattrs) pk1 pv1 pk2 pv2 pk3 pv3))
  ([tag id sattrs pk1 pv1 pk2 pv2 pk3 pv3 pk4 pv4]
   (elementOpen tag id (js->clj sattrs) pk1 pv1 pk2 pv2 pk3 pv3 pk4 pv4))
  ([tag id sattrs pk1 pv1 pk2 pv2 pk3 pv3 pk4 pv4 pk5 pv5]
   (elementOpen tag id (js->clj sattrs) pk1 pv1 pk2 pv2 pk3 pv3 pk4 pv4 pk5 pv5))
  ([tag id sattrs pk1 pv1 pk2 pv2 pk3 pv3 pk4 pv4 pk5 pv5 & attrs]
   (apply elementOpen tag id (js->clj sattrs) pk1 pv1 pk2 pv2 pk3 pv3 pk4 pv4 pk5 pv5 attrs)))

(defn element-close* [tag]
  (elementClose tag))

(defn element-void*
  ([tag id sattrs]
   (elementVoid tag id (js->clj sattrs)))
  ([tag id sattrs pk1 pv1]
   (elementVoid tag id (js->clj sattrs) pk1 pv1))
  ([tag id sattrs pk1 pv1 pk2 pv2]
   (elementVoid tag id (js->clj sattrs) pk1 pv1 pk2 pv2))
  ([tag id sattrs pk1 pv1 pk2 pv2 pk3 pv3]
   (elementVoid tag id (js->clj sattrs) pk1 pv1 pk2 pv2 pk3 pv3))
  ([tag id sattrs pk1 pv1 pk2 pv2 pk3 pv3 pk4 pv4]
   (elementVoid tag id (js->clj sattrs) pk1 pv1 pk2 pv2 pk3 pv3 pk4 pv4))
  ([tag id sattrs pk1 pv1 pk2 pv2 pk3 pv3 pk4 pv4 pk5 pv5]
   (elementVoid tag id (js->clj sattrs) pk1 pv1 pk2 pv2 pk3 pv3 pk4 pv4 pk5 pv5))
  ([tag id sattrs pk1 pv1 pk2 pv2 pk3 pv3 pk4 pv4 pk5 pv5 & attrs]
   (apply elementVoid tag id (js->clj sattrs) pk1 pv1 pk2 pv2 pk3 pv3 pk4 pv4 pk5 pv5 attrs)))

(defn text* [s]
  (text s))

(defn auto-open []
  (or (some-> (currentPointer) (.-tagName) (s/lower-case))
      (throw (ex-info "Error getting open tag name from current pointer." {}))))

(defn auto-close []
  (or (some-> (currentElement) (.-tagName) (s/lower-case))
      (throw (ex-info "Error getting open tag name from current pointer." {}))))

(def ^:dynamic *nodes-deleted* (constantly nil))
(def ^:dynamic *nodes-created* (constantly nil))

(goog.object/set notifications "nodesDeleted" #(*nodes-deleted* (vec %)))
(goog.object/set notifications "nodesCreated" #(*nodes-created* (vec %)))

(def apply-prop applyProp)

(def apply-attr applyAttr)

(defn apply-event [element name value]
  (if (js-in (str "on" name) element)
    ; prefer "on<event>" props
    (applyProp element (str "on" name) value)
    ; ad hoc impl of "on<event>"
    (let [callback-name (str "cljs-wc.dom.event/" name)]
      (when-some [callback (goog.object/get element callback-name)]
        (.removeEventListener element name callback))
      (goog.object/set element callback-name value)
      (.addEventListener element name value))))

(defmulti set-attribute
  (fn [element key name value]
    (when (keyword? key)
      (keyword (namespace key)))))

(defmethod set-attribute :event [element _ name value]
  (apply-event element name value))

(defmethod set-attribute :prop [element _ name value]
  (apply-prop element name value))

(defmethod set-attribute :static.prop [element _ name value]
  (apply-prop element name value))

(defmethod set-attribute :attr [element _ name value]
  (apply-attr element name value))

(defmethod set-attribute :static.attr [element _ name value]
  (apply-attr element name value))

(defmethod set-attribute :default [element _ name value]
  (apply-attr element name value))

(defonce ^:dynamic *form-meta* nil)

(defn set-attribute* [element name value]
  ; TODO: why doesn't *form-meta* get prn'd for every open tag?
  ; - at first I thought it was because we weren't using many attributes
  ;   but look at the basic and advanced tests
  (println (pr-str *form-meta*))
  (when-not (contains? (:ignore *form-meta*) name)
    (set-attribute element name (c/name name) value)))

(goog.object/set attributes (.-default symbols) set-attribute*)

(defn patch-outer
  "Do not call."
  [node f]
  (patchOuter node f))

(defn skip-nodes
  "Do not call."
  []
  (skip))

(defn skip-node
  "Do not call."
  []
  (skipNode))

(def ^:dynamic *host-tag-name* nil)
(def ^:dynamic *patch-node* nil)

(defn patch-element [node f]
  (binding [*patch-node* node]
    (patch node f)))

(defn js-slice [x]
  (js/Array.prototype.slice.call x))

(defn dom-zip
  "Creates a zipper over a dom node tree. By default only returns elements and
  ignores shadow-dom. Set shadow-dom? and/or nodes? to override."
  [node & {:keys [shadow-dom? nodes?]}]
  (z/zipper
    #(instance? (if nodes? js/Node js/Element) %)
    #(seq (concat (seq (js-slice (if nodes?
                                   (.-childNodes %)
                                   (.-children %))))
                  (when shadow-dom?
                    (when-some [shadow-root (.-shadowRoot %)]
                      (js-slice (if nodes?
                                  (.-childNodes %)
                                  (.-children shadow-root)))))))
    nil
    node))

; DOM Regex

(def ^:dynamic *index* nil)
(def ^:dynamic *scope* nil)

(defn tag-name? [name x]
  (and (instance? js/HTMLElement x)
       (= (.-tagName x) name)))

(defn selector-matches? [selector x]
  (and (instance? js/HTMLElement x)
       (.matches x selector)))

(defn text? [s x]
  (and (instance? js/Text x)
       (= (.-wholeText x) s)))

(defn validate-loc [test tform]
  (when-not (and (currentPointer)
                 (test (currentPointer)))
      (throw (ex-info (str "Check failed when testing DOM with " (pr-str tform))
                      {::scope *scope*}))))

; basically need rep until, but until "what" is not known :(
(defn rep* [f form min-n max-n & {:keys [opt]}]
  (binding [*scope* (random-uuid)]
    (let [open-element (currentElement)
          {:keys [i e]} (loop [i 0]
                          (if (and (or (nil? max-n) (< i max-n))
                                   (currentPointer)
                                   (= (.-parentElement (currentPointer)) open-element))
                            (if-some [e (try (binding [*index* i] (f) nil)
                                             (catch js/Error e e))]
                              {:i i :e e}
                              (recur (inc i)))
                            {:i i}))]

      (when (and min-n (not (>= i min-n)))
        (throw (ex-info (str min-n "-or-" (or max-n "more")
                             " matches not found for "
                             (pr-str form)) {})))

      (when e
        (when-not (and opt
                       (-> e ex-data ::scope (= *scope*)))
          (throw e))))))
