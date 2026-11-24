# VSim 2 code conventions

The rules every new line of code follows, and how to treat code that already exists. The README describes what VSim 2
is and how to build it; this file is the coding rulebook. When a rule changes,
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

## 3. Clean Code

We apply Clean Code (Robert C. Martin) to new code and to the code we modify (section 1 says how far):

- **Small functions that do one thing**, at one level of abstraction; a method that needs a comment to separate its
  parts is several methods. Prefer early returns over deep nesting.
- **Single responsibility**: a class has one reason to change. A panel lays out and wires; the rules live in a model
  or a core class (e.g. `PracticeModel`, `GroupCheck`), so they can be tested without Swing.
- **Intention-revealing names** (section 2): a reader should not need the body to know what a method returns or does.
  Boolean queries read as questions (`isBusy()`, `practised(output, form)`).
- **Arguments**: up to five are fine; beyond that, or when the same group of arguments travels together through
  several methods, consider a record (`Request`, `GeneratorOptions`). No boolean flag arguments that switch behaviour
  when two methods or an enum say it better.
- **No side effects hidden in queries**; a method either answers or changes state (command–query separation).
- **No duplication**: reuse the existing helper (find it before writing one); extract when the same rule appears twice.
  Name the shared piece by what it is in the domain, and where the copies differed on purpose (a colour, an offset, a
  null case) keep that difference visible as a parameter. CPD (`cpdMain`, run by `./gradlew test`) fails the build on
  any run of **70 tokens or more** (about 5–10 lines) repeated in `src/main` of the four modules, also across modules;
  names and literals count as written (no `--ignore-identifiers`: with it, every Swing layout and switch table looks
  alike). The threshold is `vsim.cpd.minimumTokens` in `gradle.properties`. The exception, only when unifying would be
  worse than the copy (say, two classes on purpose independent, or code another branch owns), is explicit in place:
  `// CPD-OFF` before the copy and `// CPD-ON` after it, with a one-line reason on the `CPD-OFF` line.
- **No magic numbers or strings**: named constants (`MAXIMUM_INPUTS`, `NODE_BUDGET`) with a word on where they come
  from.
- **No dead code**: no commented-out code, unused parameters, fields, keys or methods. Delete them; git remembers.
  A field used only in the constructor or in one method is a local variable; no local variable that is only returned;
  no condition that is always true or always false; no unused import. PMD checks (`pmdMain`): `SingularField`,
  `UnusedPrivateField`, `UnusedPrivateMethod`, `UnusedFormalParameter` (private methods only), `VariableCanBeInlined`
  (a local only returned or thrown) and `UnnecessaryImport`. `UnusedLocalVariable` and `UnusedAssignment` are off (false
  positives: see the reasons in `config/pmd/vsim-rules.xml`). Non-private unused methods, unused parameters of
  interface methods and always-true conditions are not checked automatically: look for them when you touch the code.
- **Comments say why, not what** — they complement good names, they do not excuse bad ones (the Javadoc rule of
  section 5 still applies).
- **Boy Scout rule**, inside the limits of section 1: leave the code you touch a little cleaner than you found it.
- **Tests are clean code too**: one behaviour per test, readable arrange–act–assert, helpers instead of copy-paste.

## 4. Architecture boundaries

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

## 5. Java style

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
- Javadoc holds: every `{@link}` / `@see` resolves and points to something visible from where it is written (a
  private member, or a record component, gets `{@code name}` instead); no `/** */` comment that documents nothing (a
  `/**` before a statement or another comment is a plain `//` or `/* */` comment); a Javadoc never links to itself.
  Automatic checks: `-Xdoclint:reference` on `compileJava` (a broken link is a compile error); `SourceRulesTest` (a
  `/**` not followed by a declaration, and a Javadoc whose `{@link #member}` / `{@value #member}` names the member it
  documents). A link to an inaccessible member is not caught by javac: the periodic IntelliJ analysis finds it.
