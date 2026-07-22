package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A name listed twice in a declaration — a sum's cases, a behavior's output union, {@code requires},
 * or {@code constructs} — is a meaningless duplicate that, left to codegen, produces a duplicate JVM
 * member (a malformed class file). The checker rejects it at compile time instead.
 */
class CompileDuplicateNameTest {

    @Test
    void aRepeatedSumCaseIsRejected() {
        assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                data A = { x: Int }
                data B = { y: Int }
                data S = A | A
                """));
    }

    @Test
    void aRepeatedOutputUnionCaseIsRejected() {
        assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                data Ok = { n: Int }
                data Bad
                data In = { x: Int }
                behavior mk : (i: In) -> Ok | Bad | Bad constructs Ok, Bad
                """));
    }

    @Test
    void aRepeatedRequiresIsRejected() {
        assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                data Out = { n: Int }
                data In = { x: Int }
                behavior dep : (i: In) -> Out constructs Out
                behavior use : (i: In) -> Out requires dep, dep
                let use (i, dep, dep) = dep(i)
                """));
    }

    @Test
    void aRepeatedConstructsIsRejected() {
        assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                data Out = { n: Int }
                data In = { x: Int }
                behavior mk : (i: In) -> Out constructs Out, Out
                """));
    }
}
