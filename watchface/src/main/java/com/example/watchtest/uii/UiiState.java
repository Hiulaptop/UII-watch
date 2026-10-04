package com.example.watchtest.uii;

import android.content.Context;
import android.content.SharedPreferences;

/** Small non-secret facts the home screen shows and the services share. */
public final class UiiState {
    private final SharedPreferences p;

    public UiiState(Context c) {
        p = c.getApplicationContext().getSharedPreferences("uii_state", Context.MODE_PRIVATE);
    }

    /** What Health Services says this watch supports: -1 not checked yet, 0 no, 1 yes. */
    public int heartRateSupport() { return p.getInt("support_hr", -1); }
    public int fallSupport() { return p.getInt("support_fall", -1); }
    public void setSupport(boolean heartRate, boolean fall) {
        p.edit().putInt("support_hr", heartRate ? 1 : 0).putInt("support_fall", fall ? 1 : 0).putString("capability_note", "").apply();
    }

    /** A problem reading or registering for Health Services, shown as is. */
    public String capabilityNote() { return p.getString("capability_note", ""); }
    public void setCapabilityNote(String s) { p.edit().putString("capability_note", s).apply(); }

    /** The server refused this watch's token, so the pairing was dropped. */
    public boolean pairingRevoked() { return p.getBoolean("pairing_revoked", false); }
    public void setPairingRevoked(boolean v) { p.edit().putBoolean("pairing_revoked", v).apply(); }

    public String motionStatus() { return p.getString("motion", "tắt"); }
    public void setMotionStatus(String s) { p.edit().putString("motion", s).apply(); }

    public long lastUploadMs() { return p.getLong("last_upload", 0); }
    public void setLastUploadMs(long t) { p.edit().putLong("last_upload", t).apply(); }

    public String lastUploadState() { return p.getString("last_upload_state", "-"); }
    public void setLastUploadState(String s) { p.edit().putString("last_upload_state", s).apply(); }

    public long lastFallMs() { return p.getLong("last_fall", 0); }
    public void setLastFallMs(long t) { p.edit().putLong("last_fall", t).apply(); }

    /** Ids of alerts the server refused for good, so the screen does not report them as sent. */
    public boolean isRejected(String id) { return RecentIds.contains(p.getString("rejected", ""), id); }
    public void addRejected(String id) { p.edit().putString("rejected", RecentIds.add(p.getString("rejected", ""), id, 20)).commit(); }

    public boolean permissionLost() { return p.getBoolean("permission_lost", false); }
    public void setPermissionLost(boolean v) { p.edit().putBoolean("permission_lost", v).apply(); }
}
