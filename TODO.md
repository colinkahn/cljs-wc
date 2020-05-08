# TODO

## Testing

- Write tests for https://github.com/webcomponents/custom-elements-everywhere
  test cases
- test for queries not reaching beyond one level

## Features

- light dom considerations
  - light dom queries
  - "render once" semantics
    - https://github.com/webcomponents/custom-elements-everywhere

## Developer Experience

- turn query watch into def w/ var ref as well so hot reloading works
- support `with-let` style binding in `defrender`
- macro for query watch
  - lets you do setup/cleanup in on expression
- change syntax of define-properties to be `(get [this] ...)`
- update README
- create todo mvc example app
