package config;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

public class Config {

    private static final Properties props = new Properties();

    private static final Set<String> REQUIRED_KEYS = Set.of(
        "DB_PASSWORD", "DB_HOST", "DB_NAME", "DB_USER", "ADMIN_PASS"
    );

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

            for (String key : REQUIRED_KEYS) {
                String value = get(key);
                if (value == null || value.isBlank()) {
                    throw new RuntimeException(
                        "Missing required config/env: " + key
                    );
                }
            }

        } catch (Exception e) {
            System.err.println("[Config] " + e.getMessage());
            throw new RuntimeException("Config initialization failed", e);
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
