package com.offerscribe;

import com.google.gson.Gson;
import com.offerscribe.model.TradeEvent;
import java.io.IOException;
import java.time.Instant;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Filepath;
import okhttp3.*;

/** Explicitly opt-in, idempotent event uploader. It never receives game-control commands. */
@Singleton
final class DashboardSyncClient {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String GROUP = "offerscribe", TOKEN = "dashboardToken", INSTALLATION = "installationId";
    private final OkHttpClient http; private final Gson gson; private final ConfigManager configs; private final OfferScribeConfig config;
    private final Filepath queueFile;
    private final LinkedHashMap<UUID, TradeEvent> pending = new LinkedHashMap<>();
    private final Set<Call> calls = ConcurrentHashMap.newKeySet();
    private Consumer<String> status = ignored -> {}; private boolean inFlight; private Instant nextAttempt = Instant.EPOCH; private int failures;
    private long queueVersion;

    @Inject DashboardSyncClient(OkHttpClient http, Gson gson, ConfigManager configs, OfferScribeConfig config,
        Filepath dataDirectory) {
        this.http=http; this.gson=gson; this.configs=configs; this.config=config;
        this.queueFile=dataDirectory.join("sync-queue.json");
    }
    String installationId() {
        String value = configs.getConfiguration(GROUP, INSTALLATION);
        if (value == null || value.isEmpty()) { value = UUID.randomUUID().toString(); configs.setConfiguration(GROUP, INSTALLATION, value); }
        return value;
    }
    void start(Consumer<String> listener) {
        status = listener == null ? ignored -> {} : listener;
        http.dispatcher().executorService().execute(this::loadQueue);
    }
    synchronized void stop() { for (Call call : calls) call.cancel(); calls.clear(); inFlight=false; }
    synchronized void enqueue(TradeEvent event) {
        if (pending.putIfAbsent(event.getEventId(), event) == null) persistQueue();
        tick();
    }
    synchronized void tick() {
        if (!config.syncEnabled() || inFlight || Instant.now().isBefore(nextAttempt)) return;
        HttpUrl base = validBase(); if (base == null) { status.accept("Sync needs a valid HTTPS dashboard URL"); return; }
        String token = configs.getConfiguration(GROUP, TOKEN);
        if (token == null || token.isEmpty()) { pair(base); return; }
        if (!pending.isEmpty()) upload(base, token);
        else status.accept("Dashboard connected");
    }
    private HttpUrl validBase() {
        HttpUrl value = HttpUrl.parse(config.dashboardUrl().trim()); if (value == null) return null;
        boolean local = "localhost".equals(value.host()) || "127.0.0.1".equals(value.host()) || "::1".equals(value.host());
        return value.isHttps() || local ? value : null;
    }
    private void pair(HttpUrl base) {
        String code = config.pairingCode().trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9]{8}")) { status.accept("Generate and enter an 8-character pairing code"); return; }
        Map<String,String> body = new LinkedHashMap<>(); body.put("code",code); body.put("installationId",installationId());
        send(base.resolve(path(base,"auth/pair")), null, gson.toJson(body), response -> {
            PairResult result = gson.fromJson(response, PairResult.class);
            if (result == null || result.token == null || result.token.length() < 40) throw new IllegalArgumentException("Invalid pairing response");
            configs.setConfiguration(GROUP,TOKEN,result.token); configs.unsetConfiguration(GROUP,"pairingCode"); status.accept("Dashboard paired");
        });
    }
    private synchronized void upload(HttpUrl base, String token) {
        List<TradeEvent> batch = new ArrayList<>(); for (TradeEvent event : pending.values()) { batch.add(event); if (batch.size()==100) break; }
        Map<String,Object> body = new LinkedHashMap<>(); body.put("events",batch);
        send(base.resolve(path(base,"trade-events/batch")), token, gson.toJson(body), response -> {
            synchronized (this) { for (TradeEvent event : batch) pending.remove(event.getEventId()); persistQueue(); }
            status.accept(pending.isEmpty() ? "Dashboard synced" : "Dashboard syncing…");
        });
    }
    private String path(HttpUrl base,String suffix) { String value=base.encodedPath(); return (value.endsWith("/")?value:value+"/")+suffix; }
    private void send(HttpUrl url, String token, String json, Consumer<String> success) {
        if (url == null) { status.accept("Invalid dashboard API URL"); return; }
        Request.Builder builder = new Request.Builder().url(url).post(RequestBody.create(JSON,json));
        if (token != null) builder.header("Authorization","Bearer "+token);
        Call call=http.newCall(builder.build()); calls.add(call); inFlight=true;
        call.enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException error) { completeFailure("Dashboard unavailable"); }
            @Override public void onResponse(Call call, Response response) { try (Response ignored=response) {
                String body=response.body()==null?"":response.body().string(); if (!response.isSuccessful()) { completeFailure("Dashboard returned "+response.code()); return; }
                try { success.accept(body); synchronized (DashboardSyncClient.this) { failures=0; nextAttempt=Instant.EPOCH; } }
                catch (RuntimeException error) { completeFailure("Invalid dashboard response"); return; }
                finish(call); tick();
            } catch (IOException error) { completeFailure("Dashboard response failed"); } }
            private void completeFailure(String message) { synchronized (DashboardSyncClient.this) { failures=Math.min(8,failures+1); nextAttempt=Instant.now().plusSeconds(Math.min(300,1L<<failures)); } status.accept(message+"; retry scheduled"); finish(call); }
        });
    }
    private synchronized void finish(Call call) { calls.remove(call); inFlight=false; }
    private void loadQueue() {
        if (queueFile.exists()) try {
            TradeEvent[] values=gson.fromJson(queueFile.openReader(),TradeEvent[].class);
            synchronized (this) { if(values!=null)for(TradeEvent event:values)pending.putIfAbsent(event.getEventId(),event); }
        } catch (IOException|RuntimeException ignored) { status.accept("Local sync queue could not be read"); }
        tick();
    }
    private synchronized void persistQueue() {
        List<TradeEvent> snapshot=new ArrayList<>(pending.values()); long version=++queueVersion;
        http.dispatcher().executorService().execute(() -> {
            Filepath parent=queueFile.getParent(); Filepath temporary=parent.join("sync-queue-"+version+".tmp");
            try { parent.createDirectories(); temporary.write(gson.toJson(snapshot),StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE);
                synchronized (this) {
                    if(version!=queueVersion){temporary.deleteIfExists();return;}
                    try { temporary.moveTo(queueFile,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException ignored) { temporary.moveTo(queueFile,StandardCopyOption.REPLACE_EXISTING); }
                }
            } catch (IOException ignored) { status.accept("Local sync queue could not be saved"); }
        });
    }
    private static final class PairResult { String token; }
}
