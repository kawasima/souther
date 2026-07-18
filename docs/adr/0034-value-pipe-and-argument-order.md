# ADR-0034: The value pipe `|>` threads a value through calls; the stdlib takes its main object last

Status: Accepted

## Context

Souther aims to sit where Elm sits — a bounded, domain-facing language — while drawing its
core shapes (railway output, unmarked sums, `->`) from F#. Users of both languages reach for
the value pipe `|>` constantly: they read a transformation top to bottom, `x |> f |> g`, not
inside-out as `g(f(x))`. Souther had no such pipe, and its nested combinator calls read
inside-out. A representative line from the `tagging` example:

```
sort(filter(map(split(入力.生, ","), 片 -> lowercase(trim(片))), 片 -> String.length(片) >= 1))
```

ADR-0028 explicitly left `|>` out, with the reason that Souther's combinators take the
collection first (`map(xs, f)`) whereas Elm and F# take it last precisely so a curried call
partially applied by `|>` threads the value in as the final argument. That reason is the crux:
`|>` does not fail to fit Souther inherently, the collection-first argument order is what
blocks it.

## Decision

Adopt the value pipe `|>`, and change the standard library's argument order to make it read.

- **`|>` feeds the left operand in as the last argument of the right-hand call.** `e |> f(a)`
  is `f(a, e)`; a bare function name `e |> f` is `f(e)`. It binds looser than everything else
  and is left-associative, so `a |> f |> g` is `g(f(a))`. The right side must be a call or a
  function name — `e |> 3` is a compile error.
- **It desugars at parse time**, like `require` → `if`, so no later pass sees a pipe node. This
  matters concretely: bare stdlib names are rewritten to their qualified form (`sort` →
  `List.sort`) by an early pass, so a pipe surviving into a later stage would leave `|> sort`
  unqualified. Desugaring in the parser turns it into an ordinary call before that pass runs.
- **The stdlib takes its main object last** — the collection, string, or map goes last, and the
  function, separator, or key comes first: `List.map(f, xs)`, `List.filter(p, xs)`,
  `List.fold(step, seed, xs)`, `String.split(sep, s)`, `String.replace(target, replacement, s)`,
  `Map.get(key, m)`. This is the F#/Elm order; it lands the piped value in the last argument so
  `xs |> List.map(f) |> List.filter(p)` flows top to bottom.
- **`fold`'s step block keeps `(acc, x)`** — the F# `List.fold` order — even though `foldl` in
  Elm passes the element first. Only `fold`'s own position changes (the list moves last);
  the accumulator-first step is unchanged, so existing folds need no rewrite of their lambda.

This reverses two choices ADR-0028 recorded; that ADR is annotated accordingly.

## Consequences

The pipe is pure sugar: it exists only in the lexer and parser, adds no AST node, and no type
checker or backend case. The `tagging` example above becomes a chain read top to bottom:

```
入力.生
    |> split(",")
    |> map(片 -> 片 |> trim |> lowercase)
    |> filter(片 -> String.length(片) >= 1)
    |> sort
```

The argument-order change touches every combinator call across the examples, the tests, and the
spec, and it is a silent-flip hazard for the string predicates whose arguments are both `String`
(`contains(sub, s)`, `startsWith(prefix, s)`): those type-check either way, so their meaning had
to be preserved by hand rather than caught by the compiler.

`|>` is deliberately kept distinct from `>->`: `>->` composes behaviors at the declaration level
with type routing (ADR-0007), while `|>` threads a value through ordinary function calls inside a
body. They do not overlap — a behavior name is not a value, and a value is not a pipeline stage.

## References

- Specification: `[#pipe]`, `[#stdlib]`, `[#stdlib-list]`, `[#stdlib-string]`, `[#stdlib-map]`,
  `[#delimiters]`
- Amends: ADR-0028 (which left `|>` out and kept collection-first order)
- Related: ADR-0007 (`>->` and railway), ADR-0025 (first-class functions / blocks)
