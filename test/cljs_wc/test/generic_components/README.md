# Generic Components

These are ports of the components found here:
https://github.com/thepassle/generic-components

The reason to do this was to have some real world examples to test the library
against. Because the styling on these is very simple, but there is a focus on
a11y these seemed like a good choice.

## Experience Reports

### Accordion

setting observed attribute on :host shouldn't cause attribute changed?
separate:

1. internal state changes
2. outer api consistency

deref gives true datafied view of element? specify all keys to datafy for perf?
or, support something like observables in angular, w/ observable props/attrs
would be very cyclejs like

syncing apis and state is tough

- Host attributes (always public)
  - set internally or externally
  - can change in response to other internal changes
  - has wc callback
    - any setAttribute call causes firing
    - (untested) :host syncing could cause infinite loop?
- Host properties (public)
  - set internally or externally
  - manually needs to retrigger updates
- Events
  - click/focus/keydown etc
  - easy to attach to the :host
- Slot content
  - `slotchange` triggers on add/remove nodes, not mutate of nodes
  - since it's DOM you don't control it's mostly imperative
- View content
  - declarative w/ incremental dom
  - might have side effects from setting properties/attributes

Some Ideas:

- The vdom is always "for others", IE setting an attribute on host should not
  retrigger the attribute callback
- Split the IWatchable, ISwap and IReset interfaces accordingly
  - IWatchable simply gives an interface for change detection
  - It doesn't dictate where the changes come from. It does in some ways suggest
    what changes, specifically the idea of old value and new value
  - Could have an event which triggers it, `notifywatches` or something,
    then it would be hookable, including from regular JS
  - brings up the idea that we need to consider "from js" interaction
- Actually we can use vdom to set props on the :host too, which really just
  separates external setting from internal setting (darn, not so easy actually)
- What about an action based model? Everything that goes into render has an
  action with it, like a callback for an event. 
  - Well, we still want to separate the reducer piece from the view piece,
    though our view can't really be called pure since it has the query refs

#### Light DOM operations

; it would be nice to derive these automatically, like
; (for [i (dom/count "button")]
; skip-until? more like regex, `(dom/* ...)` etc

#### Attribute features

; :event/keydown.up

#### A really good idea?

- declarative mappings, can detect cycles
- `[:prop/selected :> :attr/selected]`
- `[:prop/selected :> #(js/parseInt % 10) :> :prop/_index]`
- fancy `[:prop/a :> (if (> 1 %) :prop/b :prop/c)]`