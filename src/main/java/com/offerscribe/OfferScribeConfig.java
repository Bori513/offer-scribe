package com.offerscribe;

import java.awt.event.KeyEvent;
import net.runelite.client.config.*;

@ConfigGroup("offerscribe")
public interface OfferScribeConfig extends Config {
    @ConfigItem(keyName="syncEnabled", name="Dashboard synchronization", description="Third-party sync; sends the disclosed GE event fields and exposes your IP to that server", warning="Sends pseudonymous GE offer/fill data to a third-party server not controlled or verified by RuneLite developers.", position=1)
    default boolean syncEnabled() { return false; }
    @ConfigItem(keyName="marketDataEnabled", name="External market data", description="Fetch selected item IDs and public high/low prices from the OSRS Wiki price API. Your IP address is visible to that service.", warning="Sends the selected GE item ID to the public OSRS Wiki price API; your IP address is necessarily visible to that service.", position=2)
    default boolean marketDataEnabled() { return false; }
    @ConfigItem(keyName="diagnosticsEnabled", name="Diagnostic reporting", description="Not implemented; remains off", position=3)
    default boolean diagnosticsEnabled() { return false; }
    @Range(min=100_000, max=2_000_000_000)
    @ConfigItem(keyName="recommendationBudget", name="Legacy budget cap", description="Retained for settings compatibility; recommendations now use the visible coin stack in your inventory", hidden=true, position=4)
    default int recommendationBudget() { return 10_000_000; }
    @ConfigItem(keyName="includeMembersItems", name="Include members items", description="Include members-only items in opportunity recommendations", position=5)
    default boolean includeMembersItems() { return false; }
    @ConfigItem(keyName="quickSetKeybind", name="Quick-set price", description="While the GE price prompt is open, fill only that field with the displayed recommendation; never confirms or submits", position=6)
    default Keybind quickSetKeybind() { return new Keybind(KeyEvent.VK_E, 0); }
}
