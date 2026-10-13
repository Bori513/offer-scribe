# Privacy

Offer Scribe has no telemetry and does not require an account.

External market data is an explicit opt-in and is disabled by default. When it
is enabled, the plugin requests public market data from the OSRS Wiki Prices
API. The service receives the requester's IP address as part of normal internet
operation. Bulk requests disclose no player data; selected-item requests include
only a public OSRS item ID.

The plugin reads the visible inventory coin count solely to size a recommendation
to available cash. Coin counts remain local. It does not read or send player
names, chat, friends, bank contents, unrelated inventory contents, credentials,
hardware identifiers, or game/Jagex session tokens.

Optional API export is disabled by default. When enabled, it sends one outbound
JSON request for each newly observed GE fill to the webhook URL selected by the
user. The disclosed fields are schema version, deterministic event ID, side,
item ID, public item name, filled quantity, unit price, total coins and event
time. The configured server necessarily receives the user's IP address. The
integration accepts no inbound commands and cannot control RuneLite. Failed
deliveries are retained in a local queue and retried.
