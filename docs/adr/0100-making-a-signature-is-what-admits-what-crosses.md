# ADR-0100: Making a behavior's signature is what admits what crosses it

Status: Accepted

## Context

The rules about what a behavior's boundary may carry were six passes over a written declaration —
one for an anonymous union in a parameter, one for a tuple, one for a function, one for an optional,
one for a `Map` key, one for a name the language declares of its own operations — and every phase
below them held the behavior's `Type` and asked the boundary's question of it again.

`Runner.decoderFor` handled `Prim`, `Ref`, `List`, `Set` and `Map`; `Runner.encode` added `Union`.
Everything else in the type language fell to a fallback arm reporting a compiler defect in a reader's
vocabulary — *which `run` cannot decode yet*. Showing that none of it arrives took six arguments
spread across five rules: `E1313` for an optional, `E1311` for a tuple and a function, `E1312` for a
union in a parameter, `E1402` with `E1502` for an open type, and "not writable in a signature" for
the rest. The runner knew none of them. That its accepted set coincided with the checker's admitted
set was a non-local fact nothing checked, and a rule relaxed on one side without the other following
would make an ordinary compile succeed and `souther run` raise.

It was not one reader. `CodecGen.flatMember` raised on a union member no module declares (#456), and
`FixtureReader` asks the same questions over again. Three consumers, three assumptions about one
`Type`, three ways of being wrong.

Classifying the type just before the reader does not answer this. A `Type -> BoundaryShape` function
called where the runner is handed its work produces a classified tree and changes nothing: the
checker still validates independently, the classifier still re-derives, and the two still agree only
by coincidence. The map key would be classified twice — the *ask twice* the map-key witness
removed (#447), moved one level up.

## Decision

**Boundary validation is not a check performed on a signature. It is the construction of the
signature that downstream phases are allowed to see.**

`Sig` holds the shapes rather than the types:

```java
public final class Sig {
    Sig(List<BoundaryInput> ins, BoundaryOutput out) { … }   // package-private

    public List<BoundaryInput> ins();
    public BoundaryOutput out();
    public List<Type> inputTypes();
    public Type outputType();
}
```

Both are built by one walk inside `PipelineSigs.signatures`, which refuses what the boundary does not
admit as it goes. Holding a `Type` beside the witness would make a new thing to keep true — that they
agree — so the crossing is one-way: a signature always yields its types, and a type becomes a
signature only by going through the walk. There is no public `Type -> shape` utility for a later
phase to call, because one is a standing invitation to re-derive.

**The constructor is closed, not merely unused.** A record here would publish a canonical
constructor, and anything below the check could assemble a signature out of shapes nothing admitted —
a `Nominal` naming a type the language declares of its own operations, say — which every reader below
would then take for one the compiler stands behind. What would hold in that case is "the check
happens to build its signatures through the walk", which is a convention; what has to hold is that a
signature cannot be made any other way. So `Sig` is a final class whose constructor is
package-private, reachable from `SignatureBoundary` and the composition that calls it and nowhere
else. It keeps value equality by hand, because the check's answers are compared to decide whether
recomputing one changed anything.

The shapes themselves stay open. Anyone may describe a `BoundaryInput`; what is closed is raising one
to a signature the compiler vouches for.

**And there is one place that raises one.** A closed constructor stops the wrong kind of signature
from being made; it does not stop a second walk. Two callers of `PipelineSigs.signatures` would each
build a correct answer, and the check would read one while the phase below it read the other — the
boundary's question answered twice, which is what carrying the answer was for. Nothing observable
would change while it lasted, because the two walks are the same walk, and they would go on agreeing
until the trees they are given stop being the same tree. So the query that owns the answer is the
only caller: it hands the map to the module check, to the backend and to whoever drives a behavior,
and identity through the query is what "the same signature" means.

**Each structural position owns admissibility of the subtree under it.** There is no ordering between
rules to know:

```
Prim              → a scalar, or E1325 for `Raw`
Ref               → a name a model declared, or E1325
List / Set        → the element, recursively
Map               → the key as a whole, as a boundary map key; the value, recursively
Union in an input → E1312
Option            → E1313
Tuple / Fn        → E1311
```

So `Map<Option<Int>, String>` is `E1314` and not `E1313`, reversing the precedence #458 pinned
between what were then independent passes. The subtree sits in a key position, and the first question
that position asks is whether the type can be written as a boundary key; the answer is no. It is also
the better report — fixing `Option<Int>` to `Int` on `E1313`'s advice earns `E1314` next, because
`E1313` was never the reason *there*.

**Where a signature is wrong in more than one place, what is reported is the position the walk
reaches first**: the parameters as they are written, then the answer, and within a type from the
outside in. This is a wider change than the key position, and it is the same change. The passes were
rule-major — every parameter and the answer were asked about tuples before any of them was asked
about optionals — so `(x: Option<Int>) -> (Int, Int)` was reported for the tuple in the answer, and is
now reported for the optional in `x`. There is no order between rules left to state, because there
are no passes left to order: a position is asked everything at once and the next position is asked
after it. A precedence between rules would be a second thing to keep true; the traversal is one
thing, and it is the order the author reads their own declaration in.

**A scalar the boundary writes is a closed set that does not include `Raw`.** `Scalar(Type.Prim)`
could hold the reserved type, which left `Runner.leafDecoder` with a `RAW` arm and the catalog with
`run.decode.raw` and `run.encode.raw`. `BoundaryScalar` has six cases and one way in, and the arms
and their messages are gone with it.

**A name the boundary carries is closed the same way, and by where the witness lives.** `Nominal`,
`Cases` and the two `BoundaryMapKey` cases that name a type hold names, and a public canonical
constructor would let a refused one be assembled into a witness every reader takes for one the
compiler stands behind. A record cannot be closed — its canonical constructor is as accessible as
the record — so each is written out as a final class with a package-private constructor, and
`BoundaryInput`, `BoundaryOutput` and `BoundaryMapKey` sit beside the walk that admits a name rather
than in `types`, which is what makes admission the only way in.

Closing them is a choice between two readings of what a witness is worth, and the two cannot both
hold. Either a signature is the capability — shapes may be assembled by anyone, and what the closed
`Sig` constructor guarantees is that no forged one is ever *raised* to a signature — or a witness is
itself the evidence, and holding one means the name in it was admitted. The first would make closing
these cases pointless; the second is what the runner's guards were removed on the strength of, and it
is what `BoundaryScalar` already was, having six cases and no way to name `Raw`. This takes the
second, and takes it everywhere: a reader is handed a case of a union, an element of a list or a
map's key at least as often as it is handed a whole signature, so a guarantee that held only of the
signature would not be the guarantee the readers rest on.

**Classifying a key and admitting one are separate types, because they are separate questions.**
Closing a constructor is not enough where a public function hands the value out: `classifyConcreteMapKey`
answered a witness, and what it looked at was whether the type *has* a representation — which a name
of the language's own has as much as a model's. `SignatureBoundary` proved the gap by re-asking
`declaredByAModel` of the classified name. So the classification is `MapKeyRepresentation`, a fact
about a type that anything may work out, and `BoundaryMapKey` holds one and is made only where a key
position admitted the name.

The split is also what #462 says in the general form: a key position is established by a behavior's
boundary, by a data's field and by a fixture, and they do not admit alike, while the representation
is what all three share. It removes a package cycle as a side effect — `Ast` carries the
representation in its codec IR, which is a fact about a type and belongs in `types`, where `Ast`
could already reach.

Of the two questions a key position asks, only the first has an example in today's library: a name
with no representation at all is refused as that (`E1314`), and there is no type the language
declares of its own that a key *could* be classified as. The second obligation is the position's
whether or not anything can reach it now, which is why it is written where the position is.

With that, the runner's last two reflective guards go — `run.decode.nodecoder` and
`run.encode.noencoder`, which asked whether the class a name denotes has the factory being reached
for. Measured over every shape a witness has a name in, in both directions, neither is reachable: the
name came out of the witness, so a model declared it, and this compilation generated its class with
both factories. `CodecGen.jsonCompatible` goes with them, being what could once have withheld a
`jsonDecoder()` — its rule was stated as "no temporal anywhere in the shape" and had stopped being
implemented, so it answered yes to everything while the runner still guarded against a no.

**Every signature is one, whatever its origin.** A composition's answer is a type nobody wrote — the
last stage's, merged with the cases that left the main line — and it is admitted where it is made; a
declaration read back from a jar travels as source and is admitted by the same walk when the
importing compilation builds its signatures. Giving only locally written ones a witness would make
two kinds of signature and a fallback to reconcile them, which is this decision undone a few months
on.

**A jar carries the declaration, not the witness**, and the import admits it once. Serializing the
witness was the other candidate, on the grounds that re-admitting on import replays a proof the
writer already had. It is rejected because a module's declarations already travel as source — a data,
an invariant, a published helper, all read back by the same front end — so a serialized witness would
be a *second* representation of what the declaration already says, and the two agreeing would be a
new thing to keep true. Admitting at the import is the same walk on the same kind of input, and it is
where a compilation gets to decide what it will read. What keeps that from disagreeing with the
writer is `Backend.BOUNDARY_VERSION`: a jar whose number is not this one is refused before its
declarations are read, so no compilation ever re-admits a declaration written under rules it does not
share.

That number moves 8 → 9 here. A jar built before this was trusted twice over: what its compiler
checked was what its author wrote, so a composition it published was never asked, and a declaration a
reader takes on faith was never asked either.

## Consequences

The six `SpecChecker.reject*` passes are gone, and with them the two whole-type walks that backed
two of them (`optionalInBoundaryShape`, `foreignNameInBoundaryShape`). Every diagnostic keeps its
code, its message and the position it is reported at. What changed is who raises them, and — for a
declaration wrong in more than one place — which of them arrives.

Making the signatures is now a prerequisite of the module check rather than a step inside it. The
check is handed them and is handed nothing where they did not build, in which case it goes as far as
it can without one — a declaration, an `exposing` line, a stage's arity all still say what they found
— and abandons the module at the point where it used to build them. What went wrong was reported
where signatures are made, so there is nothing for it to add.

The backend is handed them too, rather than replaying the walk before it emits. `Backend.generate`
takes the signatures beside the imported ones it already took.

That there is one caller is held by a tripwire (`ASignatureIsMadeInOnePlaceTest`), for the reason
above: a second one is invisible to every other kind of test. It reads the compiled classes rather
than the sources, and it asks who *makes* each closed part of a signature as well as who calls the
walk — a closed constructor stops the package, and inside it a second maker is what a second walk
would be written as. Making one is three things in bytecode and one in a search of text, so a rule
written over the text of a `new` is passed by a constructor reference.

Which of a module's phases reports first is decided by neither of these. A phase reports when its
answer is worked out, and signatures are worked out early, so a module wrong in a way signatures
decide and wrong in a way they do not shows the boundary's refusal first — as it did before any of
this, the passes having run early too. It is pinned rather than stated, because it is not a decision
this makes.

A behavior whose signature rests on a name that denotes nothing has no signature and is left out of
the map, as an abandoned composition already was. The name was reported where it was written, and
nothing below has a second thing to say about it.

The runner is a consumer with no fallback arm: both of its switches are total over the shape it is
handed. `FixtureReader` and `CodecGen.flatMember` are not, and each is its own change, because each
needs something this decision does not settle.

`FixtureReader.decoderFor` served helper-parameter positions as well as boundary ones — a helper may
take an optional or a tuple, which is exactly what a boundary may not — so it asked
`classifyConcreteMapKey` of a `Type`, borrowing the boundary's rule for positions the boundary never
established. #462 gave those positions a rule of their own, and the reader stopped borrowing.

`CodecGen` reads a union's *leaf* members, and the witness does not carry them: `BoundaryOutput.Cases`
holds the members as written, so that `outputType()` reconstructs the type it was built from.
Expanding a named sum inside the witness would change what the signature's type is, so what that
reader needs is leaf-expansion carried beside the members rather than instead of them.

`BoundaryNominal` was to be a name carrying its resolved codec descriptor — a data's, a sum's, a
unit's, a newtype's — for `CodecGen.flatMember` to branch on. Measuring it for #463 refuted the
premise: a newtype's answer depends on what it wraps, so four kinds cannot state it, and the question
that reader was really asking belonged to codec derivation rather than to the boundary. What was left
of the case is the name itself, and closing it needed no descriptor.

One widening, in the one rule that was asked of the written form rather than of the type: a parameter
written `A | A` has the type `A` and is now taken. The rule reads the type at every position, which
is what lets a composed and an imported signature be subject to it at all.

## References

- Specification: `[#external-representation]`, `[#e1311]`, `[#e1312]`, `[#e1313]`, `[#e1314]`,
  `[#e1325]`, `[#behavior]`
- ADR-0040 (what a boundary map key may be), ADR-0036 (a tuple has no external representation),
  ADR-0004 (derived codecs)
- Issues #446 (this), #447 (the map key asked twice), #456 (a union member no module declares),
  #458 (the precedence this reverses)

