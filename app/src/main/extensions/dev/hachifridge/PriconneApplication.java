package dev.hachifridge;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Replacement Application for a patched Priconne install.
 *
 * The game's own Application class lives in the CrackProof-protected shell, so replacing it is
 * what lets the game start at all once that shell is gone. This class does three things and
 * nothing else: hand the real entry point its context, load the mod library, and ask for the
 * storage permissions the game needs on modern Android.
 *
 * It is compiled into extensions.dex and injected by AstraeaApkPatcher along with the manifest
 * android:name redirect. Never call into game code here beyond the reflection below: this runs
 * before anything else and an exception is a crash loop with no way back.
 */
public class PriconneApplication extends Application {
    private static final String TAG = "PriconneApplication";

    private boolean mPermissionsRequested = false;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        // The game's entry Application keeps a static context that other classes read. Pointing
        // it here keeps those reads valid without running any CrackProof initialization.
        try {
            Class<?> entryAppClass = Class.forName("jp.co.cygames.princessconnectredive.EntryApplication");
            java.lang.reflect.Field field = entryAppClass.getField("mContext");
            field.set(null, this);
            Log.i(TAG, "EntryApplication.mContext reflected successfully");
        } catch (Throwable t) {
            Log.w(TAG, "EntryApplication.mContext reflection note: " + t.getMessage());
        }

        try {
            Log.i(TAG, "Loading libdaringtact.so...");
            System.loadLibrary("daringtact");
            Log.i(TAG, "libdaringtact.so loaded successfully");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load libdaringtact.so", t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(Activity activity) {}

            @Override
            public void onActivityResumed(final Activity activity) {
                if (mPermissionsRequested) return;
                if (activity.getWindow() == null || activity.getWindow().getDecorView() == null) {
                    checkAndRequestPermissions(activity);
                    return;
                }
                activity.getWindow().getDecorView().post(new Runnable() {
                    @Override
                    public void run() {
                        if (!mPermissionsRequested && !activity.isFinishing() && !activity.isDestroyed()) {
                            checkAndRequestPermissions(activity);
                        }
                    }
                });
            }

            @Override
            public void onActivityPaused(Activity activity) {}

            @Override
            public void onActivityStopped(Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {}
        });
    }

    private void checkAndRequestPermissions(Activity activity) {
        List<String> needPerms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            String[] perms = {
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO"
            };
            for (String p : perms) {
                if (activity.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    needPerms.add(p);
                }
            }
        } else {
            String[] perms = {
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE"
            };
            for (String p : perms) {
                if (activity.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    needPerms.add(p);
                }
            }
        }

        if (needPerms.isEmpty()) {
            Log.i(TAG, "All required storage/media permissions are already granted");
            return;
        }

        mPermissionsRequested = true;
        Log.i(TAG, "Requesting missing permissions: " + needPerms);
        activity.requestPermissions(needPerms.toArray(new String[0]), 1001);
    }
}
