# cljs-wc

A small framework for writing Web Components in ClojureScript.

## Status

This library is pre-pre-pre-pre-alpha. It's untested, unfinished and unfit for
consumption (except for experimentation). With that said, if you're interested
in this library make an issue with feature requests.

## Usage

### `defcomponent`

Component definition is done by using the `cljs-wc.component/defcomponent`
macro.

```clojure
(defn render [state]
  (cljs-wc.dom/dom [:div "Hello World!"]))

(defcomponent MyComponent
  :render #'render)
```

Note:

- `defcomponent` has `defonce` semantics, thus the `#'render` var usage
- `MyComponent` has an element name of `my-element`
- `->MyComponent` is a factory for creating dom nodes you can manually attach

The component uses a single atom for its state, which when updated, will trigger
a re-render. The entire atom is passed to the `:render` function and other
lifecycle hooks. Any data can be added to the atom, but keys used and modified
by the `defcomponent` implementation are the following:

- `:cljs-wc.component/shadow-root` - the instance of the shadow dom root
- `:cljs-wc.component/element` - a reference to the component instance itself
- `:attributes` - a map of attribute keyword to value. No attempt is made to
  convert the value, so these will always be strings.

The list of possible options to pass to `defcomponent` are:

- `:render` - a function whose body contains dom instructions compiled using
  `cljs-wc.dom/dom`, gets passed the components state atom
- `:observed-attributes` - a vector of attribute strings - changes to these
  attributes will be stored in the `:attributes` map of the state atom
- `:initialize` - called once when the component is first connected, gets passed
  the components state atom
- `:connected-callback` - called whenever `connectedCallback` would be, gets
  passed the components state atom

### `dom`

A wrapper over Google's
[incremental-dom](https://github.com/google/incremental-dom) library lives in
the `cljs-wc.dom` namespace. `cljs-wc.dom/dom` is a macro that compiles to
incremental-dom instructions. It accepts a familiar hiccup style syntax.

All forms should be hiccup vectors, with the exception of a few special cases,
which are `if`, `if-not`, `when`, `when-not`, `each` (like `for` except only
takes a single binding, similar to `doseq`), and `let`. An error will be thrown
if an unknown expression form is encountered.

Likewise, attribute maps cannot be dynamically created. Any expression can be
used as an attributes value.

### `patch-element`

`cljs-wc.dom/patch-element` can be used to update a dom node with new
instructions compiled from `cljs-wc.dom/dom`. For components you don't need to
call this manually, but you can use it to mount components to your page.

```clojure
(defn render-app []
  (dom [:my-component]))

(patch-element (.-body js/document) render-app)
```
