package io.legado.app.help.tts.packages;

import androidx.annotation.Keep;
import java.io.OutputStream;
import java.util.Map;

/** Stable host boundary shared with the external, signed engine adapter. */
@Keep
public interface EnginePlugin extends AutoCloseable {
    String buildId();
    void prepare(Map<String, String> resources) throws Exception;
    void synthesize(String text, int speakerId, float speed, OutputStream wav) throws Exception;
    @Override void close();
}
