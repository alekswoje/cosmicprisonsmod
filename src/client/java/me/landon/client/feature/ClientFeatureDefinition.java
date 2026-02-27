package me.landon.client.feature;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Describes a toggleable client feature that can be surfaced in settings and gated by server
 * capability flags.
 *
 * @param id Stable feature id stored in client config.
 * @param nameTranslationKey Translation key for the display name.
 * @param iconTranslationKey Translation key for the short icon text.
 * @param descriptionTranslationKey Translation key for the settings description.
 * @param defaultEnabled Default toggle state used when no persisted value exists.
 * @param requiredServerFeatureBit Optional server feature bit needed for runtime activation.
 */
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
