# Offer Scribe

Offer Scribe is a human-in-the-loop Grand Exchange assistant for RuneLite. It
uses public OSRS Wiki market data to rank possible flips, guides the player
through each stage of a trade, and records observed fills locally.

Offer Scribe never submits, confirms, cancels, collects, or chains gameplay
actions. Every game action remains the player's choice.

## Features

- Low, medium, and high-risk opportunity filters
- Recommendations sized to the visible coin stack in the player's inventory
- Automatic exclusion of members-only items while playing on an F2P world
- Public OSRS Wiki price, volume, item-mapping, and buy-limit data
- Tax-aware expected profit and ROI estimates
- Guided highlights for buy, collect, sell, and collect-proceeds stages
- Passive in-game prompts showing the exact quantity or price to enter manually
- Local observed-offer and fill history
- Optional GE fill export to a user-configured HTTP(S) webhook

Recommendations are estimates, not guarantees. Market prices can change before
an offer completes.

## Privacy and networking

External market data is disabled by default. When enabled, Offer Scribe sends
HTTP requests to the public OSRS Wiki Prices API. The service necessarily sees
the user's IP address. Bulk opportunity requests do not contain account,
character, inventory, or session information. A selected item lookup contains
only that public item ID.

Offer Scribe reads only the visible inventory coin count for position sizing. It
does not read or transmit names, chat, friends, bank contents, unrelated
inventory contents, credentials, hardware identifiers, or Jagex session data.
There is no telemetry.

API export is a separate explicit opt-in. It sends only the documented GE fill
fields to the complete webhook URL configured by the user. Remote endpoints
must use HTTPS; loopback endpoints may use HTTP. The local queue survives
restarts and retries with backoff. This is an outbound-only integration: the
receiver cannot submit or otherwise control game actions.

PixelDisplaySim works without an adapter by using its local endpoint:

```text
http://127.0.0.1:8765/api/v1/ge_update/
```

Any other application can implement the same JSON webhook contract. Offer
Scribe sends one `POST` per observed fill with `Content-Type: application/json`:

```json
{
  "schema_version": 1,
  "event_id": "6c5501aa-30ba-3f3d-a1b8-3c17fb85fdd2",
  "side": "buy",
  "item_id": 383,
  "name": "Raw shark",
  "quantity": 1000,
  "unit_price": 912,
  "total_coins": 912000,
  "occurred_at": 1791763200
}
```

The receiver should return any `2xx` response after accepting the event and
deduplicate retries by `event_id`. Only newly observed fills are exported;
loading local history does not resend it. The receiver should treat all fields
as untrusted.

## Development

Requirements: Java 11.

```sh
./gradlew clean test build
./gradlew run
```

The project uses RuneLite's `latest.release` dependency and the standard Plugin
Hub build. No third-party runtime dependencies are required.

## Plugin Hub submission

1. Create a public GitHub repository named `offer-scribe`.
2. Push the contents of this directory to its default branch.
3. Copy the full 40-character hash of the reviewed commit.
4. Fork `runelite/plugin-hub` and add `plugins/offer-scribe`:

   ```properties
   repository=https://github.com/Bori513/offer-scribe.git
   commit=YOUR_40_CHARACTER_COMMIT_HASH
   ```

5. Open a pull request to `runelite/plugin-hub` and follow its automated and
   human review feedback.

## License and attribution

Offer Scribe is distributed under the BSD 2-Clause License. See [LICENSE](LICENSE)
and [NOTICE.md](NOTICE.md). Certain user-interface interaction patterns were
adapted from the BSD-licensed Flipping Copilot project; its recommendation
service, branding, assets, and private APIs are not included.

Old School RuneScape and RuneLite are used descriptively. This project is not
affiliated with or endorsed by Jagex or the RuneLite developers.
