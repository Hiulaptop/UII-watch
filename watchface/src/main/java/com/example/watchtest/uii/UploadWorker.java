package com.example.watchtest.uii;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Sends whatever is waiting in the outbox. WorkManager runs it when a network is available and retries with backoff. */
public final class UploadWorker extends Worker {
    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override public Result doWork() {
        Uii uii = Uii.get(getApplicationContext());
        Uploader.Result r = uii.flushNow();

        // A fall still inside its cancel window: wake up again exactly when it becomes due.
        if (r.nextDueMs > 0) UploadScheduler.kickAt(getApplicationContext(), r.nextDueMs);
        return r.state == Uploader.State.RETRY_LATER ? Result.retry() : Result.success();
    }
}
