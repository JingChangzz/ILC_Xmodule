package android.app;

import android.os.Bundle;
import android.os.IBinder;

public abstract class BroadcastReceiver extends android.content.BroadcastReceiver{
    public android.content.BroadcastReceiver.PendingResult mPendingResult;

    public static class PendingResult {
        final int mType;
        final boolean mOrderedHint;
        final boolean mInitialStickyHint;
        final IBinder mToken;
        final int mSendingUser;

        int mResultCode;
        String mResultData;
        Bundle mResultExtras;
        boolean mAbortBroadcast;
        boolean mFinished;

        public PendingResult(int resultCode, String resultData, Bundle resultExtras, int type,
                             boolean ordered, boolean sticky, IBinder token, int userId) {
            mResultCode = resultCode;
            mResultData = resultData;
            mResultExtras = resultExtras;
            mType = type;
            mOrderedHint = ordered;
            mInitialStickyHint = sticky;
            mToken = token;
            mSendingUser = userId;
        }
    }
}
