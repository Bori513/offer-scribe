package com.offerscribe.model;

import java.time.Instant;
import java.util.Objects;

public final class MarketPrice {
    public final int itemId;
    public final long instantBuyPrice;
    public final long instantSellPrice;
    public final Instant buyObservedAt;
    public final Instant sellObservedAt;

    public MarketPrice(int itemId, long instantBuyPrice, long instantSellPrice,
        Instant buyObservedAt, Instant sellObservedAt) {
        if (itemId <= 0 || instantBuyPrice <= 0 || instantSellPrice <= 0) {
            throw new IllegalArgumentException("invalid market price");
        }
        this.itemId = itemId;
        this.instantBuyPrice = instantBuyPrice;
        this.instantSellPrice = instantSellPrice;
        this.buyObservedAt = Objects.requireNonNull(buyObservedAt);
        this.sellObservedAt = Objects.requireNonNull(sellObservedAt);
    }
}
