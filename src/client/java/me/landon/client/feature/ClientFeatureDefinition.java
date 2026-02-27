package me.landon.client.feature;

import java.util.Objects;
import java.util.OptionalInt;

public record ClientFeatureDefinition(
        String id,
        String nameTranslationKey,
        String iconTranslationKey,
        String descriptionTranslationKey,
        boolean defaultEnabled,
        OptionalInt requiredServerFeatureBit) {
    public ClientFeatureDefinition {
        id = Objects.requireNonNull(id, "id");
        nameTranslationKey = Objects.requireNonNull(nameTranslationKey, "nameTranslationKey");
        iconTranslationKey = Objects.requireNonNull(iconTranslationKey, "iconTranslationKey");
        descriptionTranslationKey =
                Objects.requireNonNull(descriptionTranslationKey, "descriptionTranslationKey");
        requiredServerFeatureBit =
                Objects.requireNonNull(requiredServerFeatureBit, "requiredServerFeatureBit");
    }
}
