package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LogisimPartMapperTest {

    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();
    private static final MappingContext CONTEXT = MappingContext.of("main", REGISTRY);

    private final LogisimPartMapper mapper = new LogisimPartMapper();

    @Test
    void anAndGateKeepsItsInputsWidthAndLabelWithLogisimsDelay() {
        MappedPart part = part(LogisimLibraries.GATES, "AND Gate", "inputs", "3", "width", "32", "label", "sel");

        assertThat(part.typeId()).isEqualTo("and");
        assertThat(part.parameters()).containsEntry("inputs", 3).containsEntry("width", 32)
                .containsEntry("negate", false).containsEntry("delay", 1);
        assertThat(part.label()).isEqualTo("sel");
        assertThat(part.rotation()).isEqualTo(Rotation.R0);
        assertThat(part.notes()).isEmpty();
    }

    @Test
    void aNorGateIsAnOrWithItsOutputNegated() {
        MappedPart part = part(LogisimLibraries.GATES, "NOR Gate", "inputs", "3");

        assertThat(part.typeId()).isEqualTo("or");
        assertThat(part.parameters()).containsEntry("inputs", 3).containsEntry("negate", true);
    }

    @Test
    void aNotGateIsABufferWithItsOutputNegated() {
        MappedPart part = part(LogisimLibraries.GATES, "NOT Gate", "size", "20");

        assertThat(part.typeId()).isEqualTo("buffer");
        assertThat(part.parameters()).containsEntry("negate", true).containsEntry("delay", 1);
    }

    @Test
    void anAndWithBothInputsNegatedIsANorWithTheSameDelay() {
        MappedPart part = part(LogisimLibraries.GATES, "AND Gate", "inputs", "2", "negate0", "true",
                "negate1", "true");

        assertThat(part.typeId()).isEqualTo("or");
        assertThat(part.parameters()).containsEntry("negate", true).containsEntry("delay", 1);
        assertThat(part.extras().notInFront()).isEmpty();
        assertThat(part.notes()).isEmpty();
    }

    @Test
    void anOrWithBothInputsNegatedIsANand() {
        MappedPart part = part(LogisimLibraries.GATES, "OR Gate", "inputs", "2", "negate0", "true",
                "negate1", "true");

        assertThat(part.typeId()).isEqualTo("and");
        assertThat(part.parameters()).containsEntry("negate", true);
    }

    @Test
    void oneNegatedInputAsksForANotInFrontAndIsNoted() {
        MappedPart part = part(LogisimLibraries.GATES, "OR Gate", "inputs", "2", "negate1", "true");

        assertThat(part.typeId()).isEqualTo("or");
        assertThat(part.parameters()).containsEntry("negate", false);
        assertThat(part.extras().notInFront()).containsExactly(1);
        assertThat(part.notes()).singleElement().satisfies(note -> {
            assertThat(note.kind()).isEqualTo(ImportNote.Kind.APPROXIMATED);
            assertThat(note.messageKey()).isEqualTo(LogisimNotes.NEGATED_INPUT);
            assertThat(note.where()).contains(new LogisimPoint(10, 20));
        });
    }

    @Test
    void aNegatedXorInputOnlyFlipsTheOutput() {
        MappedPart part = part(LogisimLibraries.GATES, "XOR Gate", "inputs", "2", "negate0", "true");

        assertThat(part.typeId()).isEqualTo("xor");
        assertThat(part.parameters()).containsEntry("negate", true);
        assertThat(part.extras().notInFront()).isEmpty();
    }

    @Test
    void aGateWithMoreInputsThanVsimHasIsUnsupported() {
        LogisimMapping mapping = map(LogisimLibraries.GATES, "AND Gate", "inputs", "9");

        assertThat(mapping).isInstanceOf(NotMapped.class);
        assertThat(mapping.notes()).singleElement().satisfies(note -> {
            assertThat(note.kind()).isEqualTo(ImportNote.Kind.UNSUPPORTED);
            assertThat(note.messageKey()).isEqualTo(LogisimNotes.TOO_MANY_INPUTS);
        });
    }

    @Test
    void aGateFacingNorthIsTurnedThreeQuarters() {
        assertThat(part(LogisimLibraries.GATES, "AND Gate", "facing", "north", "inputs", "2").rotation())
                .isEqualTo(Rotation.R270);
        assertThat(part(LogisimLibraries.GATES, "AND Gate", "facing", "south", "inputs", "2").rotation())
                .isEqualTo(Rotation.R90);
    }

    @Test
    void aControlledBufferIsATriState() {
        MappedPart part = part(LogisimLibraries.GATES, "Controlled Buffer", "width", "8", "control", "left");

        assertThat(part.typeId()).isEqualTo("tri-state");
        assertThat(part.parameters()).containsEntry("width", 8).containsEntry("negate", false)
                .containsEntry("delay", 1);
    }

    @Test
    void aMultiplexerTakesTwoToTheSelectInputsAndThreeOfDelay() {
        MappedPart part = part(LogisimLibraries.PLEXERS, "Multiplexer", "select", "3", "width", "32",
                "enable", "false");

        assertThat(part.typeId()).isEqualTo("mux");
        assertThat(part.parameters()).containsEntry("inputs", "8").containsEntry("width", 32)
                .containsEntry("delay", 3);
        assertThat(part.notes()).isEmpty();
    }

    @Test
    void aMultiplexersEnableInputIsNoted() {
        MappedPart part = part(LogisimLibraries.PLEXERS, "Multiplexer");

        assertThat(part.parameters()).containsEntry("inputs", "2").containsEntry("width", 1);
        assertThat(part.notes()).extracting(ImportNote::messageKey).containsExactly(LogisimNotes.ENABLE_INPUT);
    }

    @Test
    void aBitSelectorKeepsItsWidthAndGroup() {
        MappedPart part = part(LogisimLibraries.PLEXERS, "BitSelector", "width", "31", "group", "31");

        assertThat(part.typeId()).isEqualTo("bit-selector");
        assertThat(part.parameters()).containsEntry("inputWidth", 31).containsEntry("outputWidth", 31)
                .containsEntry("delay", 3);
    }

    @Test
    void theArithmeticPartsTakeLogisimsWidthDependentDelays() {
        assertThat(part(LogisimLibraries.ARITHMETIC, "Adder", "width", "32").parameters())
                .containsEntry("width", 32).containsEntry("delay", 34);
        assertThat(part(LogisimLibraries.ARITHMETIC, "Subtractor", "width", "3").parameters())
                .containsEntry("delay", 7);
        assertThat(part(LogisimLibraries.ARITHMETIC, "Negator", "width", "32").parameters())
                .containsEntry("delay", 34);
        assertThat(part(LogisimLibraries.ARITHMETIC, "Shifter", "width", "32").parameters())
                .containsEntry("delay", 96);
        assertThat(part(LogisimLibraries.ARITHMETIC, "Multiplier", "width", "32").parameters())
                .containsEntry("delay", 1088);
    }

    @Test
    void aComparatorIsSignedUnlessItsModeIsUnsigned() {
        assertThat(part(LogisimLibraries.ARITHMETIC, "Comparator", "width", "32").parameters())
                .containsEntry("signedness", "signed").containsEntry("delay", 34);
        assertThat(part(LogisimLibraries.ARITHMETIC, "Comparator", "mode", "unsigned").parameters())
                .containsEntry("signedness", "unsigned");
    }

    @Test
    void aThirtyTwoBitMultiplierIsSignedAndANarrowerOneUnsigned() {
        // Logisim multiplies Java ints: at 32 bits they carry the sign, below it the bits are positive
        assertThat(part(LogisimLibraries.ARITHMETIC, "Multiplier", "width", "32").parameters())
                .containsEntry("signedness", "signed");
        assertThat(part(LogisimLibraries.ARITHMETIC, "Multiplier", "width", "8").parameters())
                .containsEntry("signedness", "unsigned");
    }

    @Test
    void aDividerIsUnsignedAndNotesItsDivisionByZero() {
        MappedPart part = part(LogisimLibraries.ARITHMETIC, "Divider", "width", "32");

        assertThat(part.typeId()).isEqualTo("divider");
        assertThat(part.parameters()).containsEntry("signedness", "unsigned").containsEntry("delay", 1088);
        assertThat(part.notes()).extracting(ImportNote::messageKey).containsExactly(LogisimNotes.DIVISION_BY_ZERO);
    }

    @Test
    void aShifterMapsEachKindOfShift() {
        assertThat(part(LogisimLibraries.ARITHMETIC, "Shifter").parameters())
                .containsEntry("shiftType", "logical-left");
        assertThat(part(LogisimLibraries.ARITHMETIC, "Shifter", "shift", "lr").parameters())
                .containsEntry("shiftType", "logical-right");
        assertThat(part(LogisimLibraries.ARITHMETIC, "Shifter", "shift", "ar").parameters())
                .containsEntry("shiftType", "arithmetic-right");
        assertThat(part(LogisimLibraries.ARITHMETIC, "Shifter", "shift", "rl").parameters())
                .containsEntry("shiftType", "rotate-left");
        assertThat(part(LogisimLibraries.ARITHMETIC, "Shifter", "shift", "rr").parameters())
                .containsEntry("shiftType", "rotate-right");
    }

    @Test
    void aRegisterKeepsItsTriggerWidthAndLabel() {
        MappedPart high = part(LogisimLibraries.MEMORY, "Register", "trigger", "high", "width", "32", "label", "X");
        MappedPart rising = part(LogisimLibraries.MEMORY, "Register");

        assertThat(high.typeId()).isEqualTo("register");
        assertThat(high.parameters()).containsEntry("trigger", "high").containsEntry("width", 32)
                .containsEntry("delay", 8);
        assertThat(high.label()).isEqualTo("X");
        assertThat(rising.parameters()).containsEntry("trigger", "rising").containsEntry("width", 8);
    }

    @Test
    void aRamWritesOnTheRisingEdgeWithSeparateData() {
        MappedPart part = part(LogisimLibraries.MEMORY, "RAM", "addrWidth", "24", "dataWidth", "32",
                "bus", "separate");

        assertThat(part.typeId()).isEqualTo("ram");
        assertThat(part.parameters()).containsEntry("words", "16777216").containsEntry("width", 32)
                .containsEntry("data", "separate").containsEntry("write", "clock").containsEntry("edge", "rising")
                .containsEntry("delay", 10);
        assertThat(part.notes()).isEmpty();
    }

    @Test
    void aRamWithACombinedBusIsNoted() {
        MappedPart part = part(LogisimLibraries.MEMORY, "RAM");

        assertThat(part.parameters()).containsEntry("data", "shared").containsEntry("words", "256");
        assertThat(part.notes()).extracting(ImportNote::messageKey).containsExactly(LogisimNotes.MEMORY_BUS,
                LogisimNotes.RAM_WITHOUT_WRITE_ENABLE);
    }

    @Test
    void aRomKeepsItsProgramWithLogisimsRuns() {
        MappedPart part = part(LogisimLibraries.MEMORY, "ROM", "addrWidth", "24", "dataWidth", "32",
                "contents", "addr/data: 24 32\n16379*0 40056f 852583\n0 4*7\n");

        MemoryContents contents = MemoryContents.decode((String) part.parameters().get("contents"));
        assertThat(part.typeId()).isEqualTo("rom");
        assertThat(part.parameters()).containsEntry("words", "16777216").containsEntry("delay", 10);
        assertThat(contents.word(16378)).isZero();
        assertThat(contents.word(16379)).isEqualTo(0x40056fL);
        assertThat(contents.word(16380)).isEqualTo(0x852583L);
        assertThat(contents.word(16381)).isZero();
        assertThat(contents.word(16385)).isEqualTo(7L);
        assertThat(contents.count()).isEqualTo(6);
    }

    @Test
    void unreadableMemoryContentsAreAnInvalidNoteAndAnEmptyMemory() {
        MappedPart part = part(LogisimLibraries.MEMORY, "ROM", "contents", "addr/data: 8 8\nxyz\n");

        assertThat(MemoryContents.decode((String) part.parameters().get("contents")).count()).isZero();
        assertThat(part.notes()).singleElement().satisfies(note -> {
            assertThat(note.kind()).isEqualTo(ImportNote.Kind.INVALID);
            assertThat(note.messageKey()).isEqualTo(LogisimNotes.MEMORY_CONTENTS);
        });
    }

    @Test
    void aConstantKeepsItsValueAndWidth() {
        assertThat(part(LogisimLibraries.WIRING, "Constant", "width", "32", "value", "0xffffffff").parameters())
                .containsEntry("width", 32).containsEntry("value", "0xFFFFFFFF");
        assertThat(part(LogisimLibraries.WIRING, "Constant", "facing", "south", "width", "3", "value", "0x4"))
                .satisfies(part -> {
                    assertThat(part.parameters()).containsEntry("value", "0x4").containsEntry("width", 3);
                    assertThat(part.rotation()).isEqualTo(Rotation.R0);
                    assertThat(part.extras().upright()).isTrue();
                });
    }

    @Test
    void groundAndPowerAreConstants() {
        MappedPart ground = part(LogisimLibraries.WIRING, "Ground", "width", "4");
        MappedPart power = part(LogisimLibraries.WIRING, "Power", "width", "4");

        assertThat(ground.typeId()).isEqualTo("constant");
        assertThat(ground.parameters()).containsEntry("value", "0x0").containsEntry("width", 4);
        // upright like any constant, so the value reads across; the layout routes the wire to the pin
        assertThat(ground.rotation()).isEqualTo(Rotation.R0);
        assertThat(power.parameters()).containsEntry("value", "0xF");
        assertThat(power.rotation()).isEqualTo(Rotation.R0);
    }

    @Test
    void aClockGetsTheImportsHalfPeriodAndANote() {
        MappedPart part = part(LogisimLibraries.WIRING, "Clock", "labelloc", "east");

        assertThat(part.typeId()).isEqualTo("clock");
        assertThat(part.parameters()).containsEntry("time-up", LogisimTiming.HALF_PERIOD)
                .containsEntry("time-down", LogisimTiming.HALF_PERIOD);
        assertThat(part.notes()).extracting(ImportNote::messageKey).containsExactly(LogisimNotes.CLOCK_PERIOD);
    }

    @Test
    void aClockKeepsItsDutyCycle() {
        MappedPart part = part(LogisimLibraries.WIRING, "Clock", "highDuration", "2", "lowDuration", "3");

        assertThat(part.parameters()).containsEntry("time-up", 2 * LogisimTiming.HALF_PERIOD)
                .containsEntry("time-down", 3 * LogisimTiming.HALF_PERIOD);
    }

    @Test
    void aBitExtenderKeepsItsWidthsAndFillAndNotesItsMissingDelay() {
        MappedPart part = part(LogisimLibraries.WIRING, "Bit Extender", "in_width", "12", "out_width", "32",
                "type", "sign");

        assertThat(part.typeId()).isEqualTo("bit-extender");
        assertThat(part.parameters()).containsEntry("inputWidth", 12).containsEntry("outputWidth", 32)
                .containsEntry("fill", "sign");
        assertThat(part.notes()).extracting(ImportNote::messageKey).containsExactly(LogisimNotes.NO_DELAY);
        assertThat(part(LogisimLibraries.WIRING, "Bit Extender").parameters()).containsEntry("fill", "zeros");
    }

    @Test
    void anInputPinAndAnOutputPinKeepTheirWidthAndName() {
        MappedPart input = part(LogisimLibraries.WIRING, "Pin", "width", "32", "label", "Inst");
        MappedPart output = part(LogisimLibraries.WIRING, "Pin", "facing", "west", "output", "true",
                "label", "Q");

        assertThat(input.typeId()).isEqualTo("input-pin");
        assertThat(input.parameters()).containsEntry("width", 32).containsEntry("value", "0");
        assertThat(input.label()).isEqualTo("Inst");
        assertThat(input.extras().needsName()).isFalse();
        assertThat(output.typeId()).isEqualTo("output-pin");
        assertThat(output.rotation()).isEqualTo(Rotation.R0);
        assertThat(output.label()).isEqualTo("Q");
    }

    @Test
    void aPinWithoutNameAsksForOne() {
        MappedPart pin = part(LogisimLibraries.WIRING, "Pin");

        assertThat(pin.label()).isEmpty();
        assertThat(pin.extras().needsName()).isTrue();
    }

    @Test
    void everyTunnelKeepsItsLabelAndIsATunnelWithoutDirection() {
        LogisimComponent east = component(LogisimLibraries.WIRING, "Tunnel", "label", "D31-D0", "width", "32",
                "facing", "east");
        LogisimComponent west = component(LogisimLibraries.WIRING, "Tunnel", "label", "D31-D0", "width", "32");

        MappedPart eastPart = (MappedPart) mapper.map(east, CONTEXT);
        MappedPart westPart = (MappedPart) mapper.map(west, CONTEXT);

        for (MappedPart part : List.of(eastPart, westPart)) {
            assertThat(part.typeId()).isEqualTo(TunnelKind.ENTRANCE.typeId());
            assertThat(part.parameters()).containsEntry(Tunnels.NAME, "D31-D0")
                    .containsEntry(Tunnels.DIRECTION, Tunnels.UNDIRECTED);
            assertThat(part.extras().tunnelLabel()).contains("D31-D0");
            assertThat(part.notes()).isEmpty();
        }
        assertThat(westPart.rotation()).isEqualTo(Rotation.R0);
        assertThat(eastPart.rotation()).isEqualTo(Rotation.R180);
    }

    @Test
    void aTunnelWithoutLabelJoinsNothingAndIsLeftOutQuietly() {
        LogisimMapping mapping = map(LogisimLibraries.WIRING, "Tunnel");

        assertThat(mapping).isInstanceOf(NotMapped.class);
        assertThat(mapping.notes()).isEmpty();
    }

    @Test
    void aSplitterWithTwoRunsInOneEndIsOneGroupedPin() {
        Map<String, String> attributes = new LinkedHashMap<>(Map.of("fanout", "1", "incoming", "32"));
        for (int bit = 0; bit < 32; bit++) {
            attributes.put("bit" + bit, bit <= 6 || bit >= 25 ? "0" : "none");
        }

        MappedPart part = (MappedPart) mapper.map(componentWith(LogisimLibraries.WIRING, "Splitter", attributes),
                CONTEXT);

        assertThat(part.typeId()).isEqualTo("bus-splitter");
        assertThat(part.parameters()).containsEntry("width", 32).containsEntry("groups", "0-6,25-31");
        assertThat(part.extras().splitterEnds()).containsExactly(Map.entry(0, 0));
    }

    @Test
    void anEmptySplitterEndHasNoPin() {
        // fanout 3, 6 bits: end 0 = bits 0-2, end 1 = nothing, end 2 = bits 3-5
        MappedPart part = part(LogisimLibraries.WIRING, "Splitter", "fanout", "3", "incoming", "6",
                "bit0", "0", "bit1", "0", "bit2", "0", "bit3", "2", "bit4", "2", "bit5", "2");

        assertThat(part.parameters()).containsEntry("groups", "0-2|3-5");
        assertThat(part.extras().splitterEnds()).containsExactly(Map.entry(0, 0), Map.entry(2, 1));
    }

    @Test
    void aSplitterOfOneBitPerEndInBusOrderStoresTheCanonicalEmptyGroups() {
        MappedPart part = part(LogisimLibraries.WIRING, "Splitter", "fanout", "4", "incoming", "4");

        assertThat(part.parameters()).containsEntry("groups", "");
    }

    @Test
    void aSplitterInTheDefaultEvenSplitOutOfOrder() {
        // Logisim's own defaults for fanout 4 and 8 bits: bits 0..3 to ends 0..3, the even split's 4-5 and 6-7 to 2, 3
        MappedPart part = part(LogisimLibraries.WIRING, "Splitter", "fanout", "4", "incoming", "8");

        assertThat(part.parameters()).containsEntry("groups", "0|1|2,4-5|3,6-7");
    }

    @Test
    void aJoiningSplitterIsAJoinerFacingTheOtherWay() {
        LogisimComponent splitter = component(LogisimLibraries.WIRING, "Splitter", "facing", "west",
                "fanout", "2", "incoming", "8");

        MappedPart part = (MappedPart) mapper.map(splitter, CONTEXT.withSplitterDirection(SplitterDirection.JOIN));

        assertThat(part.typeId()).isEqualTo("bus-joiner");
        assertThat(part.rotation()).isEqualTo(Rotation.R0);
        assertThat(part.parameters()).containsEntry("groups", "0,2-3|1,4-7");
    }

    @Test
    void aOneBitSplitterIsUnsupported() {
        LogisimMapping mapping = map(LogisimLibraries.WIRING, "Splitter", "fanout", "1", "incoming", "1");

        assertThat(mapping).isInstanceOf(NotMapped.class);
        assertThat(mapping.notes()).extracting(ImportNote::messageKey).containsExactly(LogisimNotes.NARROW_SPLITTER);
    }

    @Test
    void aProbeIsATextWithItsLabel() {
        LogisimMapping mapping = map(LogisimLibraries.WIRING, "Probe", "radix", "16", "label", "pc");

        assertThat(mapping).isInstanceOfSatisfying(MappedText.class, text -> {
            assertThat(text.text()).isEqualTo("pc");
            assertThat(text.probe()).isTrue();
            assertThat(text.font()).isEqualTo(LogisimFont.DEFAULT);
        });
    }

    @Test
    void aTextKeepsItsFont() {
        LogisimMapping mapping = map(LogisimLibraries.BASE, "Text", "text", "Fetch", "font",
                "SansSerif bold 14");

        assertThat(mapping).isInstanceOfSatisfying(MappedText.class, text -> {
            assertThat(text.text()).isEqualTo("Fetch");
            assertThat(text.font()).isEqualTo(new LogisimFont("SansSerif", true, false, 14));
            assertThat(text.probe()).isFalse();
        });
    }

    @Test
    void aSubcircuitInstanceIsNamedAfterItsCircuit() {
        LogisimMapping mapping = mapper.map(component(LogisimLibraries.SUBCIRCUIT, "Muldiv"), CONTEXT);

        assertThat(mapping).isInstanceOfSatisfying(MappedSubcircuit.class, subcircuit -> {
            assertThat(subcircuit.circuit()).isEqualTo("Muldiv");
            assertThat(subcircuit.facing()).isEqualTo("east");
        });
    }

    @Test
    void theTtyIsATerminalWithTheLogisimProfile() {
        MappedPart part = part(LogisimLibraries.INPUT_OUTPUT, "TTY", "rows", "24", "cols", "80",
                "trigger", "falling");

        assertThat(part.typeId()).isEqualTo("terminal");
        assertThat(part.parameters()).containsEntry("profile", "logisim").containsEntry("mode", "logisim")
                .containsEntry("data-width", "7").containsEntry("rows", 24).containsEntry("columns", 80)
                .containsEntry("edge", "falling");
    }

    @Test
    void theKeyboardTakesSevenBitsAndItsEdge() {
        MappedPart part = part(LogisimLibraries.INPUT_OUTPUT, "Keyboard", "trigger", "falling");

        assertThat(part.typeId()).isEqualTo("keyboard");
        assertThat(part.parameters()).containsEntry("data-width", "7").containsEntry("edge", "falling")
                .containsEntry("capacity", 32);
    }

    @Test
    void aLedTakesTheNearestColourAndPointsItsInputTheSameWay() {
        MappedPart part = part(LogisimLibraries.INPUT_OUTPUT, "LED", "facing", "south");

        assertThat(part.typeId()).isEqualTo("led");
        assertThat(part.parameters()).containsEntry("color", "red").containsEntry("width", 1);
        assertThat(part.rotation()).isEqualTo(Rotation.R0);
        assertThat(part(LogisimLibraries.INPUT_OUTPUT, "LED", "color", "#00c000").parameters())
                .containsEntry("color", "green");
    }

    @Test
    void theFlipFlopsMapToVsimsAndANonFallingTriggerIsNoted() {
        MappedPart data = part(LogisimLibraries.MEMORY, "D Flip-Flop", "trigger", "falling");
        MappedPart toggle = part(LogisimLibraries.MEMORY, "T Flip-Flop");

        assertThat(data.typeId()).isEqualTo("flip-flop-data");
        assertThat(data.parameters()).containsEntry("delay", 5);
        assertThat(data.notes()).isEmpty();
        assertThat(toggle.typeId()).isEqualTo("flip-flop-t");
        assertThat(toggle.notes()).extracting(ImportNote::messageKey).containsExactly(LogisimNotes.EDGE);
        assertThat(part(LogisimLibraries.MEMORY, "J-K Flip-Flop", "trigger", "falling").typeId())
                .isEqualTo("flip-flop-jk");
        assertThat(part(LogisimLibraries.MEMORY, "S-R Flip-Flop", "trigger", "falling").typeId())
                .isEqualTo("flip-flop-sr");
    }

    @Test
    void theOtherPlexersMapByTheirSelectBits() {
        assertThat(part(LogisimLibraries.PLEXERS, "Decoder", "select", "3").parameters())
                .containsEntry("inputBits", 3).containsEntry("delay", 3);
        assertThat(part(LogisimLibraries.PLEXERS, "Demultiplexer", "select", "2", "width", "8").parameters())
                .containsEntry("outputs", "4").containsEntry("width", 8);
        assertThat(part(LogisimLibraries.PLEXERS, "Priority Encoder", "select", "2").parameters())
                .containsEntry("inputs", "4");
    }

    @Test
    void theRestOfTheGenericPartsMap() {
        assertThat(part(LogisimLibraries.MEMORY, "Counter", "width", "4", "trigger", "falling"))
                .satisfies(counter -> {
                    assertThat(counter.typeId()).isEqualTo("counter");
                    assertThat(counter.parameters()).containsEntry("width", 4).containsEntry("delay", 8);
                    assertThat(counter.notes()).isEmpty();
                });
        assertThat(part(LogisimLibraries.ARITHMETIC, "BitAdder", "width", "8").typeId()).isEqualTo("bit-counter");
        assertThat(part(LogisimLibraries.INPUT_OUTPUT, "Button").typeId()).isEqualTo("push-button");
        assertThat(part(LogisimLibraries.INPUT_OUTPUT, "Hex Digit Display").typeId())
                .isEqualTo("seven-segments-display");
        assertThat(part(LogisimLibraries.GATES, "XNOR Gate", "inputs", "2").parameters())
                .containsEntry("negate", true);
        assertThat(part(LogisimLibraries.GATES, "Even Parity", "inputs", "3").typeId()).isEqualTo("xor");
        assertThat(part(LogisimLibraries.GATES, "Controlled Inverter").parameters()).containsEntry("negate", true);
    }

    @Test
    void everyBuiltInPartMapsToAPartTheRegistryAcceptsOrIsNotedAsUnsupported() throws Exception {
        LogisimCircuit main = LogisimReaderTest.read("every-part.circ").circuit("main").orElseThrow();
        Set<String> withoutEquivalent = Set.of("Pull Resistor", "Transistor", "Transmission Gate", "BitFinder",
                "Shift Register", "Random", "Joystick", "7-Segment Display", "DotMatrix");

        for (LogisimComponent component : main.components()) {
            LogisimMapping mapping = mapper.map(component, CONTEXT);
            if (withoutEquivalent.contains(component.name())) {
                assertThat(mapping).as(component.name()).isInstanceOf(NotMapped.class);
                assertThat(mapping.notes()).as(component.name()).singleElement()
                        .extracting(ImportNote::kind).isEqualTo(ImportNote.Kind.UNSUPPORTED);
            } else if (mapping instanceof MappedPart part) {
                ComponentType type = REGISTRY.require(part.typeId());
                assertThat(type.resolveParameters(part.parameters())).as(component.name())
                        .isEqualTo(part.parameters());
            } else if (component.name().equals("Tunnel")) {
                // the fixture's tunnel has no label: it joins nothing, in Logisim too
                assertThat(mapping).isEqualTo(new NotMapped(List.of()));
            } else {
                assertThat(mapping).as(component.name()).isNotInstanceOf(NotMapped.class);
            }
        }
    }

    @Test
    void anUnknownPartIsUnsupportedAndAnUnreadableValueIsInvalid() {
        LogisimMapping unknown = mapper.map(new LogisimComponent("#Fancy", "Sparkle", new LogisimPoint(1, 2),
                LogisimAttributes.of(Map.of())), CONTEXT);
        LogisimMapping unreadable = map(LogisimLibraries.ARITHMETIC, "Adder", "width", "wide");

        assertThat(unknown.notes()).singleElement().satisfies(note -> {
            assertThat(note.kind()).isEqualTo(ImportNote.Kind.UNSUPPORTED);
            assertThat(note.messageKey()).isEqualTo(LogisimNotes.UNSUPPORTED_PART);
            assertThat(note.arguments()).containsExactly("Sparkle");
        });
        assertThat(unreadable).isInstanceOf(NotMapped.class);
        assertThat(unreadable.notes()).singleElement().satisfies(note -> {
            assertThat(note.kind()).isEqualTo(ImportNote.Kind.INVALID);
            assertThat(note.messageKey()).isEqualTo(LogisimNotes.INVALID_PART);
        });
    }

    @Test
    void everyNoteKeyIsListed() {
        assertThat(LogisimNotes.MESSAGE_KEYS).contains(LogisimNotes.NEGATED_INPUT, LogisimNotes.CLOCK_PERIOD,
                LogisimNotes.UNSUPPORTED_PART, LogisimNotes.UNKNOWN_LIBRARY).doesNotHaveDuplicates();
    }

    private MappedPart part(String library, String name, String... namesAndValues) {
        LogisimMapping mapping = map(library, name, namesAndValues);
        assertThat(mapping).as(name + " " + mapping.notes()).isInstanceOf(MappedPart.class);
        return (MappedPart) mapping;
    }

    private LogisimMapping map(String library, String name, String... namesAndValues) {
        return mapper.map(component(library, name, namesAndValues), CONTEXT);
    }

    /** A component at (10, 20) with Logisim's defaults for what {@code namesAndValues} leaves out. */
    private static LogisimComponent component(String library, String name, String... namesAndValues) {
        Map<String, String> written = new LinkedHashMap<>();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            written.put(namesAndValues[index], namesAndValues[index + 1]);
        }
        return componentWith(library, name, written);
    }

    private static LogisimComponent componentWith(String library, String name, Map<String, String> written) {
        return new LogisimComponent(library, name, new LogisimPoint(10, 20),
                LogisimDefaults.logisim27().resolve(library, name, written, LogisimVersion.LATEST));
    }
}
