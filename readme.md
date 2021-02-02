# Expressions

A project for converting text predicates/expressions into a performant, compiled predicate which can work on data types

# Development

The expressions themselves are meant to be shipable as a library, though in practice they work well as part
of a larger ecosystems, such as Kafka sink transformations.

The rest app assumes a locally running Kafka instance, but just running `make` from the root will do the trick.

Then just fire up `DevMain` and head on over to `http://localhost:8080/index.html`
