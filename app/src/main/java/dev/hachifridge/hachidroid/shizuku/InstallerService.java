package dev.hachifridge.hachidroid.shizuku;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.hachifridge.hachidroid.shizuku.IInstallerService;

/**
 * Privileged install path served over a Shizuku binder. The public
 * {@link PackageInstaller} APIs cannot reach {@code IPackageInstaller} with an
 * elevated identity, so this service reflects into {@code ServiceManager} /
 * {@code IPackageManager$Stub} / {@code IPackageInstaller}, guarded at runtime
 * by the Shizuku permission check.
 */
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
public class InstallerService extends IInstallerService.Stub {

    @FunctionalInterface
    private interface SessionStreamWriter {
        void writeTo(PackageInstaller.Session session) throws Exception;
    }

    private static Object getPackageManager() throws Exception {
        Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
        Method getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String.class);
        IBinder binder = (IBinder) getServiceMethod.invoke(null, "package");
        Class<?> stubClass = Class.forName("android.content.pm.IPackageManager$Stub");
        Method asInterfaceMethod = stubClass.getDeclaredMethod("asInterface", IBinder.class);
        return asInterfaceMethod.invoke(null, binder);
    }

    private static Object getPackageInstaller(Object iPackageManager) throws Exception {
        Method getPackageInstallerMethod = iPackageManager.getClass().getMethod("getPackageInstaller");
        return getPackageInstallerMethod.invoke(iPackageManager);
    }

    private static String getStatusDescription(int status) {
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                return "Signature or package conflict (the app may already be installed with a different signature)";
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                return "Insufficient device storage";
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return "Package is incompatible with this device or ABI";
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                return "Blocked by device administrator or package verifier";
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                return "Installation cancelled or aborted";
            case PackageInstaller.STATUS_FAILURE_INVALID:
                return "Invalid APK package or corrupted file";
            default:
                return "Installation failed";
        }
    }

    @Override
    public String install(List<String> apkPaths) throws RemoteException {
        return runInstallerSession(session -> {
            for (String apkPath : apkPaths) {
                File file = new File(apkPath);
                try (InputStream in = new FileInputStream(file);
                     OutputStream out = session.openWrite(file.getName(), 0, file.length())) {
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                    }
                    session.fsync(out);
                }
            }
        });
    }

    @Override
    public String installFromFds(List<ParcelFileDescriptor> fds, List<String> names) throws RemoteException {
        try {
            return runInstallerSession(session -> {
                for (int i = 0; i < fds.size(); i++) {
                    ParcelFileDescriptor pfd = fds.get(i);
                    if (pfd == null) continue;
                    String name = (names != null && i < names.size()) ? names.get(i) : (i + ".apk");
                    long size = pfd.getStatSize();
                    if (size < 0) size = 0;

                    try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                         OutputStream out = session.openWrite(name, 0, size)) {
                        byte[] buffer = new byte[65536];
                        int count;
                        while ((count = in.read(buffer)) != -1) {
                            out.write(buffer, 0, count);
                        }
                        session.fsync(out);
                    }
                }
            });
        } finally {
            // Ensure any unconsumed or remaining descriptors are closed on this end
            if (fds != null) {
                for (ParcelFileDescriptor pfd : fds) {
                    if (pfd != null) {
                        try { pfd.close(); } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    private String runInstallerSession(SessionStreamWriter writer) {
        Object iPackageManager;
        Object iPackageInstaller;

        try {
            iPackageManager = getPackageManager();
            if (iPackageManager == null) return "Failed to get PackageManager service.";

            iPackageInstaller = getPackageInstaller(iPackageManager);
            if (iPackageInstaller == null) return "Failed to get PackageInstaller service.";
        } catch (Exception e) {
            return "Failed to get system services: " + e.getMessage();
        }

        PackageInstaller.Session session = null;
        try {
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            try {
                Field installFlags = params.getClass().getDeclaredField("installFlags");
                installFlags.setAccessible(true);
                installFlags.setInt(params, 0x00000002 /* INSTALL_REPLACE_EXISTING */);
            } catch (Exception e) {
                System.err.println("Failed to set installFlags: " + e.getMessage());
            }

            String installerPackageName = "com.android.vending";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    Method setPackageSourceMethod = params.getClass().getMethod("setPackageSource", int.class);
                    setPackageSourceMethod.invoke(params, PackageInstaller.PACKAGE_SOURCE_STORE);
                } catch (Exception e) {
                    System.err.println("Failed to set package source: " + e.getMessage());
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                params.setInstallerPackageName(installerPackageName);
            }

            int sessionId;
            int userId = 0;
            Class<?> iPackageInstallerClass = iPackageInstaller.getClass();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Method createSessionMethod = iPackageInstallerClass.getMethod("createSession", PackageInstaller.SessionParams.class, String.class, String.class, int.class);
                sessionId = (int) createSessionMethod.invoke(iPackageInstaller, params, installerPackageName, installerPackageName, userId);
            } else {
                Method createSessionMethod = iPackageInstallerClass.getMethod("createSession", PackageInstaller.SessionParams.class, String.class, int.class);
                sessionId = (int) createSessionMethod.invoke(iPackageInstaller, params, installerPackageName, userId);
            }

            PackageInstaller packageInstaller;
            try {
                Class<?> packageInstallerClass = Class.forName("android.content.pm.PackageInstaller");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Constructor<?> constructor = packageInstallerClass.getConstructor(Class.forName("android.content.pm.IPackageInstaller"), String.class, String.class, int.class);
                    packageInstaller = (PackageInstaller) constructor.newInstance(iPackageInstaller, "com.android.shell", null, 0);
                } else {
                    Constructor<?> constructor = packageInstallerClass.getConstructor(Class.forName("android.content.pm.IPackageInstaller"), String.class, int.class);
                    packageInstaller = (PackageInstaller) constructor.newInstance(iPackageInstaller, "com.android.shell", 0);
                }
            } catch (Exception e) {
                return "Failed to create public PackageInstaller wrapper: " + e;
            }

            session = packageInstaller.openSession(sessionId);

            // Stream APKs into the session
            writer.writeTo(session);

            final CountDownLatch latch = new CountDownLatch(1);
            final Intent[] resultIntent = new Intent[1];

            IntentSender intentSender = IntentSenderUtils.newInstance(new IIntentSenderAdaptor() {
                @Override
                public void send(Intent intent) {
                    resultIntent[0] = intent;
                    latch.countDown();
                }
            });

            session.commit(intentSender);
            // Multi-gigabyte split package installs / dexopt can take longer than 60 seconds on budget devices
            if (!latch.await(300, TimeUnit.SECONDS)) {
                return "Installation timed out: no result from PackageInstaller after 300s.";
            }
            if (resultIntent[0] == null) {
                return "Installation failed: no result from PackageInstaller.";
            }

            int status = resultIntent[0].getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_SUCCESS) {
                return null;
            } else {
                String message = resultIntent[0].getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                String detail = (message != null && !message.isEmpty()) ? message : getStatusDescription(status);
                return "Installation failed with status " + status + ": " + detail;
            }
        } catch (Exception e) {
            if (session != null) {
                try { session.abandon(); } catch (Exception ignored) {}
            }
            return e.getMessage();
        }
    }
}
