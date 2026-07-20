package net.unit8.souther.compiler.diag;


import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a diagnostic as a single JSON object for tools and agents. {@code code} and the type
 * strings are locale-independent (the stable identity); {@code message}, {@code hints}, and
 * secondary {@code label}s follow the selected locale.
 */
public final class JsonRenderer implements DiagnosticRenderer {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public String render(Diagnostic d, SourceContext src, Locale locale) {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("severity", d.severity().name().toLowerCase(Locale.ROOT));
        if (d.code() != null) {
            obj.put("code", d.code());
        }
        if (src != null && src.fileName() != null) {
            obj.put("file", src.fileName());
        }
        if (d.region() != null) {
            obj.put("region", region(d.region()));
        }
        if (!d.secondary().isEmpty()) {
            List<Object> secs = new ArrayList<>();
            for (LabeledRegion sec : d.secondary()) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("region", region(sec.region()));
                s.put("label", Messages.get(sec.labelKey(), locale, sec.labelArgs()));
                secs.add(s);
            }
            obj.put("secondary", secs);
        }
        obj.put("message", DiagnosticRenderer.body(d, locale));
        if (d.diff() != null) {
            obj.put("actualType", d.diff().actualType());
            obj.put("expectedType", d.diff().expectedType());
        }
        List<String> hints = new ArrayList<>();
        for (Note note : d.notes()) {
            hints.add(Messages.get(note.messageKey(), locale, note.args()));
        }
        if (!hints.isEmpty()) {
            obj.put("hints", hints);
        }
        if (d.suggestion() != null) {
            obj.put("suggestion", d.suggestion());
        }
        return JSON.writeValueAsString(obj);
    }

    private Map<String, Object> region(Region region) {
        SourcePos s = region.start();
        SourcePos e = region.end();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("startLine", s.line());
        r.put("startCol", s.column());
        r.put("endLine", e.line());
        r.put("endCol", e.column());
        return r;
    }
}
