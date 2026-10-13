package com.offerscribe.model;

import java.time.Instant;

public final class Recommendation {
    public final int itemId;
    public final OfferSnapshot.Side side;
    public final long suggestedPrice;
    public final int suggestedQuantity;
    public final long expectedUnitProfit;
    public final double roi;
    public final Instant dataTimestamp;
    public final String evidence;

    public Recommendation(int itemId, OfferSnapshot.Side side, long suggestedPrice,
        int suggestedQuantity, long expectedUnitProfit, double roi, Instant dataTimestamp,
        String evidence) {
        this.itemId = itemId;
        this.side = side;
        this.suggestedPrice = suggestedPrice;
        this.suggestedQuantity = suggestedQuantity;
        this.expectedUnitProfit = expectedUnitProfit;
        this.roi = roi;
        this.dataTimestamp = dataTimestamp;
        this.evidence = evidence;
    }
}
