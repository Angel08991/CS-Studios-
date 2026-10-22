package com.csstudios.ixkits;

import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Small async HTTP client for the optional IxKits community API.
 */
public final class CatalogClient {
    private final JavaPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public CatalogClient(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("catalog.enabled", false) && !baseUrl().isBlank();
    }

    private String baseUrl() {
        String url = plugin.getConfig().getString("catalog.base-url", "").trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    public CompletableFuture<List<TemplateSummary>> search(String query, int page, int limit) {
        String path = "/api/templates?page=" + Math.max(1, page) + "&limit=" + Math.max(1, Math.min(50, limit));
        if (query != null && !query.isBlank()) path += "&query=" + encode(query);
        return get(path).thenApply(body -> {
            Object parsed = Json.parse(body);
            Map<String, Object> root = asMap(parsed);
            List<TemplateSummary> results = new ArrayList<>();
            Object rawItems = root.get("items");
            if (rawItems instanceof List<?> items) {
                for (Object raw : items) {
                    Map<String, Object> item = asMap(raw);
                    results.add(new TemplateSummary(
                        string(item, "id"), string(item, "title"), string(item, "author"),
                        string(item, "category"), string(item, "mcVersion"), string(item, "description"),
                        number(item.get("downloads")).longValue(),
                        number(item.get("averageRating")).doubleValue(),
                        number(item.get("ratingCount")).intValue()
                    ));
                }
            }
            return results;
        });
    }

    public CompletableFuture<DownloadedTemplate> download(String id) {
        return get("/api/templates/" + encodePath(id) + "/download").thenApply(body -> {
            Map<String, Object> root = asMap(Json.parse(body));
            String yaml = string(root, "projectYaml");
            if (yaml.isBlank()) throw new IllegalStateException("Catalog response did not contain project YAML.");
            return new DownloadedTemplate(string(root, "id"), string(root, "title"), yaml);
        });
    }

    public CompletableFuture<TemplateSummary> publish(String title, String author, String category,
                                                       String description, String minecraftVersion,
                                                       String license, String projectYaml) {
        String token = plugin.getConfig().getString("catalog.publish-token", "").trim();
        if (token.isBlank()) return CompletableFuture.failedFuture(new IllegalStateException("catalog.publish-token is empty in config.yml."));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("author", author);
        payload.put("category", category);
        payload.put("description", description);
        payload.put("mcVersion", minecraftVersion);
        payload.put("license", license);
        payload.put("projectYaml", projectYaml);
        return request("POST", "/api/templates", Json.stringify(payload), token).thenApply(body -> {
            Map<String, Object> item = asMap(Json.parse(body));
            return new TemplateSummary(string(item, "id"), string(item, "title"), string(item, "author"),
                string(item, "category"), string(item, "mcVersion"), string(item, "description"),
                number(item.get("downloads")).longValue(), number(item.get("averageRating")).doubleValue(),
                number(item.get("ratingCount")).intValue());
        });
    }

    public CompletableFuture<Void> rate(String templateId, String playerId, int rating) {
        Map<String, Object> payload = Map.of("reviewerId", playerId, "value", rating);
        return request("POST", "/api/templates/" + encodePath(templateId) + "/rate", Json.stringify(payload), null)
            .thenApply(ignored -> null);
    }

    public CompletableFuture<Void> toggleFavorite(String templateId, String playerId) {
        Map<String, Object> payload = Map.of("userId", playerId);
        return request("POST", "/api/templates/" + encodePath(templateId) + "/favorite", Json.stringify(payload), null)
            .thenApply(ignored -> null);
    }

    private CompletableFuture<String> get(String path) {
        return request("GET", path, null, null);
    }

    private CompletableFuture<String> request(String method, String path, String body, String bearer) {
        if (baseUrl().isBlank()) return CompletableFuture.failedFuture(new IllegalStateException("Set catalog.base-url in config.yml."));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json");
        if (bearer != null && !bearer.isBlank()) builder.header("Authorization", "Bearer " + bearer);
        if (body != null) builder.header("Content-Type", "application/json; charset=utf-8");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(response -> {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String detail = response.body();
                    if (detail.length() > 240) detail = detail.substring(0, 240);
                    throw new IllegalStateException("Catalog HTTP " + response.statusCode() + ": " + detail);
                }
                return response.body();
            });
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static Number number(Object value) {
        return value instanceof Number number ? number : 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Collections.emptyMap();
    }

    public record TemplateSummary(String id, String title, String author, String category, String mcVersion,
                                 String description, long downloads, double averageRating, int ratingCount) {}
    public record DownloadedTemplate(String id, String title, String projectYaml) {}

    /** Tiny dependency-free JSON codec for the API's object/array/string/number payloads. */
    private static final class Json {
        private final String input;
        private int index;

        private Json(String input) { this.input = input; }

        static Object parse(String input) {
            Json parser = new Json(input == null ? "" : input);
            Object value = parser.value();
            parser.whitespace();
            if (parser.index != parser.input.length()) throw new IllegalArgumentException("Unexpected characters in catalog JSON.");
            return value;
        }

        private Object value() {
            whitespace();
            if (index >= input.length()) throw new IllegalArgumentException("Unexpected end of catalog JSON.");
            char c = input.charAt(index);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == 't' && take("true")) return true;
            if (c == 'f' && take("false")) return false;
            if (c == 'n' && take("null")) return null;
            return numeric();
        }

        private Map<String, Object> object() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (peek('}')) { index++; return result; }
            while (true) {
                whitespace();
                String key = string();
                whitespace();
                expect(':');
                result.put(key, value());
                whitespace();
                if (peek('}')) { index++; return result; }
                expect(',');
            }
        }

