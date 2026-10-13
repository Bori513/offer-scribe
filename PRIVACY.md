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

Optional dashboard synchronization is disabled by default. When enabled and
paired, it sends pseudonymous installation/profile/session IDs and immutable GE
fill facts: time, slot, item ID, side, price, quantity and coin deltas,
cumulative totals, offer state, source, tax-rule version and plugin version.
The configured server necessarily receives the user's IP address. No dashboard
endpoint can control RuneLite.
