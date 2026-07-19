package net.unit8.souther.compiler.diag;

import net.unit8.souther.compiler.SourcePos;

import java.util.ArrayList;
import java.util.List;

/**
 * A compile diagnostic as data, not a pre-formatted string. A renderer turns it into human text
 * (Elm-style, with a source snippet) or JSON (for tools and agents). Two message forms coexist:
 *
 * <ul>
 *   <li>a catalog {@code messageKey} plus {@code args}, resolved against the selected locale — the
 *       target form for migrated sites;</li>
 *   <li>a {@code literalMessage} already in English — the compatibility form, so the ~180 existing
 *       throw sites render through the same pipeline before they are migrated.</li>
 * </ul>
 *
 * The {@code code} (e.g. {@code E1301}) and the {@link TypeComparison} types are locale-independent
 * — the stable identity a tool keys on. Everything else (title, message, hints, secondary labels)
 * follows the locale.
 */
public record Diagnostic(Severity severity,
                         String code,
                         String titleKey,
                         Region region,
                         List<LabeledRegion> secondary,
                         String messageKey,
                         Object[] args,
                         String literalMessage,
                         TypeComparison diff,
                         List<Note> notes,
                         String suggestion) {

    /** The primary source position (the region's start). */
    public SourcePos pos() {
        return region == null ? null : region.start();
    }

    /** A pre-formatted English message wrapped verbatim — the compatibility path for a site that
     * has not yet been moved onto a catalog key. {@code pos} may be null for a position-less error. */
    public static Diagnostic literal(SourcePos pos, String code, String message) {
        return new Diagnostic(Severity.ERROR, code, null, pos == null ? null : Region.point(pos),
                List.of(), null, null, message, null, List.of(), null);
    }

    /** A literal-message diagnostic that still carries a named title (e.g. SYNTAX ERROR) — the
     * compatibility path for a site whose body is not yet a catalog key but whose title should be
     * consistent with its migrated siblings. */
    public static Diagnostic titledLiteral(SourcePos pos, String titleKey, int width, String message) {
        Region region = pos == null ? null : Region.ofWidth(pos, width);
        return new Diagnostic(Severity.ERROR, null, titleKey, region,
                List.of(), null, null, message, null, List.of(), null);
    }

    /** A builder for a catalog-keyed diagnostic. {@code code} may be null for an uncoded error. */
    public static Builder of(String code, String messageKey) {
        return new Builder(code, messageKey);
    }

    public static final class Builder {
        private final String code;
        private final String messageKey;
        private String titleKey;
        private Region region;
        private final List<LabeledRegion> secondary = new ArrayList<>();
        private Object[] args = new Object[0];
        private TypeComparison diff;
        private final List<Note> notes = new ArrayList<>();
        private String suggestion;

        private Builder(String code, String messageKey) {
            this.code = code;
            this.messageKey = messageKey;
        }

        /** An explicit title-bar key, for an uncoded error that still deserves a named title
         * (e.g. TYPE MISMATCH). A coded error derives its title from the code. */
        public Builder title(String titleKey) {
            this.titleKey = titleKey;
            return this;
        }

        public Builder at(SourcePos pos) {
            this.region = pos == null ? null : Region.point(pos);
            return this;
        }

        public Builder at(SourcePos pos, int width) {
            this.region = pos == null ? null : Region.ofWidth(pos, width);
            return this;
        }

        public Builder at(Region region) {
            this.region = region;
            return this;
        }

        public Builder args(Object... args) {
            this.args = args;
            return this;
        }

        public Builder secondary(Region region, String labelKey, Object... labelArgs) {
            this.secondary.add(new LabeledRegion(region, labelKey, labelArgs));
            return this;
        }

        public Builder diff(String actualType, String expectedType) {
            this.diff = new TypeComparison(actualType, expectedType);
            return this;
        }

        public Builder hint(String hintKey, Object... hintArgs) {
            this.notes.add(new Note(hintKey, hintArgs));
            return this;
        }

        public Builder suggestion(String suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        public Diagnostic build() {
            return new Diagnostic(Severity.ERROR, code, titleKey, region, List.copyOf(secondary),
                    messageKey, args, null, diff, List.copyOf(notes), suggestion);
        }
    }
}
