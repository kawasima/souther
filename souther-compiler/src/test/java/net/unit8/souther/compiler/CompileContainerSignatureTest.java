package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A value class's accessor and backing field for a List/Set/Map/Option field carry a generic
 * {@code Signature}, so a Java consumer reads {@code List<LineItem>} rather than a raw {@code List}
 * and casts on every read. Reflection's {@code getGenericReturnType} / {@code getGenericType} read
 * exactly that attribute, so they see the raw type when it is missing.
 */
class CompileContainerSignatureTest {

    private static final String MODULE = """
            module demo

            data LineItem = { qty: Int }
            data 商品ID = String

            data Cart = {
                items: List<LineItem>
                , counts: List<Int>
                , tags: Set<String>
                , stock: Map<商品ID, Int>
                , note: String?
            }
            """;

    private Class<?> cart() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        return loader.loadClass("demo.Cart");
    }

    @Test
    void listAccessorCarriesItsElementType() throws Exception {
        assertEquals("java.util.List<demo.LineItem>",
                cart().getMethod("items").getGenericReturnType().getTypeName());
    }

    @Test
    void listFieldCarriesItsElementType() throws Exception {
        assertEquals("java.util.List<demo.LineItem>",
                cart().getDeclaredField("items").getGenericType().getTypeName());
    }

    @Test
    void aPrimitiveElementIsBoxedInTheSignature() throws Exception {
        assertEquals("java.util.List<java.lang.Long>",
                cart().getMethod("counts").getGenericReturnType().getTypeName());
    }

    @Test
    void setAccessorCarriesItsElementType() throws Exception {
        assertEquals("java.util.Set<java.lang.String>",
                cart().getMethod("tags").getGenericReturnType().getTypeName());
    }

    @Test
    void mapAccessorCarriesItsKeyAndValueTypes() throws Exception {
        assertEquals("java.util.Map<demo.商品ID, java.lang.Long>",
                cart().getMethod("stock").getGenericReturnType().getTypeName());
    }

    @Test
    void optionAccessorCarriesItsElementType() throws Exception {
        assertEquals("net.unit8.souther.runtime.Option<java.lang.String>",
                cart().getMethod("note").getGenericReturnType().getTypeName());
    }
}
