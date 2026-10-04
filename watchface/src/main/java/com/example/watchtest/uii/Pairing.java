package com.example.watchtest.uii;

import org.json.JSONException;
import org.json.JSONObject;

/** Exchanges the 8-digit code shown on the website for a device token and stores it. */
public final class Pairing {
    public enum Result { PAIRED, INVALID_CODE, TOO_MANY_DEVICES, RATE_LIMITED, NETWORK, ERROR }

    private Pairing() {}

    /** Digits only, exactly 8, or null. Spaces and dashes typed by the user are ignored. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("[\\s-]", "");
        return d.matches("\\d{8}") ? d : null;
    }

    public static Result pair(Api api, SecretStore store, String rawCode, String name, String model) {
        String code = normalize(rawCode);
        if (code == null) return Result.INVALID_CODE;

        ApiResult r = api.pair(code, name, model);
        switch (r.outcome) {
            case OK:
                try {
                    JSONObject o = new JSONObject(r.body);
                    String token = o.getString("token");
                    String id = o.getString("device_id");
                    if (token.isEmpty()) return Result.ERROR;
                    store.save(id, token);
                    return Result.PAIRED;
                } catch (JSONException e) {
                    return Result.ERROR;
                }
            case UNAUTHORIZED:
                return Result.INVALID_CODE;
            case REJECTED:
                return r.status == 409 ? Result.TOO_MANY_DEVICES : Result.ERROR;
            case RETRY:
            default:
                return r.status == 429 ? Result.RATE_LIMITED : Result.NETWORK;
        }
    }
}
