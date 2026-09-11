package souther.compiler.diag.msg;

import souther.compiler.diag.DiagnosticCode;

/** What an example row, the file it is written in, and the fakes it runs against are told. */
public sealed interface ExampleMessage extends Message {

    // --- the target ---

    /** The example names something this module does not declare as a behavior. */
    @Code(DiagnosticCode.E1901)
    record NoBehaviorOfThatName(String target) implements ExampleMessage, Reported {}

    /** The target is a behavior nothing can evaluate. */
    @Code(DiagnosticCode.E1902)
    record TheTargetCannotBeEvaluated(String target) implements ExampleMessage, Reported {}

    /** What an example can run, and what this one is. */
    record WhatAnExampleRuns(String here) implements ExampleMessage, Supporting {}

    /** `examples for` names a module that is not being compiled. */
    @Code(DiagnosticCode.E1907)
    record TheModuleIsNotBeingCompiled(String module) implements ExampleMessage, Reported {}

    // --- the row ---

    /** The row hands over a different number of inputs than the behavior takes. */
    @Code(DiagnosticCode.E1903)
    record TheRowHandsOverAnotherNumberOfInputs(String target, String takes, String given)
            implements ExampleMessage, Reported {}

    /** An input of the row could not be built. */
    @Code(DiagnosticCode.E1903)
    record AnInputCouldNotBeBuilt(String target, String at, String why) implements ExampleMessage, Reported {}

    /** The expected value could not be built. */
    @Code(DiagnosticCode.E1903)
    record TheExpectedValueCouldNotBeBuilt(String target, String why) implements ExampleMessage, Reported {}

    /** The row expects a case the behavior does not answer with. */
    @Code(DiagnosticCode.E1904)
    record NotOneOfTheResultCases(String expected, String target) implements ExampleMessage, Reported {}

    /** Which cases it does answer with. */
    record TheResultCasesAre(String cases) implements ExampleMessage, Supporting {}

    /** The row does not hold. */
    @Code(DiagnosticCode.E1905)
    record TheRowDoesNotHold() implements ExampleMessage, Reported {}

    /** Where the two are not of one type, which reading the two values does not say: an encoder
     *  writes a newtype as the base it wraps, so both sides read alike. */
    record TheTwoAreOfDifferentTypes(String at, String expected, String actual)
            implements ExampleMessage, Supporting {}

    /** What the row said and what came back. */
    record WhatTheRowSaid(String said) implements ExampleMessage, Supporting {}

    /**
     * A name is on more than one row of one behavior, so it says which row nowhere.
     *
     * <p>Said at every row that carries it, in the source that row is written in. Which of them is
     * the one that should have been named otherwise is not a question the language answers.
     */
    @Code(DiagnosticCode.E1925)
    record TheNameIsOnMoreThanOneRow(String name, String target) implements ExampleMessage, Reported {}

    /** The row reached a point the model says cannot arise. */
    @Code(DiagnosticCode.E1911)
    record TheRowReachedAnUnreachablePoint(String reason) implements ExampleMessage, Reported {}

    /** Which of the two that is. */
    record EitherTheRowOrTheReasonIsWrong() implements ExampleMessage, Supporting {}

    // --- the file ---

    /** An `examples for` file holds only what belongs in one. */
    @Code(DiagnosticCode.E1906)
    record AnExamplesFileHoldsOnlyExamples() implements ExampleMessage, Reported {}

    /** A value the file names is already declared by the module it is about. */
    @Code(DiagnosticCode.E1906)
    record TheNameIsAlreadyDeclared(String name, String module) implements ExampleMessage, Reported {}

    /** A declaration of the model names a value an attached file declares. */
    @Code(DiagnosticCode.E1626)
    record TheModelNamesAValueAnAttachedFileDeclares(String name)
            implements ExampleMessage, Reported {}

    /** Where such a value belongs if the model is to name it. */
    record MoveTheValueIntoTheModuleItself(String name) implements ExampleMessage, Supporting {}

