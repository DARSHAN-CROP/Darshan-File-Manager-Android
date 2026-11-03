package com.darshan.filemanager;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * Auto mode. Android's own JobScheduler runs this every 15 minutes (the minimum Android allows),
 * keeps it across reboots (setPersisted) and runs it even when the app is closed.
 */
public class AutoSortJob extends JobService {
    static final int JOB_ID = 1001;
    static final long PERIOD_MS = 15 * 60 * 1000L;

    static boolean schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return false;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, AutoSortJob.class))
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .build();
        return js.schedule(job) == JobScheduler.RESULT_SUCCESS;
    }

    static void cancel(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) js.cancel(JOB_ID);
    }

    static boolean isScheduled(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return false;
        for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return true;
        return false;
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context c = getApplicationContext();
        if (!Store.autoOn(c)) return false;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (Store.hasAllFilesAccess(c)) Store.runSort(c, Store.autoMode(c), true);
                    else Store.log(c, "Auto mode: All files access is off, nothing moved");
                } catch (Throwable t) {
                    Store.log(c, "Auto mode error: " + t);
                }
                jobFinished(params, false);
            }
        }, "auto-sort").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return false; }
}
