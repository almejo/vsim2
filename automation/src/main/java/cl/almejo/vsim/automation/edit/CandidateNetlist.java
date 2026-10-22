package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;

/**
 * The netlist of the candidate while an edit is planned, compiled again only when the candidate changed since (its
 * {@link CircuitDocument#revision()} moves with every element change, reverts included): a connect reuses the netlist
 * the operation before it left instead of compiling the same circuit again (review M8). One planning call only.
 */
final class CandidateNetlist {

    private final NetlistCompiler compiler;
    private CircuitDocument compiledDocument;
    private long compiledRevision;
    private Netlist netlist;

    CandidateNetlist(ComponentTypeRegistry registry) {
        this.compiler = new NetlistCompiler(registry);
    }

    Netlist of(CircuitDocument document) {
        if (netlist == null || document != compiledDocument || document.revision() != compiledRevision) {
            netlist = compiler.compile(document);
            compiledDocument = document;
            compiledRevision = document.revision();
        }
        return netlist;
    }
}
