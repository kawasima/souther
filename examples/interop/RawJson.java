package app.member.web;

import net.unit8.souther.runtime.Raw;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code Raw}（Souther の外部表現。spec 6章）を、Jackson がそのまま JSON 化できる素の
 * Java 値（Map / List / String / Long / Boolean / BigDecimal / null）へ落とすヘルパ。
 * encoder が返す {@code Raw} をレスポンスボディにするときに使う。
 */
public final class RawJson {

    private RawJson() {}

    public static Object toPlain(Raw raw) {
        return switch (raw) {
            case Raw.NullValue ignored -> null;
            case Raw.BoolValue b -> b.value();
            case Raw.IntValue i -> i.value();
            case Raw.DecimalValue d -> d.value();
            case Raw.TextValue t -> t.value();
            case Raw.ListValue l -> l.value().stream().map(RawJson::toPlain).toList();
            case Raw.ObjectValue o -> {
                Map<String, Object> m = new LinkedHashMap<>();
                o.value().forEach((k, v) -> m.put(k, toPlain(v)));
                yield m;
            }
        };
    }
}
