package dev.hachifridge.hachidroid.shizuku;

import android.os.ParcelFileDescriptor;

interface IInstallerService {
    String install(in List<String> apkPaths);
    String installFromFds(in List<ParcelFileDescriptor> fds, in List<String> names);
}
