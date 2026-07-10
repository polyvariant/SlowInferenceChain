# Development

Notes for people working on the `SlowInferenceChain` rule itself. For how to
*use* the rule, see the [README](./README.md).

## Building and testing

```bash
sbt rules/compile   # compile the rule
sbt tests/test      # run the Scalafix testkit suite (input/ fixtures)
```

Fixtures live in `input/`; positive and negative cases are documented in the
README under "Current fixture coverage". Add a fixture there when you change what
the rule flags.

## Proving a flag is (or isn't) a slow compile

The rule is a heuristic: it flags call sites that *structurally* match the
[scala/scala3#18763](https://github.com/scala/scala3/issues/18763) pattern. That
is not the same as proving a given call site actually compiles slowly. When you
suspect a false positive — or want to confirm a real one — measure the **typer
phase** with the Scala 3 compiler's built-in profiler.

The key is an isolated **A/B compile**: the same code, same classpath, differing
*only* in the annotation the rule wants you to add (e.g. `foo[IO](...)` vs
`foo(...)`). Compare the typer time between the two.

### Getting isolated per-phase timing

`-Vprofile` only prints a complexity summary — **not** timing. For timing use
`-Yprofile-enabled`, which writes a CSV of per-phase wall-clock nanoseconds:

```bash
scalac -Yprofile-enabled -Yprofile-destination out.prof Foo.scala
# with scala-cli, pass compiler flags via -O:
scala-cli compile Foo.scala --server=false \
  -O -Yprofile-enabled -O -Yprofile-destination -O out.prof
```

Use `--server=false` so each run is a fresh compile (no Bloop/BSP caching
skewing the numbers).

The `typer` row is the one that matters for #18763. Wall-clock nanos are in
column 11:

```bash
awk -F, '$6=="typer"{print $11}' out.prof   # nanoseconds
```

### A/B recipe

Create two files identical except for the annotation, then:

```bash
for v in With Without; do
  scala-cli compile "$v.scala" --server=false \
    -O -Yprofile-enabled -O -Yprofile-destination -O "$v.prof" >/dev/null 2>&1
  ns=$(awk -F, '$6=="typer"{print $11}' "$v.prof" | head -1)
  echo "$v typer_ms=$(( ns / 1000000 ))"
done
```

Run each a few times — the first compile includes JIT warmup, so take the
median, not the minimum.

### Interpreting the result

- A **reproducible** gap (e.g. consistently ~1.5× / a few hundred ms on the
  typer row, identical across runs) is a real penalty — the annotation is worth
  adding.
- A gap **within run-to-run noise** means this particular site doesn't hit the
  pathology, even if the rule flagged it. That happens when the flagged def's own
  body already fully determines `F` (so the expensive inference is paid once *at
  the def*, not re-driven at the call site) — the compiler solves `F` at the call
  site cheaply from the surrounding context.

The effect is **superlinear in the depth of the chained, omitted-annotation
inference**: a single omitted site whose def leaks an unsolved `F` into a deep
chain of further `Resource[F, X[F]]` calls compounds; a single site backed by a
def that fixes `F` internally usually does not. When building a minimal repro to
study this, nest several levels of `def stepN[F[_]: Async: ...](...): Resource[F,
Handler[F, Int]]` where each level's body is a `for`-comprehension of calls to
the level below, and vary whether those calls carry the `[F]`/`[IO]` argument.

### Measuring a real (non-minimal) file

To profile an actual project file rather than a minimal repro, inject the flags
into that module's `scalacOptions` and force a recompile of just that file:

```bash
touch path/to/Target.scala
sbt \
  'set myproject/Compile/scalacOptions += "-Yprofile-enabled"' \
  'set myproject/Compile/scalacOptions ++= Seq("-Yprofile-destination", "/tmp/target.prof")' \
  'myproject/Compile/compile'
awk -F, '$6=="typer"{print $11}' /tmp/target.prof | tail -1
```

Note the whole-file typer time can swamp a single call site's contribution, so a
minimal repro is usually the clearer signal for the mechanism; the real-file
measurement answers "does *this file* compile slowly", the repro answers "does
*this pattern* compile slowly".

## Project layout

- `rules/` — the rule implementation
- `input/` — test fixtures consumed by Scalafix testkit
- `output/` — unused for this linter, kept for the standard testkit layout
- `tests/` — the Scalafix test suite
