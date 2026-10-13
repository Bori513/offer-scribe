package com.offerscribe.core;

import com.offerscribe.model.MarketPrice;
import com.offerscribe.model.OfferSnapshot;
import com.offerscribe.model.Recommendation;
import java.time.Instant;

public final class RecommendationEngine {
    public Recommendation recommend(MarketPrice price, OfferSnapshot.Side side, int requestedQuantity) {
        long tax = GeTaxCalculator.taxPerItem(price.instantBuyPrice);
        long profit = price.instantBuyPrice - price.instantSellPrice - tax;
        long suggestedPrice = side == OfferSnapshot.Side.BUY
            ? price.instantSellPrice
            : price.instantBuyPrice;
        int quantity = Math.max(1, Math.min(requestedQuantity > 0 ? requestedQuantity : 1, 100));
        double roi = price.instantSellPrice == 0 ? 0 : (double) profit / price.instantSellPrice;
        Instant timestamp = price.buyObservedAt.isBefore(price.sellObservedAt)
            ? price.buyObservedAt : price.sellObservedAt;
        String evidence = "OSRS Wiki real-time high/low; observed spread, current GE tax; no fill guarantee";
        return new Recommendation(price.itemId, side, suggestedPrice, quantity, profit, roi, timestamp, evidence);
    }
}
