package com.offerscribe.core;

public final class GeTaxCalculator {
    public static final String CURRENT_VERSION = "osrs-2025-05-29-v1";
    private static final long CAP_PER_ITEM = 5_000_000L;
    private GeTaxCalculator() {}
    public static long taxPerItem(long salePrice) {
        if (salePrice < 0) throw new IllegalArgumentException("negative price");
        return Math.min(CAP_PER_ITEM, salePrice * 2 / 100);
    }
    public static long tax(long salePrice, long quantity) {
        if (quantity < 0) throw new IllegalArgumentException("negative quantity");
        return Math.multiplyExact(taxPerItem(salePrice), quantity);
    }
}

