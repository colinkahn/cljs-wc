(ns cljs-wc.component
  (:require #?(:clj [camel-snake-kebab.core :as csk])
            #?(:clj [clojure.core :as c])
            #?(:cljs [cljs-wc.dom :refer [patch-element]]))
  #?(:cljs (:require-macros cljs-wc.component)))

#?(:cljs
   (defn maybe-initialize [this render initialize]
     (when-not (.-state-atom this)
       (let [_ (.attachShadow this (cljs.core/js-obj "mode" "open"))
             state-atom (atom {::shadow-root (.-shadowRoot this) ::element this})
             _ (set! (.-state-atom this) state-atom)
             render-dom (fn []
                          (let [{:keys [::shadow-root]} @state-atom]
                            (cljs-wc.dom/patch-element shadow-root
                                                       (partial render state-atom))))
             _ (add-watch state-atom ::render render-dom)]
         (when initialize (initialize state-atom))
         (render-dom)))))

#?(:cljs
   (defn get-state-atom [el]
     (.-state-atom el)))

(defmacro defcomponent
  [name & {:keys [render connected-callback initialize observed-attributes]}]
  (let [el-name (c/name (csk/->kebab-case name))]
    `(defonce ~(symbol (str "->" (c/name name)))
       (let []
         (defn ~name []
           (js/Reflect.construct js/HTMLElement (cljs.core/array) ~name))

         (js/Object.setPrototypeOf (.-prototype ~name) (.-prototype js/HTMLElement))
         (js/Object.setPrototypeOf ~name js/HTMLElement)

         (js/Object.defineProperty ~name "observedAttributes"
                                   (cljs.core/js-obj "get" (fn [] (to-array ~observed-attributes))))

         (set! (.. ~name -prototype -attributeChangedCallback)
               (fn [attribute# old-value# new-value#]
                 (cljs.core/this-as
                   this#
                   (swap! (.-state-atom this#) assoc-in [:attributes (keyword attribute#)] new-value#))))

         (set! (.. ~name -prototype -connectedCallback)
               (fn []
                 (cljs.core/this-as
                   this#
                   (cljs-wc.component/maybe-initialize this# ~render ~initialize)
                   ; FIXME
                   (when ~connected-callback (apply ~connected-callback [(.-state-atom this#)])))))

         (.define js/customElements ~el-name ~name)

         (fn []
           (js/document.createElement ~(c/name name)))))))
