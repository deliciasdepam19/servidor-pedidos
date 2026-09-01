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

    public long guardar(String titulo, String mensaje, String icono, String color) {
        String sql = "INSERT INTO notificaciones_app (titulo, mensaje, icono, color, fecha, hora, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id";
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
        String sql = "SELECT id, titulo, mensaje, icono, color, fecha, hora, created_at "
                + "FROM notificaciones_app ORDER BY id DESC LIMIT 50";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> n = new LinkedHashMap<>();
                    n.put("id", rs.getInt("id"));
                    n.put("titulo", rs.getString("titulo"));
                    n.put("mensaje", rs.getString("mensaje"));
                    n.put("icono", rs.getString("icono"));
                    n.put("color", rs.getString("color"));
                    n.put("fecha", rs.getString("fecha"));
                    n.put("hora", rs.getString("hora"));
                    n.put("created_at", rs.getLong("created_at"));
                    result.add(n);
                }
            }
        } catch (SQLException e) {
            System.err.println("[NotificacionDAO] listarRecientes: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return result;
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
