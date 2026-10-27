package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.VisibleState.MemoryAccess;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.memory.MutableMemoryContents;
import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * A RAM or a ROM while simulating (memory spec §2). Reading is combinational: Q (or a shared D) shows the word at A
 * {@code delay} after A changes. A RAM writes D at A on the clock's falling edge, A, D, WE and CS sampled at the edge,
 * or, writing «sin reloj», all the time WE = 1. Only with CS = 1 (unconnected counts as 1) and WE = 1 (unconnected
 * counts as 0); a conflict on either, or an address with a Z bit, writes nothing and reads all Z; an undefined D
 * stores an undefined word, which reads back all Z.
 *
 * <p>With «un solo bus D» the memory drives D only while CS = 1 and WE = 0, and lets go of it at once (no delay)
 * otherwise, cancelling a read still on its way, so a student's driver can take the bus to write.
 *
 * <p>Belongs to the simulation thread. Other threads see it through {@link #visibleState()} (immutable, built only
 * when something changed) and copies made on that thread ({@link #liveContents()}).
 */
public final class MemoryBehavior implements Behavior {

    /** How many words the content window shows. */
    static final int WINDOW_WORDS = 5;

    private final MemoryPinout pinout;
    private final long words;
    private final int delay;
    /** The initial contents, cut to what this memory addresses (the parameter keeps the rest). */
    private final MemoryContents initial;
    private final MemoryAccessLog accessLog;
    private MutableMemoryContents live;
    /** Addresses whose word was written undefined; they read all Z until written again. */
    private final Set<Long> undefinedWords = new HashSet<>();

    private Value previousClock = Value.HIGH_IMPEDANCE;
    /** The address of the last counted read, or -1 when the next defined read counts whatever its address. */
    private long lastReadAddress = -1;
    /** The last write «sin reloj»: address and value, so a level write is counted once while nothing changes. */
    private long lastLevelWriteAddress = -1;
    /** The word that last write stored (all Z for an undefined one). */
    private Value lastLevelWrite = Value.HIGH_IMPEDANCE;
    private boolean enabled;
    private MemoryAccess lastAccess = MemoryAccess.NONE;
    private long lastAddress = -1;
    /** What the outputs (Q, or a shared D) were last told to show; null before the first time. */
    private Value requestedOutput;
    /** Bumped on each shared-D request, so a read still on its way never drives after a release. */
    private long dataGeneration;
    private VisibleState.MemoryState visibleState;

    MemoryBehavior(MemoryPinout pinout, long words, int delay, MemoryContents contents) {
        this.pinout = pinout;
        this.words = words;
        this.delay = delay;
        this.initial = fitted(contents, words, pinout.width());
        this.accessLog = new MemoryAccessLog(words);
        this.live = new MutableMemoryContents(initial);
    }

    /** {@code contents} without the words past the last address and the bits past the width. */
    private static MemoryContents fitted(MemoryContents contents, long words, int width) {
        long mask = Value.maskFor(width);
        if (contents.highestAddress() < words && contents.widestBits() <= width) {
            return contents;
        }
        MutableMemoryContents fitted = new MutableMemoryContents();
        contents.forEachNonZero((address, value) -> {
            if (address < words) {
                fitted.set(address, value & mask);
            }
        });
        return fitted.snapshot();
    }

    /** The reads and writes so far, for the memory map. */
    public MemoryAccessLog accessLog() {
        return accessLog;
    }

    /** The words now (writes included; an undefined word as 0), as immutable contents sharing unchanged pages. */
    public MemoryContents liveContents() {
        return live.snapshot();
    }

    /**
     * The word at {@code address} now (an undefined word, or one past the last address, as 0), for the memory map:
     * one lookup, no copy. Simulation thread only.
     */
    public long liveWord(long address) {
        return address < 0 || address >= words ? 0 : live.word(address);
    }

    @Override
    public void initialize(BehaviorContext context) {
        if (pinout.clocked()) {
            previousClock = context.input(pinout.clock());
        }
        refresh(context, true);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        boolean fallingEdge = false;
        if (pinIndex == pinout.clock()) {
            Value clock = context.input(pinIndex);
            fallingEdge = previousClock.equals(Value.ONE) && clock.equals(Value.ZERO);
            previousClock = clock;
        }
        boolean wrote = pinout.isRam() && writeIfDue(context, fallingEdge);
        // While writing «sin reloj» every evaluation is part of the write, never a read.
        boolean levelWriting = pinout.isRam() && !pinout.clocked() && writing(context);
        refresh(context, !wrote && !levelWriting);
    }

    @Override
    public void restoreInitialState(BehaviorContext context) {
        live = new MutableMemoryContents(initial);
        undefinedWords.clear();
        lastReadAddress = -1;
        lastLevelWriteAddress = -1;
        lastAccess = MemoryAccess.NONE;
        lastAddress = -1;
        visibleState = null;
        refresh(context, false);
    }

    /** Writes D at A when this is the moment to (the falling edge, or WE = 1 «sin reloj»); true when it wrote. */
    private boolean writeIfDue(BehaviorContext context, boolean fallingEdge) {
        if (!writing(context)) {
            lastLevelWriteAddress = -1;
            return false;
        }
        if (pinout.clocked() ? !fallingEdge : sharedDataStillDriven()) {
            return false;
        }
        OptionalLong address = address(context);
        if (address.isEmpty()) {
            return false;
        }
        Value input = word(context, pinout.firstData());
        Value data = input.isFullyDefined() ? input : Value.highImpedance(pinout.width());
        if (!pinout.clocked() && address.getAsLong() == lastLevelWriteAddress && data.equals(lastLevelWrite)) {
            return false;
        }
        store(context, address.getAsLong(), data);
        return true;
    }

    /** CS = 1 and WE = 1, both resolved by the block rules. */
    private boolean writing(BehaviorContext context) {
        return selected(context) && BlockSignals.control(context.input(pinout.writeEnable()), false).orElse(false);
    }

    private boolean selected(BehaviorContext context) {
        return BlockSignals.control(context.input(pinout.chipSelect()), true).orElse(false);
    }

    /** A shared D the memory has not let go of yet: what the net shows is partly the memory's own read. */
    private boolean sharedDataStillDriven() {
        return pinout.shared() && requestedOutput != null && !requestedOutput.isFullyHighImpedance();
    }

    /** Stores {@code data} (fully defined, or all Z for an undefined word) at {@code address}. */
    private void store(BehaviorContext context, long address, Value data) {
        if (data.isFullyDefined()) {
            live.set(address, data.bits());
            undefinedWords.remove(address);
        } else {
            live.set(address, 0);
            undefinedWords.add(address);
        }
        lastLevelWriteAddress = pinout.clocked() ? -1 : address;
        lastLevelWrite = data;
        accessLog.write(address, context.now());
        lastAccess = MemoryAccess.WRITE;
        lastAddress = address;
        lastReadAddress = -1;
        visibleState = null;
    }

    /**
     * Puts on the outputs what they must show now and counts a read when {@code countRead} and it is one (spec §2:
     * a new address, or the first read after a write or after CS came back).
     */
    private void refresh(BehaviorContext context, boolean countRead) {
        boolean nowEnabled = selected(context);
        if (nowEnabled != enabled) {
            enabled = nowEnabled;
            visibleState = null;
        }
        if (!nowEnabled) {
            lastReadAddress = -1;
            release(context);
            return;
        }
        OptionalLong address = address(context);
        if (address.isEmpty() || pinout.shared() && !readingOnSharedData(context)) {
            release(context);
            return;
        }
        long at = address.getAsLong();
        drive(context, undefinedWords.contains(at) ? Value.highImpedance(pinout.width())
                : Value.of(pinout.width(), live.word(at)));
        if (countRead && at != lastReadAddress) {
            accessLog.read(at, context.now());
            lastReadAddress = at;
            lastAccess = MemoryAccess.READ;
            lastAddress = at;
            visibleState = null;
        }
    }

    /** A shared D is driven only while WE is surely 0 (unconnected counts as 0; a conflict lets go of the bus). */
    private boolean readingOnSharedData(BehaviorContext context) {
        Optional<Boolean> writeEnable = BlockSignals.control(context.input(pinout.writeEnable()), false);
        return writeEnable.isPresent() && !writeEnable.get();
    }

    private void release(BehaviorContext context) {
        Value released = Value.highImpedance(pinout.width());
        if (pinout.shared()) {
            if (requestedOutput != null && requestedOutput.equals(released)) {
                return;
            }
            requestedOutput = released;
            dataGeneration++;
            driveWord(context, pinout.firstData(), released, 0);
            return;
        }
        drive(context, released);
    }

    /** Shows {@code value} on the data out ({@code delay} later), unless that is already what it was told. */
    private void drive(BehaviorContext context, Value value) {
        if (value.equals(requestedOutput)) {
            return;
        }
        requestedOutput = value;
        if (!pinout.shared()) {
            driveWord(context, pinout.firstOutput(), value, delay);
            return;
        }
        long generation = ++dataGeneration;
        context.schedule(delay, () -> {
            if (generation == dataGeneration) {
                driveWord(context, pinout.firstData(), value, 0);
            }
        });
    }

    /** Drives a word on its bus pin, or bit by bit on loose pins from {@code firstPin}. */
    private void driveWord(BehaviorContext context, int firstPin, Value value, long driveDelay) {
        if (!pinout.loose()) {
            context.drive(firstPin, value, driveDelay);
            return;
        }
        for (int bit = 0; bit < pinout.width(); bit++) {
            Value bitValue = (value.highImpedanceMask() >>> bit & 1L) != 0 ? Value.HIGH_IMPEDANCE
                    : Value.of((value.bits() >>> bit & 1L) != 0);
            context.drive(firstPin + bit, bitValue, driveDelay);
        }
    }

    /** The address on A, empty unless every bit is 0 or 1. */
    private OptionalLong address(BehaviorContext context) {
        Value address = word(context, pinout.firstAddress());
        return address.isFullyDefined() ? OptionalLong.of(address.bits()) : OptionalLong.empty();
    }

    /**
     * The word on a bus pin, or gathered from the loose pins from {@code firstPin} (one bit each). With loose pins only
     * whether it is fully defined and its bits matter: any Z or conflict makes it undefined.
     */
    private Value word(BehaviorContext context, int firstPin) {
        int bits = firstPin == pinout.firstAddress() ? pinout.addressBits() : pinout.width();
        if (!pinout.loose()) {
            return context.input(firstPin);
        }
        long value = 0;
        for (int bit = 0; bit < bits; bit++) {
            Value input = context.input(firstPin + bit);
            if (!input.isFullyDefined()) {
                return Value.highImpedance(bits);
            }
            value |= input.bits() << bit;
        }
        return Value.of(bits, value);
    }

    @Override
    public VisibleState visibleState() {
        VisibleState.MemoryState state = visibleState;
        if (state == null) {
            state = buildVisibleState();
            visibleState = state;
        }
        return state;
    }

    private VisibleState.MemoryState buildVisibleState() {
        int shown = (int) Math.min(WINDOW_WORDS, words);
        long center = Math.max(0, lastAddress);
        long first = Math.max(0, Math.min(center - WINDOW_WORDS / 2, words - shown));
        List<Long> window = new ArrayList<>(shown);
        long undefinedRows = 0;
        for (int offset = 0; offset < shown; offset++) {
            window.add(live.word(first + offset));
            if (undefinedWords.contains(first + offset)) {
                undefinedRows |= 1L << offset;
            }
        }
        return new VisibleState.MemoryState(enabled, lastAccess, lastAddress, first, window, undefinedRows);
    }
}
