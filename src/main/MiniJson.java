package main;

import java.util.*;

/**
 * Minimal, dependency-free JSON parser. Supports objects, arrays, strings (with \n \" \\ \t \r XXXX
 * escapes), numbers, booleans, and null. Built for trusted, hand-authored data files (like dialogue
 * content), not for parsing untrusted or network input.
 */
public class MiniJson {
    private final String src;
    private int pos = 0;

    /** Creates a MiniJson instance and initializes the state required by its collaborators. */
    private MiniJson(String src) {
        this.src = src;
    }

    /** Resolves the parse result used by MiniJson. */
    public static Object parse(String json) {
        MiniJson parser = new MiniJson(json);
        parser.skipWhitespace();
        return parser.parseValue();
    }

    /** Parses the value for MiniJson. */
    private Object parseValue() {
        skipWhitespace();
        char c = peek();
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> parseNumber();
        };
    }

    /** Parses the object for MiniJson. */
    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            char c = src.charAt(pos++);
            if (c == '}') break;
            if (c != ',') throw new RuntimeException("Expected ',' or '}' at " + pos);
        }
        return map;
    }

    /** Parses the array for MiniJson. */
    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(parseValue());
            skipWhitespace();
            char c = src.charAt(pos++);
            if (c == ']') break;
            if (c != ',') throw new RuntimeException("Expected ',' or ']' at " + pos);
        }
        return list;
    }

    /** Parses the string for MiniJson. */
    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = src.charAt(pos++);
            if (c == '"') break;
            if (c == '\\') {
                char esc = src.charAt(pos++);
                switch (esc) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'u' -> {
                        String hex = src.substring(pos, pos + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                    }
                    default -> sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Parses the boolean for MiniJson. */
    private Boolean parseBoolean() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return true;
        }
        if (src.startsWith("false", pos)) {
            pos += 5;
            return false;
        }
        throw new RuntimeException("Invalid literal at " + pos);
    }

    /** Parses the null for MiniJson. */
    private Object parseNull() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new RuntimeException("Invalid literal at " + pos);
    }

    /** Parses the number for MiniJson. */
    private Double parseNumber() {
        int start = pos;
        while (pos < src.length() && "-+.eE0123456789".indexOf(src.charAt(pos)) >= 0) {
            pos++;
        }
        return Double.parseDouble(src.substring(start, pos));
    }

    /** Performs the skip whitespace operation for MiniJson. */
    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    /** Resolves the peek result used by MiniJson. */
    private char peek() {
        return src.charAt(pos);
    }

    /** Performs the expect operation for MiniJson. */
    private void expect(char c) {
        skipWhitespace();
        if (src.charAt(pos) != c) {
            throw new RuntimeException("Expected '" + c + "' at " + pos);
        }
        pos++;
    }
}
