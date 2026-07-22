package com.agentcraft.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared parser for {@code KEY=VALUE} env-style secrets files (e.g.
 * {@code /minecraft/fleet-secrets.env}). All loaders must use this so the same
 * file parses identically everywhere. Semantics (canonical, mirroring the
 * historical PersistenceManager parser):
 * <ul>
 *   <li>lines are trimmed; blank lines and {@code #} comments are skipped</li>
 *   <li>the first {@code =} splits key from value; lines without one (or with
 *       an empty key) are skipped</li>
 *   <li>keys and values are trimmed</li>
 *   <li>surrounding double quotes around a value are stripped</li>
 * </ul>
 */
public final class EnvFileParser {

    private EnvFileParser() {
    }

    /** Parses the full contents of a reader. The caller owns closing it. */
    public static Map<String, String> parse(Reader reader) throws IOException {
        BufferedReader buffered = reader instanceof BufferedReader b ? b : new BufferedReader(reader);
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = buffered.readLine()) != null) {
            lines.add(line);
        }
        return parse(lines);
    }

    public static Map<String, String> parse(List<String> lines) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            if (key.isEmpty()) continue;
            String value = line.substring(eq + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            result.put(key, value);
        }
        return result;
    }
}