        private List<Object> array() {
            expect('[');
            List<Object> result = new ArrayList<>();
            whitespace();
            if (peek(']')) { index++; return result; }
            while (true) {
                result.add(value());
                whitespace();
                if (peek(']')) { index++; return result; }
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (index < input.length()) {
                char c = input.charAt(index++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (index >= input.length()) throw new IllegalArgumentException("Bad escape in catalog JSON.");
                char escape = input.charAt(index++);
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (index + 4 > input.length()) throw new IllegalArgumentException("Bad Unicode escape in catalog JSON.");
                        out.append((char) Integer.parseInt(input.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("Bad escape in catalog JSON.");
                }
            }
            throw new IllegalArgumentException("Unterminated catalog JSON string.");
        }

        private Number numeric() {
            int start = index;
            while (index < input.length() && "-+0123456789.eE".indexOf(input.charAt(index)) >= 0) index++;
            if (start == index) throw new IllegalArgumentException("Bad number in catalog JSON.");
            String value = input.substring(start, index);
            try {
                if (value.contains(".") || value.contains("e") || value.contains("E")) return Double.parseDouble(value);
                return Long.parseLong(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Bad number in catalog JSON.", ex);
            }
        }

        private boolean take(String expected) {
            if (!input.startsWith(expected, index)) return false;
            index += expected.length();
            return true;
        }

        private void expect(char expected) {
            whitespace();
            if (index >= input.length() || input.charAt(index) != expected) throw new IllegalArgumentException("Malformed catalog JSON.");
            index++;
        }

        private boolean peek(char expected) { return index < input.length() && input.charAt(index) == expected; }

        private void whitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) index++;
        }

        static String stringify(Object value) {
            if (value == null) return "null";
            if (value instanceof String string) return quote(string);
            if (value instanceof Number || value instanceof Boolean) return value.toString();
            if (value instanceof Map<?, ?> map) {
                List<String> parts = new ArrayList<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) parts.add(quote(String.valueOf(entry.getKey())) + ":" + stringify(entry.getValue()));
                return "{" + String.join(",", parts) + "}";
            }
            if (value instanceof Iterable<?> values) {
                List<String> parts = new ArrayList<>();
                for (Object item : values) parts.add(stringify(item));
                return "[" + String.join(",", parts) + "]";
            }
            return quote(value.toString());
        }

        private static String quote(String value) {
            StringBuilder out = new StringBuilder("\"");
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                        else out.append(c);
                    }
                }
            }
            return out.append('"').toString();
        }
    }
}