- **Lambdas are short**: a lambda is one expression or a body of at most 3 lines; anything longer is a named method
  passed as a method reference (`this::applyZoom`). When the logic is worth testing, the method goes where a test
  reaches it (package-private, or a model/core class), not only a private method of a panel. A lambda that only calls
  a method is that method's reference. A listener repeated in more than one place is a named class; the ones that
  only forward an event to a `Runnable` already exist in `ui.panels` (`WindowEvents`, `FocusEvents`, `ComponentEvents`,
  `MouseClicks`, `PopupMouseListener`, `TextChanges`) and in `ui.actions` (`Actions.of`, `unnamed`, `doNothing`): use
  them before writing an anonymous `WindowAdapter`, `FocusAdapter`, `ComponentAdapter`, `MouseAdapter`,
  `DocumentListener` or `AbstractAction`. Automatic checks
  (PMD, `config/pmd/vsim-rules.xml`): `LambdaBodyAtMostThreeLines` (measures the lines between the block's braces,
  comments and blank lines included, so a body of 3 statements plus a comment fails; a lambda written as an
  expression is not measured) and `LambdaCanBeMethodReference`; the code review judges the repeated listeners.
- Compilation runs with `-Xlint:all`: new code introduces no warnings. The build also runs PMD
  (`config/pmd/vsim-rules.xml`) and CPD; every rule in this file with an automatic check names it, and an exception is
  explicit in place (`@SuppressWarnings("PMD.<Rule>")` or `// CPD-OFF`, with the reason beside it).

## 6. Errors and logging

- Catch the most specific exception that can happen; never swallow one silently. Log with SLF4J (`LoggerFactory`),
  never `System.out`/`System.err` outside the command-line tools.
- The user never reads an internal (English) exception message: show a fixed sentence from the bundles and put the
  details in the log.

## 7. UI rules

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

### Interface patterns (from the interface audit, 2026-11-20)

The interface stays simple for students and the same everywhere. A new screen reuses these pieces instead of building
its own.

- **Never hide, disable with a reason.** A control that does not apply stays in its place, disabled, and says why:
  `Actions.disabledBecause(action, () -> reason)` adds «— reason» to the tooltip (menus, toolbar, Ctrl+K). The toolbar
  never changes its contents; when it does not fit, what is left over goes to its «»» button
  (`OverflowToolBarLayout`), in the same order. New buttons go into a `ToolbarSlot`.
- **Keys are shown, never written by hand.** Tooltips get their key from `AcceleratorTooltip`; a canvas-scope key is
  shown on its menu item with `ShownAcceleratorItem` / `Actions.SHOWN_ACCELERATOR` (shown, not bound). A text never
  says «(F8)».
- **Dialogs:** `DialogButtons`: the action button on the LEFT and Cancelar on the right, the default button, Esc to
  cancel or close, standard insets, and `fitToScreen` after `pack()` (never larger than the usable screen; the content
  scrolls instead of being cut). Every window closes with Esc. A confirmation that destroys something defaults to
  Cancelar. Notices have the feature's title, never the generic «Mensaje».
- **Cards** (floating panels): `CardLook.CORNER` and its close icon.
- **Readable colours:** secondary text with `ReadableColors.mutedText()`, errors `errorText()`, warnings
  `warningText()`; `Label.disabledForeground` only for what is really disabled. Text ≥ 4.5:1 and icons or knobs ≥ 3:1
  against what is really behind them: on the canvas or a diagram that is the `ColorScheme` background, not the theme.
- **Sizes follow the interface size (100–200 %):** pixel constants through `UIScale.scale(..)`, fonts derived from
  `UIManager.getFont("Label.font")` at paint time, row heights from font metrics. No `deriveFont(11f)`, no
  `static final` pixel heights for text rows.