    // --- the fakes ---

    /** A {@code fake} or a {@code with} names something that is no behavior in scope. */
    @Code(DiagnosticCode.E1932)
    record AFakeNamesNoBehavior(String named) implements ExampleMessage, Reported {}

    /**
     * More than one {@code fake} block names one behavior, so none of them stands in for it.
     *
     * <p>Said at every block that names it, in the source that block is written in. The rows of one
     * block are an ordered table and there is no order between a module's own source and the files
     * attached to it, so two of them cannot be read as one; which is the one to write differently
     * is not a question the language answers.
     */
    @Code(DiagnosticCode.E1933)
    record MoreThanOneFakeStandsInForOneBehavior(String behavior) implements ExampleMessage, Reported {}

    /** What to write instead. */
    record WriteTheRowsAsOneFake(String behavior) implements ExampleMessage, Supporting {}

    /** Where another of the blocks standing in for it is written. */
    record AnotherFakeStandsInForItHere(String behavior) implements ExampleMessage, Supporting {}

    /** A dependency has no fake. */
    @Code(DiagnosticCode.E1908)
    record ADependencyHasNoFake(String behavior, String dependency) implements ExampleMessage, Reported {}

    /** The same, where the dependency is reached through something else. */
    @Code(DiagnosticCode.E1908)
    record ADependencyReachedThroughHasNoFake(String behavior, String dependency, String through)
            implements ExampleMessage, Reported {}

    /** What to write for it. */
    record WriteAFakeLikeThis(String shown) implements ExampleMessage, Supporting {}

    /** The fake's value could not be built. */
    @Code(DiagnosticCode.E1908)
    record TheFakeValueCouldNotBeBuilt(String dependency, String why) implements ExampleMessage, Reported {}

    /** The fake itself could not be built. */
    @Code(DiagnosticCode.E1908)
    record TheFakeCouldNotBeBuilt(String dependency, String why) implements ExampleMessage, Reported {}

    /**
     * A row states arguments an earlier row of the table states, so the table answers with that one.
     *
     * <p>The dispatch takes the first row stating what it is asked, so a second is written and never
     * reached.
     */
    @Code(DiagnosticCode.E1926)
    record AnEarlierRowAnswersTheseArguments(String fake) implements ExampleMessage, Reported {}

    /** A `_` row is followed by another, which is what the table falls through to instead. */
    @Code(DiagnosticCode.E1926)
    record ALaterDefaultRowAnswersInstead(String fake) implements ExampleMessage, Reported {}

    /** Where the row that does answer is written. */
    record TheRowThatAnswersIsHere(String fake) implements ExampleMessage, Supporting {}

    /** A fake was asked for an input its table has no output for. */
    @Code(DiagnosticCode.E1909)
    record AFakeHadNoOutputForAnInput(String input) implements ExampleMessage, Reported {}

    /**
     * The row states an input and an answer the behavior's {@code ensures} says cannot go together.
     *
     * <p>{@code why} is what the check said when it was run over the row's own values, so the clause
     * that was not kept, and the case the row's answer turned out to be, are named by the thing that
     * found it rather than worked out again here.
     */
    @Code(DiagnosticCode.E1928)
    record ARowDoesNotKeepWhatTheBehaviorStates(String target, String why)
            implements ExampleMessage, Reported {}

    /**
     * A row of a fake's table stands in with an answer the dependency's {@code ensures} says it
     * cannot give for the input that row states.
     */
    @Code(DiagnosticCode.E1929)
    record AFakeRowDoesNotKeepWhatTheDependencyStates(String dependency, String why)
            implements ExampleMessage, Reported {}

