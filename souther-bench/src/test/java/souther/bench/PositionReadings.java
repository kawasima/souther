package souther.bench;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a stage's calls arrive at raw structure, and what answers for them on the way.
 *
 * <p>What it reads and which classes it reads are two things. The rule about this compiler names
 * the partitioning stage and the compiler's own sums; a check of the reading itself hands it code
 * written to be read, and neither is a copy of the other. A second walk written for the second
 * purpose would be a check of the copy.
 *
 * <p>Everything about the vocabulary is an argument for the same reason. What a declaration is and
 * what is built out of types are stated by the sums the caller names, so the walk derives both from
 * the class files rather than knowing either — and a caller can therefore hand it a sum of its own
 * and see what the walk makes of it.
 *
 * <p><b>What a call is, here, is the receiver it names.</b> A method reached only through an
 * interface, or through an override of the method a call names, has no edge: what is read is the
 * class written into the call and not what stands there when it runs. So the reachability this
 * answers is over the calls as written. Where a seam is an interface, what is on the far side of it
 * is held by being in the stage — or by nothing, which is where this stops being an argument and
 * the reader has to look.
 */
final class PositionReadings {

    private PositionReadings() {}

    /** Whether a walk stops at an authority or carries on through it. */
    enum Traversal {

        /**
         * The answer is complete before anything of the caller's runs, so what the authority reads
         * to make it is its own business and the walk stops here.
         */
        OPAQUE,

        /**
         * The owner of the question, walked through all the same. What it answers with is composed
         * by something the caller handed it, so stopping here would put whatever the caller wrote
         * beyond this — and what the caller wrote is the thing being checked.
         *
         * <p>What carrying on buys is the calls this place makes itself. It does not follow the
         * caller's own reading into the callback: that arrives by an interface, and what is
         * followed here is the receiver a call names rather than what stands there when it runs.
         * A reading written in the stage is checked because it is in the stage, not because this
         * reached it.
         */
        TRANSPARENT
    }

    /**
     * A place that owns a question, and what a walk does when it arrives.
     *
     * @param question what it answers, in the words the answer is about. A place that cannot be
     *                 given one that some other authority does not already answer is not an
     *                 authority: it is a second reading under another name
     * @param owns     the class, or the one method of it, that answers. A class holding several
     *                 unrelated questions is named by the method, because that is the size of the
     *                 owner and not because the rest of the class is being excused
     */
    record Authority(String question, String owns, Traversal traversal) {

        /** Whether this answers for {@code at}, named with or without what it takes and returns. */
        boolean answersFor(String at) {
            String named = at.contains("(") ? at.substring(0, at.indexOf('(')) : at;
            String owner = named.substring(0, named.indexOf('#'));
            return owns.contains("#")
                    ? named.equals(owns)
                    : owner.equals(owns) || owner.startsWith(owns + "$");
        }
    }

    /**
     * What the walk is over.
     *
     * @param classes     the compiled classes to read
     * @param stage       the package whose methods are held to the rule, by name prefix
     * @param declaration the sum whose cases are what a module wrote
     * @param lookup      the one method that answers what a name declares, or null where the
     *                    vocabulary has none
     * @param compound    the sum whose cases are built out of types
     * @param held        the descriptor of what a compound's components hold, so that a component
     *                    made of them is told from one that is not
     * @param authorities what answers for a reading, and what a walk does at each
     */
    record Over(List<ClassModel> classes, String stage, String declaration, String lookup,
                String compound, String held, List<Authority> authorities) {}

    /**
     * What the classes hold and what follows from it.
     *
     * @param observers the methods whose own code reads raw structure
     * @param inTheStage the stage's compiled methods that call something, which is every one a way
     *                  out of the stage could start at. One that calls nothing reaches nothing
     * @param encountered the authorities standing on a way from the stage to a reading: met by the
     *                  walk back from a reading, and reachable forwards from the stage. Two
     *                  questions and both of them, because either alone is about a wider set than
     *                  the sentence — a place on a way to a reading somewhere is not on a way from
     *                  here, and a place the stage can reach is not thereby on the way to anything.
     *                  Where an authority stands is the table's to say and reachability's to
     *                  confirm: a helper on the same way is on the way and not an answer to it
     * @param madeOfNonRecords the cases of the compound sum whose components this cannot read,
     *                  because they are not records. What holds another type would be invisible
     * @param bypassing the stage's methods that reach raw structure with no authority between
     * @param bypasses  the same, each with the path that gets there, for a reader to go and look
     */
    record Reading(Set<String> observers, Set<String> inTheStage,
                   Set<String> encountered, List<String> madeOfNonRecords,
                   List<String> bypassing, List<String> bypasses) {

        /** Which methods those are, by name alone, which is what a claim about them is written in. */
        List<String> named() {
            List<String> out = new ArrayList<>();
            for (String each : bypassing) {
                out.add(each.substring(each.indexOf('#') + 1, each.indexOf('(')));
            }
            out.sort(null);
            return out;
        }
    }

