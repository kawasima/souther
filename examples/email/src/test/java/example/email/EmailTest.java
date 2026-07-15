package example.email;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** email.sou を SoutherProcessor が生成し、その decoder を型付きで叩く（最小例）。 */
class EmailTest {

    @Test
    void newtypeのinvariantが検査される() {
        Result<Email> ok = Email.decoder().decode("a@example.com", Path.ROOT);
        assertInstanceOf(Ok.class, ok);

        Result<Email> bad = Email.decoder().decode("no-at-sign", Path.ROOT);
        assertInstanceOf(Err.class, bad, "invariant contains(value, \"@\") に反する");
    }
}
