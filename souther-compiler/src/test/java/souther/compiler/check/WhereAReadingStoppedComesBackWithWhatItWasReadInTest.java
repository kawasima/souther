package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.numeric.LinearForm;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An expression this walk could not read comes back with the environment it was being read in.
 *
 * <p>The pair is the contract {@link AffineForms.ReadThrough} already states: a value stands for a
 * name in the environment the binding was made in, which is not always the one the name was read
 * in. An answer that carries the expression alone puts the caller in the position of reading it
 * again in whatever it happens to hold — which is the same reading done twice, and free to disagree
 * with this one the day the two environments come apart.
 *
 * <p>Asked of the walk directly rather than of a model, because no model this compiler accepts
 * tells the two environments apart yet. A test written over one would pass on the coincidence and
 * say nothing about the contract.
 */
class WhereAReadingStoppedComesBackWithWhatItWasReadInTest {

    private static final SourcePos SOMEWHERE = new SourcePos(1, 1);

    private static final BindingId A_NAME =
            new BindingId(new BindingOwner.OfValue("m", "f"), 0);

    /** A name, whose value is an expression the walk has no rule for and the caller cannot name. */
    private static final Core READ = new Core.Read("n", A_NAME, Type.INT, SOMEWHERE);

    private static final Core UNREADABLE = new Core.Str("nothing composes this", Type.STRING,
            SOMEWHERE);

    /**
     * The environment the value was handed back in, and not the one the walk was called with.
     *
     * <p>This is the whole of the contract. Where they agree the test proves nothing, so the
     * reading below answers a different environment for the value than the one it was asked in.
     */
    @Test
    void theStopCarriesTheEnvironmentTheExpressionWasReadIn() {
        AffineForms.Outcome.StoppedAt<String, String> stopped =
                stopOf(AffineForms.outcome(READ, "where the name was read", reading()));

        assertEquals(UNREADABLE, stopped.node(),
                "the expression with no rule here, and not the name over it");
        assertEquals("where the value was written", stopped.at(),
                "read in the environment the value came back with");
    }

    /** And a stop met where nothing was read through carries the environment it was asked in. */
    @Test
    void aStopWithNoNameOverItCarriesTheEnvironmentItWasAskedIn() {
        assertEquals("where the name was read",
                stopOf(AffineForms.outcome(UNREADABLE, "where the name was read", reading())).at());
    }

    /** The stop {@code read} came to, or a failure saying what it came to instead. */
    private static AffineForms.Outcome.StoppedAt<String, String> stopOf(
            AffineForms.Outcome<String, String> read) {
        if (read instanceof AffineForms.Outcome.StoppedAt<String, String> stopped) {
            return stopped;
        }
        throw new AssertionError("nothing here composes a form, so the reading stopped: " + read);
    }

    /**
     * A walk whose environment for a name's value is not the one the name was read in, and which
     * can name nothing.
     */
    private static AffineForms.Reading<String, String> reading() {
        return new AffineForms.Reading<>() {

            @Override
            public Symbols symbols() {
                return Symbols.none(DefaultStdlib.get());
            }

            @Override
            public PublishedDeclarations published() {
                return PublishedDeclarations.NONE;
            }

            @Override
            public DeclarationKinds kinds() {
                return DeclarationKinds.NONE;
            }

            @Override
            public NewtypeInners inners() {
                return NewtypeInners.NONE;
            }

            @Override
            public LinearForm<String> leafOf(Core e, String at) {
                return null;
            }

            @Override
            public String inside(Core.LetIn li, String at) {
                return at;
            }

            @Override
            public AffineForms.ReadThrough<String> readThrough(Core.Read read, String at) {
                return read.binding().equals(A_NAME)
                        ? new AffineForms.ReadThrough<>(UNREADABLE, "where the value was written")
                        : null;
            }

            /** One name denotes one value here, and no name stands for several: what this test is
             *  about is the environment a stop comes back with, which a plurality would not add to. */
            @Override
            public java.util.List<AffineForms.ReadThrough<String>> alternativesOf(Core.Read read,
                                                                                  String at) {
                return null;
            }

            @Override
            public boolean readsThrough(Core.FieldAccess fa, String at) {
                return false;
            }
        };
    }
}
