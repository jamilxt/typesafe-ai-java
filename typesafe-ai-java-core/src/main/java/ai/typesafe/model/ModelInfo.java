package ai.typesafe.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import ai.typesafe.exception.TypeSafeException;
import ai.typesafe.json.Json;

import java.util.List;
import java.util.Map;

import static ai.typesafe.json.Json.MAPPER;

/**
 * One entry of the {@code GET /v1/models} response: a model id plus any
 * metadata the API returns alongside it (unknown fields are preserved).
 *
 * <p>Parsed leniently: an entry that is a plain string becomes a
 * {@code ModelInfo} with only {@link #id()} set, so older wire shapes keep
 * working.</p>
 */
public record ModelInfo(String id, Map<String, Object> metadata) {

    public ModelInfo {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Convenience constructor for an id with no metadata. */
    public static ModelInfo of(String id) {
        return new ModelInfo(id, Map.of());
    }

    /** Reads a metadata value as a String, or null when absent or of another type. */
    public String string(String field) {
        Object v = metadata.get(field);
        return v instanceof String s ? s : null;
    }

    /** Reads a metadata value as a Number, or null when absent or of another type. */
    public Number number(String field) {
        Object v = metadata.get(field);
        return v instanceof Number n ? n : null;
    }

    /** Reads a metadata value as a List, or null when absent or of another type. */
    public List<Object> list(String field) {
        Object v = metadata.get(field);
        return v instanceof List<?> l ? List.copyOf(l) : null;
    }

    /**
     * Parses a {@code GET /v1/models} response body. Entries may be plain
     * strings ({@code ["jev-latest"]}) or objects carrying an id plus metadata
     * ({@code [{"id":"jev-latest","created":...}]}) — both leniently accepted.
     */
    public static java.util.List<ModelInfo> parseModels(String body) {
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode models = root.path("models");
            if (models.isMissingNode() && root.isArray()) {
                models = root;
            }
            java.util.List<ModelInfo> out = new java.util.ArrayList<>();
            if (models.isArray()) {
                for (JsonNode entry : models) {
                    if (entry.isTextual()) {
                        out.add(ModelInfo.of(entry.asText()));
                    } else if (entry.isObject()) {
                        String id = entry.has("id") ? entry.get("id").asText(null)
                                : entry.has("model") ? entry.get("model").asText(null) : null;
                        if (id != null) {
                            Map<String, Object> meta = MAPPER.convertValue(entry,
                                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
                            meta.remove("id");
                            meta.remove("model");
                            out.add(new ModelInfo(id, meta));
                        }
                    }
                }
            }
            return java.util.List.copyOf(out);
        } catch (Exception e) {
            throw new TypeSafeException("Failed to parse models response", e);
        }
    }
}
