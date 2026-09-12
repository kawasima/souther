package souther.cli;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The options this command line takes, and what has to hold of a line that writes them.
 *
 * <p>Each command used to answer this for itself, out of the tokens it had a {@code case} for and a
 * default branch that read everything else as a path. That answers one question — is this token
 * mine — and only in the commands that thought to ask it: {@code doc} and {@code api} never did, so
 * {@code api Option --nope} ran as though nothing had been written. It also cannot answer the other
 * question at all. An option arrives as a local variable, and a local carries its value and not the
 * fact that somebody wrote it, so an option whose reader sits behind a condition that did not hold
 * is indistinguishable from one nobody asked for. {@code --limit} without {@code --search} is that
 * shape: accepted, never read, and answered with the listing it would have printed anyway.
 *
 * <p>So what a line wrote is kept as what it wrote — the set of options present, before any of them
 * is turned into a value — and the constraints are read off this table once, above the dispatch,
 * for every command. Three of them are structural and are written here: which command an option
 * belongs to, which other option it needs beside it, and which two may not be written together. The
 * fourth kind is not: whether an option's value makes the next one pointless (a colour under a JSON
 * renderer) is a question about what the values mean, which belongs to the command that means them.
 */
enum CliOption {

    DIRECTORY("compile/init", "<path>", "where what this command writes goes", "-d", "--dir"),
    ADEQUACY("compile", "off|witness|all",
            "how much of the model to measure and warn about (default off)", "--adequacy"),
    WARNINGS("compile", "report|error", "refuse a compile that warns (default report)",
            "--warnings"),
    BEHAVIOR("run/examples", "<name>", "report only this behavior", "--behavior"),
    INPUT("run", "<json>", "the behavior's input, as JSON", "--input"),
    WRITE("fmt", null, "write the formatted source back in place", "-w", "--write"),
    CHECK("fmt", null, "write no file; exit non-zero on a file that is not formatted", "--check"),
    MODULE("examples/init", "<name>", "report only this module", "--module"),
    BUILD("init", "maven|gradle", "which build to write, where one is created (default maven)",
            "--build"),
    MODEL("init", "none|minimal|full",
            "how much of a model to start with (default full when creating, none when adding)",
            "--model"),
    GENERATE("examples", null, "print commented rows for what nothing covers", "--generate"),
    STRICT("examples", null, "exit non-zero on a gap the report names", "--strict"),
    SEARCH("doc/api", "<term>", "sections and topics that say the term, best answer first",
            "--search"),
    LIMIT("doc", "<n>", "with --search, how many hits to show (default 20; 0 for all)", "--limit"),
    SOURCE("api", "<Module>", "a stdlib module's own source, design comments included", "--source"),
    CLASS_PATH("compile/run/examples/japi", "<path>",
            "where to find modules another project compiled", "-cp", "--class-path"),
    FORMAT("compile/run/examples", "human|json",
            "how to render a compile error (default human)", "--format"),
    LANG("compile/run/examples/init", "<tag>",
            "message locale as a language tag, e.g. ja or en; overrides SOUTHER_LANG, and with "
                    + "neither, en", "--lang"),
    COLOR("compile/run/examples", "auto|always|never",
            "color the human output (default auto); not read under --format json", "--color"),
    /** Every command's, which is what the null owner means. */
    HELP(null, null, "what this command takes, and what its options mean", "--help", "-h");

    /** Why a command line is refused: a catalog key and what fills it. */
    record Refusal(String key, Object... args) {}

    /**
     * What one reading of a command line found: why it is refused, if it is, the language it is to
     * be answered in, and whether it asks what the command takes.
     *
     * <p>The three come from the same walk because they are answers about the same tokens. Reading
     * the line twice — once to find {@code --lang}, once to check it — is two rules for what a token
     * is, and they part company on the lines that need them most: a value that is spelt like an
     * option is a value to one walk and an option to the other.
     *
     * <p>{@code help} is that same reading and not a search for the word. {@code run --input --help}
     * hands {@code run} the input {@code --help}; a line scanned for the token finds a request for
     * help in it, and answers a question its author did not ask instead of running what they wrote.
     * So this is true where the walk recognised {@link #HELP} in a position an option is read in,
     * and there is no other way for it to become true.
     */
    record Reading(Refusal refusal, String lang, boolean help) {}