    static Reading of(Over over) throws IOException {
        Map<String, ClassModel> models = new LinkedHashMap<>();
        for (ClassModel model : over.classes()) {
            models.put(model.thisClass().asInternalName().replace('/', '.'), model);
        }
        Set<String> declarations = descendantsOf(models, over.declaration());
        Map<String, Set<String>> apart =
                componentsHolding(models, descendantsOf(models, over.compound()), over.held());

        Set<String> observers = new LinkedHashSet<>();
        Set<String> stage = new LinkedHashSet<>();
        Map<String, Set<String>> callersOf = new LinkedHashMap<>();
        Map<String, Set<String>> callsOf = new LinkedHashMap<>();
        Map<String, Set<String>> named = new LinkedHashMap<>();
        Map<String, String> why = new LinkedHashMap<>();
        for (Compiled.Site site : Compiled.sitesIn(over.classes())) {
            if (site.from().startsWith(over.stage())) {
                stage.add(site.at());
            }
            named.computeIfAbsent(site.at().substring(0, site.at().indexOf('(')),
                    k -> new LinkedHashSet<>()).add(site.at());
            callersOf.computeIfAbsent(site.owner() + "#" + site.member(),
                    k -> new LinkedHashSet<>()).add(site.at());
            callsOf.computeIfAbsent(site.at(), k -> new LinkedHashSet<>())
                    .add(site.owner() + "#" + site.member());
            String read = observation(site, declarations, apart, over.lookup());
            if (read != null) {
                observers.add(site.at());
                why.putIfAbsent(site.at(), read);
            }
        }

        // Which methods the stage can arrive at, forwards, whatever answers on the way. Asked
        // apart from the walk below and answered before it: stopping the walk where an authority
        // answers is one question, and whether the stage can arrive at a place at all is another.
        // Read off the walk alone, a place on a way to a reading in some other part of this
        // compiler would count as standing on a way from here.
        Set<String> fromTheStage = new LinkedHashSet<>(stage);
        Deque<String> forwards = new ArrayDeque<>(stage);
        while (!forwards.isEmpty()) {
            for (String call : callsOf.getOrDefault(forwards.poll(), Set.of())) {
                for (String at : named.getOrDefault(call, Set.of())) {
                    if (fromTheStage.add(at)) {
                        forwards.add(at);
                    }
                }
            }
        }

        // Backwards from every reading of raw structure, stopping where an authority answers. A
        // method inside one is not carrying the reading outwards — it is making the answer.
        Map<String, Step> through = new LinkedHashMap<>();
        Set<String> encountered = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (String at : observers) {
            if (!standing(over, at, encountered, fromTheStage)
                    && through.putIfAbsent(at, new Step.Reading(why.get(at))) == null) {
                queue.add(at);
            }
        }
        while (!queue.isEmpty()) {
            String at = queue.poll();
            for (String caller
                    : callersOf.getOrDefault(at.substring(0, at.indexOf('(')), Set.of())) {
                if (standing(over, caller, encountered, fromTheStage)
                        || through.putIfAbsent(caller, new Step.Through(at)) != null) {
                    continue;
                }
                queue.add(caller);
            }
        }

        List<String> bypassing = new ArrayList<>();
        List<String> bypasses = new ArrayList<>();
        for (String at : stage) {
            if (through.containsKey(at)) {
                bypassing.add(at);
                bypasses.add(at.substring(over.stage().length()) + "  ->  " + pathFrom(at, through));
            }
        }
        bypassing.sort(null);
        bypasses.sort(null);
        return new Reading(observers, stage, encountered,
                madeOfNonRecords(models, descendantsOf(models, over.compound())),
                bypassing, bypasses);
    }

    /**
     * The cases of the compound sum this cannot read the components of.
     *
     * <p>What is built out of types is derived from the sum, and which of a case's components hold
     * one is read off the record attribute. A case that is not a record has no such attribute, so
     * what it holds would be invisible and a walk taking it apart would read as taking nothing
     * apart. The claim that a case added to the sum joins the rule holds exactly as far as this is
     * empty, so it is answered rather than assumed.
     */
    private static List<String> madeOfNonRecords(Map<String, ClassModel> models,
                                                 Set<String> compounds) {
        List<String> out = new ArrayList<>();
        for (String each : compounds) {
            ClassModel model = models.get(each);
            if (model == null || (model.flags().flagsMask() & 0x0600) != 0) {
                continue;   // not built here, or an interface or abstract class holding no value
            }
            if (model.findAttribute(Attributes.record()).isEmpty()) {
                out.add(each);
            }
        }
        out.sort(null);
        return out;
    }

