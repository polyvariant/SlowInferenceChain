# SlowInferenceChain

Scalafix semantic rule that flags Scala 3 call sites which can trigger slow type inference when:

- a method omits a type argument,
- that type argument affects the result type,
- it only shows up through implicit / context evidence,
- and the result is immediately chained on, including `for` generators.

## Project layout

- `rules/` — the rule implementation
- `input/` — test fixtures consumed by Scalafix testkit
- `output/` — unused for this linter, kept for the standard testkit layout
- `tests/` — the Scalafix test suite

## Commands

```bash
sbt rules/compile
sbt tests/test
```

## Current fixture coverage

Positive cases:

- chained method call on the inferred result
- `for`-comprehension generator

Negative cases:

- explicit type argument already present
- no chaining after the call
- type parameter appears in a non-implicit parameter
- no implicit / given evidence for the type parameter
- return type does not depend on the inferred type parameter