    /**
     * The commands that take the option, in the form a refusal names them, or null for an option
     * every command takes.
     *
     * <p>Not the commands it is documented under. The usage text groups an option under one heading
     * — {@code --behavior} under {@code examples} — and {@code run} takes it too; a refusal built
     * from the heading would send an author to the wrong command line.
     *
     * <p>Null rather than the commands written out, where the answer is all of them. Writing them
     * out is a copy of {@link CliCommand} with nothing holding the two together, and the copy goes
     * wrong exactly where it matters: a command added to that table and missed here would be the one
     * command that cannot be asked what it takes, and every check on this table would still pass.
     */
    private final String owners;

    /**
     * How this option's value is written where it takes one, and null where it takes none.
     *
     * <p>Also what says which of the two it is. These were two statements — a {@code Value} in this
     * table beside a value spelling written in the usage text — and an option's value is one fact: a
     * token follows it exactly where there is something for that token to be.
     */
    private final String valueSpelling;

    /**
     * What the option does, in the words a reader is shown beside it.
     *
     * <p>The option's own, which is not always the whole of it: what {@code --behavior} selects
     * differs between the command that drives one and the command that reports on one. A command
     * that reads it differently says so itself (see {@code CliCommand}), and this is the reading
     * every other command takes.
     */
    private final String description;

    /** Every spelling of the option, the first of which is the one a refusal names it by. */
    private final List<String> spellings;

    CliOption(String owners, String valueSpelling, String description, String... spellings) {
        this.owners = owners;
        this.valueSpelling = valueSpelling;
        this.description = description;
        this.spellings = List.of(spellings);
    }

    /** The spelling a refusal names this option by, and the one a section lists it under. */
    String spelling() {
        return spellings.get(0);
    }

    /** Every spelling of this option, in the order a section writes them. */
    List<String> spellings() {
        return spellings;
    }

    /** How this option's value is written, or null where it takes none. */
    String valueSpelling() {
        return valueSpelling;
    }

    /** What this option does, where the command reading it has nothing of its own to say. */
    String description() {
        return description;
    }

    /** Whether the token after this one is read as its value. */
    boolean takesAValue() {
        return valueSpelling != null;
    }

    /**
     * What each option needs written beside it.
     *
     * <p>The usage text has said this all along — {@code doc --search <term> [--limit <n>]} — as a
     * nesting of brackets, which is a statement no program reads. Here it is the same statement in
     * the form the check is made from.
     */
    private static final Map<CliOption, CliOption> NEEDS = new EnumMap<>(Map.of(
            LIMIT, SEARCH));

    /**
     * The pairs no line may write together, each declared once and read both ways round. Written as
     * a pair rather than as a field on either option, because a conflict is a fact about the two of
     * them and stating it twice is two things to keep in step.
     */
    private static final List<Set<CliOption>> EXCLUSIVE = List.of(EnumSet.of(WRITE, CHECK));

    private static final Map<String, CliOption> BY_SPELLING = spellingIndex();

    private static Map<String, CliOption> spellingIndex() {
        Map<String, CliOption> index = new LinkedHashMap<>();
        for (CliOption option : values()) {
            for (String spelling : option.spellings) {
                index.put(spelling, option);
            }
        }
        return Map.copyOf(index);
    }

    /** Every spelling this compiler knows, in the order the table writes them. */
    static Set<String> everySpelling() {
        return new LinkedHashSet<>(BY_SPELLING.keySet());
    }

    /** The commands that take the option written this way, or null where this compiler has none. */
    static String owners(String spelling) {
        CliOption option = BY_SPELLING.get(spelling);
        return option == null ? null : option.owners();
    }

    /**
     * The commands that take this option, in the form a refusal names them.
     *
     * <p>Asked of {@link CliCommand} where the answer is all of them, and asked when it is asked
     * rather than when this table is built: an enum whose constants read another enum's constants
     * while both are being initialised sees whichever of them got there first.
     */
    String owners() {
        if (owners != null) {
            return owners;
        }
        StringBuilder every = new StringBuilder();
        for (CliCommand command : CliCommand.values()) {
            every.append(every.isEmpty() ? "" : "/").append(command.spelling());
        }
        return every.toString();
    }