    /**
     * Whether the walk stops here, noting any authority it met on the way.
     *
     * <p>Met on a way from the stage to a reading is what makes an authority one that is standing
     * somewhere, and it is the walk that knows. Worked out from what the stage calls instead, an
     * ordinary helper on the same way is indistinguishable from the place that answers — and the
     * way to make such a check green is to call the helper an authority, which is what a table of
     * owners must not reward.
     *
     * <p>One that carries on is met all the same. It owns the question and says so; what it does
     * not do is finish before the caller's own code runs, which is why the walk goes past it.
     *
     * <p><b>Stopping and standing are two answers.</b> The walk stops wherever an authority is,
     * because taint that goes no further can reach no method of the stage either way; what is
     * recorded as standing is only what the stage can arrive at. Recorded on meeting alone, a place
     * answering for some other part of this compiler would be credited with standing on a way from
     * here — which is a wider set than the sentence names.
     */
    private static boolean standing(Over over, String at, Set<String> encountered,
                                    Set<String> fromTheStage) {
        boolean stops = false;
        for (Authority each : over.authorities()) {
            if (each.answersFor(at)) {
                if (fromTheStage.contains(at)) {
                    encountered.add(each.owns());
                }
                stops |= each.traversal() == Traversal.OPAQUE;
            }
        }
        return stops;
    }

    /**
     * The next step out of a method towards the reading it arrives at.
     *
     * <p>Two things a step can be, said as two and not told apart by how they are spelled. A method
     * carries the reading up from what it calls; a sentence is where the reading is, and is the end
     * of the path. Held as one string, the end of a path and a step of it are told apart by
     * sniffing for a bracket — which is a reading of a value that could have said which it was.
     */
    private sealed interface Step {

        /** The method this one reaches it through. */
        record Through(String at) implements Step {}

        /** What is read here, which is where the path stops. */
        record Reading(String said) implements Step {}
    }

    /** How the reading is arrived at from here, for a reader who has to go and look. */
    private static String pathFrom(String at, Map<String, Step> through) {
        List<String> path = new ArrayList<>();
        String walk = at;
        Set<String> seen = new LinkedHashSet<>();
        while (walk != null && seen.add(walk) && path.size() < 8) {
            switch (through.get(walk)) {
                case Step.Through(String next) -> {
                    path.add(next.substring(0, next.indexOf('(')));
                    walk = next;
                }
                case Step.Reading(String said) -> {
                    path.add(said);
                    walk = null;
                }
                case null -> walk = null;
            }
        }
        return String.join(" -> ", path);
    }

    /** What this site reads of raw structure, or null where it reads none. */
    private static String observation(Compiled.Site site, Set<String> declarations,
                                      Map<String, Set<String>> apart, String lookup) {
        if (declarations.contains(site.owner())) {
            return "reaches a declaration: " + site.owner() + "#" + site.member();
        }
        if (lookup != null && (site.owner() + "#" + site.member()).equals(lookup)) {
            return "asks what a name declares";
        }
        if (!apart.containsKey(site.owner())) {
            return null;
        }
        return switch (site.how()) {
            case ASKS, NAMES -> "asks which compound a type is: " + site.owner();
            case CALLS, REFERS -> apart.get(site.owner()).contains(site.member())
                    ? "takes a compound apart: " + site.owner() + "#" + site.member() : null;
            // Making one is not reading one, and a field of a compound holds no type to read.
            case MAKES, READS -> null;
        };
    }

    /** Every class among {@code models} that is a {@code root}, root included. */
    private static Set<String> descendantsOf(Map<String, ClassModel> models, String root) {
        Set<String> found = new LinkedHashSet<>(Set.of(root));
        boolean grew = true;
        while (grew) {
            grew = false;
            for (var entry : models.entrySet()) {
                if (found.contains(entry.getKey())) {
                    continue;
                }
                List<String> above = new ArrayList<>();
                entry.getValue().superclass()
                        .ifPresent(each -> above.add(each.asInternalName().replace('/', '.')));
                entry.getValue().interfaces()
                        .forEach(each -> above.add(each.asInternalName().replace('/', '.')));
                if (above.stream().anyMatch(found::contains)) {
                    found.add(entry.getKey());
                    grew = true;
                }
            }
        }
        return found;
    }

    /**
     * For each compound, the components of it that hold {@code held}.
     *
     * <p>Off the generic signature where there is one: a list of them erases to a list, and a rule
     * written over descriptors alone would not see what a tuple or a function holds.
     */
    private static Map<String, Set<String>> componentsHolding(Map<String, ClassModel> models,
                                                              Set<String> compounds, String held) {
        Map<String, Set<String>> found = new LinkedHashMap<>();
        for (String each : compounds) {
            ClassModel model = models.get(each);
            if (model == null) {
                continue;
            }
            Set<String> holding = new LinkedHashSet<>();
            model.findAttribute(Attributes.record()).ifPresent(record -> {
                for (var component : record.components()) {
                    String written = component.findAttribute(Attributes.signature())
                            .map(signature -> signature.signature().stringValue())
                            .orElse(component.descriptor().stringValue());
                    if (written.contains(held)) {
                        holding.add(component.name().stringValue());
                    }
                }
            });
            found.put(each, holding);
        }
        return found;
    }
}
