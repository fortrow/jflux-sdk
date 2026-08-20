# Todash package

`com.fortrow.jflux.todash.__` is jflux’s compact Java utility facade inspired
by common Lodash operations. The class remains available under its historical
name for compatibility and has no instance state.

## Compatibility utilities

Existing helpers remain available for comparisons, null checks, string casing,
encoding, console output, progress lines, numeric conversion, and ID creation.
String equality is now null-safe. String trimming uses Java’s allocation-aware
`strip` methods, regex patterns are reused, and ID construction uses a single
`StringBuilder` instead of repeated concatenation.

`isEmpty` recognizes empty strings, collections, maps, iterables, and arrays in
addition to `null`, matching Lodash’s `isEmpty` semantics. The historical
`empty(Object)` and `isset(Object)` methods retain their null-oriented behavior
because downstream request parsers use them to distinguish a missing key from
an existing empty array or object. Code that needs only a null check can use
`isNil` directly.

## Collection helpers

The class now includes allocation-conscious loop-based implementations of:

- `map`, `filter`, `find`, `every`, `some`, and `compact`;
- `uniq`, `flatten`, `chunk`, `take`, `drop`, `first`, and `last`;
- `groupBy`, `keyBy`, `countBy`, `sortBy`, and `reverse`;
- `get`, `has`, `pick`, `omit`, `includes`, and `without`; and
- `range`, `clamp`, `size`, and `defaultTo`.

Collection results are independent mutable `ArrayList`/`LinkedHashMap` copies,
so callers can modify them without changing the input collection. Ordering is
preserved where the operation has a natural ordering contract.

## Strings and console

`capitalize`, `upperFirst`, `lowerFirst`, `kebabCase`, `words`, and
`splitLiteral` complement the existing casing and split functions. Console
input reuses one UTF-8 scanner instead of allocating a new scanner per prompt;
secure input falls back to normal input when no terminal console is available.
Progress bars now render bounded 0–100% output, and `LineCollection` exposes
`size`, `get`, and `render` for programmatic use.

The package targets the project’s Java 23 build and uses standard Java
collections and functional interfaces rather than adding a Lodash dependency.
