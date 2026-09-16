package main;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DialogueLoader {
    public static List<String> loadLines(String resourcePath){
        List<String> lines = new ArrayList<>();
        try(InputStream is = DialogueLoader.class.getResourceAsStream(resourcePath)){
            if(is == null){
                System.out.println("[DIALOGUE] Missing file: " + resourcePath);
                return lines;
            }
            StringBuilder sb = new StringBuilder();
            try(BufferedReader br = new BufferedReader(new InputStreamReader(is))){
                String row;
                while((row = br.readLine()) != null){
                    sb.append(row).append('\n');
                }
            }
            Object parsed = MiniJson.parse(sb.toString());
            if(parsed instanceof Map<?,?> root){
                Object rawLines = root.get("lines");
                if(rawLines instanceof List<?> list){
                    for(Object item : list){
                        if(item instanceof Map<?,?> lineObj){
                            Object text = lineObj.get("text");
                            if(text != null){
                                lines.add(text.toString());
                            }
                        }
                    }
                }
            }
        } catch(Exception e){
            e.printStackTrace();
        }
        return lines;
    }
}