- **Empty states say what to do** (an empty panel, a search with no result), in muted text.
- **Focusable custom components paint a focus ring** with `Component.focusColor`.
- **Menus:** a toggle is a check item; a choice among several is a radio item that follows the real state from any
  source (`EditorView.addToolMenuItem` for tools). Items of one group stay together; «…» only when a dialog follows.
- **Before merging a visible change, look at it**: light and dark, 100 % and 200 %, Spanish and English, in the default
  1280 px window. Layout tests measure with the real fonts, catalog and widths; a test that passes while the screen
  cuts a text is not enough.

### Terminology of the texts (Spanish bundle; the English one follows)

One word per thing; a new text uses these and an old one that strays is fixed.

| Say | Never | Notes |
|---|---|---|
| cuadrícula | grilla | the grid of the circuit and of the diagrams |
| carpeta | directorio | what the user sees; `directory` stays in code and preference ids |
| Mis componentes | librería, biblioteca | the student's own components (menus, Propiedades, Problemas, dialogs, docs); English «My components». «Biblioteca» only for Logisim's libraries |
| esquema de colores | esquema de color | |
| Restaurar | Restablecer | back to the default or the original |
| Pulsa | Presiona | the key or the button; always the tú form |
| Borrar | | destroys something of the drawing (a wire, a guide, a segment) |
| Quitar | | takes something out of a list or a structure (a favorite, a split, a waveform) |
| Eliminar | | removes a component from Mis componentes |
| piezas, cables | | the parts of a circuit; «elementos» only for a mixed selection |
| « » | “ ” | quotes in Spanish; the English bundle uses “ ” (`EnglishBundleStyleTest`) |

English: sentence case in menu items, buttons, titles and tooltips (proper nouns and acronyms keep their capitals),
American spelling.

## 8. File format

- Until VSim 2.0 is declared final the VSim 2 format may change freely (agreed with the user 2026-10-20): no
  compatibility with older VSim 2 builds is needed, but every committed file (`examples/`, bundled examples, test
  resources) is migrated in the same work so it keeps loading. The VSim 1 importer stays.
- From 2.0 final on, files stay compatible: new fields are optional with a default, old files load unchanged and save
  byte-for-byte the same when nothing changed. The one field allowed to differ is `savedWith` (the version of VSim
  that saved the file): re-saving after an upgrade changes it, and that is the only change.

## 9. Tests

- **Test first**: write the key test, see it fail for the expected reason, then implement.
- JUnit 5 + **AssertJ**. Tests are **headless and deterministic**: no shown `JFrame`/`JDialog`/popups, no sleeps, no
  wall-clock asserts; fake tickers/clocks, `AppPreferences.inMemory()`, `@TempDir` for files,
  `UiTestSupport.awaitSimulation(document)` after a simulation request.
- Core logic gets exhaustive or randomized checks against an independent brute force where the input space allows
  (Karnaugh groups, minimizer, generated circuits).
- Before every commit: `./gradlew test :ui:executableJarTest --continue` (in the main checkout,
  `ExampleCircuitsTest.everyCommittedFileHasABuilderAndViceVersa` fails only because of the user's untracked files).

## 10. Commits and documentation

- One logical change per commit; imperative English message; authored as alejandro.vera@gmail.com, **no**
  `Co-Authored-By:` trailer (agreed with the user 2026-10-22). Stage by path only (never `git add -A` / `git commit -a`); never commit the user's untracked circuits, scratch
  folders or `CLAUDE.md`. Push only with the user's explicit OK.
- A user-visible feature updates three documents (all in Spanish) in the same work: its one-line entry in the
  README's «Funciones» list (under its area, linking to its section), its full detail in its area's file
  `docs/funciones/<area>.md` (`docs/funciones.md` is the index; never by milestone) and an entry in `CHANGELOG.md`
  (newest section first, «Agregado» / «Cambiado» / «Arreglado», written as what the user notices). User-visible
  fixes get a changelog entry too. Decisions and deferred items are recorded with the project's design notes.
