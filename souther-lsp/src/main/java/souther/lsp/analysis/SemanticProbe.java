package souther.lsp.analysis;

import souther.compiler.cst.SourceLayout;
import souther.compiler.cst.CstLexer;
import souther.compiler.cst.CstParser;
import souther.compiler.cst.GreenToken;
import souther.compiler.cst.SyntaxKind;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Abandonment;
import souther.compiler.query.Compilation;
import souther.compiler.source.SourceId;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The document being typed in, made whole enough to be resolved, without any of it being answered
 * from an earlier one.
 *
 * <p>What an editor asks about a position is asked while the position is mid-edit. {@code
 * request.plannedCost.} is where a field list is wanted and is not a document that parses — the
 * parser reads {@code .} as a field access only with a name after it — and a document that does not
 * parse is held out of the compile, so the compiler has nothing to say about it exactly where it is
 * being asked. That is not a gap in what the compiler knows. It is the current source not reaching
 * the compiler at all.
 *
 * <p>So the current source is made to reach it. Not the last one that did: a receiver edited from
 * {@code customer.address.} to {@code customer.amount.} would be answered with the fields of
 * {@code Address}, which is a wrong answer wearing the shape of a right one, and worse than no
 * answer. What is compiled here is always what the buffer says now.
 *
 * <p><b>Insertion only, at or after the cursor.</b> Nothing is deleted and nothing replaced, so
 * every offset before the first inserted character is the offset it was, and an extent that ends
 * there covers the characters it covered. What may be read off a probe is exactly that far: an
 * extent ending at or before the insertion. Not "an extent that does not overlap the insertion" —
 * a name put in front of the next line changes how the next line reads without touching it, and
 * overlap does not tell the two apart.
 *
 * <p>Which is enough for what asks. A field list wants the type of what is left of the {@code .},
 * and that ends before it. A signature help wants the callee and which argument is being written:
 * the callee's name ends before the {@code (}, and the argument's number is counted in the source as
 * the author left it — the call's own extent, which would take in a bracket that was put there, is
 * not read.
 *
 * <p>Its own compile, kept beside the workspace's rather than written into it. Two reasons, and each
 * on its own is enough. The workspace's compile publishes diagnostics, and a marker made by a
 * bracket nobody typed is a marker about nothing. And it is incremental: answers stand until
 * something they read changes, so a store fed the real text and the repaired one by turns re-answers
 * the edited module every time it is asked — which is the module every one of these questions is
 * about.
 */
final class SemanticProbe {

    /**
     * The name put in where one is missing.
     *
     * <p>Letters only: a leading {@code _} does not begin a name in this language. It is spelled so
     * as not to be something an author wrote, and nothing rests on that — a module that happened to
     * declare it would resolve the inserted name to it, and the inserted name is inside the
     * insertion, which nothing may read.
     */
    private static final String SENTINEL = "souLspProbe";

    /** A repair, and where it starts. Everything before {@code firstInserted} is the source
     *  unaltered, which is what makes an extent ending there readable. */
    record Repair(String text, int firstInserted) {}

    /**
     * A probe that answered: the compile the repaired source is in, and how far into it may be read.
     *
     * <p>{@code firstInserted} is a place rather than an offset because what is compared against it
     * is an extent, and an extent is two places.
     */
    record Reading(Compilation compilation, String uri, String repaired, SourcePos firstInserted) {

        /**
         * Whether what is written over {@code extent} is the author's rather than the probe's.
         *
         * <p>Ends at or before the insertion, in the file the insertion is in. An extent in another
         * file is the author's too — nothing was inserted there — and one with no extent at all is
         * nothing that can be shown.
         */
        boolean mayBeRead(Region extent) {
            if (extent == null) {
                return false;
            }
            if (!extent.end().isInTheSameTextAs(firstInserted)) {
                return true;
            }
            // Ends at it as well as before it: an extent that stops where the insertion begins
            // covers none of it.
            return !firstInserted.isBefore(extent.end());
        }
    }

    private Compilation compile;
    private ModulePath compiledAgainst;

