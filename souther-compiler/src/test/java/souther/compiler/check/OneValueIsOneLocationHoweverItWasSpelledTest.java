package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.types.BinOp;
import souther.compiler.ast.Hir;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.Type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A fact is about a value, and which value is decided by the binding a name was answered with. Two
 * bindings of one spelling are two values, and one binding read twice is one value — neither of
 * which the text can say.
 */
class OneValueIsOneLocationHoweverItWasSpelledTest {

    private static final SourcePos POS = new SourcePos(1, 1);
    private static final Hir.Binders BINDERS =
            new Hir.Binders(new BindingOwner.OfValue("demo", "test"));

    @Test
    void oneSpellingBoundTwiceIsTwoLocations() {
        BindingId first = BINDERS.binder("a", POS).id();
        BindingId second = BINDERS.binder("a", POS).id();

        assertNotEquals(Location.of(first), Location.of(second),
                "a second binding is a second value, whatever it is called");
    }

    @Test
    void oneBindingReadTwiceIsOneLocation() {
        BindingId a = BINDERS.binder("a", POS).id();

        assertEquals(Location.of(a), Location.of(a));
        assertEquals(of(read(a, "a")), of(read(a, "written-another-way")),
                "how a read was spelled says nothing about which value it is");
    }

    @Test
    void aFieldPathIsPartOfTheLocation() {
        BindingId i = BINDERS.binder("i", POS).id();
        Location root = Location.of(i);

        assertNotEquals(root, root.then(Type.INT, "n", DeclarationNewtypes.NONE));
        assertNotEquals(root.then(Type.INT, "n", DeclarationNewtypes.NONE),
                root.then(Type.INT, "m", DeclarationNewtypes.NONE));
    }

    @Test
    void whatIsComputedIsNowhereAFactCanBeAbout() {
        assertNull(of(new Core.Int(1, Type.INT, POS)));
        assertNull(of(new Core.Binary(BinOp.ADD,
                new Core.Int(1, Type.INT, POS), new Core.Int(2, Type.INT, POS),
                ConstructOccurrence.unwritten(), Type.INT, POS)));
    }

    private static Core read(BindingId binding, String spelledAs) {
        return new Core.Read(spelledAs, binding, Type.INT, POS);
    }

    private static Location of(Core e) {
        return Location.of(e, DeclarationNewtypes.NONE, Location::of);
    }
}
