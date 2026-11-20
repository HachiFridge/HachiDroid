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
 * Replacement Application for a patched UM:PD Global install.
 *
 * Global's stock Application ({@code com.cygames.umamusume.EntryApplication}) belongs to the
 * native wrapper: its {@code attachBaseContext} invokes the shell entrypoint, which loads the
 * stub payload and prepares {@code libil2cpp.so}. The patched split carries the already-restored
 * library and the Hachimi runtime as {@code libmain.so}, so none of that needs to run.
 *
 * This class does two things and nothing else: keep the entry class's static context valid, and
 * ask for the storage permissions the game needs on modern Android. It deliberately loads no
 * library — the mod runtime is the loader itself.
 */
public class GlbApplication extends Application {
    private static final String TAG = "GlbApplication";

    private boolean mPermissionsRequested = false;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        // The game's entry Application keeps a static context that other classes read. Pointing
        // it here keeps those reads valid without running any packer initialization.
        try {
            Class<?> entryAppClass = Class.forName("com.cygames.umamusume.EntryApplication");
            java.lang.reflect.Field field = entryAppClass.getField("mContext");
            field.set(null, this);
            Log.i(TAG, "EntryApplication.mContext reflected successfully");
        } catch (Throwable t) {
            Log.w(TAG, "EntryApplication.mContext reflection note: " + t.getMessage());
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
