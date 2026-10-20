package dao;

import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NotificacionDAO {

    // ── Retención: 7 días de notificaciones visibles ──────────────────
    private static final long UNA_SEMANA_MS = 7 * 24 * 60 * 60 * 1000L;

    // ── Notificación global (avisos manuales de la panadería) ─────────
    // telefono NULL = la ven todos los clientes en su campanita.
    public long guardar(String titulo, String mensaje, String icono, String color) {
        return guardar(titulo, mensaje, icono, color, null);
    }

    // ── Notificación dirigida (cambio de estado de un pedido) ─────────
    // telefono con 9 dígitos = solo ese cliente la ve en su campanita.
    public long guardar(String titulo, String mensaje, String icono, String color, String telefono) {
        String sql = "INSERT INTO notificaciones_app (titulo, mensaje, icono, color, fecha, hora, created_at, telefono) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            ZoneId chile = ZoneId.of("America/Santiago");
            ZonedDateTime now = ZonedDateTime.now(chile);
            String fecha = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String hora = now.format(DateTimeFormatter.ofPattern("HH:mm"));
            long createdAt = System.currentTimeMillis();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, titulo);
                ps.setString(2, mensaje);
                ps.setString(3, icono != null && !icono.isBlank() ? icono : "bell-outline");
                ps.setString(4, color != null && !color.isBlank() ? color : "#40cee0");
                ps.setString(5, fecha);
                ps.setString(6, hora);
                ps.setLong(7, createdAt);
                if (telefono == null || telefono.isBlank()) {
                    ps.setNull(8, Types.VARCHAR);
                } else {
                    ps.setString(8, telefono);
                }
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        } catch (SQLException e) {
            System.err.println("[NotificacionDAO] guardar: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return -1;
    }

    public List<Map<String, Object>> listarRecientes() {
        List<Map<String, Object>> result = new ArrayList<>();
        // Globales: telefono NULL. Las dirigidas se listan con listarRecientesPara.
        String sql = "SELECT id, titulo, mensaje, icono, color, fecha, hora, created_at "
                + "FROM notificaciones_app WHERE telefono IS NULL AND created_at > ? ORDER BY id DESC LIMIT 50";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, System.currentTimeMillis() - UNA_SEMANA_MS);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(mapearFila(rs));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[NotificacionDAO] listarRecientes: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return result;
    }

    // ── Lista para un teléfono: globales + las dirigidas a él ──────────
    public List<Map<String, Object>> listarRecientesPara(String telefono) {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT n.id, n.titulo, n.mensaje, n.icono, n.color, n.fecha, n.hora, n.created_at, "
                + "CASE WHEN l.id IS NULL THEN false ELSE true END AS leida "
                + "FROM notificaciones_app n "
                + "LEFT JOIN notificaciones_leidas l ON l.notificacion_id = n.id AND l.telefono = ? "
                + "WHERE (n.telefono IS NULL OR right(regexp_replace(n.telefono, '[^0-9]', '', 'g'), 9) = ?) "
                + "AND n.created_at > ? ORDER BY n.id DESC LIMIT 50";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, telefono);
                ps.setString(2, telefono);
                ps.setLong(3, System.currentTimeMillis() - UNA_SEMANA_MS);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> n = mapearFila(rs);
                        n.put("leida", rs.getBoolean("leida"));
                        result.add(n);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[NotificacionDAO] listarRecientesPara: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return result;
    }

    private Map<String, Object> mapearFila(ResultSet rs) throws SQLException {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", rs.getInt("id"));
        n.put("titulo", rs.getString("titulo"));
        n.put("mensaje", rs.getString("mensaje"));
        n.put("icono", rs.getString("icono"));
        n.put("color", rs.getString("color"));
        n.put("fecha", rs.getString("fecha"));
        n.put("hora", rs.getString("hora"));
        n.put("created_at", rs.getLong("created_at"));
        return n;
    }

    // ── Purga manual: elimina notificaciones fuera de la ventana de 7 días ─
    // NO se invoca desde el GET /api/notificaciones: ese endpoint corre en polling
    // (cada 15s por dispositivo) y notificaciones_app es historial de negocio.
    // La ventana de 7 días se aplica en la vista vía WHERE created_at > ?.
    // Reservado para una tarea de mantenimiento explícita si alguna vez se necesita.
    public boolean purgaViejas() {
        String sql = "DELETE FROM notificaciones_app WHERE created_at < ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, System.currentTimeMillis() - UNA_SEMANA_MS);
                int eliminadas = ps.executeUpdate();
                if (eliminadas > 0) {
                    System.out.println("[NotificacionDAO] purgaViejas: " + eliminadas + " notificaciones eliminadas");
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[NotificacionDAO] purgaViejas: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
    }

    // ── Marcar como leída para un teléfono (idempotente) ───────────────
    public boolean marcarLeida(long notificacionId, String telefono) {
        String sql = "INSERT INTO notificaciones_leidas (notificacion_id, telefono) VALUES (?, ?) "
                + "ON CONFLICT (notificacion_id, telefono) DO NOTHING";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, notificacionId);
                ps.setString(2, telefono);
                ps.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[NotificacionDAO] marcarLeida: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
    }

    public List<String> obtenerTodosLosTokens() {
        List<String> tokens = new ArrayList<>();
        String sql = "SELECT expo_push_token FROM dispositivos WHERE activo = true";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tokens.add(rs.getString("expo_push_token"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[NotificacionDAO] obtenerTodosLosTokens: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return tokens;
    }
}
