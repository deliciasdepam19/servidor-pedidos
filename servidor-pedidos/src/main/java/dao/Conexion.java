package dao;

import java.sql.*;
import java.util.concurrent.*;

public class Conexion {

    public static final String BASE_PATH = "G:/Mi unidad/";
    public static final String REPORTES_VENTAS_TXT = "C:/DeliciasPam/Reportes/Reportes Ventas/TXT/";
    public static final String REPORTES_VENTAS_EXCEL = "G:/Mi unidad/Reportes/Reportes Ventas/Excel";
    public static final String REPORTES_EMPANADAS_TXT = "C:/DeliciasPam/Reportes/Reportes Empanadas/TXT/";
    public static final String REPORTES_EMPANADAS_EXCEL = "G:/Mi unidad/Reportes/Reportes Empanadas/Excel/";
    public static final String REPORTES_RAPIDOS_EXCEL = "G:/Mi unidad/Reportes/Reportes Prod.Rapidos/excel";

    private static final String URL = "jdbc:sqlite:" + BASE_PATH + "deliciasPam.db";
    private static final long CACHE_TTL_MS = 2000;

    private static Connection sharedConn;
    private static final Object LOCK = new Object();

    private static ConcurrentHashMap<String, CachedResult> cache;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            cache = new ConcurrentHashMap<>();
            sharedConn = crearConexion();
            System.out.println("✓ Conexión inicializada");
            System.out.println("✓ BD: " + URL);
        } catch (Exception e) {
            System.err.println("❌ Error inicializando conexión: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Connection crearConexion() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=NORMAL;");
            st.execute("PRAGMA cache_size=10000;");
            st.execute("PRAGMA temp_store=MEMORY;");
            st.execute("PRAGMA busy_timeout=10000;");
        }
        return conn;
    }

    public static Connection conectar() throws SQLException {
        synchronized (LOCK) {
            try {
                if (sharedConn == null || sharedConn.isClosed()) {
                    System.out.println("⚠️ Reconectando...");
                    sharedConn = crearConexion();
                }
                try (Statement st = sharedConn.createStatement()) {
                    st.execute("SELECT 1");
                }
                return sharedConn;
            } catch (SQLException e) {
                try {
                    if (sharedConn != null) {
                        sharedConn.close();

                    }
                } catch (Exception ignored) {
                }
                sharedConn = crearConexion();
                return sharedConn;
            }
        }
    }

    public static void devolver(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.getAutoCommit()) {
                    try {
                        conn.rollback();
                    } catch (SQLException ignored) {
                    }
                    conn.setAutoCommit(true);
                }
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
        return "Conexión única | Caché: " + cache.size() + " entradas";
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
        System.out.println("✓ Conexión cerrada");
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
