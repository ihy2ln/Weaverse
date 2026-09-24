package com.ihy2ln.weaverse.mihon.shizuku;

import android.content.res.AssetFileDescriptor;
import android.content.IntentSender;

interface IShellInterface {
    void install(in AssetFileDescriptor apk, in IntentSender intentSender) = 1;
    void destroy() = 16777114;
}
