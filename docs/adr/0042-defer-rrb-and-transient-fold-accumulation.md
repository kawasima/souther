# ADR-0042: Defer RRB concatenation and transient fold accumulation

Status: Accepted

## Context

A `List` value is a persistent bit-mapped vector trie and a `Map`/`Set` a persistent CHAMP trie, so
the fold-derived combinators grow their result by structural sharing rather than whole-collection
copying — which is what turned `map`/`filter`/`groupBy` from O(n²) into O(n) (`List.fold` is the one
recursive helper these are written over, ADR-0028, ADR-0051). On top of that baseline a performance
review listed several further optimizations. Two of them were built or scoped and then set aside;
this ADR records why, so they are not re-proposed without new evidence.

The two completed in the same pass, for contrast, were: walking a fold's source with an `Iterator`
instead of `get(i)` (a persistent vector's iterator is O(1) amortized per element, `get(i)` is
O(log₃₂ n)), and canonicalizing the CHAMP trie on `remove` (inlining a collapsed single-entry
sub-node so a key set reached by deletions keeps the same shape and iteration order as one built
directly). Both are unconditional improvements and were kept.

The two considered here are different in kind:

- **RRB (relaxed radix balanced) concatenation**, to join two large lists in O(log n) instead of
  appending the right operand element by element. `++` on the fold hot path is `acc ++ [x]`, already
  O(1) amortized through `append`; only concatenating two *large* lists is O(right).
- **A transient (edit-token) accumulator threaded through `fold`**, to cut the per-element allocation
  of the growing accumulator — the new tail array and vector object that `append` allocates each step.
  A bulk `Builder` already exists for the `from`/`sort` path, where linear (unaliased) use is obvious.

## Decision

Do not implement RRB concatenation. Do not thread a transient accumulator through `fold`. Keep the
non-relaxed vector trie and the persistent (allocating) `append` on the fold path.

RRB was implemented far enough to run against an oracle (11 of 13 cases green) and then reverted. It
is not a local addition: correct RRB concatenation is a rewrite of the vector's traversal, iteration,
and append paths plus a rebalancing merge, and it carries permanent costs that outlast the change —

- once a vector is concatenated it becomes *relaxed* and stays relaxed for every descendant, so
  `get` and iteration pay a size-table search at each level (a worse constant) from then on;
- every relaxed node carries an `int[]` size table, so a concatenated structure costs extra memory;
- the trie now has two node shapes, a standing maintenance tax on every future change to the vector;
- `RandomAccess` becomes a weaker promise, since a relaxed `get` is no longer effectively constant.

against a benefit that only appears when two *large* lists are concatenated — rare in a domain model,
where lists are grown incrementally and joined with singletons.

The transient fold accumulator is a *constant-factor* change, not an asymptotic one: `fold` is
already O(n) and `acc ++ [x]` already O(1) amortized. Capturing it safely means teaching codegen to
recognize a fold that grows a list by concat-append and to flow a mutable list value through the loop
— and the combinators do not share one shape (`map` is `acc ++ [f x]`, `filter` is
`if p x then acc ++ [x] else acc`), so no single peephole covers them. It also needs the accumulator's
linear, unaliased use to be guaranteed, which is exactly the property that is *not* self-evident in a
general fold body (it is self-evident for `from`/`sort`, which is why the bulk `Builder` is confined
there). Invasive, breakable, and narrow.

## Consequences

The performance backlog for the persistent collections is closed: the unconditional wins
(fold-iterator traversal, CHAMP canonicalization) are taken, and the two remaining items are recorded
here as deliberately deferred rather than left as open tasks in a plan or a memory. Large-list `++`
stays O(right) and fold accumulation keeps its persistent per-element allocation.

Neither door is nailed shut. If a real workload makes large-list concatenation hot, RRB can be built
against the oracle harness already sketched; if allocation on the fold path shows up in a profile, a
transient accumulator can be revisited behind a linearity check. The decision is "not now, for this
cost/benefit," not "never" — reopen it with a workload, not on principle.

## References

- ADR-0028 (the stdlib is Souther over an intrinsic kernel; the fold path this ADR chooses not
  to further optimize)
- ADR-0051 (`fold` is a recursive helper, not a privileged loop)
- ADR-0039 (Set is a collection type; the persistent-collection backing these decisions sit on)
- Prior art: Bagwell & Rompf, *RRB-Trees: Efficient Immutable Vectors*; Steindorfer & Vinju, *Optimizing Hash-Array Mapped Tries* (CHAMP)
