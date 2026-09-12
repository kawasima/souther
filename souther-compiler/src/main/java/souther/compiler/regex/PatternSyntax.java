package souther.compiler.regex;

import java.util.List;

/**
 * A pattern of the subset this compiler reads, as what it says rather than as how it is written.
 *
 * <p>What is kept is what the language depends on and nothing else. A reading that dropped an arm of
 * a choice, an upper bound of a repetition, or the far end of a class would be a tree that answers
 * for a narrower language than the pattern names — and the answer would be wrong in the direction
 * nothing catches, since a narrower set still accepts the values somebody wrote.
 *
 * <p><b>No node for a literal.</b> One written character is one symbol, which is a set of one, and a
 * second shape for it would be two spellings of a thing that is compared. {@link Symbols} is where a
 * literal, a class, a negated class, a shorthand and {@code .} all arrive, told apart only by which
 * symbols they hold — which is the whole of what they say.
 *
 * <p>How a match is walked leaves no trace. Whether a repetition is greedy, whether a group
 * captures, and whether a marker is reluctant say what an engine does on the way rather than which
 * strings come out, so none of them is here.
 *
 * <p>An anchor is not one of those, and is kept ({@link Anchor}). Where it is written decides which
 * strings are accepted: {@code ^ab} accepts what {@code ab} accepts, and {@code a^b} accepts
 * nothing at all, since no position is both after an {@code a} and at the start. Read as adding
 * nothing wherever it appeared, the second was accepted as the first — a pattern this compiler
 * said it had read exactly, and had read as another one.
 */
public sealed interface PatternSyntax {

    /** The one string of no symbols, which is what an empty branch of a choice accepts. */
    record Nothing() implements PatternSyntax {}

    /**
     * No string at all, which is not the same as the empty one.
     *
     * <p>What an anchor nobody can satisfy leaves. {@code a^b} asks for a position that is both
     * after an {@code a} and at the start of the string, and there is none — so the sequence
     * holding it accepts nothing, and a choice holding that sequence is its other arms.
     *
     * <p>Never written by an author. It is what {@link #withoutAnchors} puts where an anchor was,
     * so that a reading of the pattern says which strings it accepts without a second kind of
     * answer for the ones that accept none.
     */
    record Never() implements PatternSyntax {}

    /**
     * One symbol out of a set of them.
     *
     * <p>Every way of writing one character arrives here. `.` is the universe less the line
     * terminators, `[^a]` is the universe less one symbol, `\d` is the ten digits — what tells them
     * apart is the set, and a reader of this needs nothing else about how it was spelled.
     */
    record Symbols(CodePoints held) implements PatternSyntax {

        public Symbols {
            if (held == null) {
                throw new IllegalArgumentException("one symbol comes out of some set of them");
            }
        }
    }

    /**
     * A place a match must be at, written {@code ^} or {@code $}.
     *
     * <p>Kept as itself, because what it comes to is not its own. At the front of what it is part
     * of, {@code ^} is satisfied by every string and accepts the empty one; anywhere after
     * something that accepts a symbol, no string satisfies it and the whole sequence accepts none.
     * So the node says which anchor it is and where it stands is read by whoever holds the
     * sequence.
     *
     * @param end whether it is {@code $} rather than {@code ^}
     */
    record Anchor(boolean end) implements PatternSyntax {}

    /** One after another. */
    record InTurn(List<PatternSyntax> parts) implements PatternSyntax {

        public InTurn {
            parts = List.copyOf(parts);
        }
    }

    /**
     * Any one of them.
     *
     * <p>Every arm and not the first. A reading that kept one arm answers for a language the author
     * did not write, and the ones it dropped are exactly the values a row may carry.
     */
    record EitherOf(List<PatternSyntax> arms) implements PatternSyntax {

        public EitherOf {
            arms = List.copyOf(arms);
            if (arms.size() < 2) {
                throw new IllegalArgumentException("a choice is between two or more");
            }
        }
    }

