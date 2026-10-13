package com.offerscribe;

import com.google.gson.Gson;
import com.offerscribe.model.TradeEvent;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.util.Filepath;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Opt-in, outbound-only delivery of documented GE fill events to a user-selected API. */
@Singleton
final class FillWebhookClient
{
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient http;
    private final Gson gson;
    private final OfferScribeConfig config;
    private Filepath queueFile;
    private final LinkedHashMap<UUID, WebhookFill> pending = new LinkedHashMap<>();
    private final Set<Call> calls = ConcurrentHashMap.newKeySet();
    private Consumer<String> status = ignored -> { };
    private boolean inFlight;
    private Instant nextAttempt = Instant.EPOCH;
    private int failures;
    private long queueVersion;

    @Inject
    FillWebhookClient(OkHttpClient http, Gson gson, OfferScribeConfig config)
    {
        this.http = http;
        this.gson = gson;
        this.config = config;
    }

    void start(Filepath dataDirectory, Consumer<String> listener)
    {
        this.queueFile = dataDirectory.join("webhook-queue.json");
        status = listener == null ? ignored -> { } : listener;
        http.dispatcher().executorService().execute(this::loadQueue);
    }

    synchronized void stop()
    {
        for (Call call : calls)
        {
            call.cancel();
        }
        calls.clear();
        inFlight = false;
    }

    synchronized void enqueue(TradeEvent event, String itemName)
    {
        WebhookFill fill = WebhookFill.from(event, itemName);
        if (pending.putIfAbsent(fill.event_id, fill) == null)
        {
            persistQueue();
        }
        tick();
    }

    synchronized void tick()
    {
        if (!config.webhookEnabled())
        {
            status.accept("API export off");
            return;
        }
        if (inFlight || Instant.now().isBefore(nextAttempt))
        {
            return;
        }
        HttpUrl endpoint = validEndpoint();
        if (endpoint == null)
        {
            status.accept("API needs a valid HTTPS URL (HTTP is local-only)");
            return;
        }
        if (pending.isEmpty())
        {
            status.accept("API ready");
            return;
        }
        upload(endpoint, pending.values().iterator().next());
    }

    private HttpUrl validEndpoint()
    {
        HttpUrl value = HttpUrl.parse(config.webhookUrl().trim());
        if (value == null)
        {
            return null;
        }
        boolean loopback = "localhost".equalsIgnoreCase(value.host())
            || "127.0.0.1".equals(value.host()) || "::1".equals(value.host());
        return value.isHttps() || loopback && "http".equals(value.scheme()) ? value : null;
    }

    private synchronized void upload(HttpUrl endpoint, WebhookFill fill)
    {
        Request request = new Request.Builder().url(endpoint)
            .post(RequestBody.create(JSON, gson.toJson(fill))).build();
        Call call = http.newCall(request);
        calls.add(call);
        inFlight = true;
        call.enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException error)
            {
                completeFailure(call, "API unavailable");
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try (Response ignored = response)
                {
                    if (!response.isSuccessful())
                    {
                        completeFailure(call, "API returned " + response.code());
                        return;
                    }
                    synchronized (FillWebhookClient.this)
                    {
                        pending.remove(fill.event_id);
                        failures = 0;
                        nextAttempt = Instant.EPOCH;
                        persistQueue();
                        finish(call);
                    }
                    status.accept(pending.isEmpty() ? "API synced" : "API syncing…");
                    tick();
                }
            }
        });
    }

    private void completeFailure(Call call, String message)
    {
        synchronized (this)
        {
            failures = Math.min(8, failures + 1);
            nextAttempt = Instant.now().plusSeconds(Math.min(300, 1L << failures));
            finish(call);
        }
        status.accept(message + "; retry scheduled");
    }

    private synchronized void finish(Call call)
    {
        calls.remove(call);
        inFlight = false;
    }

    private void loadQueue()
    {
        if (queueFile.exists())
        {
            try
            {
                WebhookFill[] values = gson.fromJson(queueFile.openReader(), WebhookFill[].class);
                synchronized (this)
                {
                    if (values != null)
                    {
                        for (WebhookFill value : values)
                        {
                            if (value != null && value.event_id != null)
                            {
                                pending.putIfAbsent(value.event_id, value);
                            }
                        }
                    }
                }
            }
            catch (IOException | RuntimeException ignored)
            {
                status.accept("Local API queue could not be read");
            }
        }
        tick();
    }

    private synchronized void persistQueue()
    {
        List<WebhookFill> snapshot = new ArrayList<>(pending.values());
        long version = ++queueVersion;
        http.dispatcher().executorService().execute(() -> {
            Filepath parent = queueFile.getParent();
            Filepath temporary = parent.join("webhook-queue-" + version + ".tmp");
            try
            {
                parent.createDirectories();
                temporary.write(gson.toJson(snapshot), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                synchronized (this)
                {
                    if (version != queueVersion)
                    {
                        temporary.deleteIfExists();
                        return;
                    }
                    try
                    {
                        temporary.moveTo(queueFile, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                    }
                    catch (AtomicMoveNotSupportedException ignored)
                    {
                        temporary.moveTo(queueFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            catch (IOException ignored)
            {
                status.accept("Local API queue could not be saved");
            }
        });
    }

    /** Stable public webhook schema; snake_case is directly compatible with PixelDisplaySim. */
    private static final class WebhookFill
    {
        private final int schema_version = 1;
        private final UUID event_id;
        private final String side;
        private final int item_id;
        private final String name;
        private final int quantity;
        private final long unit_price;
        private final long total_coins;
        private final long occurred_at;

        private WebhookFill(UUID eventId, String side, int itemId, String name, int quantity,
            long unitPrice, long totalCoins, long occurredAt)
        {
            this.event_id = eventId;
            this.side = side;
            this.item_id = itemId;
            this.name = name;
            this.quantity = quantity;
            this.unit_price = unitPrice;
            this.total_coins = totalCoins;
            this.occurred_at = occurredAt;
        }

        private static WebhookFill from(TradeEvent event, String itemName)
        {
            long unitPrice = event.getQuantityDelta() == 0 ? event.getPricePerItem()
                : event.getCoinsDelta() / event.getQuantityDelta();
            if (unitPrice <= 0)
            {
                unitPrice = event.getPricePerItem();
            }
            return new WebhookFill(event.getEventId(), event.getSide().name().toLowerCase(Locale.ROOT),
                event.getItemId(), itemName, event.getQuantityDelta(), unitPrice,
                event.getCoinsDelta(), event.getObservedAt().getEpochSecond());
        }
    }
}
