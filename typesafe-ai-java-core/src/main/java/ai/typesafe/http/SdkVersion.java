package ai.typesafe.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Resolves the SDK version from build metadata ({@code sdk.properties},
 * populated by Maven resource filtering) so the User-Agent always reports the
 * released version. Falls back to {@code unknown} when the resource is absent,
 * e.g. when running from an IDE without a Maven build.
 */
final class SdkVersion {

    private SdkVersion() {
    }

    static String version() {
        try (InputStream in = SdkVersion.class.getResourceAsStream("/ai/typesafe/sdk.properties")) {
            if (in != null) {
                String v = new Properties().getProperty("version");
                if (v != null && !v.isBlank() && !v.contains("${")) {
                    return v.trim();
                }
            }
        } catch (IOException e) {
            // fall through
        }
        return "unknown";
    }
}
