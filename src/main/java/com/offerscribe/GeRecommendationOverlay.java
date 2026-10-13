package com.offerscribe;

import com.offerscribe.model.Recommendation;
import com.offerscribe.model.Opportunity;
import java.awt.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
final class GeRecommendationOverlay extends Overlay {
    enum GuidedPhase { BUY, WAIT_BUY, COLLECT_BUY, SELL, WAIT_SELL, COLLECT_SELL }
    // Current GE setup price is a 64-bit varp since the 2026-09-30 max-cash update.
    private static final int GE_SETUP_PRICE_VARP = 5753;
    private static final int OFFER_CONTAINER_CHILD = 26;
    private static final int SET_PRICE_CHILD = 54;
    private final Client client;
    private volatile Recommendation recommendation;
    private volatile Opportunity opportunity;
    private volatile GuidedPhase phase = GuidedPhase.BUY;
    private volatile int activeSlot = -1;

    @Inject GeRecommendationOverlay(Client client) {
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    void show(Recommendation recommendation) { this.recommendation = recommendation; }
    void showOpportunity(Opportunity opportunity) { this.opportunity = opportunity; }
    void showPhase(GuidedPhase phase, int activeSlot) { this.phase = phase; this.activeSlot = activeSlot; }
    void clear() { recommendation = null; opportunity = null; }

    @Override public Dimension render(Graphics2D graphics) {
        Opportunity active = opportunity;
        Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
        if (active != null && highlightGuidedStep(graphics, active, setup)) return null;
        Recommendation value = recommendation;
        if (value == null || setup == null || setup.isHidden() || setup.getBounds() == null) return null;
        if (client.getVarpLongValue(GE_SETUP_PRICE_VARP) == value.suggestedPrice) return null;

        Widget offerContainer = client.getWidget(InterfaceID.GE_OFFERS, OFFER_CONTAINER_CHILD);
        Widget setPrice = offerContainer == null ? null : offerContainer.getChild(SET_PRICE_CHILD);
        Rectangle bounds = setPrice == null ? setup.getBounds() : setPrice.getBounds();
        graphics.setColor(new Color(45, 190, 210, 210));
        graphics.setStroke(new BasicStroke(3f));
        graphics.drawRoundRect(bounds.x + 1, bounds.y + 6,
            Math.max(1, Math.min(33, bounds.width - 2)), Math.max(1, Math.min(23, bounds.height - 7)), 6, 6);

        String line = String.format("Offer Scribe: manually set %,d gp · qty %,d", value.suggestedPrice, value.suggestedQuantity);
        FontMetrics metrics = graphics.getFontMetrics();
        int textWidth = metrics.stringWidth(line) + 16;
        Rectangle setupBounds = setup.getBounds();
        int textY = Math.max(24, setupBounds.y - 8);
        graphics.setColor(new Color(15, 23, 42, 225));
        graphics.fillRoundRect(setupBounds.x, textY - metrics.getHeight(), textWidth, metrics.getHeight() + 8, 8, 8);
        graphics.setColor(Color.WHITE);
        graphics.drawString(line, setupBounds.x + 8, textY);
        return null;
    }

    private boolean highlightGuidedStep(Graphics2D graphics, Opportunity active, Widget setup) {
        if (phase == GuidedPhase.WAIT_BUY || phase == GuidedPhase.WAIT_SELL) return false;
        if (phase == GuidedPhase.COLLECT_BUY || phase == GuidedPhase.COLLECT_SELL) {
            Widget topBar = client.getWidget(InterfaceID.GE_OFFERS, 6);
            Widget collect = topBar == null ? null : topBar.getChild(2);
            if (outline(graphics, collect, new Rectangle(2, 1, 81, 18))) return true;
            Widget slot = activeSlot < 0 ? null : client.getWidget(InterfaceID.GE_OFFERS, 7 + activeSlot);
            return outline(graphics, slot, null);
        }
        int selectedSlot = client.getVarbitValue(VarbitID.GE_SELECTEDSLOT) - 1;
        if (selectedSlot < 0) {
            GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
            for (int slot = 0; slot < offers.length; slot++) {
                if (offers[slot] != null && offers[slot].getState() == GrandExchangeOfferState.EMPTY) {
                    Widget slotWidget = client.getWidget(InterfaceID.GE_OFFERS, 7 + slot);
                    int child = phase == GuidedPhase.SELL ? 1 : 0;
                    return outline(graphics, slotWidget == null ? null : slotWidget.getChild(child), null);
                }
            }
            return false;
        }
        if (setup == null || setup.isHidden()) return false;
        Widget container = client.getWidget(InterfaceID.GE_OFFERS, OFFER_CONTAINER_CHILD);
        if (container == null) return false;
        int item = client.getVarpValue(VarPlayer.CURRENT_GE_ITEM);
        if (item != active.itemId) return outline(graphics, container.getChild(0), null);
        int quantity = client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY);
        if (quantity != active.quantity) return outline(graphics, container.getChild(51), new Rectangle(1, 6, 33, 23));
        long price = client.getVarpLongValue(GE_SETUP_PRICE_VARP);
        long targetPrice = phase == GuidedPhase.SELL ? active.sellPrice : active.buyPrice;
        if (price != targetPrice) return outline(graphics, container.getChild(54), new Rectangle(1, 6, 33, 23));
        return outline(graphics, container.getChild(58), new Rectangle(1, 1, 150, 38));
    }

    private boolean outline(Graphics2D graphics, Widget widget, Rectangle adjustment) {
        if (widget == null || widget.isHidden() || widget.getBounds() == null) return false;
        Rectangle value = widget.getBounds();
        Rectangle bounds = adjustment == null ? value : new Rectangle(value.x + adjustment.x,
            value.y + adjustment.y, Math.min(value.width, adjustment.width), Math.min(value.height, adjustment.height));
        graphics.setColor(new Color(45, 190, 210, 210));
        graphics.setStroke(new BasicStroke(3f));
        graphics.drawRoundRect(bounds.x, bounds.y, Math.max(1, bounds.width), Math.max(1, bounds.height), 8, 8);
        return true;
    }
}
