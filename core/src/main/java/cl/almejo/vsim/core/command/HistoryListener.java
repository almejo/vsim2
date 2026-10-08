package cl.almejo.vsim.core.command;

@FunctionalInterface
public interface HistoryListener {
    void historyChanged(History history);
}
