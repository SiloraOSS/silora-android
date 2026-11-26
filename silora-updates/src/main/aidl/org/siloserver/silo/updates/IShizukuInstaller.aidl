package org.siloserver.silo.updates;

import android.os.ParcelFileDescriptor;
import android.content.IntentSender;
import org.siloserver.silo.updates.IUpdateCommitCheck;

interface IShizukuInstaller {
    boolean install(String packageName, in ParcelFileDescriptor[] apks, in String[] names, in long[] sizes,
                    in IntentSender result, IUpdateCommitCheck commitCheck) = 1;
    int getUid() = 2;
    void destroy() = 16777114;
}
