package cl.almejo.vsim.core.logic;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Reads a truth table written in a spreadsheet: comma or semicolon separated text, tab separated text (what copying
 * cells in Excel or LibreOffice puts in the clipboard) or an {@code .xlsx} workbook (its first sheet, read with the JDK
 * only). The first non-blank row has the names; each following row is one input combination with its outputs.
 * <p>
 * Reading gives the raw cells ({@link ImportedGrid}); {@link #toSpec} then understands them, given how many of the
 * columns are inputs, and lists every problem with the row and column the user sees in the spreadsheet.
 */
public final class TableImport {

    /**
     * The most bytes read: inflated from all the entries of an {@code .xlsx} together, the ones skipped too (a guard
     * against zip bombs), or from a text file.
     */
    public static final int MAXIMUM_FILE_MEGABYTES = 64;
    /**
     * The largest circuit «Crear» builds from an imported table: past it the layout and the check take more than
     * about 3 s (a 10-input parity needs 2 229 gates) and the circuit is too large to read anyway.
     */
    public static final int MAXIMUM_GATES = 2_000;
    /** Excel's last row and last column ({@code XFD}): a reference beyond them is not a real workbook. */
    private static final int MAXIMUM_ROW = 1_048_576;
    private static final int MAXIMUM_COLUMN = 16_384;

    private static final String SPREADSHEET_NAMESPACE = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String RELATIONSHIPS_NAMESPACE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String WORKBOOK = "xl/workbook.xml";
    private static final String WORKBOOK_RELATIONSHIPS = "xl/_rels/workbook.xml.rels";
    private static final String SHARED_STRINGS = "xl/sharedStrings.xml";
    private static final String WORKSHEETS = "xl/worksheets/";
    private static final Pattern NUMBER_IN_NAME = Pattern.compile("(\\d+)\\.xml$");
    private static final Pattern BINARY_NUMBER = Pattern.compile("([01])(?:[.,]0*)?");
    private static final Pattern CELL_REFERENCE = Pattern.compile("([A-Za-z]+)(\\d*)");
    private static final Pattern SINGLE_LETTER_NAME = Pattern.compile("([a-z])_?\\d*");
    private static final Set<Character> OUTPUT_LETTERS = Set.of('f', 'g', 'h', 'o', 'q', 's', 'y', 'z');
    private static final List<String> OUTPUT_PREFIXES = List.of("out", "sal", "cout", "carry", "sum", "res");

    private TableImport() {
    }

    // --- text ---

    /**
     * Reads comma separated text; the separator is the one of {@code ,}, {@code ;} or a tab that appears most in the
     * first line with content (a comma when tied). Quotes as in RFC 4180 ({@code ""} inside a quoted cell is one
     * quote); CR, LF or CRLF line ends; a leading byte order mark is skipped.
     */
    public static ImportedGrid readCsv(String text) {
        String content = withoutByteOrderMark(text);
        return grid(records(content, separatorOf(content)));
    }

    /** Reads tab separated text, as copying cells from a spreadsheet leaves in the clipboard. */
    public static ImportedGrid readTabSeparated(String text) {
        return grid(records(withoutByteOrderMark(text), '\t'));
    }

    private static String withoutByteOrderMark(String text) {
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    private static char separatorOf(String text) {
        for (String line : text.split("\r\n|\r|\n")) {
            if (line.chars().noneMatch(Character::isLetterOrDigit)) {
                continue;
            }
            int commas = 0;
            int semicolons = 0;
            int tabs = 0;
            boolean quoted = false;
            for (char character : line.toCharArray()) {
                if (character == '"') {
                    quoted = !quoted;
                } else if (!quoted) {
                    switch (character) {
                        case ',' -> commas++;
                        case ';' -> semicolons++;
                        case '\t' -> tabs++;
                        default -> {
                        }
                    }
                }
            }
            if (semicolons > commas && semicolons >= tabs) {
                return ';';
            }
            return tabs > commas ? '\t' : ',';
        }
        return ',';
    }

    private static List<List<String>> records(String text, char separator) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean cellStarted = false;
        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        cell.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(character);
                }
            } else if (character == '"' && !cellStarted) {
                quoted = true;
                cellStarted = true;
            } else if (character == separator) {
                record.add(cell.toString());
                cell.setLength(0);
                cellStarted = false;
            } else if (character == '\r' || character == '\n') {
                if (character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
                record.add(cell.toString());
                records.add(record);
                record = new ArrayList<>();
                cell.setLength(0);
                cellStarted = false;
            } else {
                cell.append(character);
                cellStarted = true;
            }
            index++;
        }
        if (cellStarted || cell.length() > 0 || !record.isEmpty()) {
            record.add(cell.toString());
            records.add(record);
        }
        return records;
    }

    /** The grid of records numbered from 1: the first non-blank one is the header, trailing blank ones are dropped. */
    private static ImportedGrid grid(List<List<String>> records) {
        int first = 0;
        while (first < records.size() && isBlank(records.get(first))) {
            first++;
        }
        if (first == records.size()) {
            return new ImportedGrid(List.of(), List.of(), 0);
        }
        int last = records.size() - 1;
        while (isBlank(records.get(last))) {
            last--;
        }
        return new ImportedGrid(records.get(first), records.subList(first + 1, last + 1), first + 1);
    }

    private static boolean isBlank(List<String> record) {
        return record.stream().allMatch(String::isBlank);
    }

    // --- xlsx ---

    /**
     * Reads the first sheet of an {@code .xlsx} workbook: shared strings, inline strings, numbers (as written, e.g.
     * {@code 1} or {@code 1.0}), booleans ({@code TRUE}/{@code FALSE}) and formula results; missing cells are empty.
     * The XML is read without DTDs or external entities; a part with a DOCTYPE is refused.
     *
     * @throws TableImportException when the file is not a readable workbook or is too large
     * @throws IOException          when reading the stream fails
     */
    public static ImportedGrid readXlsx(InputStream input) throws IOException {
        Map<String, byte[]> parts = xlsxParts(input);
        try {
            String sheet = firstSheet(parts);
            if (sheet == null) {
                throw notASpreadsheet(null);
            }
            List<String> sharedStrings = parts.containsKey(SHARED_STRINGS)
                    ? sharedStrings(parts.get(SHARED_STRINGS)) : List.of();
            return grid(sheetRecords(parts.get(sheet), sharedStrings));
        } catch (XMLStreamException | RuntimeException exception) {
            throw notASpreadsheet(exception);
        }
    }

    /** Refuses a file of {@code bytes} bytes when it is larger than {@value #MAXIMUM_FILE_MEGABYTES} MB. */
    public static void checkSize(long bytes) throws TableImportException {
        if (bytes > MAXIMUM_FILE_MEGABYTES * 1024L * 1024L) {
            throw new TableImportException(ImportProblem.general(ImportProblem.TOO_LARGE, MAXIMUM_FILE_MEGABYTES),
                    null);
        }
    }

    private static Map<String, byte[]> xlsxParts(InputStream input) throws IOException {
        Map<String, byte[]> parts = new HashMap<>();
        long limit = MAXIMUM_FILE_MEGABYTES * 1024L * 1024L;
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().startsWith("/") ? entry.getName().substring(1) : entry.getName();
                // A part that is not read is still inflated to reach the next one: its bytes count too, so a zip
                // bomb in an image or a style sheet costs no more than one in the sheet.
                boolean wanted = !entry.isDirectory() && isWanted(name);
                ByteArrayOutputStream bytes = wanted ? new ByteArrayOutputStream() : null;
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    total += read;
                    if (total > limit) {
                        throw new TableImportException(
                                ImportProblem.general(ImportProblem.TOO_LARGE, MAXIMUM_FILE_MEGABYTES), null);
                    }
                    if (wanted) {
                        bytes.write(buffer, 0, read);
                    }
                }
                if (wanted) {
                    parts.put(name, bytes.toByteArray());
                }
            }
        } catch (ZipException exception) {
            throw notASpreadsheet(exception);
        }
        return parts;
    }

    private static boolean isWanted(String name) {
        return name.equals(WORKBOOK) || name.equals(WORKBOOK_RELATIONSHIPS) || name.equals(SHARED_STRINGS)
                || name.startsWith(WORKSHEETS) && name.endsWith(".xml") && name.indexOf('/', WORKSHEETS.length()) < 0;
    }

    private static TableImportException notASpreadsheet(Exception cause) {
        return new TableImportException(ImportProblem.general(ImportProblem.NOT_A_SPREADSHEET), cause);
    }

    /** The part name of the workbook's first sheet, through the relationships, else the lowest numbered sheet. */
    private static String firstSheet(Map<String, byte[]> parts) throws XMLStreamException {
        if (parts.containsKey(WORKBOOK) && parts.containsKey(WORKBOOK_RELATIONSHIPS)) {
            String relationship = firstSheetRelationship(parts.get(WORKBOOK));
            String target = relationship == null ? null
                    : relationshipTargets(parts.get(WORKBOOK_RELATIONSHIPS)).get(relationship);
            if (target != null) {
                String name = target.startsWith("/") ? target.substring(1) : "xl/" + target;
                if (parts.containsKey(name)) {
                    return name;
                }
            }
        }
        return parts.keySet().stream()
                .filter(name -> name.startsWith(WORKSHEETS))
                .min(Comparator.comparingInt(TableImport::numberInName).thenComparing(Comparator.naturalOrder()))
                .orElse(null);
    }

    private static int numberInName(String name) {
        Matcher matcher = NUMBER_IN_NAME.matcher(name);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : Integer.MAX_VALUE;
    }

    private static String firstSheetRelationship(byte[] workbook) throws XMLStreamException {
        XMLStreamReader reader = reader(workbook);
        try {
            while (reader.hasNext()) {
                if (next(reader) == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("sheet")) {
                    String identifier = reader.getAttributeValue(RELATIONSHIPS_NAMESPACE, "id");
                    return identifier != null ? identifier : attribute(reader, "id");
                }
            }
            return null;
        } finally {
            reader.close();
        }
    }

    private static Map<String, String> relationshipTargets(byte[] relationships) throws XMLStreamException {
        Map<String, String> targets = new HashMap<>();
        XMLStreamReader reader = reader(relationships);
        try {
            while (reader.hasNext()) {
                if (next(reader) == XMLStreamConstants.START_ELEMENT
                        && reader.getLocalName().equals("Relationship")) {
                    targets.put(attribute(reader, "Id"), attribute(reader, "Target"));
                }
            }
        } finally {
            reader.close();
        }
        return targets;
    }

    private static List<String> sharedStrings(byte[] part) throws XMLStreamException {
        List<String> strings = new ArrayList<>();
        XMLStreamReader reader = reader(part);
        try {
            while (reader.hasNext()) {
                if (next(reader) == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("si")) {
                    strings.add(richText(reader, "si"));
                }
            }
        } finally {
            reader.close();
        }
        return strings;
    }

    /**
     * The text of the {@code t} elements inside the current element (a shared string or an inline string), runs
     * joined; phonetic hints ({@code rPh}) left out. Ends on the element's end tag.
     */
    private static String richText(XMLStreamReader reader, String element) throws XMLStreamException {
        StringBuilder text = new StringBuilder();
        int phoneticDepth = 0;
        boolean inText = false;
        while (reader.hasNext()) {
            int event = next(reader);
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (reader.getLocalName().equals("rPh")) {
                    phoneticDepth++;
                } else if (reader.getLocalName().equals("t") && phoneticDepth == 0) {
                    inText = true;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String name = reader.getLocalName();
                if (name.equals(element)) {
                    break;
                } else if (name.equals("rPh")) {
                    phoneticDepth--;
                } else if (name.equals("t")) {
                    inText = false;
                }
            } else if (inText && isText(event)) {
                text.append(reader.getText());
            }
        }
        return text.toString();
    }

    private static List<List<String>> sheetRecords(byte[] part, List<String> sharedStrings)
            throws XMLStreamException {
        TreeMap<Integer, TreeMap<Integer, String>> cells = new TreeMap<>();
        XMLStreamReader reader = reader(part);
        try {
            int rowNumber = 0;
            int columnNumber = 0;
            while (reader.hasNext()) {
                if (next(reader) != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                String name = reader.getLocalName();
                if (name.equals("row")) {
                    String number = attribute(reader, "r");
                    rowNumber = within(number == null ? rowNumber + 1 : Integer.parseInt(number.strip()), MAXIMUM_ROW);
                    columnNumber = 0;
                } else if (name.equals("c")) {
                    String reference = attribute(reader, "r");
                    columnNumber = within(reference == null ? columnNumber + 1 : columnOf(reference), MAXIMUM_COLUMN);
                    String value = cellValue(reader, attribute(reader, "t"), sharedStrings);
                    if (!value.isEmpty()) {
                        cells.computeIfAbsent(rowNumber, ignored -> new TreeMap<>()).put(columnNumber, value);
                    }
                }
            }
        } finally {
            reader.close();
        }
        List<List<String>> records = new ArrayList<>();
        int lastRow = cells.isEmpty() ? 0 : cells.lastKey();
        for (int row = 1; row <= lastRow; row++) {
            TreeMap<Integer, String> rowCells = cells.get(row);
            if (rowCells == null) {
                records.add(List.of());
                continue;
            }
            String[] record = new String[rowCells.lastKey()];
            Arrays.fill(record, "");
            rowCells.forEach((column, value) -> record[column - 1] = value);
            records.add(Arrays.asList(record));
        }
        return records;
    }

    /** {@code number} when it is 1..{@code maximum}; otherwise the sheet is not a real one (it would not fit). */
    private static int within(int number, int maximum) {
        if (number < 1 || number > maximum) {
            throw new IllegalArgumentException("row or column out of range: " + number);
        }
        return number;
    }

    /** The text of the current {@code c} element, by its type; ends on the cell's end tag. */
    private static String cellValue(XMLStreamReader reader, String type, List<String> sharedStrings)
            throws XMLStreamException {
        String value = null;
        String inline = null;
        while (reader.hasNext()) {
            int event = next(reader);
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (reader.getLocalName().equals("v")) {
                    value = reader.getElementText();
                } else if (reader.getLocalName().equals("is")) {
                    inline = richText(reader, "is");
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && reader.getLocalName().equals("c")) {
                break;
            }
        }
        if (type == null) {
            return value == null ? "" : value;
        }
        return switch (type) {
            case "s" -> value == null ? "" : sharedStrings.get(Integer.parseInt(value.strip()));
            case "b" -> value == null ? "" : value.strip().equals("1") ? "TRUE" : "FALSE";
            case "inlineStr" -> inline != null ? inline : value == null ? "" : value;
            default -> value != null ? value : inline == null ? "" : inline;
        };
    }

    /** The 1-based column of a reference like {@code B2} or {@code AA10}. */
    private static int columnOf(String reference) {
        Matcher matcher = CELL_REFERENCE.matcher(reference.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("bad cell reference: " + reference);
        }
        int column = 0;
        for (char letter : matcher.group(1).toUpperCase(Locale.ROOT).toCharArray()) {
            column = column * 26 + letter - 'A' + 1;
            if (column > 16_384) {
                throw new IllegalArgumentException("column out of range: " + reference);
            }
        }
        return column;
    }

    private static XMLStreamReader reader(byte[] part) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try {
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        } catch (IllegalArgumentException unsupported) {
            // DTDs are already off; this property is an extra guard only some parsers know.
        }
        return factory.createXMLStreamReader(new ByteArrayInputStream(part));
    }

    /** The next event; a DOCTYPE is refused (workbooks never have one). */
    private static int next(XMLStreamReader reader) throws XMLStreamException {
        int event = reader.next();
        if (event == XMLStreamConstants.DTD) {
            throw new XMLStreamException("DOCTYPE not allowed");
        }
        return event;
    }

    private static boolean isText(int event) {
        return event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA
                || event == XMLStreamConstants.SPACE;
    }

    private static String attribute(XMLStreamReader reader, String localName) {
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            if (reader.getAttributeLocalName(index).equals(localName)) {
                return reader.getAttributeValue(index);
            }
        }
        return null;
    }

    // --- understanding the grid ---

    /**
     * Understands a grid as a truth table whose first {@code inputCount} columns are the inputs and the rest the
     * outputs. Columns with no name and no value are left out (keeping the other columns' numbers). Names are taken
     * without surrounding spaces. Inputs must be 0 or 1; outputs 0, 1 or X ({@code x}, {@code -} and empty also mean
     * X); {@code TRUE}/{@code FALSE}/{@code VERDADERO}/{@code FALSO}, {@code 0.0}/{@code 1.0} and, with a
     * decimal comma, {@code 0,0}/{@code 1,0} (in semicolon or tab separated text; with commas they are two cells) are
     * accepted. Rows may
     * come in any order; a combination written twice must have the same outputs; missing combinations become X.
     */
    public static ImportResult toSpec(ImportedGrid grid, int inputCount, String title) {
        int width = width(grid);
        if (width == 0) {
            return failed(List.of(ImportProblem.general(ImportProblem.EMPTY_FILE)), 0);
        }
        List<Integer> columns = columns(grid);
        int outputCount = columns.size() - inputCount;
        List<ImportProblem> problems = new ArrayList<>(countProblems(inputCount, outputCount));
        if (!problems.isEmpty()) {
            return failed(problems, 0);
        }

        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int column : columns) {
            String name = cell(grid.header(), column).strip();
            if (name.isEmpty()) {
                problems.add(new ImportProblem(ImportProblem.BLANK_NAME, List.of(), grid.headerRow(), column + 1));
            } else if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                problems.add(new ImportProblem(ImportProblem.REPEATED_NAME, List.of(name), grid.headerRow(),
                        column + 1));
            }
            names.add(name);
        }
        if (grid.rows().stream().allMatch(TableImport::isBlank)) {
            problems.add(ImportProblem.general(ImportProblem.NO_ROWS));
            return failed(problems, 0);
        }

        int rowCount = 1 << inputCount;
        LogicValue[][] values = new LogicValue[rowCount][];
        int[] firstRows = new int[rowCount];
        for (int index = 0; index < grid.rows().size(); index++) {
            List<String> row = grid.rows().get(index);
            int rowNumber = grid.headerRow() + 1 + index;
            if (!isBlank(row)) {
                readRow(row, rowNumber, columns, names, inputCount, width, values, firstRows, problems);
            }
        }

        int missingRows = 0;
        List<List<LogicValue>> outputs = new ArrayList<>();
        for (int output = 0; output < outputCount; output++) {
            outputs.add(new ArrayList<>());
        }
        for (int combination = 0; combination < rowCount; combination++) {
            if (values[combination] == null) {
                missingRows++;
            }
            for (int output = 0; output < outputCount; output++) {
                outputs.get(output).add(values[combination] == null
                        ? LogicValue.DONT_CARE : values[combination][output]);
            }
        }
        if (!problems.isEmpty()) {
            return failed(problems, missingRows);
        }
        TruthTableSpec spec = new TruthTableSpec(title, names.subList(0, inputCount),
                names.subList(inputCount, names.size()), outputs);
        return new ImportResult(Optional.of(spec), List.of(), missingRows);
    }

    private static List<ImportProblem> countProblems(int inputCount, int outputCount) {
        List<ImportProblem> problems = new ArrayList<>();
        if (inputCount < 1) {
            problems.add(ImportProblem.general(ImportProblem.NO_INPUTS));
        } else if (inputCount > TruthTableSpec.MAXIMUM_INPUTS) {
            problems.add(ImportProblem.general(ImportProblem.TOO_MANY_INPUTS, inputCount,
                    TruthTableSpec.MAXIMUM_INPUTS));
        }
        if (outputCount < 1) {
            problems.add(ImportProblem.general(ImportProblem.NO_OUTPUTS));
        } else if (outputCount > TruthTableSpec.MAXIMUM_OUTPUTS) {
            problems.add(ImportProblem.general(ImportProblem.TOO_MANY_OUTPUTS, outputCount,
                    TruthTableSpec.MAXIMUM_OUTPUTS));
        }
        return problems;
    }

    private static void readRow(List<String> row, int rowNumber, List<Integer> columns, List<String> names,
                                int inputCount, int width, LogicValue[][] values, int[] firstRows,
                                List<ImportProblem> problems) {
        int cellCount = row.size();
        while (cellCount > 0 && row.get(cellCount - 1).isBlank()) {
            cellCount--;
        }
        if (cellCount > width) {
            problems.add(new ImportProblem(ImportProblem.WRONG_CELL_COUNT, List.of(cellCount, width), rowNumber, 0));
            return;
        }
        int problemsBefore = problems.size();
        int combination = 0;
        for (int input = 0; input < inputCount; input++) {
            int column = columns.get(input);
            String text = cell(row, column).strip();
            LogicValue value = valueOf(text);
            if (text.isEmpty()) {
                problems.add(new ImportProblem(ImportProblem.EMPTY_INPUT, List.of(), rowNumber, column + 1));
            } else if (value == null || value == LogicValue.DONT_CARE) {
                problems.add(new ImportProblem(ImportProblem.INPUT_NOT_BINARY, List.of(text), rowNumber, column + 1));
            } else {
                combination = combination << 1 | (value == LogicValue.ONE ? 1 : 0);
            }
        }
        LogicValue[] outputs = new LogicValue[columns.size() - inputCount];
        for (int output = 0; output < outputs.length; output++) {
            int column = columns.get(inputCount + output);
            String text = cell(row, column).strip();
            outputs[output] = valueOf(text);
            if (outputs[output] == null) {
                problems.add(new ImportProblem(ImportProblem.INVALID_VALUE, List.of(text), rowNumber, column + 1));
            }
        }
        if (problems.size() > problemsBefore) {
            return;
        }
        if (values[combination] == null) {
            values[combination] = outputs;
            firstRows[combination] = rowNumber;
            return;
        }
        List<String> differing = new ArrayList<>();
        for (int output = 0; output < outputs.length; output++) {
            if (outputs[output] != values[combination][output]) {
                differing.add(names.get(inputCount + output));
            }
        }
        if (!differing.isEmpty()) {
            StringBuilder written = new StringBuilder();
            for (int input = inputCount - 1; input >= 0; input--) {
                written.append(written.isEmpty() ? "" : " ").append(combination >> input & 1);
            }
            problems.add(ImportProblem.general(ImportProblem.CONFLICTING_ROW, written.toString(),
                    List.copyOf(differing), differing.size(), firstRows[combination], rowNumber));
        }
    }

    private static ImportResult failed(List<ImportProblem> problems, int missingRows) {
        return new ImportResult(Optional.empty(), problems, missingRows);
    }

    /**
     * The columns of {@code grid} that make the table (0 = first), up to the last name written: those with a name or
     * a value. {@link #toSpec}'s input count counts these; a blank spacer column is not one of them.
     */
    public static List<Integer> columns(ImportedGrid grid) {
        List<Integer> columns = new ArrayList<>();
        int width = width(grid);
        for (int column = 0; column < width; column++) {
            if (!cell(grid.header(), column).isBlank() || columnHasValues(grid, column)) {
                columns.add(column);
            }
        }
        return columns;
    }

    /** The header up to its last name. */
    private static int width(ImportedGrid grid) {
        int width = grid.header().size();
        while (width > 0 && grid.header().get(width - 1).isBlank()) {
            width--;
        }
        return width;
    }

    private static boolean columnHasValues(ImportedGrid grid, int column) {
        return grid.rows().stream().anyMatch(row -> !cell(row, column).isBlank());
    }

    private static String cell(List<String> row, int column) {
        return column < row.size() ? row.get(column) : "";
    }

    /** The value a cell means, or null when it means none of 0, 1 or X. */
    public static LogicValue valueOf(String text) {
        String value = text.strip();
        if (value.isEmpty() || value.equalsIgnoreCase("x") || value.equals("-")) {
            return LogicValue.DONT_CARE;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.equals("TRUE") || upper.equals("VERDADERO")) {
            return LogicValue.ONE;
        }
        if (upper.equals("FALSE") || upper.equals("FALSO")) {
            return LogicValue.ZERO;
        }
        Matcher matcher = BINARY_NUMBER.matcher(value);
        if (matcher.matches()) {
            return matcher.group(1).equals("1") ? LogicValue.ONE : LogicValue.ZERO;
        }
        return null;
    }

    // --- proposal ---

    /**
     * How many of the leading columns probably are inputs: the names before the first one that looks like an output
     * (a single letter F, G, H, O, Q, S, Y or Z, maybe followed by digits, or a name starting with «out», «sal»,
     * «cout», «carry», «sum» or «res»); when none does, half of the columns. Always at least 1, and within the limits
     * of inputs and outputs when the column count allows it.
     */
    public static int proposedInputCount(List<String> header) {
        int columnCount = header.size();
        if (columnCount <= 1) {
            return 1;
        }
        int proposed = firstOutputLooking(header).orElse(columnCount / 2);
        return Math.clamp(proposed, smallestInputCount(columnCount), largestInputCount(columnCount));
    }

    /**
     * How many of the table's columns ({@link #columns}) probably are inputs, looking at the names and then at the
     * rows: the names before the first one that looks like an output (as {@link #proposedInputCount(List)}), unless
     * more than a quarter of the rows repeat a combination of those columns (a parity of A..J, where F looks like an
     * output); when no name does, or the names mislead, the fewest leading columns that hold only 0 and 1 and whose
     * combinations are all different across the rows (a complete table gives log2 of its rows; missing rows or any
     * order are fine); when none do, half of the columns. Within the limits of inputs and outputs when the column
     * count allows it.
     */
    public static int proposedInputCount(ImportedGrid grid) {
        List<Integer> columns = columns(grid);
        int columnCount = columns.size();
        if (columnCount <= 1) {
            return 1;
        }
        List<String> names = columns.stream().map(column -> cell(grid.header(), column).strip()).toList();
        int smallest = smallestInputCount(columnCount);
        int largest = largestInputCount(columnCount);
        Optional<Integer> byName = firstOutputLooking(names);
        List<List<String>> rows = grid.rows().stream().filter(row -> !isBlank(row)).toList();
        if (byName.isPresent()) {
            int named = Math.clamp(byName.get(), smallest, largest);
            if (repeatedCombinations(rows, columns.subList(0, named)) * 4 <= rows.size()) {
                return named;
            }
            // Far more rows than the named inputs can tell apart (inputs A..J, where F, G and H look like outputs):
            // the rows decide, when they can.
            return byRows(rows, columns, smallest, largest).orElse(named);
        }
        return byRows(rows, columns, smallest, largest).orElse(Math.clamp(columnCount / 2, smallest, largest));
    }

    /** The fewest leading columns, from {@code smallest} to {@code largest}, whose combinations are all different. */
    private static Optional<Integer> byRows(List<List<String>> rows, List<Integer> columns, int smallest,
                                            int largest) {
        for (int count = smallest; count <= largest && !rows.isEmpty(); count++) {
            if (areDistinctCombinations(rows, columns.subList(0, count))) {
                return Optional.of(count);
            }
        }
        return Optional.empty();
    }

    /** How many rows repeat the cells of an earlier row in {@code columns}. */
    private static int repeatedCombinations(List<List<String>> rows, List<Integer> columns) {
        Set<List<String>> seen = new HashSet<>();
        int repeated = 0;
        for (List<String> row : rows) {
            List<String> combination = new ArrayList<>();
            for (int column : columns) {
                combination.add(cell(row, column).strip());
            }
            if (!seen.add(combination)) {
                repeated++;
            }
        }
        return repeated;
    }

    /** Whether every row has only 0 or 1 in {@code columns} and no two rows have the same values there. */
    private static boolean areDistinctCombinations(List<List<String>> rows, List<Integer> columns) {
        Set<List<LogicValue>> seen = new HashSet<>();
        for (List<String> row : rows) {
            List<LogicValue> combination = new ArrayList<>();
            for (int column : columns) {
                LogicValue value = valueOf(cell(row, column));
                if (value != LogicValue.ZERO && value != LogicValue.ONE) {
                    return false;
                }
                combination.add(value);
            }
            if (!seen.add(combination)) {
                return false;
            }
        }
        return true;
    }

    /** The index of the first name after the first that looks like an output. */
    private static Optional<Integer> firstOutputLooking(List<String> names) {
        for (int index = 1; index < names.size(); index++) {
            if (looksLikeOutput(names.get(index))) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    private static int smallestInputCount(int columnCount) {
        return Math.max(1, columnCount - TruthTableSpec.MAXIMUM_OUTPUTS);
    }

    private static int largestInputCount(int columnCount) {
        return Math.max(smallestInputCount(columnCount), Math.min(TruthTableSpec.MAXIMUM_INPUTS, columnCount - 1));
    }

    private static boolean looksLikeOutput(String name) {
        String lower = name.strip().toLowerCase(Locale.ROOT);
        Matcher matcher = SINGLE_LETTER_NAME.matcher(lower);
        if (matcher.matches()) {
            return OUTPUT_LETTERS.contains(matcher.group(1).charAt(0));
        }
        return OUTPUT_PREFIXES.stream().anyMatch(lower::startsWith);
    }
}