    /**
     * An implementation supplied for an injected behavior answered a row's input with something the
     * behavior's {@code ensures} says it cannot answer with.
     *
     * <p>Told apart from {@link ARowDoesNotKeepWhatTheBehaviorStates} because the two are about
     * different texts: that one is a row recording something the model rules out, and this is code
     * outside the model doing something the model says it will not. What an author does about them
     * is not the same — one is a row to correct, the other an implementation to correct.
     *
     * <p>A behavior with a body cannot reach this. Its own {@code apply} checks its answer where it
     * answers, so what it answers has already been held to the declaration by the time a row sees it.
     */
    @Code(DiagnosticCode.E1930)
    record AnImplementationDoesNotKeepWhatTheBehaviorStates(String target, String why)
            implements ExampleMessage, Reported {}

    /** What to do about any of them: the declaration is what says what the behavior answers, so a row
     *  stating otherwise records something the behavior will not do. */
    record TheDeclarationIsWhatSaysWhatItAnswers(String target)
            implements ExampleMessage, Supporting {}

    /**
     * What answers the behavior was built against another revision of the module, and the two say
     * different things about something a value crossing between them depends on.
     */
    @Code(DiagnosticCode.E1927)
    record TheAnswerIsOfAnotherBuild(String target, String module, String declaration)
            implements ExampleMessage, Reported {}

    /** What to do about it: the two are one model, built twice. */
    record BuildWhatAnswersItAgainstThisRevision(String module)
            implements ExampleMessage, Supporting {}

    /** What to do about classes that are short of a module rather than wrong about one: the
     *  artifact naming it is fine, and what is missing is somewhere for the name to be read from. */
    record BuildWhatAnswersItAgainstAPathThatCarries(String module)
            implements ExampleMessage, Supporting {}

    /**
     * Whether what answers the behavior was built against this module could not be told — which is
     * not the same as their disagreeing, and is said as its own thing for that reason.
     */
    @Code(DiagnosticCode.E1927)
    record WhetherTheAnswerIsOfThisModuleCannotBeTold(String target, String module)
            implements ExampleMessage, Reported {}

    /** Nothing was published for the module: the classes carry no declarations at all. */
    record ItsClassesCarryNoDeclarations(String module) implements ExampleMessage, Supporting {}

    /** Declarations were published and this compiler does not read them. */
    record WhatItPublishedCannotBeReadHere(String module) implements ExampleMessage, Supporting {}

    /** The answer never said which build's declarations it reads a row's values by. */
    record ItDidNotSayWhichBuildItReadsBy(String module) implements ExampleMessage, Supporting {}

    /** The side that could not be read is the one the rows are written for, not the answer's. */
    record ThisCompileCannotReadItsOwnDeclarationsOf(String module)
            implements ExampleMessage, Supporting {}

    // --- what the evaluation could not finish ---

    /** The evaluation did not answer in time. */
    @Code(DiagnosticCode.E1923)
    record TheEvaluationDidNotAnswer(String within) implements ExampleMessage, Reported {}

    /** What that says and what it does not. */
    record NotAnsweringIsNotNotTerminating() implements ExampleMessage, Supporting {}

    /** The evaluation spent its step budget. */
    @Code(DiagnosticCode.E1910)
    record TheEvaluationSpentItsSteps(String budget) implements ExampleMessage, Reported {}

    /** What that says and what it does not. */
    record SpendingTheBudgetIsNotDiverging() implements ExampleMessage, Supporting {}

    /** The evaluation reached its recursion-depth limit. */
    @Code(DiagnosticCode.E1910)
    record TheEvaluationReachedItsDepthLimit(String limit) implements ExampleMessage, Reported {}

    /** What that says and what it does not. */
    record ReachingTheDepthLimitIsNotDiverging() implements ExampleMessage, Supporting {}

    /** The JVM stack ran out before the depth limit did. */
    @Code(DiagnosticCode.E1924)
    record TheStackRanOutBeforeTheDepthLimit(String stack, String limit)
            implements ExampleMessage, Reported {}

    /** What to do about it. */
    record TheDepthLimitIsWhatShouldStopIt() implements ExampleMessage, Supporting {}

