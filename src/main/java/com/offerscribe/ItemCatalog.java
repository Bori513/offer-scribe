package com.offerscribe;

import com.offerscribe.model.MarketItemSnapshot;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;

/** Resolves stable item ids to presentation metadata without changing immutable trade events. */
@Singleton
final class ItemCatalog {
    private final ItemManager itemManager;
    private final Map<Integer, String> names = new ConcurrentHashMap<>();

    @Inject ItemCatalog(ItemManager itemManager) { this.itemManager = itemManager; }

    void update(List<MarketItemSnapshot> items) {
        for (MarketItemSnapshot item : items) names.put(item.itemId, item.name);
    }

    String name(int itemId) {
        String known = names.get(itemId);
        if (known != null && !known.isEmpty()) return known;
        ItemComposition composition = itemManager.getItemComposition(itemId);
        String resolved = composition == null ? null : composition.getName();
        if (resolved == null || resolved.isEmpty() || "null".equalsIgnoreCase(resolved)) {
            return "Item " + itemId;
        }
        names.put(itemId, resolved);
        return resolved;
    }
}
