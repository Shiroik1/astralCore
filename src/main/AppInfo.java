package main;
import java.util.Properties;
public final class AppInfo {
    private AppInfo(){}
    public static final String VERSION=readVersion();
    private static String readVersion(){
        try(var stream=AppInfo.class.getResourceAsStream("/build-info.properties")){
            if(stream!=null){Properties p=new Properties();p.load(stream);return p.getProperty("version","0.1.0-alpha");}
        }catch(java.io.IOException ignored){}
        return "0.1.0-alpha";
    }
}