    // --- what the rows do not cover ---

    /** No row expects a case the signature declares. */
    @Code(DiagnosticCode.E1913)
    record NoRowExpectsThatCase(String caseName, String behavior) implements ExampleMessage, Reported {}

    /** What to write for it. */
    record WriteARowExpectingThatCase(String caseName) implements ExampleMessage, Supporting {}

    /** No row applies the behavior to a case one of its inputs declares. */
    @Code(DiagnosticCode.E1915)
    record NoRowAppliesItToThatCase(String caseName, String at, String behavior)
            implements ExampleMessage, Reported {}

    /**
     * No row's value at a position falls in one of the classes the model divides it into.
     *
     * <p>Told apart from {@link NoRowAppliesItToThatCase} by a code of its own, because the two are
     * different findings about the same value and are answered by different rows. A case an input
     * declares is a fact about the type; a class is what the model's own rules divide the position
     * into, and a position with no sum at all has classes. The same {@code C} can be a case no row
     * uses and a class no row is in, and one row closes both — which is what makes the pair easy to
     * read as one thing and wrong to report as one.
     *
     * <p>{@code className} is the class as the partition names it, which is the name the report
     * writes beside the same position. Two spellings of one class would have a reader matching a
     * warning against a report to find out whether they are about the same thing.
     */
    @Code(DiagnosticCode.E1931)
    record NoRowIsInThatClass(String className, String at, String behavior)
            implements ExampleMessage, Reported {}

    /** What answers it, said as the row rather than as the class: a class is met by a value, and an
     *  author writes values. */
    record WriteARowWhoseValueThereIsInThatClass(String at, String className)
            implements ExampleMessage, Supporting {}

    /**
     * No row takes one of the rules of the decision a body states.
     *
     * <p>The behavior and nothing else. A rule is told apart by the distinctions it consulted,
     * written the one way round that makes a comparison and its denial one column — which is what
     * an account keys on and is not what an author wrote. Said here, an author would be shown a
     * comparison they did not write, the wrong way round. Which rule it is, is said underneath: one
     * note per condition, sending the reader to the construct that drew it.
     */
    @Code(DiagnosticCode.E1935)
    record NoRowTakesADecisionRule(String behavior) implements ExampleMessage, Reported {}

    /**
     * One condition of that rule: a comparison the author wrote, which the rule takes holding.
     *
     * <p>Carries nothing. Which comparison it is is where the label is put, so a reader reads the
     * comparison they wrote rather than one this compiler spelled from the proposition its account
     * keys on — {@code n > 100} in a body is held there as {@code n <= 100} denied.
     */
    record TheRuleTakesThisComparisonHolding() implements ExampleMessage, Supporting {}

    /** The same, where the rule takes it failing. Two entries and not one that selects a word: a
     *  wording that turns on a value is two messages. */
    record TheRuleTakesThisComparisonFailing() implements ExampleMessage, Supporting {}

    /** One condition of that rule: a fork of the body, and which of its arms the rule goes down. */
    record TheRuleGoesThroughThisArm(souther.compiler.diag.Localizable arm)
            implements ExampleMessage, Supporting {}

    /** A condition of the rule with nothing to send a reader to, so that a rule is never described
     *  by fewer conditions than it turns on. */
    record OneConditionOfTheRuleIsNotShown() implements ExampleMessage, Supporting {}

