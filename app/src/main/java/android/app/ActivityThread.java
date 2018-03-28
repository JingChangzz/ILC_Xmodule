package android.app;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;

public class ActivityThread{


final class ReceiverData extends BroadcastReceiver.PendingResult {
    public ReceiverData(Intent intent, int resultCode, String resultData, Bundle resultExtras,
                        boolean ordered, boolean sticky, IBinder token, int sendingUser) {
        super(resultCode, resultData, resultExtras, 0, ordered, sticky,
                token, sendingUser);
        this.intent = intent;
    }

    public Intent intent;
    ActivityInfo info;
    //    CompatibilityInfo compatInfo;
}
}