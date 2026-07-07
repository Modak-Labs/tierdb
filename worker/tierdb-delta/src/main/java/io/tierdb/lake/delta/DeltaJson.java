package io.tierdb.lake.delta;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DeltaJson {

    private DeltaJson() {}

    public static String write(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(e.getKey())).append(':').append(quote(e.getValue()));
        }
        return sb.append('}').toString();
    }

    public static Map<String, String> read(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null) {
            return out;
        }
        int i = 0;
        int n = json.length();
        while (i < n) {
            while (i < n && json.charAt(i) != '"') {
                i++;
            }
            if (i >= n) {
                break;
            }
            StringBuilder key = new StringBuilder();
            i = readString(json, i, key);
            while (i < n && json.charAt(i) != ':') {
                i++;
            }
            i++;
            while (i < n && json.charAt(i) != '"') {
                i++;
            }
            if (i >= n) {
                break;
            }
            StringBuilder value = new StringBuilder();
            i = readString(json, i, value);
            out.put(key.toString(), value.toString());
        }
        return out;
    }

    private static int readString(String s, int quotePos, StringBuilder into) {
        int i = quotePos + 1;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char next = s.charAt(i + 1);
                into.append(switch (next) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    default -> next;
                });
                i += 2;
                continue;
            }
            if (c == '"') {
                return i + 1;
            }
            into.append(c);
            i++;
        }
        return i;
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