    /**
     * No row is at one of the points a border owes, the rule that drew it having a name.
     *
     * <p>{@code point} is which of them, in the word domain testing gives it (ISTQB CTAL-TA v4.0
     * §3.1.1). The report writes the same word for the same finding, and a reader handed a
     * vocabulary by one of the two surfaces was being asked to translate at the other.
     *
     * <p>The same word in every catalog. {@code ON} and {@code OFF} are the technique's terms for
     * the two points against the line rather than English words for them, so they are handed over
     * as a name is and what a catalog holds is the sentence around them. A catalog that translated
     * them would be answering a reader who practises the technique in words it has no term for.
     *
     * <p>The border is named by the rule that drew it and not by the point: only one of the two
     * points carries the value the rule was written with, so a sentence saying the line is here
     * would be false of whichever point is the step over.
     */
    @Code(DiagnosticCode.E1916)
    record NoRowIsAtThePointOfTheBorderARuleDrew(String point, String at, String value, String rule)
            implements ExampleMessage, Reported {}

    /**
     * The same, of a line a body's own rule drew, which is owed once wherever it is read.
     *
     * <p>Its own sentence because it names no quantity. A body's comparison has no authored spelling
     * of what it is on: a guard on a name every case of a sum spreads is read once under each case,
     * each reading names the position it met the line at, and none of them can stand for the rest.
     * So this says where on the line the point is — {@code level}, the value the rule cut at, which
     * every reading writes the same — and what each reading asks is said beside it
     * ({@link TheLineAsReadAt}).
     *
     * <p>{@code rule} is the name the author gave it, where the rule has one ({@code ensures}).
     */
    @Code(DiagnosticCode.E1916)
    record NoRowIsAtThePointOfTheLineARuleDrew(String point, String rule)
            implements ExampleMessage, Reported {}

    /**
     * The same, where a fork of a body drew the line.
     *
     * <p>A rule of its own because a fork has no name to put in the other one's slot. What went
     * there was a phrase built in Java — `a guard` — which is a rendering, so it read as English in
     * every language the rest of the sentence was written in, and it named one construct where three
     * draw a line this way. A type and an invariant have names, and a name is the same in every
     * language; this has none, so the words are the catalog's.
     *
     * <p>Which construct it was is a {@link souther.compiler.diag.Localizable}, and not three rules
     * of this one. What the sentence says does not turn on it — the phrase is a noun dropped into one
     * wording, which is what a token category already is — so what varies is a catalog entry rather
     * than a sentence written twice.
     *
     * <p>Where the construct is written is not here either. It is a place, and a place is pointed at.
     */
    @Code(DiagnosticCode.E1916)
    record NoRowIsAtThePointOfTheLineAConstructDrew(String point,
                                                   souther.compiler.diag.Localizable construct)
            implements ExampleMessage, Reported {}

    /**
     * One reading of a line owed once, said under the point it is a reading of.
     *
     * <p>The quantity's name lives here and nowhere in the sentence above: {@code at} is the position
     * this reading met the line at, and {@code asks} is what a row there has to do, in that
     * position's terms. One of these per reading, in the order the readings' sentences sort, so
     * that which reading a walk met first is not what a reader sees first.
     */
    record TheLineAsReadAt(String at, String asks) implements ExampleMessage, Supporting {}

    /** How many readings were not said, where a line has more than a sentence has room for. A
     *  count and not a silence: left out unsaid, the readings shown read as all there are. */
    record MoreReadingsOfTheLine(int more) implements ExampleMessage, Supporting {}

    /**
     * The same, for one of the two points away from the line.
     *
     * <p>Its own rule and its own code, because which criterion a build is held to decides whether it
     * is said at all: a row on the line and a row one step over are what simplified domain coverage
     * asks for, and a row well inside and a row well outside are the two items reliable domain
     * coverage adds. One sentence for both would be one code covering two rules, and a build asking
     * to be held to the first would be refused over the second's.
     */
    @Code(DiagnosticCode.E1917)
    record NoRowIsAtThePointAwayFromTheBorderARuleDrew(String point, String at, String value,
                                                       String rule)
            implements ExampleMessage, Reported {}

    /** The same, of a line a body's own rule drew and named, which names no quantity for the
     *  reason {@link NoRowIsAtThePointOfTheLineARuleDrew} does not. */
    @Code(DiagnosticCode.E1917)
    record NoRowIsAtThePointAwayFromTheLineARuleDrew(String point, String rule)
            implements ExampleMessage, Reported {}

