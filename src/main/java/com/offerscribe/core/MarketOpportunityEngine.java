package com.offerscribe.core;

import com.offerscribe.model.MarketItemSnapshot;
import com.offerscribe.model.Opportunity;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class MarketOpportunityEngine {
    private static final long MAX_AGE_SECONDS = 30 * 60;
    private static final double VOLUME_FRACTION = 0.02;

    public List<Opportunity> rank(List<MarketItemSnapshot> items, long budget,
        boolean includeMembers, int maximum, Instant now) {
        List<Opportunity> ranked = new ArrayList<>();
        for (MarketItemSnapshot item : items) {
            if ((!includeMembers && item.members) || item.low <= 0 || item.high <= item.low) continue;
            long age = Math.max(0, Duration.between(
                item.highTime.isBefore(item.lowTime) ? item.highTime : item.lowTime, now).getSeconds());
            if (age > MAX_AGE_SECONDS) continue;
            long balancedVolume = Math.min(item.highVolume, item.lowVolume);
            if (balancedVolume < 10) continue;
            long tax = GeTaxCalculator.taxPerItem(item.high);
            long net = item.high - item.low - tax;
            double roi = (double) net / item.low;
            if (net <= 0 || roi <= 0 || roi > 0.20) continue;
            long buyLimit = item.buyLimit > 0 ? item.buyLimit : Long.MAX_VALUE;
            long affordable = budget / item.low;
            long liquid = Math.max(1, (long) Math.floor(balancedVolume * VOLUME_FRACTION));
            long rawQuantity = Math.min(buyLimit, Math.min(affordable, liquid));
            if (rawQuantity < 1) continue;
            int quantity = (int) Math.min(Integer.MAX_VALUE, rawQuantity);
            long expected = Math.multiplyExact(net, quantity);
            double lowDeviation = Math.abs(item.low - item.averageLow) / (double) Math.max(1, item.averageLow);
            double highDeviation = Math.abs(item.high - item.averageHigh) / (double) Math.max(1, item.averageHigh);
            double deviation = lowDeviation + highDeviation;
            double freshness = Math.max(0.1, 1 - age / (double) MAX_AGE_SECONDS);
            double stability = 1 / (1 + deviation * 8);
            double score = expected * Math.log1p(balancedVolume) * freshness * stability;
            String risk = deviation > 0.10 || balancedVolume < 50 ? "HIGH"
                : deviation > 0.04 || balancedVolume < 250 ? "MEDIUM" : "LOW";
            ranked.add(new Opportunity(item.itemId, item.name, item.low, item.high, quantity,
                tax, net, expected, roi, balancedVolume,
                item.highTime.isBefore(item.lowTime) ? item.highTime : item.lowTime, risk, score));
        }
        return ranked.stream()
            .sorted(Comparator.comparingDouble((Opportunity value) -> value.score).reversed()
                .thenComparingInt(value -> value.itemId))
            .limit(Math.max(1, maximum))
            .collect(Collectors.toList());
    }
}
