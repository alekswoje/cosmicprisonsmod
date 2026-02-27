/**
 * Client runtime and server-authoritative state handling.
 *
 * <p>New server-driven features should integrate through:
 *
 * <ol>
 *   <li>{@code CompanionClientRuntime} for event hooks, rendering, and feature gating
 *   <li>{@code ConnectionSessionState} for per-connection data
 *   <li>{@code HudWidgetCatalog} for widget/event metadata and parsing helpers
 * </ol>
 *
 * <p>Runtime behavior should always degrade safely when capability flags or payload data are
 * missing.
 */
package me.landon.client.runtime;
