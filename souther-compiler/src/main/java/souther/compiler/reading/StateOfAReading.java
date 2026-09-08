package souther.compiler.reading;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A state of a reading of the rules: what the operations that compose one reach, and nothing else.
 *
 * <p>What a type says by carrying this is that its parts state relations to each other that no part
 * states on its own — a whole that holds nothing is not a position that holds nothing, alternatives
 * are never an empty union, a refusal is one the work that built the reading noted. None of those
 * is a property a constructor call can be asked to keep, so the parts are the type's own: it holds
 * them as one record of its own, it publishes no constructor, and a caller reaches one by doing to
 * a reading what the reading says was done to it.
 *
 * <p><b>Declared and not read off the shape.</b> A type that is one of these says so here, and what
 * it says is then held to ({@code AReadingIsWhatItsOperationsReach}). Worked out instead from
 * whether a type already keeps its parts the way these do, the reading would find the states that
 * are already right and pass over the one written as a record of its parts — which is the state
 * this whole arrangement exists to refuse, and the one such a reading would never see.
 *
 * <p>Its own package because the languages a reading is written in are their own, and one of them
 * ({@code souther.compiler.numeric}) depends on nothing else here. A word both of them can say has
 * to be somewhere neither of them owns.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface StateOfAReading {
}
