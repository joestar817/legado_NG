package io.legado.app.quickjs;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

interface IQuickJsSandbox {
    Bundle evalString(in ParcelFileDescriptor script, int expectedChars);
}
