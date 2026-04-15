package dao;

import java.sql.*;
import java.util.*;

public class AdminDAO {

    public void registrarLog(String ip, String metodo, String endpoint,
            int statusCode, String userAgent, String correo) {

        String sql = "INSERT INTO request_logs (ip, metodo, endpoint, status_code, user_agent, correo) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        Connection conn = null;

        try {
            conn = Conexion.conectar();

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, ip);
            ps.setString(2, metodo);
            ps.setString(3, endpoint);
            ps.setInt(4, statusCode);
            ps.setString(5, userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 500)) : null);
            ps.setString(6, correo);

            ps.executeUpdate();
            ps.close();

        } catch (SQLException e) {
            System.err.println("Error registrando log: " + e.getMessage());
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public List<Map<String, Object>> obtenerLogs(int limite) {

        String sql = "SELECT id, ip, metodo, endpoint, status_code, correo, timestamp "
                + "FROM request_logs ORDER BY timestamp DESC LIMIT ?";

        List<Map<String, Object>> lista = new ArrayList<>();
        Connection conn = null;

        try {

            conn = Conexion.conectar();

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, limite);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                Map<String, Object> row = new LinkedHashMap<>();

                row.put("id", rs.getInt("id"));
                row.put("ip", rs.getString("ip"));
                row.put("metodo", rs.getString("metodo"));
                row.put("endpoint", rs.getString("endpoint"));
                row.put("status_code", rs.getInt("status_code"));
                row.put("correo", rs.getString("correo"));
                row.put("timestamp", rs.getString("timestamp"));

                lista.add(row);
            }

            rs.close();
            ps.close();

        } catch (SQLException e) {
            System.err.println("Error obteniendo logs: " + e.getMessage());
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }

        return lista;
    }

    public Map<String, Object> obtenerEstadisticas() {

        Map<String, Object> stats = new LinkedHashMap<>();
        Connection conn = null;

        try {

            conn = Conexion.conectar();

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM request_logs WHERE DATE(timestamp) = CURRENT_DATE")) {

                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    stats.put("requests_hoy", rs.getLong(1));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM request_logs")) {

                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    stats.put("requests_total", rs.getLong(1));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(DISTINCT ip) FROM request_logs WHERE DATE(timestamp) = CURRENT_DATE")) {

                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    stats.put("ips_unicas_hoy", rs.getLong(1));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM request_logs WHERE endpoint='/api/pedidos' "
                    + "AND metodo='POST' AND status_code=200 "
                    + "AND DATE(timestamp) = CURRENT_DATE")) {

                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    stats.put("pedidos_hoy", rs.getLong(1));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM usuarios_web")) {

                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    stats.put("usuarios_total", rs.getLong(1));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ip_bloqueadas WHERE desbloqueada=false")) {

                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    stats.put("ips_bloqueadas", rs.getLong(1));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(precio_unitario * cantidad), 0) "
                    + "FROM ventas_rapidas WHERE DATE(fecha) = CURRENT_DATE")) {

                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    stats.put("ventas_hoy", rs.getDouble(1));
                }
            } catch (Exception ignored) {
                stats.put("ventas_hoy", 0);
            }

        } catch (SQLException e) {
            System.err.println("Error stats: " + e.getMessage());
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }

        return stats;
    }

    public void registrarOActualizarUsuario(String correo, String nombre, String ip) {

        if (correo == null || correo.isBlank()) {
            return;
        }

        Connection conn = null;

        try {

            conn = Conexion.conectar();

            PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO usuarios_web (correo, nombre, ip_ultimo) "
                    + "VALUES (?, ?, ?) "
                    + "ON CONFLICT (correo) DO NOTHING");

            ins.setString(1, correo.trim().toLowerCase());
            ins.setString(2, nombre);
            ins.setString(3, ip);

            ins.executeUpdate();
            ins.close();

            PreparedStatement upd = conn.prepareStatement(
                    "UPDATE usuarios_web SET total_pedidos=total_pedidos+1, "
                    + "ultimo_pedido=NOW(), ip_ultimo=?, "
                    + "nombre=COALESCE(?, nombre) WHERE correo=?");

            upd.setString(1, ip);
            upd.setString(2, nombre);
            upd.setString(3, correo.trim().toLowerCase());

            upd.executeUpdate();
            upd.close();

        } catch (SQLException e) {
            System.err.println("Error registrando usuario: " + e.getMessage());
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public List<Map<String, Object>> obtenerUsuarios(int limite) {

        String sql = "SELECT correo, nombre, ip_ultimo, total_pedidos, primer_pedido, ultimo_pedido "
                + "FROM usuarios_web ORDER BY ultimo_pedido DESC LIMIT ?";

        List<Map<String, Object>> lista = new ArrayList<>();
        Connection conn = null;

        try {

            conn = Conexion.conectar();

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, limite);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                Map<String, Object> row = new LinkedHashMap<>();

                row.put("correo", rs.getString("correo"));
                row.put("nombre", rs.getString("nombre"));
                row.put("ip_ultimo", rs.getString("ip_ultimo"));
                row.put("total_pedidos", rs.getInt("total_pedidos"));
                row.put("primer_pedido", rs.getString("primer_pedido"));
                row.put("ultimo_pedido", rs.getString("ultimo_pedido"));

                lista.add(row);
            }

            rs.close();
            ps.close();

        } catch (SQLException e) {
            System.err.println("Error obteniendo usuarios: " + e.getMessage());
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }

        return lista;
    }
}
