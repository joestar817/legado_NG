package io.legado.app.help.tts.packages;

import android.os.ParcelFileDescriptor;

interface IMicrosoftOfflineService {
    String synthesize(String revision, String voiceId, String text, in ParcelFileDescriptor output);
    void cancel();
}
