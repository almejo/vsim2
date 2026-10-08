package cl.almejo.vsim.core.netlist;

public record NetId(String value) {
    @Override
    public String toString() {
        return value;
    }
}
