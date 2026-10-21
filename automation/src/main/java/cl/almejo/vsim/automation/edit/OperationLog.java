package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The receipts of one circuit's last applied edit calls, by the AI's operation id (A§6.3): a retry whose answer was
 * lost gets the same receipt instead of a second half adder, and the same id with other arguments is refused. Kept
 * for the last {@link #CAPACITY} calls while VSim runs; not a promise across restarts.
 */
final class OperationLog {

    /** How many calls are remembered per circuit (plan IA.1 Task 3). */
    static final int CAPACITY = 200;

    private record Entry(EditRequest request, EditReceipt receipt) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > CAPACITY;
        }
    };

    /**
     * The receipt of an earlier call with {@code request}'s operation id and the same arguments; empty when the id is
     * new. The same id with other arguments throws {@link AutomationError#OPERATION_CONFLICT}.
     */
    synchronized Optional<EditReceipt> replay(EditRequest request) {
        Entry entry = entries.get(request.operationId());
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.request().equals(request)) {
            throw new AutomationException(AutomationError.OPERATION_CONFLICT,
                    "The operation id " + request.operationId() + " was used for another edit; use a new id",
                    Map.of("operationId", request.operationId()));
        }
        return Optional.of(entry.receipt());
    }

    synchronized void record(EditRequest request, EditReceipt receipt) {
        entries.put(request.operationId(), new Entry(request, receipt));
    }
}
