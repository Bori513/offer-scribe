package com.offerscribe;

import java.awt.event.KeyEvent;
import net.runelite.client.config.*;

@ConfigGroup("offerscribe")
public interface OfferScribeConfig extends Config {
    @ConfigItem(keyName="webhookEnabled", name="Send fills to API", description="Send completed GE fill events to the configured webhook endpoint", warning="This feature submits the disclosed trade data and your IP address to a 3rd-party server not controlled or verified by RuneLite developers", position=1)
    default boolean webhookEnabled() { return false; }
    @ConfigItem(keyName="webhookUrl", name="Webhook URL", description="Complete POST endpoint. PixelDisplaySim default: http://127.0.0.1:8765/api/v1/ge_update/. Remote endpoints must use HTTPS.", position=2)
    default String webhookUrl() { return "http://127.0.0.1:8765/api/v1/ge_update/"; }
    @ConfigItem(keyName="marketDataEnabled", name="External market data", description="Fetch selected item IDs and public high/low prices from the OSRS Wiki price API. Your IP address is visible to that service.", warning="This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers", position=3)
    default boolean marketDataEnabled() { return false; }
    @ConfigItem(keyName="diagnosticsEnabled", name="Diagnostic reporting", description="Not implemented; remains off", hidden=true, position=4)
    default boolean diagnosticsEnabled() { return false; }
    @Range(min=100_000, max=2_000_000_000)
    @ConfigItem(keyName="recommendationBudget", name="Legacy budget cap", description="Retained for settings compatibility; recommendations now use the visible coin stack in your inventory", hidden=true, position=5)
    default int recommendationBudget() { return 10_000_000; }
    @ConfigItem(keyName="includeMembersItems", name="Include members items", description="Include members-only items in opportunity recommendations", position=6)
    default boolean includeMembersItems() { return false; }
    @ConfigItem(keyName="quickSetKeybind", name="Legacy quick-set key", description="Retained for settings compatibility; values are now displayed for manual entry", hidden=true, position=7)
    default Keybind quickSetKeybind() { return new Keybind(KeyEvent.VK_E, 0); }
}
