package net.unit8.souther.compiler.codegen;

import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.Type;
import net.unit8.souther.compiler.check.TypeChecker;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.unit8.souther.compiler.codegen.Descriptors.*;

/**
 * The module-wide state every generator shares: the symbol table and package map, the name
 * resolution built on them (a type or behavior name to its {@link ClassDesc}), and the sink for the
 * synthetic {@code Fn} classes escaping lambdas compile to. It resolves names and types; it never
 * touches a {@code CodeBuilder}. One instance is built per module in {@link Backend#generate} and
 * handed to {@link Backend.Gen}, and later to the codec and value-class generators.
 */
final class CodegenContext {

    final String pkg;
    final Map<String, Ast.Def> symbols;
    final Map<String, List<String>> caseToSums;
    final Map<String, String> typePackage;
    /** True when the module has no {@code exposing} clause: everything stays public. */
    final boolean exposeAll;
    /** Base names the module exposes (only these are public when {@link #exposeAll} is false). */
    final Set<String> exposed;
    /** The module's recursive helpers, lowered to static methods on {@code $Fns} (spec 13.1), keyed
     * by helper name. A call to one is an {@code invokestatic}, not an inlined body. */
    final Map<String, Ast.FnDef> recursiveHelpers;

    /** Synthetic {@code Fn} classes generated for escaping lambdas (spec §blocks), merged into the
     * module output once every behavior is generated. */
    private final Map<String, byte[]> synthClasses = new LinkedHashMap<>();
    private int lambdaCounter = 0;

    CodegenContext(String pkg, Map<String, Ast.Def> symbols, Map<String, List<String>> caseToSums,
                   Map<String, String> typePackage, boolean exposeAll, Set<String> exposed,
                   Map<String, Ast.FnDef> recursiveHelpers) {
        this.pkg = pkg;
        this.symbols = symbols;
        this.caseToSums = caseToSums;
        this.typePackage = typePackage;
        this.exposeAll = exposeAll;
        this.exposed = exposed;
        this.recursiveHelpers = recursiveHelpers;
    }

    /** {@code ACC_PUBLIC} when the name is exposed (or the module exposes all), else 0. */
    int pub(String name) {
        return (exposeAll || exposed.contains(name)) ? ClassFile.ACC_PUBLIC : 0;
    }

    ClassDesc cd(String typeName) {
        return ClassDesc.of(typePackage.getOrDefault(typeName, pkg) + "." + typeName);
    }

    ClassDesc cdBehavior(String name) {
        return ClassDesc.of(typePackage.getOrDefault(name, pkg) + "." + behaviorClass(name));
    }

    /** The implementation class behind a fn/pipe behavior's public interface: {@code <名>$Impl}.
     * The interface (named {@link #behaviorClass}) is what Java code declares; the {@code $Impl}
     * holds the fields, constructor and {@code apply}, and is what a pipeline instantiates. Injected
     * behaviors have no {@code $Impl} (their abstract base is the named class). */
    ClassDesc cdBehaviorImpl(String name) {
        return ClassDesc.of(typePackage.getOrDefault(name, pkg) + "." + behaviorImplClass(name));
    }

    /**
     * The generated class simple-name for a behavior: its name with the first letter capitalized
     * (spec 19.5). A Japanese leading character has no upper-case form, so a Japanese-named behavior
     * is emitted unchanged. The behavior's name stays lower-case wherever it is an identity — an
     * injected field name, a requirement-set entry, a signature-map key — and only the emitted class
     * name is capitalized.
     */
    static String behaviorClass(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** The {@code $Impl} simple-name for a fn/pipe behavior (see {@link #cdBehaviorImpl}). */
    static String behaviorImplClass(String name) {
        return behaviorClass(name) + "$Impl";
    }

    /** The {@code $Fns} method name for a recursive helper. A module-own helper keeps its bare name;
     * a prelude recursive helper reached under a qualified name ({@code List.foldFrom}) has the dot
     * mangled to {@code $}, since a JVM method name cannot contain a dot. */
    static String recursiveHelperMethod(String name) {
        return name.replace('.', '$');
    }

    /** The generated result-union simple-name for a behavior with an anonymous-union output
     * (spec 19.8): {@code <名>Result}. Only the union case gets one; a named-sum or single-case
     * output uses that type directly. */
    static String behaviorResultClass(String name) {
        return behaviorClass(name) + "Result";
    }

    /** The JVM class for an output case: the built-in {@code DivisionByZero}, otherwise the
     * generated data class in this module. An invariant violation is no longer a case — it aborts
     * (spec 7.3, 9.4) — so there is no 制約違反 case here. */
    ClassDesc caseClass(String typeName) {
        return switch (typeName) {
            case "DivisionByZero" -> CD_DivisionByZero;
            default -> cd(typeName);
        };
    }

    /** The class a match case is tested against: a boxed/reference class for a primitive case,
     * otherwise the case's data or built-in class. */
    ClassDesc matchCaseClass(String caseName) {
        return switch (caseName) {
            case "Int" -> CD_Long;
            case "Bool" -> CD_Boolean;
            case "Decimal" -> CD_BigDecimal;
            case "String" -> CD_String;
            case "Date" -> CD_LocalDate;
            case "DateTime" -> CD_LocalDateTime;
            default -> caseClass(caseName);
        };
    }

    ClassDesc[] caseInterfaces(String name) {
        List<ClassDesc> ifaces = new ArrayList<>();
        for (String sum : caseToSums.getOrDefault(name, List.of())) {
            ifaces.add(cd(sum));
        }
        return ifaces.toArray(new ClassDesc[0]);
    }

    /**
     * The single reference class a behavior's input or output success type maps to, for a generic
     * {@code Behavior<In, Out>} signature: the {@code <名>Result} interface for an anonymous union, the
     * named data/sum for a single case, the boxed class for a primitive. Returns {@code null} for a
     * list/option/map, which has no single reference class to name here.
     */
    ClassDesc refTypeOrNull(Type t, String behaviorName) {
        if (t instanceof Type.Union) {
            return cd(behaviorResultClass(behaviorName));
        }
        if (t instanceof Type.Ref r) {
            return cd(r.name());
        }
        return JvmTypes.boxedPrim(t);
    }

    Map<String, Type> fieldTypes(Ast.Data data) {
        return TypeChecker.fieldTypes(data, symbols);
    }

    Type resolveType(Ast.TypeRef ref) {
        return TypeChecker.resolveType(ref, symbols);
    }

    Type successType(Ast.RetType ret) {
        return TypeChecker.successType(ret, symbols);
    }

    /** Whether {@code name} is an imported type or behavior (declared in another module, spec 4). */
    boolean isImported(String name) {
        return typePackage.containsKey(name);
    }

    // --- synthetic-class sink ---

    /** The next id for an escaping lambda's generated {@code $Fn} class (spec §blocks). */
    int nextLambdaId() {
        return lambdaCounter++;
    }

    void addSynth(String className, byte[] bytes) {
        synthClasses.put(className, bytes);
    }

    /** The synthetic classes accumulated so far, for merging into the module output. */
    Map<String, byte[]> synthClasses() {
        return synthClasses;
    }
}
