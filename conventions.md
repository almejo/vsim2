# VSim 2 code conventions

The rules every new line of code follows, and how to treat code that already exists. `CLAUDE.md` describes the
architecture and `docs/superpowers/workflow.md` how we work; this file is the coding rulebook. When a rule changes,
change it here in the same commit.

## 1. New code and modified code

- **New code** (new files, classes, methods) follows every rule in this file.
- **Code you modify** follows the rules in the lines you write or change. If a method you change breaks a rule
  (an abbreviated name, a cached colour, a hardcoded text), fix it inside that method when it is cheap and safe.
- **Do not reformat or rename code you are not otherwise touching.** Wider cleanups go in their own commit
  («Rename … to …», «Read … at paint time»), never mixed into a feature commit, so diffs stay reviewable.
- When the surrounding code has an established local idiom that this file does not cover, match it.

## 2. Language and names

- Code, comments, Javadoc, commit messages and planning docs in **English**. Everything the user sees (UI, README,
  messages) in **Spanish**, with the English bundle kept in step.
- **Complete names, never abbreviations**: `exception`, not `e`; `index`, not `i`; `graphics`, not `g`; `event`, not
  `evt`; `button`, not `btn`. This includes loop variables, lambda parameters and catch clauses
  (`catch (IOException exception)`). Established domain words are fine (`netlist`, `xor`, `id`).
- Names say what a thing is in the domain: `selectedOutput`, `pinnedGroup`, `rowSpan`, not `value2` or `tmp`.
- Test methods are sentences describing the behaviour:
  `draggingAGateDragsItsWireAndTheLampStaysConnected()`.

## 3. Architecture boundaries

- `core` is headless: it never imports `java.awt` or `javax.swing` (see `core/package-info.java`). Core returns enums,
  records and **message keys + arguments**, never formatted user text.
- Every change of the document goes through a `Command` (`apply`/`revert`, `description()` key); **one user action =
  one command = one undo step**.
- The simulation engine is owned by `SimulationThread`. The UI never touches the engine; it queues requests and reads
  the published snapshots. Never touch Swing off the EDT, never block the EDT (slow work goes to `Background` with a
  generation counter so a stale result never overwrites a newer one).
- Features are added through `EditorExtension.install(view)`; the returned `Runnable` undoes **every** registration it
  made on shared objects (document, history, library, preferences, listeners, timers).
- Never modify `ui/.../HitTester.java`.

## 4. Java style

- Java 21. Prefer **records** for values, **sealed interfaces** for closed hierarchies, **switch expressions** over
  if-chains on enums.
- Classes are `final` unless designed for extension; members are as private as possible (package-private for what
  tests in the same package need, rather than public).
- **Immutable by default**: records copy their collections (`List.copyOf`, `Set.copyOf`); return unmodifiable views.
- **No `null` in APIs**: use `Optional`, `OptionalInt`, an empty collection or `-1` documented as «none». Validate
  arguments at public entry points (`Objects.requireNonNull`, `IllegalArgumentException` with a useful message).
- Explicit types instead of `var`. No wildcard imports. 4-space indentation; keep lines within about 120 columns.
- Javadoc on every public and package-level type and on non-obvious methods: say **what and why**, with the domain
  example when it helps (`«Faltan 2 unos por agrupar»`). Comments explain intent and decisions, not what the next line
  plainly does. Match the comment density of the surrounding code.
- Compilation runs with `-Xlint:all`: new code introduces no warnings.

## 5. Errors and logging

- Catch the most specific exception that can happen; never swallow one silently. Log with SLF4J (`LoggerFactory`),
  never `System.out`/`System.err` outside the command-line tools.
- The user never reads an internal (English) exception message: show a fixed sentence from the bundles and put the
  details in the log.

## 6. UI rules

- **Every visible string** comes from `ui/src/main/resources/i18n/messages_{es,en}.properties` through `Messages`, in
  both bundles, under the section header of the feature. In texts with `{0}` placeholders apostrophes are doubled
  (`VSim''s`; `MessagesTest` checks it).
- **Colours and fonts are read at paint time** from the current `ColorScheme` / FlatLaf theme / `WizardColors`, never
  cached in fields (theme and scheme change live). Check light **and** dark themes, including text contrast.
- Shortcuts are declared in `ShortcutCatalog` (one id, default stroke, scope); actions are mapped by id. New menu items
  or shortcuts update the exact-list tests.
- Dialogs, choosers, confirmations, clipboards and timers are **injectable** (an `Opener`, a confirmation interface, a
  `FrameTicker`), so tests never show a window or wait on a real clock.
- Preferences go through `AppPreferences`.

## 7. File format

- Files stay compatible: new fields are optional with a default, old files load unchanged and save byte-for-byte the
  same when nothing changed.

## 8. Tests

- **Test first**: write the key test, see it fail for the expected reason, then implement.
- JUnit 5 + **AssertJ**. Tests are **headless and deterministic**: no shown `JFrame`/`JDialog`/popups, no sleeps, no
  wall-clock asserts; fake tickers/clocks, `AppPreferences.inMemory()`, `@TempDir` for files,
  `UiTestSupport.awaitSimulation(document)` after a simulation request.
- Core logic gets exhaustive or randomized checks against an independent brute force where the input space allows
  (Karnaugh groups, minimizer, generated circuits).
- Before every commit: `./gradlew test :ui:executableJarTest --continue` (in the main checkout,
  `ExampleCircuitsTest.everyCommittedFileHasABuilderAndViceVersa` fails only because of the user's untracked files).

## 9. Commits and documentation

- One logical change per commit; imperative English message; the `Co-Authored-By:` trailer of the model that wrote
  it. Stage by path only (never `git add -A` / `git commit -a`); never commit the user's untracked circuits, scratch
  folders or `CLAUDE.md`. Push only with the user's explicit OK.
- A user-visible feature updates the README (Spanish) in the same work; decisions and deferred items go to
  `docs/superpowers/plans/2026-10-08-m1-followups.md`.
