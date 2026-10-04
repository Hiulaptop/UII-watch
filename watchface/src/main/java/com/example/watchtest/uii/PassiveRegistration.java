package com.example.watchtest.uii;

import android.content.Context;

import androidx.health.services.client.HealthServices;
import androidx.health.services.client.PassiveMonitoringClient;
import androidx.health.services.client.data.DataType;
import androidx.health.services.client.data.HealthEvent;
import androidx.health.services.client.data.PassiveListenerConfig;
import androidx.health.services.client.data.PassiveMonitoringCapabilities;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Subscribes to heart rate and fall events, but only for what this watch says it supports. */
public final class PassiveRegistration {
    private static final Executor EXEC = Executors.newSingleThreadExecutor();

    private PassiveRegistration() {}

    public static void register(Context context) {
        final Context app = context.getApplicationContext();
        final UiiState state = Uii.get(app).state;
        final PassiveMonitoringClient client;
        try {
            client = HealthServices.getClient(app).getPassiveMonitoringClient();
        } catch (RuntimeException e) {
            state.setCapabilityNote("Health Services không khả dụng: " + e.getClass().getSimpleName());
            return;
        }

        final ListenableFuture<PassiveMonitoringCapabilities> caps = client.getCapabilitiesAsync();
        caps.addListener(() -> {
            try {
                PassiveMonitoringCapabilities c = caps.get();
                boolean hr = c.getSupportedDataTypesPassiveMonitoring().contains(DataType.HEART_RATE_BPM);
                boolean fall = c.getSupportedHealthEventTypes().contains(HealthEvent.Type.FALL_DETECTED);
                state.setSupport(hr, fall);
                if (!hr && !fall) return;

                Set<DataType<?, ?>> types = new HashSet<>();
                if (hr) types.add(DataType.HEART_RATE_BPM);
                Set<HealthEvent.Type> events = new HashSet<>();
                if (fall) events.add(HealthEvent.Type.FALL_DETECTED);

                PassiveListenerConfig config = new PassiveListenerConfig.Builder()
                        .setDataTypes(types)
                        .setHealthEventTypes(events)
                        .build();
                final ListenableFuture<Void> set = client.setPassiveListenerServiceAsync(HealthPassiveService.class, config);
                set.addListener(() -> {
                    try {
                        set.get();
                        state.setPermissionLost(false);
                    } catch (Exception e) {
                        state.setCapabilityNote("Đăng ký Health Services lỗi: " + rootName(e));
                    }
                }, EXEC);
            } catch (Exception e) {
                state.setCapabilityNote("Không đọc được khả năng của đồng hồ: " + rootName(e));
            }
        }, EXEC);
    }

    public static void unregister(Context context) {
        try {
            HealthServices.getClient(context.getApplicationContext()).getPassiveMonitoringClient().clearPassiveListenerServiceAsync();
        } catch (RuntimeException ignored) {
            // Nothing to clear.
        }
    }

    private static String rootName(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + (t.getMessage() != null ? " (" + t.getMessage() + ")" : "");
    }
}
