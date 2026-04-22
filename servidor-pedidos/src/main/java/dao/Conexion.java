package dao;

import config.Config;
import java.sql.*;
import java.util.concurrent.*;

public class Conexion {

    public static final String BASE_PATH = "G:/Mi unidad/";
    public static final String REPORTES_VENTAS_TXT = "C:/DeliciasPam/Reportes/Reportes Ventas/TXT/";
    public static final String REPORTES_VENTAS_EXCEL = "G:/Mi unidad/Reportes/Reportes Ventas/Excel";
    public static final String REPORTES_EMPANADAS_TXT = "C:/DeliciasPam/Reportes/Reportes Empanadas/TXT/";
    public static final String REPORTES_EMPANADAS_EXCEL = "G:/Mi unidad/Reportes/Reportes Empanadas/Excel/";
    public static final String REPORTES_RAPIDOS_EXCEL = "G:/Mi unidad/Reportes/Reportes Prod.Rapidos/excel";

    private static String getConfig(String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }

        try {
            return Config.get(key);
        } catch (Exception e) {
            throw new RuntimeException("Falta configuración: " + key);
        }
    }

    private static final String POSTGRES_HOST = getConfig("DB_HOST");
    private static final String POSTGRES_DB = getConfig("DB_NAME");
    private static final String POSTGRES_PORT = getConfig("DB_PORT");
    private static final String POSTGRES_USER = getConfig("DB_USER");
    private static final String POSTGRES_PASSWORD = getConfig("DB_PASSWORD");

    private static final String URL
            = "jdbc:postgresql://" + POSTGRES_HOST + ":" + POSTGRES_PORT + "/" + POSTGRES_DB;

    private static final long CACHE_TTL_MS = 2000;

    private static Connection sharedConn;
    private static final Object LOCK = new Object();

    private static ConcurrentHashMap<String, CachedResult> cache;

    static {
        try {
            Class.forName("org.postgresql.Driver");

            cache = new ConcurrentHashMap<>();

            sharedConn = crearConexion();

            System.out.println("Conexión PostgreSQL inicializada");
            System.out.println("URL: " + URL);
            System.out.println("Usuario: " + POSTGRES_USER);

        } catch (Exception e) {
            System.err.println("Error inicializando conexión: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Connection crearConexion() throws SQLException {
        Connection conn = DriverManager.getConnection(
                URL,
                POSTGRES_USER,
                POSTGRES_PASSWORD
        );

        conn.setAutoCommit(true);
        return conn;
    }

    public static Connection conectar() throws SQLException {
        return DriverManager.getConnection(
                URL,
                POSTGRES_USER,
                POSTGRES_PASSWORD
        );
    }

    public static void devolver(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
        }
    }

    public static void cacheResult(String key, Object result) {
        if (key != null && result != null) {
            cache.put(key, new CachedResult(result, System.currentTimeMillis()));
        }
    }

    public static Object getCached(String key) {
        if (key == null) {
            return null;
        }

        CachedResult cr = cache.get(key);

        if (cr != null && (System.currentTimeMillis() - cr.timestamp) < CACHE_TTL_MS) {
            return cr.result;
        }

        cache.remove(key);
        return null;
    }

    public static void invalidateCache(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            cache.clear();
            return;
        }

        cache.keySet().removeIf(k -> k.contains(pattern));
    }

    public static void clearCache() {
        cache.clear();
    }

    public static String getPoolStatus() {
        return "PostgreSQL | Caché: " + cache.size() + " entradas";
    }

    public static void shutdown() {
        try {
            if (sharedConn != null && !sharedConn.isClosed()) {
                sharedConn.close();
            }
        } catch (SQLException e) {
            System.err.println("Error cerrando conexión: " + e.getMessage());
        }

        cache.clear();
        System.out.println("✓ Conexión PostgreSQL cerrada");
    }

    private static class CachedResult {

        Object result;
        long timestamp;

        CachedResult(Object result, long timestamp) {
            this.result = result;
            this.timestamp = timestamp;
        }
    }
}
