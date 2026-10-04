package com.example.watchtest.uii;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** When to run {@link UploadWorker}. All work needs a network; WorkManager waits for one if there is none. */
public final class UploadScheduler {
    private UploadScheduler() {}

    private static Constraints online() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    /** Signal batches: no hurry, queued behind any run in progress. */
    public static void kick(Context c) {
        OneTimeWorkRequest w = new OneTimeWorkRequest.Builder(UploadWorker.class)
                .setConstraints(online())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(c).enqueueUniqueWork("uii-upload", ExistingWorkPolicy.APPEND_OR_REPLACE, w);
    }

    /** Alerts: run as soon as possible, ahead of ordinary background work. */
    public static void kickUrgent(Context c) {
        OneTimeWorkRequest w = new OneTimeWorkRequest.Builder(UploadWorker.class)
                .setConstraints(online())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(c).enqueueUniqueWork("uii-upload-urgent", ExistingWorkPolicy.APPEND_OR_REPLACE, w);
    }

    /** Wake up when a held-back alert (a fall waiting out its cancel window) becomes due. */
    public static void kickAt(Context c, long atMs) {
        long delay = Math.max(0, atMs - System.currentTimeMillis());
        OneTimeWorkRequest w = new OneTimeWorkRequest.Builder(UploadWorker.class)
                .setConstraints(online())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(c).enqueueUniqueWork("uii-upload-due", ExistingWorkPolicy.REPLACE, w);
    }

    /** Safety net: anything left behind is retried every 15 minutes. */
    public static void ensurePeriodic(Context c) {
        PeriodicWorkRequest w = new PeriodicWorkRequest.Builder(UploadWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(online())
                .build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("uii-upload-periodic", ExistingPeriodicWorkPolicy.KEEP, w);
    }

    public static void cancelAll(Context c) {
        WorkManager wm = WorkManager.getInstance(c);
        wm.cancelUniqueWork("uii-upload");
        wm.cancelUniqueWork("uii-upload-urgent");
        wm.cancelUniqueWork("uii-upload-due");
        wm.cancelUniqueWork("uii-upload-periodic");
    }
}
