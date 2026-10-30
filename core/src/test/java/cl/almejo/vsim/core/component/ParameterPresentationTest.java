package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Groups, multi-line text and conditional rows: how the inspector presents a type's parameters. */
class ParameterPresentationTest {

    @Test
    void aParameterHasNoGroupUntilPutInOne() {
        ParameterSpec rows = ParameterSpec.integer("rows", 24, 1, 100);

        assertThat(rows.group()).isEmpty();
        assertThat(rows.inGroup("screen").group()).contains("screen");
    }

    @Test
    void theModifiersKeepEachOther() {
        ParameterSpec text = ParameterSpec.text("expected", "").asDisplayOnly().inGroup("teaching").asMultiline()
                .visibleWhen("source", "text");

        assertThat(text.displayOnly()).isTrue();
        assertThat(text.group()).contains("teaching");
        assertThat(text.multiline()).isTrue();
        assertThat(text.visibility()).contains(new VisibilityCondition("source", "text"));
        assertThat(text.asHidden().multiline()).isTrue();
        assertThat(text.asHidden().group()).contains("teaching");
    }

    @Test
    void onlyATextCanBeMultiline() {
        assertThat(ParameterSpec.text("note", "").multiline()).isFalse();
        assertThatThrownBy(() -> ParameterSpec.integer("rows", 1, 1, 9).asMultiline())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aConditionalRowIsShownOnlyWhileItsConditionHolds() {
        ComponentType type = typeWithConditionalRow();
        ParameterSpec encoding = type.parameters().get(1);

        assertThat(type.isShown(encoding, Map.of())).as("7 bits by default").isFalse();
        assertThat(type.isShown(encoding, Map.of("width", 8))).isTrue();
        assertThat(type.isShown(encoding, Map.of("width", "8"))).as("raw text from a field").isTrue();
        assertThat(type.isShown(encoding, Map.of("width", "eight"))).as("unreadable").isFalse();
        assertThat(type.isShown(type.parameters().get(0), Map.of())).isTrue();
    }

    @Test
    void aHiddenParameterIsNeverShown() {
        ComponentType type = new ComponentType("h", ComponentCategory.INPUT_OUTPUT, new GridSize(2, 2), List.of(),
                List.of(ParameterSpec.text("contents", "").asHidden()), parameters -> (context, pin) -> { });

        assertThat(type.isShown(type.parameters().get(0), Map.of())).isFalse();
    }

    @Test
    void aConditionMustNameAnotherParameterAndOneOfItsValues() {
        assertThatThrownBy(() -> new ComponentType("bad", ComponentCategory.INPUT_OUTPUT, new GridSize(2, 2),
                List.of(), List.of(ParameterSpec.text("encoding", "utf8").visibleWhen("missing", 8)),
                parameters -> (context, pin) -> { })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComponentType("bad", ComponentCategory.INPUT_OUTPUT, new GridSize(2, 2),
                List.of(), List.of(ParameterSpec.integer("width", 7, 7, 8),
                        ParameterSpec.text("encoding", "utf8").visibleWhen("width", 99)),
                parameters -> (context, pin) -> { })).isInstanceOf(IllegalArgumentException.class);
    }

    private static ComponentType typeWithConditionalRow() {
        return new ComponentType("conditional", ComponentCategory.INPUT_OUTPUT, new GridSize(2, 2), List.of(),
                List.of(ParameterSpec.integer("width", 7, 7, 8),
                        ParameterSpec.choice("encoding", "utf8", List.of("utf8", "latin1")).visibleWhen("width", 8)),
                parameters -> (context, pin) -> { });
    }
}