    /** The same again, where a fork of a body drew the line and there is no name to put in the slot. */
    @Code(DiagnosticCode.E1917)
    record NoRowIsAtThePointAwayFromTheLineAConstructDrew(String point,
                                                         souther.compiler.diag.Localizable construct)
            implements ExampleMessage, Reported {}

    /** What a row well inside shows: the border is the only thing keeping the values apart there. */
    record ARowWellInsideShowsTheBorderIsWhatDivides() implements ExampleMessage, Supporting {}

    /** The same, from the other side. */
    record ARowWellOutsideShowsTheBorderIsWhatDivides() implements ExampleMessage, Supporting {}

    /** Said beside the construct a line was drawn in, the sentence naming the rule without a place —
     *  a fork has no name, and where it is written is a place a renderer resolves a file for. */
    record TheConstructThatDrawsTheLine(souther.compiler.diag.Localizable construct)
            implements ExampleMessage, Supporting {}

    /**
     * What a row at the {@code ON} point shows.
     *
     * <p>Said in the same words the sentence above it just used. A hint keyed on where the value
     * falls against the line would be a second vocabulary for one finding, which is the cost this
     * diagnostic was being fixed to stop charging — and it is not the same axis: which of the two
     * points carries the line's own value turns on whether the border is closed, so
     * {@code n <= 100} is at its {@code ON} point on the line and {@code n < 100} is at its
     * {@code OFF} point there.
     *
     * <p>What tells a rule written {@code <=} from one written {@code <} is the border being closed
     * or open, which the report says of the border and not of either of its points.
     */
    record ARowJustInsideShowsTheBorderIsNotFurtherIn() implements ExampleMessage, Supporting {}

    /** And what a row at the {@code OFF} point shows, which is the other half of the pair. */
    record ARowJustOutsideShowsTheBorderIsNotFurtherOut() implements ExampleMessage, Supporting {}

    /**
     * No row goes through an arm of the body.
     *
     * <p>{@code arm} is localized where the sentence is. What an outcome is called depends on the
     * construct it belongs to — an {@code if} has a {@code then} to quote, a {@code guard} has the
     * rest of its block and no word for it — and the analysis that found the arm has no reader and
     * so no language to name it in.
     */
    @Code(DiagnosticCode.E1918)
    record NoRowGoesThroughThatArm(souther.compiler.diag.Localizable arm, String behavior)
            implements ExampleMessage, Reported {}

    /** Which of the two that is. */
    record EitherARowIsMissingOrNothingReachesIt() implements ExampleMessage, Supporting {}

    /**
     * A row goes through an arm of the body with its answer still owed.
     *
     * <p>Not {@link NoRowGoesThroughThatArm}, which is an arm nothing reaches. A row does reach
     * this one, and what it is short of is the answer — the same thing the rows reported beside it
     * are short of, and the same thing to do about it, so it is that rule's code and not the arm
     * rule's.
     */
    @Code(DiagnosticCode.E1934)
    record ARowAtThatArmAwaitsItsAnswer(souther.compiler.diag.Localizable arm, String behavior)
            implements ExampleMessage, Reported {}

    /** What answering it takes. */
    record ReplaceTheMarkWithWhatTheSystemAnswers() implements ExampleMessage, Supporting {}

    /**
     * A row is written and its answer is not.
     *
     * <p>Said of the row and at the row, because that is what it is about. What the row goes
     * through is a separate thing to say and is said of the arm; a behavior with no arms at all has
     * nothing there to say it, and the row is owed an answer either way.
     */
    @Code(DiagnosticCode.E1934)
    record TheRowsAnswerIsOwed(String behavior) implements ExampleMessage, Reported {}

    /** The same, for a row that names itself. */
    @Code(DiagnosticCode.E1934)
    record TheNamedRowsAnswerIsOwed(String row, String behavior)
            implements ExampleMessage, Reported {}

