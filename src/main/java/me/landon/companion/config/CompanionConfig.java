package me.landon.companion.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CompanionConfig {
    public static final String OFFICIAL_ALLOWED_SERVER_ID = "cosmicprisons.com";
    private static final String SIGNATURE_POLICY_OFF = "OFF";
    private static final String SIGNATURE_POLICY_LOG_ONLY = "LOG_ONLY";
    private static final String SIGNATURE_POLICY_ENFORCE = "ENFORCE";
    public static final String HUD_WIDGET_EVENTS_ID = "events";
    public static final String HUD_WIDGET_COOLDOWNS_ID = "cooldowns";
    public static final String HUD_WIDGET_SATCHELS_ID = "satchels";
    public static final String HUD_EVENT_METEORITE = "meteorite";
    public static final String HUD_EVENT_METEOR = "meteor";
    public static final String HUD_EVENT_ALTAR_SPAWN = "altar_spawn";
    public static final String HUD_EVENT_KOTH = "koth";
    public static final String HUD_EVENT_CREDIT_SHOP_RESET = "credit_shop_reset";
    public static final String HUD_EVENT_JACKPOT = "jackpot";
    public static final String HUD_EVENT_FLASH_SALE = "flash_sale";
    public static final String HUD_EVENT_MERCHANT = "merchant";
    public static final String HUD_EVENT_NEXT_REBOOT = "next_reboot";
    public static final String HUD_EVENT_NEXT_LEVEL_CAP_UNLOCK = "next_level_cap_day_unlock";
    public static final List<String> HUD_EVENT_KEYS =
            List.of(
                    HUD_EVENT_METEORITE,
                    HUD_EVENT_METEOR,
                    HUD_EVENT_ALTAR_SPAWN,
                    HUD_EVENT_KOTH,
                    HUD_EVENT_CREDIT_SHOP_RESET,
                    HUD_EVENT_JACKPOT,
                    HUD_EVENT_FLASH_SALE,
                    HUD_EVENT_MERCHANT,
                    HUD_EVENT_NEXT_REBOOT,
                    HUD_EVENT_NEXT_LEVEL_CAP_UNLOCK);
    public static final List<String> HUD_WIDGET_IDS =
            List.of(HUD_WIDGET_EVENTS_ID, HUD_WIDGET_COOLDOWNS_ID, HUD_WIDGET_SATCHELS_ID);

    public List<String> allowedServerIds = new ArrayList<>();
    public boolean enablePayloadCodecFallback = false;
    public Map<String, Boolean> featureToggles = new LinkedHashMap<>();
    public Map<String, HudWidgetPosition> hudWidgetPositions = new LinkedHashMap<>();
    public Map<String, Boolean> hudEventVisibility = new LinkedHashMap<>();
    public String serverSignaturePolicy = SIGNATURE_POLICY_LOG_ONLY;
    // Legacy field retained for config compatibility.
    public boolean requireServerSignature;
    public List<String> serverSignaturePublicKeys = new ArrayList<>();
    public boolean logMalformedOncePerConnection = true;

    public static CompanionConfig defaults() {
        CompanionConfig config = new CompanionConfig();
        config.serverSignaturePolicy = SIGNATURE_POLICY_LOG_ONLY;
        config.requireServerSignature = false;
        config.allowedServerIds = List.of(OFFICIAL_ALLOWED_SERVER_ID);
        config.hudWidgetPositions = defaultHudWidgetPositions();
        config.hudEventVisibility = defaultHudEventVisibility();
        return config;
    }

    public void sanitize() {
        if (allowedServerIds == null) {
            allowedServerIds = new ArrayList<>();
        }

        if (serverSignaturePublicKeys == null) {
            serverSignaturePublicKeys = new ArrayList<>();
        }

        if (featureToggles == null) {
            featureToggles = new LinkedHashMap<>();
        }

        if (hudWidgetPositions == null) {
            hudWidgetPositions = new LinkedHashMap<>();
        }

        if (hudEventVisibility == null) {
            hudEventVisibility = new LinkedHashMap<>();
        }

        // Official policy: only the production domain is allowed.
        allowedServerIds = List.of(OFFICIAL_ALLOWED_SERVER_ID);

        if (serverSignaturePolicy == null || serverSignaturePolicy.isBlank()) {
            // Fall back to LOG_ONLY when the new policy field is missing.
            serverSignaturePolicy = SIGNATURE_POLICY_LOG_ONLY;
        } else {
            serverSignaturePolicy = serverSignaturePolicy.trim().toUpperCase(Locale.ROOT);
        }

        if (!SIGNATURE_POLICY_OFF.equals(serverSignaturePolicy)
                && !SIGNATURE_POLICY_LOG_ONLY.equals(serverSignaturePolicy)
                && !SIGNATURE_POLICY_ENFORCE.equals(serverSignaturePolicy)) {
            serverSignaturePolicy = SIGNATURE_POLICY_LOG_ONLY;
        }

        requireServerSignature = SIGNATURE_POLICY_ENFORCE.equals(serverSignaturePolicy);

        serverSignaturePublicKeys =
                serverSignaturePublicKeys.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();

        Map<String, Boolean> cleanedFeatureToggles = new LinkedHashMap<>();

        for (Map.Entry<String, Boolean> entry : featureToggles.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            String key = entry.getKey().trim();

            if (!key.isEmpty()) {
                cleanedFeatureToggles.put(key, entry.getValue());
            }
        }

        featureToggles = cleanedFeatureToggles;

        Map<String, HudWidgetPosition> cleanedWidgetPositions = defaultHudWidgetPositions();
        for (Map.Entry<String, HudWidgetPosition> entry : hudWidgetPositions.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            String normalizedId = entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (!HUD_WIDGET_IDS.contains(normalizedId)) {
                continue;
            }

            cleanedWidgetPositions.put(normalizedId, entry.getValue().clamped());
        }
        hudWidgetPositions = cleanedWidgetPositions;

        Map<String, Boolean> cleanedHudEventVisibility = defaultHudEventVisibility();
        for (Map.Entry<String, Boolean> entry : hudEventVisibility.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            String normalizedKey = entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (!HUD_EVENT_KEYS.contains(normalizedKey)) {
                continue;
            }

            cleanedHudEventVisibility.put(normalizedKey, entry.getValue());
        }
        hudEventVisibility = cleanedHudEventVisibility;
    }

    public boolean isSignatureVerificationEnforced() {
        return SIGNATURE_POLICY_ENFORCE.equals(serverSignaturePolicy);
    }

    public boolean isSignatureVerificationLogOnly() {
        return SIGNATURE_POLICY_LOG_ONLY.equals(serverSignaturePolicy);
    }

    public boolean isSignatureVerificationOff() {
        return SIGNATURE_POLICY_OFF.equals(serverSignaturePolicy);
    }

    public static final class HudWidgetPosition {
        public double x;
        public double y;

        public HudWidgetPosition() {}

        public HudWidgetPosition(double x, double y) {
            this.x = x;
            this.y = y;
        }

        private HudWidgetPosition clamped() {
            return new HudWidgetPosition(clamp01(x), clamp01(y));
        }

        private static double clamp01(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return 0.0D;
            }
            return Math.max(0.0D, Math.min(1.0D, value));
        }
    }

    private static Map<String, HudWidgetPosition> defaultHudWidgetPositions() {
        Map<String, HudWidgetPosition> defaults = new LinkedHashMap<>();
        defaults.put(HUD_WIDGET_EVENTS_ID, new HudWidgetPosition(0.73D, 0.10D));
        defaults.put(HUD_WIDGET_COOLDOWNS_ID, new HudWidgetPosition(0.05D, 0.16D));
        defaults.put(HUD_WIDGET_SATCHELS_ID, new HudWidgetPosition(0.73D, 0.43D));
        return defaults;
    }

    private static Map<String, Boolean> defaultHudEventVisibility() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        for (String key : HUD_EVENT_KEYS) {
            defaults.put(key, true);
        }
        return defaults;
    }
}
