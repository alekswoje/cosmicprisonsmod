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

    public List<String> allowedServerIds = new ArrayList<>();
    public boolean enablePayloadCodecFallback = false;
    public Map<String, Boolean> featureToggles = new LinkedHashMap<>();
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
}