    /**
     * The same thing some number of times over.
     *
     * <p>Both ends carried. {@code {2,6}} is not {@code {2}} with something forgotten: the strings
     * of length three to six are in the language and a reading holding the floor alone leaves them
     * out. {@link #NO_CEILING} is what {@code *}, {@code +} and {@code {n,}} put there, which is a
     * bound nothing reaches rather than a large one.
     */
    record Repeated(PatternSyntax what, int least, int most) implements PatternSyntax {

        /** What an unbounded repetition has instead of a ceiling. */
        public static final int NO_CEILING = -1;

        public Repeated {
            if (what == null) {
                throw new IllegalArgumentException("something is repeated");
            }
            if (least < 0) {
                throw new IllegalArgumentException("a repetition happens at least no times");
            }
            if (most != NO_CEILING && most < least) {
                throw new IllegalArgumentException(
                        "a repetition's ceiling is not below its floor: " + least + ".." + most);
            }
        }

        public boolean unbounded() {
            return most == NO_CEILING;
        }
    }

    /**
     * The same pattern with every anchor read as what it comes to, or null where one of them cannot
     * be settled.
     *
     * <p>Whole-string matching is what gives an anchor an answer. {@code ^} asks to be at the start
     * of the string, so it is satisfied by every string where nothing before it can take a symbol
     * and by none where something before it must — which makes it the empty string in the first
     * case and {@link Never} in the second. {@code $} is the same question about the end.
     *
     * <p><b>Null where neither holds.</b> {@code (a|)^b} has something before the anchor that
     * sometimes takes a symbol and sometimes does not, and the strings it accepts are the ones that
     * took the second way — an answer neither arm of the two above gives, and one this compiler has
     * no shape for. So the pattern is not read at all rather than read as one of them. The same for
     * an anchor under a repetition, where how many copies precede it is not a thing the shape says.
     *
     * <p>Asked in one place because it is one rule. Whoever reads a pattern asks whether it can be
     * settled and whoever builds it asks what it comes to, and two spellings of the same rule would
     * be two answers to which strings a pattern accepts.
     */
    static PatternSyntax withoutAnchors(PatternSyntax syntax) {
        return Anchors.placed(syntax);
    }

    /**
     * The one string {@code written} is.
     *
     * <p>By code point and not by char, so a symbol outside the basic plane is one symbol here as
     * it is everywhere else in this package. Written as a sequence of one-symbol sets, which is
     * what a literal is ({@link Symbols}) — there is no node for a run of characters, and inventing
     * one would be a second spelling of a thing that is compared.
     */
    static PatternSyntax text(String written) {
        List<PatternSyntax> symbols = written.codePoints()
                .mapToObj(point -> (PatternSyntax) new Symbols(CodePoints.of(point)))
                .toList();
        return symbols.isEmpty() ? new Nothing() : new InTurn(symbols);
    }

    /**
     * Every string there is.
     *
     * <p>Every symbol and not what {@code .} holds. A dot is the universe less the line
     * terminators, which is a fact about how a pattern is written; what stands on either side of
     * text somebody looked for is any string at all, newlines included.
     */
    static PatternSyntax anything() {
        return ofAnySymbols(0, Repeated.NO_CEILING);
    }

    /**
     * Every string of between {@code least} and {@code most} symbols.
     *
     * <p>What a rule counting a string's characters leaves, said in the one vocabulary a language is
     * written in. A rule about how many there are and a rule about which they are reach one position
     * and are read by different things, and a value has to clear both — so whoever holds the count
     * puts it here and meets the two.
     *
     * <p>{@link Repeated#NO_CEILING} for a count nothing caps, which is the bound nothing reaches
     * rather than a large one.
     */
    static PatternSyntax ofAnySymbols(int least, int most) {
        return new Repeated(new Symbols(CodePoints.EVERYTHING), least, most);
    }
}

