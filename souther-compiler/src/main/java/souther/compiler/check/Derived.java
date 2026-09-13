package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.derive.Deriver;
import souther.compiler.types.TypeKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A normalized declaration with the boundary representation derived for it, and the module where
 * every declaration it writes has one.
 *
 * <p>What is established here is the representation and only that: a product data has the decoder
 * and encoder derived from its declared shape, so a reader asking how a value of it crosses is
 * answered rather than left to find out that nothing had worked it out yet. That the constructions
 * in the clauses are constructions was established one rung up ({@link Normalized}), and is what
 * every reader of a declaration gets whether or not a representation could be derived for it.
 *
 * <p>Not the boundary representation in general. What a sum's alternatives are called as they cross
 * is derived where it is read ({@code check.Boundary}) and a unit has none, so the two of them are
 * carried here as the declarations they are and nothing more.
 *
 * <p>Two members and they say different things. {@link Def} is one declaration's answer, and a
 * declaration that has no answer costs the readers that name it and no others. {@link Module} is
 * the conjunction — every declaration the module writes has one — which is what a reader wanting the
 * module rather than a declaration is asking for. A module missing a declaration it writes would be
 * read as one that does not declare it, which is a different thing to say and not a true one.
 */
public final class Derived {

    private Derived() {}

    /**
     * One normalized declaration with the representation derived for it.
     *
     * <p>Reached from {@link Normalized.Def} and from nothing else. What one of these depends on is
     * the normalized declaration; that the normalizing and the settling behind it are worked out a
     * module at a time is the query graph's business and not a fact about this.
     *
     * <p>Which kind of declaration it is, is what the three cases say, and a reader that has to tell
     * them apart switches over them rather than over the node inside. The kinds are the ones a
     * declaration can be, so what a later stage adds to one of them has somewhere to go that the
     * others do not reach.
     */
    public sealed interface Def permits Data, Sum, Unit {

        /**
         * {@code declaration} with, where it is a product, the boundary representation derived for
         * it — or null where a field of it does not name a type, which is a product no reader below
         * can be told how a value of crosses.
         *
         * <p>Built over a declaration already normalized rather than over the settled one. What is
         * added here is the representation and only that, so the one thing a failure here decides is
         * whether a reader can be told how a value crosses. Normalizing again would put a second
         * producer of the normalized form beside {@link Normalized.Def#of}, and a declaration read
         * through the two could come back in two forms.
         */
        static Def derive(Normalized.Def declaration, ResolvedSymbols scope,
                          DeclarationKinds kinds, PublishedDeclarations published) {
            return switch (declaration) {
                case Normalized.Data d -> {
                    Deriver.Codecs codecs = Deriver.derive(d.node(), scope, kinds, published);
                    yield codecs == null ? null : new Data(d, codecs);
                }
                case Normalized.Sum s -> new Sum(s);
                case Normalized.Unit u -> new Unit(u);
            };
        }

        /**
         * What the language itself declares, for which there is nothing to derive.
         *
         * <p>A second way in, and it is one because the proposition this state carries is empty of a
         * sum and of a unit. What deriving establishes is the boundary representation read off a
         * product's shape; a sum's is worked out where it is read ({@code check.Boundary}) and a
         * unit has none to carry. So the two cases hold of the declaration as it stands, and there
         * is nothing for a pass to have done to it.
         *
         * <p>Which is why a product is refused rather than admitted the same way. The library
         * declares none today, and one written tomorrow would need its codec derived like any other
         * — admitted here it would be a declaration this state says came out and nothing derived,
         * and every reader below would be told otherwise by the type it holds. The refusal is a
         * fault in the compiler, because the library is this compiler's own source.
         *
         * @throws IllegalStateException where the language declares a product
         */
        static Def ofLanguage(Normalized.Def declaration) {
            return switch (declaration) {
                case Normalized.Sum s -> new Sum(s);
                case Normalized.Unit u -> new Unit(u);
                case Normalized.Data d -> throw new IllegalStateException(
                        "the standard library declares the product `" + d.declaredKey()
                                + "`, which needs its boundary representation derived before a"
                                + " reader below the derivation can be given it");
            };
        }

        /**
         * The declaration a representation was derived for.
         *
         * <p>One declaration at a time and never a table. A table of these turned back into a table
         * of nodes is the other thing entirely: it hands every reader below the stage a declaration
         * with nothing left saying it reached it.
         */
        Normalized.Def declaration();

        /** The name it is declared under. */
        default String name() {
            return declaration().name();
        }

        /** Which declaration it is — the module that wrote it and the name it was written under. */
        default TypeKey declaredKey() {
            return declaration().declaredKey();
        }
    }

    /**
     * A product declaration that came out, and the boundary representation derived for it.
     *
     * <p>Both, and never one without the other. What a reader below the derivation asks of a product
     * is how a value of it is read and written, and the answer is here because deriving it is what
     * made one of these — there is no state in which a product has reached this stage and has none.
     */
    public static final class Data implements Def {

        private final Normalized.Data declaration;
        private final Hir.DecoderDef decoder;
        private final Hir.EncoderDef encoder;

        private Data(Normalized.Data declaration, Deriver.Codecs codecs) {
            this.declaration = declaration;
            this.decoder = codecs.decoder();
            this.encoder = codecs.encoder();
        }

        /** How a value of it is read at the boundary. */
        public Hir.DecoderDef decoder() {
            return decoder;
        }

        /** And how one is written. */
        public Hir.EncoderDef encoder() {
            return encoder;
        }

        @Override
        public Normalized.Data declaration() {
            return declaration;
        }

