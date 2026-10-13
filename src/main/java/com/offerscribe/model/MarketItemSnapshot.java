package com.offerscribe.model;

import java.time.Instant;

public final class MarketItemSnapshot {
    public final int itemId;
    public final String name;
    public final boolean members;
    public final int buyLimit;
    public final long high;
    public final long low;
    public final Instant highTime;
    public final Instant lowTime;
    public final long averageHigh;
    public final long averageLow;
    public final long highVolume;
    public final long lowVolume;

    public MarketItemSnapshot(int itemId, String name, boolean members, int buyLimit,
        long high, long low, Instant highTime, Instant lowTime, long averageHigh,
        long averageLow, long highVolume, long lowVolume) {
        this.itemId = itemId;
        this.name = name;
        this.members = members;
        this.buyLimit = buyLimit;
        this.high = high;
        this.low = low;
        this.highTime = highTime;
        this.lowTime = lowTime;
        this.averageHigh = averageHigh;
        this.averageLow = averageLow;
        this.highVolume = highVolume;
        this.lowVolume = lowVolume;
    }
}
