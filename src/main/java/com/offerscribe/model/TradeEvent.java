package com.offerscribe.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TradeEvent {
    public enum Source { LIVE_EVENT, LOGIN_RECONCILIATION, STARTUP_RECONCILIATION, MANUAL_IMPORT }
    public static final int SCHEMA_VERSION = 1;
    private final UUID eventId;
    private final String accountProfileId, installationId, clientSessionId, offerState, pluginVersion;
    private final Instant observedAt;
    private final int offerSlot, itemId, quantityDelta, cumulativeQuantity;
    private final long pricePerItem;
    private final long coinsDelta, cumulativeCoins;
    private final OfferSnapshot.Side side;
    private final Source source;
    private final String taxRuleVersion;

    public TradeEvent(String accountProfileId, String installationId, String clientSessionId,
        OfferSnapshot current, int quantityDelta, long coinsDelta, Source source,
        String taxRuleVersion, String pluginVersion) {
        if (quantityDelta <= 0 || coinsDelta < 0) throw new IllegalArgumentException("delta must be positive");
        this.accountProfileId = required(accountProfileId); this.installationId = required(installationId);
        this.clientSessionId = required(clientSessionId); this.observedAt = current.getObservedAt();
        this.offerSlot = current.getSlot(); this.itemId = current.getItemId(); this.side = current.getSide();
        this.pricePerItem = current.getPricePerItem(); this.quantityDelta = quantityDelta; this.coinsDelta = coinsDelta;
        this.cumulativeQuantity = current.getFilledQuantity(); this.cumulativeCoins = current.getSpentOrReceived();
        this.offerState = current.getState().name(); this.source = Objects.requireNonNull(source);
        this.taxRuleVersion = required(taxRuleVersion); this.pluginVersion = required(pluginVersion);
        String key = accountProfileId + '|' + current.identity() + '|' + cumulativeQuantity + '|' + cumulativeCoins + '|' + observedAt;
        this.eventId = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
    private static String required(String value) { if (value == null || value.isEmpty()) throw new IllegalArgumentException("required"); return value; }
    public UUID getEventId() { return eventId; }
    public int getSchemaVersion() { return SCHEMA_VERSION; }
    public String getAccountProfileId() { return accountProfileId; }
    public String getInstallationId() { return installationId; }
    public String getClientSessionId() { return clientSessionId; }
    public Instant getObservedAt() { return observedAt; }
    public int getOfferSlot() { return offerSlot; }
    public int getItemId() { return itemId; }
    public OfferSnapshot.Side getSide() { return side; }
    public long getPricePerItem() { return pricePerItem; }
    public int getQuantityDelta() { return quantityDelta; }
    public long getCoinsDelta() { return coinsDelta; }
    public int getCumulativeQuantity() { return cumulativeQuantity; }
    public long getCumulativeCoins() { return cumulativeCoins; }
    public String getOfferState() { return offerState; }
    public Source getSource() { return source; }
    public String getTaxRuleVersion() { return taxRuleVersion; }
    public String getPluginVersion() { return pluginVersion; }
}