    // --- a stand-in and a row that disagree ---

    /** A row and a fake state different answers for one input. */
    @Code(DiagnosticCode.E1919)
    record TheRowAndTheFakeDisagree(String behavior) implements ExampleMessage, Reported {}

    /** The same, where the stand-in is a `with`. */
    @Code(DiagnosticCode.E1919)
    record TheRowAndTheWithDisagree(String behavior) implements ExampleMessage, Reported {}

    /** What each of them says. */
    record WhatTheRowSaysAndWhatTheFakeSays(String row, String fake) implements ExampleMessage, Supporting {}

    /** The same, for a `with`. */
    record WhatTheRowSaysAndWhatTheWithSays(String row, String with) implements ExampleMessage, Supporting {}

    /** Where the fake that disagrees is written — the other half of the disagreement, since neither
     *  statement is the one the model is to be held to. */
    record TheFakeRowIsHere(String behavior) implements ExampleMessage, FindingRegion {}

    /** Where the `with` that disagrees is written, for the same reason. */
    record TheWithIsHere(String behavior) implements ExampleMessage, FindingRegion {}

    // --- a table that could not be built, so nothing it states was checked ---

    /** Building the fake's table spent its step budget. */
    @Code(DiagnosticCode.E1921)
    record TheTableSpentItsSteps(String fake, String budget) implements ExampleMessage, Reported {}

    /** Building the fake's table reached the depth limit. */
    @Code(DiagnosticCode.E1921)
    record TheTableReachedItsDepthLimit(String fake, String limit) implements ExampleMessage, Reported {}

    /** Building the fake's table ran out of JVM stack. */
    @Code(DiagnosticCode.E1921)
    record TheTableRanOutOfStack(String fake, String limit) implements ExampleMessage, Reported {}

    /** Building the fake's table did not answer in time. */
    @Code(DiagnosticCode.E1921)
    record TheTableDidNotAnswer(String fake, String within) implements ExampleMessage, Reported {}

    /** What a table over its steps says. */
    record TheTableGoesRoundTooManyTimes(String fake) implements ExampleMessage, Supporting {}

    /** What a table past the depth limit says. */
    record TheTableRecursesTooDeeply(String fake) implements ExampleMessage, Supporting {}

    /** What a table that overran the stack says. */
    record TheStackGotThereFirst(String fake) implements ExampleMessage, Supporting {}

    /** What a table that did not answer says. */
    record TheTableNotAnsweringIsNotTheTableBeingWrong(String fake) implements ExampleMessage, Supporting {}

    /** The fake could not be compared with the rows: its table spent its steps. */
    @Code(DiagnosticCode.E1920)
    record NotComparedTheTableSpentItsSteps(String behavior, String budget)
            implements ExampleMessage, Reported {}

    /** The same, at the depth limit. */
    @Code(DiagnosticCode.E1920)
    record NotComparedTheTableReachedItsDepthLimit(String behavior, String limit)
            implements ExampleMessage, Reported {}

    /** The same, out of stack. */
    @Code(DiagnosticCode.E1920)
    record NotComparedTheTableRanOutOfStack(String behavior, String limit)
            implements ExampleMessage, Reported {}

    /** The same, unanswered. */
    @Code(DiagnosticCode.E1920)
    record NotComparedTheTableDidNotAnswer(String behavior, String within)
            implements ExampleMessage, Reported {}

    /** What each of those says, in turn. */
    record TheTableComparedGoesRoundTooManyTimes(String behavior) implements ExampleMessage, Supporting {}

    record TheTableComparedRecursesTooDeeply(String behavior) implements ExampleMessage, Supporting {}

    record TheStackGotThereFirstWhenComparing(String behavior) implements ExampleMessage, Supporting {}

    record NotAnsweringIsNotTwoAnswers(String behavior) implements ExampleMessage, Supporting {}
}
