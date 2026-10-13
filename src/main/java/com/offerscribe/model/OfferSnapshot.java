package com.offerscribe.model;

import java.time.Instant;
import java.util.Objects;

public final class OfferSnapshot {
    public enum Side { BUY, SELL }
    public enum State { EMPTY, PENDING, ACTIVE, CANCELLED, COMPLETED }

    private final Instant observedAt;
    private final int slot;
    private final int itemId;
    private final Side side;
    private final long pricePerItem;
    private final int totalQuantity;
    private final int filledQuantity;
    private final long spentOrReceived;
    private final State state;

    public OfferSnapshot(Instant observedAt, int slot, int itemId, Side side,
        long pricePerItem, int totalQuantity, int filledQuantity,
        long spentOrReceived, State state) {
        this.observedAt = Objects.requireNonNull(observedAt);
        this.side = Objects.requireNonNull(side);
        this.state = Objects.requireNonNull(state);
        if (slot < 0 || itemId < 0 || pricePerItem < 0 || totalQuantity < 0 ||
            filledQuantity < 0 || filledQuantity > totalQuantity || spentOrReceived < 0) {
            throw new IllegalArgumentException("invalid offer snapshot");
        }
        this.slot = slot; this.itemId = itemId; this.pricePerItem = pricePerItem;
        this.totalQuantity = totalQuantity; this.filledQuantity = filledQuantity;
        this.spentOrReceived = spentOrReceived;
    }
    public Instant getObservedAt() { return observedAt; }
    public int getSlot() { return slot; }
    public int getItemId() { return itemId; }
    public Side getSide() { return side; }
    public long getPricePerItem() { return pricePerItem; }
    public int getTotalQuantity() { return totalQuantity; }
    public int getFilledQuantity() { return filledQuantity; }
    public long getSpentOrReceived() { return spentOrReceived; }
    public State getState() { return state; }
    public String identity() { return slot + ":" + itemId + ":" + side + ":" + pricePerItem + ":" + totalQuantity; }
}
