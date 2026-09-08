package souther.compiler;

import souther.compiler.core.CompleteSignature;
import souther.compiler.core.Core;
import souther.compiler.core.DeclaredOperation;
import souther.compiler.diag.SourcePos;
import souther.compiler.stdlib.Stdlib;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.FixtureReferenceOrigin;
import souther.compiler.types.ReferenceOrigin;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;

/**
 * Calls a representation keeps standing, for a test that needs one to read.
 *
 * <p>Read off the library rather than written out here. A fixture that stated an operation's
 * signature itself would be a second table: it would agree with the library on the day it was
 * written, and a test standing on it would go on passing over a call the checker could never have
 * built. So a test says which operation, and what that operation takes comes from the same place
 * the checker takes it from.
 */
public final class KeptCalls {

    private static final BindingOwner OWNER = new BindingOwner.OfValue("demo", "call");

    /** The operation, read against the library's declaration of it. */
    public static DeclaredOperation declared(ValueName.Stdlib.Operation operation) {
        return signature(operation).declaring();
    }

    /** A value kept standing, which declares no parameters and answers {@code type}. */
    public static DeclaredOperation settledValue(ValueName name, Type type) {
        return CompleteSignature.ofSettledValue(name, type).declaring();
    }

    /** These calls are this fixture's own: no source wrote the name or the application, and a
     *  written one would be a number this fixture invented, standing for something in a file nobody
     *  read. */
    private static final ReferenceOrigin NAME = new FixtureReferenceOrigin(0);

    /** And the application is composed for the same reason. */
    private static final ApplicationOrigin COMPOSED = new ApplicationOrigin.ComposedFixture();

    /** A call to {@code operation} over {@code args}, answering {@code type}. */
    public static Core.PreservedCall to(ValueName.Stdlib.Operation operation, List<Core> args,
                                        Type type, SourcePos pos) {
        return new Core.PreservedCall(declared(operation), args,
                new Core.KeptCallPlace(NAME, COMPOSED), type, pos);
    }

    /**
     * A call to {@code operation} with a binding standing at each argument it declares, answering
     * what it declares.
     *
     * <p>For a test whose subject is the call rather than what is in it.
     */
    public static Core.PreservedCall to(ValueName.Stdlib.Operation operation, SourcePos pos) {
        CompleteSignature signature = signature(operation);
        List<Core> args = new ArrayList<>(signature.params().size());
        for (int i = 0; i < signature.params().size(); i++) {
            args.add(new Core.Read("arg" + i, new BindingId(OWNER, i), signature.params().get(i),
                    pos));
        }
        return new Core.PreservedCall(signature.declaring(), args,
                new Core.KeptCallPlace(NAME, COMPOSED), signature.result(), pos);
    }

    /** What the library declares {@code operation} to be. */
    public static CompleteSignature signature(ValueName.Stdlib.Operation operation) {
        Stdlib.Entry entry = DefaultStdlib.get().entry(operation);
        if (entry == null) {
            throw new IllegalStateException(operation + " is not declared by the library");
        }
        return CompleteSignature.ofDeclaration(operation, entry.signature().params(),
                entry.signature().result());
    }

    private KeptCalls() {}
}
