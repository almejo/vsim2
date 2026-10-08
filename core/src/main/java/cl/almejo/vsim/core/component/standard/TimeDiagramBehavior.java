package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.TimeDiagramSample;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Samples its inputs periodically (VSim plotted every 150 units). */
final class TimeDiagramBehavior implements Behavior {

    private final int pinCount;
    private final int sampleInterval;
    private final int history;
    private final Deque<TimeDiagramSample> samples = new ArrayDeque<>();

    TimeDiagramBehavior(int pinCount, int sampleInterval, int history) {
        this.pinCount = pinCount;
        this.sampleInterval = sampleInterval;
        this.history = history;
    }

    @Override
    public void initialize(BehaviorContext context) {
        sample(context);
    }

    private void sample(BehaviorContext context) {
        List<Value> values = new ArrayList<>(pinCount);
        for (int index = 0; index < pinCount; index++) {
            values.add(context.input(index));
        }
        samples.addLast(new TimeDiagramSample(context.now(), values));
        while (samples.size() > history) {
            samples.removeFirst();
        }
        context.schedule(sampleInterval, () -> sample(context));
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
    }

    @Override
    public VisibleState visibleState() {
        return new VisibleState.TimeDiagramState(List.copyOf(samples));
    }
}
