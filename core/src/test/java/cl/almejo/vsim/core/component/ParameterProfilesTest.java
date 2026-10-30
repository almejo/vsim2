package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParameterProfilesTest {

    private static final String PROFILE = "profile";

    /** A screen-like part: a profile, two looks a profile sets, and rows a profile never sets. */
    static ComponentType profiledType() {
        return new ComponentType("screen", ComponentCategory.INPUT_OUTPUT, new GridSize(4, 4), List.of(),
                List.of(ParameterSpec.choice(PROFILE, "classic", List.of("classic", "modern")).asDisplayOnly(),
                        ParameterSpec.integer("rows", 24, 1, 100),
                        ParameterSpec.integer("history", 0, 0, 10_000),
                        ParameterSpec.choice("colors", "green", List.of("green", "white", "paper"))),
                parameters -> (context, pinIndex) -> { })
                .withProfiles(PROFILE, List.of(
                        new ParameterProfile("classic", Map.of("history", 0, "colors", "green")),
                        new ParameterProfile("modern", Map.of("history", 1000, "colors", "white"))));
    }

    private final ComponentType type = profiledType();

    @Test
    void theTypeKnowsItsProfileParameterAndProfiles() {
        assertThat(type.profileParameter()).contains(PROFILE);
        assertThat(type.profiles()).extracting(ParameterProfile::name).containsExactly("classic", "modern");
        assertThat(type.profile("modern")).map(profile -> profile.values().get("history")).contains(1000);
    }

    @Test
    void aTypeWithoutProfilesHasNoProfileParameter() {
        ComponentType plain = new ComponentType("plain", ComponentCategory.INPUT_OUTPUT, new GridSize(2, 2),
                List.of(), List.of(ParameterSpec.integer("rows", 1, 1, 9)), parameters -> (context, pin) -> { });

        assertThat(plain.profileParameter()).isEmpty();
        assertThat(plain.profiles()).isEmpty();
        assertThat(ParameterProfiles.differences(plain, Map.of("rows", 3))).isEmpty();
    }

    @Test
    void theDefaultsOfTheDefaultProfileHaveNoDifferences() {
        assertThat(ParameterProfiles.differences(type, Map.of())).isEmpty();
    }

    @Test
    void aValueOtherThanTheProfilesCountsAndOneSetBackByHandStopsCounting() {
        assertThat(ParameterProfiles.differences(type, Map.of("colors", "paper", "history", 5)))
                .containsExactly("history", "colors");
        assertThat(ParameterProfiles.differences(type, Map.of("colors", "green", "history", "0"))).isEmpty();
    }

    @Test
    void parametersAProfileDoesNotListNeverCount() {
        assertThat(ParameterProfiles.differences(type, Map.of("rows", 40))).isEmpty();
    }

    @Test
    void differencesAreAgainstTheChosenProfile() {
        assertThat(ParameterProfiles.differences(type, Map.of(PROFILE, "modern"))).containsExactly("history",
                "colors");
        assertThat(ParameterProfiles.differences(type, Map.of(PROFILE, "modern", "history", 1000,
                "colors", "white"))).isEmpty();
    }

    @Test
    void anUnreadableValueCountsAsDifferent() {
        assertThat(ParameterProfiles.differences(type, Map.of("history", "lots"))).containsExactly("history");
    }

    @Test
    void applyingAProfileSetsEveryValueItListsAndKeepsTheOthers() {
        Map<String, Object> applied = ParameterProfiles.apply(type, Map.of("rows", 40, "colors", "paper"), "modern");

        assertThat(applied).containsEntry(PROFILE, "modern").containsEntry("history", 1000)
                .containsEntry("colors", "white").containsEntry("rows", 40);
        assertThat(applied).containsOnlyKeys(PROFILE, "rows", "history", "colors");
        assertThat(ParameterProfiles.differences(type, applied)).isEmpty();
    }

    @Test
    void applyingAnUnknownProfileIsRefused() {
        assertThatThrownBy(() -> ParameterProfiles.apply(type, Map.of(), "retro"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retro");
    }

    @Test
    void restoringOneParameterSetsOnlyItBackToTheProfilesValue() {
        Map<String, Object> restored = ParameterProfiles.restore(type,
                Map.of(PROFILE, "modern", "history", 5, "colors", "paper"), "history");

        assertThat(restored).containsEntry("history", 1000).containsEntry("colors", "paper");
        assertThat(ParameterProfiles.differences(type, restored)).containsExactly("colors");
    }

    @Test
    void restoringAParameterTheProfileDoesNotListIsRefused() {
        assertThatThrownBy(() -> ParameterProfiles.restore(type, Map.of(), "rows"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void profilesAreCheckedWhenGiven() {
        ComponentType base = new ComponentType("base", ComponentCategory.INPUT_OUTPUT, new GridSize(2, 2), List.of(),
                List.of(ParameterSpec.choice(PROFILE, "a", List.of("a")), ParameterSpec.integer("size", 1, 1, 9),
                        ParameterSpec.text("name", "")),
                parameters -> (context, pin) -> { });

        assertThatThrownBy(() -> base.withProfiles("size", List.of(new ParameterProfile("a", Map.of()))))
                .as("the profile parameter must be a choice").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> base.withProfiles(PROFILE, List.of(new ParameterProfile("b", Map.of()))))
                .as("a profile must be one of the choices").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> base.withProfiles(PROFILE, List.of(new ParameterProfile("a", Map.of("size", 99)))))
                .as("a value must be valid").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> base.withProfiles(PROFILE, List.of(new ParameterProfile("a", Map.of("nope", 1)))))
                .as("a value must name a parameter").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> base.withProfiles(PROFILE, List.of())).as("every choice needs a profile")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void profileValuesAreStoredNormalized() {
        ComponentType normalized = new ComponentType("n", ComponentCategory.INPUT_OUTPUT, new GridSize(2, 2),
                List.of(), List.of(ParameterSpec.choice(PROFILE, "a", List.of("a")),
                        ParameterSpec.integer("size", 1, 1, 9)), parameters -> (context, pin) -> { })
                .withProfiles(PROFILE, List.of(new ParameterProfile("a", Map.of("size", "4"))));

        assertThat(normalized.profile("a").orElseThrow().values()).containsEntry("size", 4);
        assertThat(ParameterProfiles.differences(normalized, Map.of("size", 4))).isEmpty();
    }

    @Test
    void aSpecializedTypeKeepsItsProfiles() {
        ComponentType layoutType = ComponentType.parameterized("sized", ComponentCategory.INPUT_OUTPUT,
                List.of(ParameterSpec.choice(PROFILE, "a", List.of("a")), ParameterSpec.integer("size", 2, 1, 9)),
                parameters -> new ComponentShape(new GridSize((Integer) parameters.get("size"), 2), List.of()),
                parameters -> (context, pin) -> { })
                .withProfiles(PROFILE, List.of(new ParameterProfile("a", Map.of("size", 2))));

        ComponentType specialized = layoutType.specializedFor(Map.of("size", 5));

        assertThat(specialized.size()).isEqualTo(new GridSize(5, 2));
        assertThat(specialized.profiles()).isEqualTo(layoutType.profiles());
        assertThat(specialized.profileParameter()).contains(PROFILE);
    }
}
