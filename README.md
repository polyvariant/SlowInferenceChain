# SlowInferenceChain

Scalafix semantic rule that flags Scala 3 call sites which can trigger slow type inference when:

- a method omits a type argument,
- that type argument is higher-kinded (for example `F[_]`),
- that type argument affects the result type, but not as the top-level result constructor itself (for example `Resource[F, Unit]` is checked, `F[Int]` is not),
- it only shows up through implicit / context evidence,
- and the result is immediately chained on, including `for` generators.

This rule is a workaround for the Scala 3 compiler issue [scala/scala3#18763](https://github.com/scala/scala3/issues/18763).

## Setup

Add the rule to your build:

```scala
ThisBuild / scalafixDependencies +=
  "org.polyvariant" %% "SlowInferenceChain" % version
```

Then run Scalafix on your sources:

```bash
sbt "scalafix SlowInferenceChain"
```

or add it to your `.scalafix.conf`:

```diff
rules = [
  DisableSyntax,
  LeakingImplicitClassVal,
  NoAutoTupling,
  NoValInForComprehension,
  RedundantSyntax,
+ SlowInferenceChain,
]

```

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

Working on the rule itself? See [DEVELOPMENT.md](./DEVELOPMENT.md), which
includes how to profile the typer phase to confirm whether a flagged call site
actually compiles slowly.

## Current fixture coverage

Positive cases:

- chained method call on the inferred result
- `for`-comprehension generator

Negative cases:

- explicit type argument already present
- plain type parameter rather than a higher-kinded one
- no chaining after the call
- type parameter appears in a non-implicit parameter
- no implicit / given evidence for the type parameter
- matching higher-kinded type parameter and corresponding implicit evidence already exist in an enclosing scope
- return type does not depend on the inferred type parameter
- return type is directly `F[...]`
