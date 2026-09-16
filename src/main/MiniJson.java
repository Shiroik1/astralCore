package main;

import java.util.*;

/**
 * Minimal, dependency-free JSON parser. Supports objects, arrays, strings
 * (with \n \" \\ \t \r XXXX escapes), numbers, booleans, and null.
 * Built for trusted, hand-authored data files (like dialogue content),
 * not for parsing untrusted or network input.
 */
public class MiniJson {
    private final String src;
    private int pos = 0;

    private MiniJson(String src){
        this.src = src;
    }

    public static Object parse(String json){
        MiniJson parser = new MiniJson(json);
        parser.skipWhitespace();
        return parser.parseValue();
    }

    private Object parseValue(){
        skipWhitespace();
        char c = peek();
        return switch (c){
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObject(){
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if(peek() == '}'){ pos++; return map; }
        while(true){
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            char c = src.charAt(pos++);
            if(c == '}') break;
            if(c != ',') throw new RuntimeException("Expected ',' or '}' at " + pos);
        }
        return map;
    }

    private List<Object> parseArray(){
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if(peek() == ']'){ pos++; return list; }
        while(true){
            list.add(parseValue());
            skipWhitespace();
            char c = src.charAt(pos++);
            if(c == ']') break;
            if(c != ',') throw new RuntimeException("Expected ',' or ']' at " + pos);
        }
        return list;
    }

    private String parseString(){
        expect('"');
        StringBuilder sb = new StringBuilder();
        while(true){
            char c = src.charAt(pos++);
            if(c == '"') break;
            if(c == '\\'){
                char esc = src.charAt(pos++);
                switch(esc){
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

    private Boolean parseBoolean(){
        if(src.startsWith("true", pos)){ pos += 4; return true; }
        if(src.startsWith("false", pos)){ pos += 5; return false; }
        throw new RuntimeException("Invalid literal at " + pos);
    }

    private Object parseNull(){
        if(src.startsWith("null", pos)){ pos += 4; return null; }
        throw new RuntimeException("Invalid literal at " + pos);
    }

    private Double parseNumber(){
        int start = pos;
        while(pos < src.length() && "-+.eE0123456789".indexOf(src.charAt(pos)) >= 0){
            pos++;
        }
        return Double.parseDouble(src.substring(start, pos));
    }

    private void skipWhitespace(){
        while(pos < src.length() && Character.isWhitespace(src.charAt(pos))){
            pos++;
        }
    }

    private char peek(){
        return src.charAt(pos);
    }

    private void expect(char c){
        skipWhitespace();
        if(src.charAt(pos) != c){
            throw new RuntimeException("Expected '" + c + "' at " + pos);
        }
        pos++;
    }
}