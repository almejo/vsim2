package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.geometry.Axis;
import cl.almejo.vsim.core.geometry.SnapKind;
import cl.almejo.vsim.core.geometry.SnapMatch;
import cl.almejo.vsim.core.geometry.SnapTarget;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The guides among a gesture's alignment matches: what the guide overlay highlights (alignment-guides spec §4.4). */
public final class MatchedGuides {

    private MatchedGuides() {
    }

    /** Every guide one of {@code matches} lines up with, once each, in the order of the matches. */
    public static List<Guide> of(List<SnapMatch> matches) {
        Set<Guide> guides = new LinkedHashSet<>();
        for (SnapMatch match : matches) {
            for (SnapTarget target : match.targets()) {
                if (target.kind() == SnapKind.GUIDE) {
                    guides.add(guideOf(target));
                }
            }
        }
        return List.copyOf(guides);
    }

    /** The guide a GUIDE target came from: a line on X is a vertical guide (guides sit on whole units). */
    static Guide guideOf(SnapTarget target) {
        Guide.Orientation orientation = target.axis() == Axis.X ? Guide.Orientation.VERTICAL
                : Guide.Orientation.HORIZONTAL;
        return new Guide(orientation, (int) Math.round(target.position()));
    }
}
