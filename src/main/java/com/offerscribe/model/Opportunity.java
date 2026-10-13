package com.offerscribe.model;

import java.time.Instant;

public final class Opportunity {
    public final int itemId;
    public final String name;
    public final long buyPrice;
    public final long sellPrice;
    public final int quantity;
    public final long taxPerItem;
    public final long profitPerItem;
    public final long expectedProfit;
    public final double roi;
    public final long balancedHourlyVolume;
    public final Instant dataTimestamp;
    public final String risk;
    public final double score;

    public Opportunity(int itemId, String name, long buyPrice, long sellPrice, int quantity,
        long taxPerItem, long profitPerItem, long expectedProfit, double roi,
        long balancedHourlyVolume, Instant dataTimestamp, String risk, double score) {
        this.itemId = itemId;
        this.name = name;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.quantity = quantity;
        this.taxPerItem = taxPerItem;
        this.profitPerItem = profitPerItem;
        this.expectedProfit = expectedProfit;
        this.roi = roi;
        this.balancedHourlyVolume = balancedHourlyVolume;
        this.dataTimestamp = dataTimestamp;
        this.risk = risk;
        this.score = score;
    }
}
