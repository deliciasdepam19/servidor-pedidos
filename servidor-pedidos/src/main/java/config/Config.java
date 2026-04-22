package config;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class Config {

    private static final Properties props = new Properties();

    static {
        try {
            InputStream input = Config.class
                    .getClassLoader()
                    .getResourceAsStream("config.properties");

            if (input == null) {
                input = new FileInputStream("config.properties");
            }

            props.load(input);
            System.out.println("config.properties cargado");

        } catch (Exception e) {
            System.out.println("No se encontró config.properties");
            e.printStackTrace();
        }
    }

    public static String get(String key) {

        String env = System.getenv(key);

        if (env != null && !env.isBlank()) {
            return env;
        }

        return props.getProperty(key);
    }
}