    /**
     * What {@code text} would be with what the cursor is in the middle of finished off, or null
     * where it is not in the middle of anything this knows how to finish.
     *
     * <p>Every candidate is tried against the parser and the first that parses is the repair, so a
     * probe exists only where the source it compiles is a source. Which candidate it was is not
     * recorded: what a reader needs is where the insertion begins, and two repairs that begin in the
     * same place are the same promise about what may be read.
     */
    static Repair repair(String text, int cursor) {
        if (text == null || cursor < 0 || cursor > text.length() || parses(text)) {
            return null;
        }
        String closers = unclosed(text);
        // Both, and either, and in that order. An author reaching for a field of an argument has
        // left two things unfinished at once — `keep(request.` — and a policy that could apply one
        // repair or the other would answer that with nothing while answering each half of it. What
        // decides between them is the parser: the first that makes a source is the source.
        Repair[] tried = {
            inserting(text, cursor, SENTINEL, closers),
            inserting(text, cursor, SENTINEL, ""),
            inserting(text, cursor, "", closers),
        };
        for (Repair candidate : tried) {
            if (candidate != null && parses(candidate.text())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The text with {@code name} put at the cursor and {@code closers} at the end, or null where
     * there is nothing to put.
     *
     * <p>A name only where the cursor is straight after a {@code .}: elsewhere there is no missing
     * name, and one put in would be a name the author did not write standing where they are typing.
     * The brackets go at the end rather than at the cursor because an argument may still be being
     * written after it, and what is wanted is that the call closes somewhere.
     *
     * <p>The insertion is where the earlier of the two is, which is the cursor whenever a name went
     * in — everything before that is the source, character for character, and is what may be read.
     */
    private static Repair inserting(String text, int cursor, String name, String closers) {
        boolean naming = !name.isEmpty();
        if (naming && !aDotEndsAt(text, cursor)) {
            return null;
        }
        if (!naming && closers.isEmpty()) {
            return null;
        }
        String repaired = naming
                ? text.substring(0, cursor) + name + text.substring(cursor) + closers
                : text + closers;
        return new Repair(repaired, naming ? cursor : text.length());
    }

    /**
     * Whether the token that ends at {@code cursor} is a {@code .}.
     *
     * <p>One statement of it, for the two readers that need it: what the probe puts in, and what the
     * editor calls a member position. Read apart they could disagree, and where they did the editor
     * would call a place a member position while the probe supplied no member to it — which is an
     * empty answer given for a reason nobody meant.
     *
     * <p>Asked of the reading the language does. A dot is not always a field's: {@code .5} is a
     * number, and one inside a string literal is part of the string. Neither is a rule this has to
     * hold, since the token stream is the same one the parser goes on to read.
     */
    static boolean aDotEndsAt(String text, int cursor) {
        if (text == null || cursor <= 0 || cursor > text.length()) {
            return false;
        }
        int at = 0;
        for (GreenToken token : CstLexer.lex(text).tokens()) {
            at += token.width();
            if (at == cursor) {
                return token.kind() == SyntaxKind.DOT;
            }
            if (at > cursor) {
                return false;
            }
        }
        return false;
    }

    /**
     * The brackets {@code text} opens and does not close, innermost first, as the characters that
     * would close them.
     *
     * <p>Asked of the lexer rather than counted in the characters. A bracket inside a string
     * literal or a comment is text, and a reading that counted those would close a call the author
     * never opened — which is a source that parses and says something else.
     */
    private static String unclosed(String text) {
        Deque<SyntaxKind> open = new ArrayDeque<>();
        for (GreenToken token : CstLexer.lex(text).tokens()) {
            switch (token.kind()) {
                case LPAREN, LBRACKET, LBRACE -> open.push(token.kind());
                case RPAREN -> popIf(open, SyntaxKind.LPAREN);
                case RBRACKET -> popIf(open, SyntaxKind.LBRACKET);
                case RBRACE -> popIf(open, SyntaxKind.LBRACE);
                default -> { }
            }
        }
        StringBuilder closers = new StringBuilder();
        for (SyntaxKind each : open) {
            closers.append(switch (each) {
                case LPAREN -> ')';
                case LBRACKET -> ']';
                case LBRACE -> '}';
                default -> throw new IllegalStateException("not a bracket: " + each);
            });
        }
        return closers.toString();
    }

    /** Closes the innermost bracket where it is the one being closed. A closer with nothing open, or
     *  with something else open, is the author's mistake and not a bracket this supplies. */
    private static void popIf(Deque<SyntaxKind> open, SyntaxKind opener) {
        if (open.peek() == opener) {
            open.pop();
        }
    }

    /**
     * The current source of {@code uri}, repaired and compiled, or null where there was nothing to
     * repair or no repair that parses.
     *
     * <p>{@code joining} and {@code broken} are the workspace as the analyzer already sorted it — the
     * documents that can join a compile and the modules of those that cannot. Sorted once and handed
     * over, because which of the two a document is is a question with one answer, and a second
     * reading of it here would be a second answer free to differ.
     */
    Reading of(Map<String, String> joining, Set<String> broken, ModulePath path, String uri,
               String text, int cursor, Abandonment abandonment) {
        Repair repair = repair(text, cursor);
        if (repair == null) {
            return null;
        }
        Map<String, String> sources = new LinkedHashMap<>(joining);
        sources.put(uri, repair.text());
        if (compile == null || !path.equals(compiledAgainst)) {
            compile = Compilation.ofDocuments(sources, broken, path);
            compiledAgainst = path;
        } else {
            compile.update(sources, broken);
        }
        compile.abandonWhen(abandonment);
        // In the document it was inserted into, and said so: a place that names no text is in the
        // same text as nothing, and every extent would compare as being somewhere else — which
        // reads as "the author wrote this" about all of them.
        //
        // Laid out as repaired, because the extents it is compared against are places in the text
        // that was compiled. Laid out as the author left it, the count would be of a text that does
        // not parse and the two would be counting different things.
        return new Reading(compile, uri, repair.text(),
                SourceLayout.of(repair.text(), new SourceId(uri)).placeAt(repair.firstInserted()));
    }

    private static boolean parses(String text) {
        try {
            return CstParser.parse(text).errors().isEmpty();
        } catch (RuntimeException | StackOverflowError _) {
            return false;
        }
    }
}
