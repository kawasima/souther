package example.contact;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** contact.sou の直和を判別デコーダで decode し、判別子タグ付きで encode し直す。 */
class ContactTest {

    @Test
    void 直和は判別子typeでケースへ振り分けられencodeで往復する() {
        Map<String, Object> raw = Map.of("type", "EmailContact", "email", "a@example.com");
        Result<Contact> decoded = Contact.decoder().decode(raw, Path.ROOT);

        switch (decoded) {
            case Ok<Contact> ok -> {
                Map<String, Object> encoded = Contact.encoder().encode(ok.value());
                assertEquals("EmailContact", encoded.get("type"));
                assertEquals("a@example.com", encoded.get("email"));
            }
            case Err<Contact> err -> throw new AssertionError("should decode: " + err.issues().asList());
        }
    }

    @Test
    void 未知のタグはErrになる() {
        Result<Contact> unknown = Contact.decoder().decode(Map.of("type", "Nope"), Path.ROOT);
        assertInstanceOf(Err.class, unknown);
    }
}