    /** Whether the named command is one of this option's, which is what a section lists it under. */
    boolean ownedBy(String command) {
        if (owners == null) {
            return true;   // every command's, whichever commands there turn out to be
        }
        for (String owner : owners.split("/")) {
            if (owner.equals(command)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Why this command may not be run as written, or null where nothing on the line refuses it.
     *
     * <p>Asked before the command is dispatched, so the answer is the same one whichever command was
     * named, and a command written after this one is under it without having to remember to be.
     *
     * <p>A token reading as an option is a token beginning {@code --}: a bare {@code --} is one, and
     * is refused as an option nobody has. A single dash is not, which is the rule {@code run} has
     * always had — a file may be named {@code -d} and a command that has no {@code -d} still reads it
     * as one. That makes the short options command-scoped: {@code fmt} knows {@code -w} and
     * {@code compile} reads the same token as a path.
     */
    static Reading read(String command, String[] args) {
        EnumSet<CliOption> written = EnumSet.noneOf(CliOption.class);
        Map<CliOption, String> asWritten = new EnumMap<>(CliOption.class);
        Refusal token = null;
        String lang = null;
        boolean help = false;
        for (int i = 0; i < args.length; i++) {
            String word = args[i];
            CliOption option = BY_SPELLING.get(word);
            if (option == null) {
                if (word.startsWith("--") && token == null) {
                    token = new Refusal("cli.option.unknown", word, command);
                }
                continue;   // a source file, a name, or a short option this compiler has no such
            }
            if (!option.ownedBy(command)) {
                if (word.startsWith("--") && token == null) {
                    token = new Refusal("cli.option.foreign", word, command, option.owners);
                }
                continue;   // a short option this command does not know still reads as a path
            }
            written.add(option);
            asWritten.put(option, word);
            if (option == HELP) {
                help = true;   // recognised where an option is read, which is the whole of the rule
            }
            if (option.takesAValue()) {
                // Whatever follows is this option's value, option-shaped or not: `--module
                // --generate` names a module, which is what the command's own parser reads it as.
                if (++i >= args.length) {
                    // And an option written last has no value at all. Answered here rather than
                    // where each one is read: `--limit` is read inside a loop that skipped it when
                    // nothing followed, so `doc --search newtype --limit` searched under the
                    // default and said nothing — the same silence as an option nobody reads.
                    if (token == null) {
                        token = new Refusal("cli.option.value", word);
                    }
                    break;
                }
                if (option == LANG) {
                    lang = args[i];   // the last one written, which is the one the parsers read
                }
            }
        }
        return new Reading(token != null ? token : unmet(written, asWritten), lang, help);
    }

    /**
     * Whether this token is a spelling of {@link #HELP}.
     *
     * <p>For the one position no walk reaches: the command name. {@code souther --help} writes it
     * where a command goes, and there is no command yet whose options could be read. Asked of the
     * same table the walk asks, so that how help is spelt is stated once — a command line matching
     * the two strings for itself is the second rule this class exists to stop there being.
     */
    static boolean isHelp(String token) {
        return BY_SPELLING.get(token) == HELP;
    }

    /** The first constraint between options this line does not meet, or null where it meets them. */
    private static Refusal unmet(Set<CliOption> written, Map<CliOption, String> asWritten) {
        for (CliOption option : written) {
            CliOption needed = NEEDS.get(option);
            if (needed != null && !written.contains(needed)) {
                return new Refusal("cli.option.needs",
                        asWritten.get(option), needed.spellings.get(0));
            }
        }
        for (Set<CliOption> pair : EXCLUSIVE) {
            if (written.containsAll(pair)) {
                List<CliOption> both = List.copyOf(pair);
                return new Refusal("cli.option.exclusive",
                        asWritten.get(both.get(0)), asWritten.get(both.get(1)));
            }
        }
        return null;
    }

    /** Whether the token after this one is read as its value. */
    static boolean takesValue(String spelling) {
        CliOption option = BY_SPELLING.get(spelling);
        return option != null && option.takesAValue();
    }

    /** What the option needs written beside it, or null where it stands on its own. */
    static String needed(String spelling) {
        CliOption option = BY_SPELLING.get(spelling);
        CliOption needs = option == null ? null : NEEDS.get(option);
        return needs == null ? null : needs.spellings.get(0);
    }

    /** The pairs no command line may write together, as the spellings a refusal names them by. */
    static List<List<String>> exclusive() {
        List<List<String>> pairs = new java.util.ArrayList<>();
        for (Set<CliOption> pair : EXCLUSIVE) {
            List<String> spelt = new java.util.ArrayList<>();
            for (CliOption option : pair) {
                spelt.add(option.spellings.get(0));
            }
            pairs.add(List.copyOf(spelt));
        }
        return List.copyOf(pairs);
    }
}