        /** Both of what it holds. The representation is derived from the declaration, so two of
         * these over equal declarations hold equal representations — compared all the same, because
         * what makes them equal is what they say and not how they were made. */
        @Override
        public boolean equals(Object o) {
            return o instanceof Data other && declaration.equals(other.declaration)
                    && decoder.equals(other.decoder) && encoder.equals(other.encoder);
        }

        @Override
        public int hashCode() {
            return declaration.hashCode() * 31 + decoder.hashCode();
        }
    }

    /** A sum declaration that came out. */
    public static final class Sum implements Def {

        private final Normalized.Sum declaration;

        private Sum(Normalized.Sum declaration) {
            this.declaration = declaration;
        }

        @Override
        public Normalized.Sum declaration() {
            return declaration;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Sum other && declaration.equals(other.declaration);
        }

        @Override
        public int hashCode() {
            return declaration.hashCode();
        }
    }

    /** A unit declaration that came out. */
    public static final class Unit implements Def {

        private final Normalized.Unit declaration;

        private Unit(Normalized.Unit declaration) {
            this.declaration = declaration;
        }

        @Override
        public Normalized.Unit declaration() {
            return declaration;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Unit other && declaration.equals(other.declaration);
        }

        @Override
        public int hashCode() {
            return declaration.hashCode();
        }
    }

    /**
     * The module where every declaration it writes came out.
     *
     * <p>An assembly and not a stage of its own: each declaration is answered on its own and says
     * what it has to say there, and this is the conjunction of those answers.
     */
    public static final class Module {

        private final InvariantSettled settled;
        private final List<Def> defs;
        /** Worked out once. The parts are what this holds; the tree is the shape they take, and a
         *  rung above asks for it every time it rewrites one of its own parts. */
        private volatile Hir.Module projected;

        private Module(InvariantSettled settled, List<Def> defs) {
            this.settled = settled;
            this.defs = List.copyOf(defs);
        }

        /**
         * {@code settled} with each declaration replaced by what that declaration derived to, or
         * null where one of them has no answer.
         *
         * <p>The completeness is the point, so it is asked here rather than remembered: a caller
         * holding one of these is holding a module where every declaration came out, and there is
         * no way to hold one otherwise.
         *
         * <p>What comes out holds the answers. Pouring them into a tree would leave the module
         * saying every declaration came out and no declaration of it saying so, which is where the
         * rungs above lost the claim (#714): a state built from a tree has nothing but nodes to
         * answer a route with.
         *
         * <p>Which declaration each answer is for is checked and not taken from the key it arrived
         * under. {@code derived} is keyed by bare name, and a bare name is a name in some module —
         * so an answer for another module's declaration of the same name would otherwise be built
         * into this module, and the claim would be made of a declaration this module does not write.
         *
         * @throws IllegalArgumentException where an answer is for a declaration other than the one
         *     it stands in for
         */
        public static Module assemble(InvariantSettled settled, Map<String, Def> derived) {
            List<Def> defs = new ArrayList<>();
            for (Hir.Def def : settled.module().defs()) {
                Def came = derived.get(def.name());
                if (came == null) {
                    return null;
                }
                if (!came.declaredKey().equals(def.declaredKey())) {
                    throw new IllegalArgumentException("the declaration derived under `" + def.name()
                            + "` is " + came.declaredKey() + ", not " + def.declaredKey());
                }
                defs.add(came);
            }
            return new Module(settled, defs);
        }

        /**
         * Whether this was derived from {@code settling}.
         *
         * <p>Asked rather than answered with the settling itself. What a rung was made from is its
         * provenance and not a value on offer: handed it, a reader could read the declarations as
         * they were before this rung, and the two are the same type so nothing would say which it
         * got.
         *
         * <p>The state and not its tree, because a settling answers a module beside the recursive
         * calls its clauses left standing and says so itself. Two settlings over one tree that left
         * different calls standing are two answers, and a caller comparing the trees would be
         * comparing the half they share.
         */
        boolean settledFrom(InvariantSettled settling) {
            return settled.equals(settling);
        }

        /** What the module is called. */
        public String name() {
            return settled.name();
        }

        /** Its declarations, each of them the derived declaration and not the node. */
        public List<Def> defs() {
            return defs;
        }

        /** The behaviors this module declares, which no rung at or below this one rewrites. */
        public List<Hir.BehaviorDef> behaviors() {
            return settled.module().behaviors();
        }

        /** Its definitions, which this state says nothing about — what it declares is what it
         * answered for. */
        public List<Hir.FnDef> fns() {
            return settled.fns();
        }

        /**
         * The parts written back into the shape a pass over a whole module takes.
         *
         * <p>One direction only. What this module declares is the list above; this is that list
         * projected, and nothing reads a module back into parts — a state made that way would be
         * claiming of nodes what was established of the answers they replaced.
         */
        Hir.Module module() {
            Hir.Module built = projected;
            if (built == null) {
                List<Hir.Def> nodes = new ArrayList<>();
                for (Def def : defs) {
                    nodes.add(def.declaration().node());
                }
                projected = built = settled.module().withDefs(nodes);
            }
            return built;
        }

        /**
         * The same, for the tests that audit what a module carries at each stage.
         *
         * <p>They ask about the payload rather than about the claim — what shape the module has
         * here — which is what this is for and the whole of it. A reader in the compiler that needs
         * a declaration to have been derived asks for {@link Def}.
         */
        public Hir.Module tree() {
            return module();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Module other && module().equals(other.module());
        }

        @Override
        public int hashCode() {
            return module().hashCode();
        }
    }
}
