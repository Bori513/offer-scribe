package com.offerscribe;

import com.offerscribe.model.Recommendation;
import java.awt.event.KeyEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.VarClientStr;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;

/**
 * User-triggered, single-field GE input helper. Adapted from Flipping Copilot's
 * OfferEditor/OfferHandler/KeybindHandler under BSD-2-Clause; see NOTICE.md.
 */
@Singleton
final class GeInputAssistant {
    private static final int INPUT_INT = 7;
    private static final int INPUT_LONG = 30;
    private final Client client;
    private final ClientThread clientThread;
    private final KeyManager keyManager;
    private final OfferScribeConfig config;
    private volatile Recommendation recommendation;
    private Widget prompt;

    @Inject GeInputAssistant(Client client, ClientThread clientThread, KeyManager keyManager,
        OfferScribeConfig config) {
        this.client = client;
        this.clientThread = clientThread;
        this.keyManager = keyManager;
        this.config = config;
    }

    private final KeyListener listener = new KeyListener() {
        @Override public void keyTyped(KeyEvent event) {}
        @Override public void keyReleased(KeyEvent event) {}
        @Override public void keyPressed(KeyEvent event) {
            Keybind keybind = config.quickSetKeybind();
            if (event.getKeyCode() == KeyEvent.VK_ENTER || keybind == null || !keybind.matches(event)) return;
            clientThread.invokeLater(GeInputAssistant.this::setSuggestedValue);
        }
    };

    void start() { keyManager.registerKeyListener(listener); }
    void stop() { keyManager.unregisterKeyListener(listener); recommendation = null; prompt = null; }

    void setRecommendation(Recommendation value) {
        recommendation = value;
        clientThread.invokeLater(this::showPromptIfOpen);
    }

    void clear() { recommendation = null; }

    void onVarClientIntChanged(VarClientIntChanged event) {
        if (event.getIndex() != VarClientID.MESLAYERMODE) return;
        int mode = client.getVarcIntValue(VarClientID.MESLAYERMODE);
        if (mode == INPUT_INT || mode == INPUT_LONG) clientThread.invokeLater(this::showPromptIfOpen);
        else prompt = null;
    }

    private boolean isPricePromptOpen() {
        int mode = client.getVarcIntValue(VarClientID.MESLAYERMODE);
        Widget title = client.getWidget(ComponentID.CHATBOX_TITLE);
        return (mode == INPUT_INT || mode == INPUT_LONG) && title != null
            && ("Set a price for each item:".equals(title.getText())
                || "How many do you wish to buy?".equals(title.getText())
                || "How many do you wish to sell?".equals(title.getText()))
            && client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER) != null;
    }

    private boolean matchesSelectedItem() {
        Recommendation value = recommendation;
        return value != null && client.getVarpValue(VarPlayer.CURRENT_GE_ITEM) == value.itemId;
    }

    private void showPromptIfOpen() {
        if (!isPricePromptOpen() || !matchesSelectedItem()) return;
        if (prompt != null && !prompt.isHidden()) {
            updatePromptText();
            return;
        }
        Widget parent = client.getWidget(ComponentID.CHATBOX_CONTAINER);
        if (parent == null) return;
        prompt = parent.createChild(-1, WidgetType.TEXT);
        prompt.setTextColor(0x0040FF);
        prompt.setFontId(FontID.VERDANA_11_BOLD);
        prompt.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
        prompt.setOriginalX(10);
        prompt.setOriginalY(40);
        prompt.setOriginalHeight(20);
        prompt.setXTextAlignment(WidgetTextAlignment.LEFT);
        prompt.setWidthMode(WidgetSizeMode.MINUS);
        prompt.setHasListener(true);
        prompt.setAction(0, "Set Offer Scribe value");
        prompt.setOnOpListener((JavaScriptCallback) event -> setSuggestedValue());
        prompt.setOnMouseRepeatListener((JavaScriptCallback) event -> prompt.setTextColor(0xFFFFFF));
        prompt.setOnMouseLeaveListener((JavaScriptCallback) event -> prompt.setTextColor(0x0040FF));
        updatePromptText();
        Widget title = client.getWidget(ComponentID.CHATBOX_TITLE);
        if (title != null) {
            title.setOriginalY(title.getOriginalY() + 7);
            title.revalidate();
        }
        prompt.revalidate();
    }

    private void updatePromptText() {
        if (prompt == null || recommendation == null) return;
        Keybind keybind = config.quickSetKeybind();
        String keyText = keybind == null || Keybind.NOT_SET.equals(keybind) ? "click" : "click or press [" + keybind + "]";
        boolean quantity = isQuantityPrompt();
        prompt.setText(quantity
            ? String.format("Offer Scribe: %s to set quantity %,d", keyText, recommendation.suggestedQuantity)
            : String.format("Offer Scribe: %s to set price %,d gp", keyText, recommendation.suggestedPrice));
        prompt.revalidate();
    }

    private boolean isQuantityPrompt() {
        Widget title = client.getWidget(ComponentID.CHATBOX_TITLE);
        return title != null && title.getText() != null && title.getText().startsWith("How many do you wish to");
    }

    private void setSuggestedValue() {
        if (!isPricePromptOpen() || !matchesSelectedItem()) return;
        Widget input = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
        if (input == null) return;
        long value = isQuantityPrompt() ? recommendation.suggestedQuantity : recommendation.suggestedPrice;
        input.setText(value + "*");
        client.setVarcStrValue(VarClientStr.INPUT_TEXT, Long.toString(value));
    }
}
