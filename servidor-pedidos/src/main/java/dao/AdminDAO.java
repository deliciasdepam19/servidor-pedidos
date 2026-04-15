package dao;

import java.sql.*;
import java.util.*;

public class AdminDAO {

    public void registrarLog(String ip, String metodo, String endpoint,
            int statusCode, String userAgent, String correo) {

        String sql = "INSERT INTO request_logs (ip, metodo, endpoint, status_code, user_agent, correo) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = Conexion.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, ip);
            ps.setString(2, metodo);
            ps.setString(3, endpoint);
            ps.setInt(4, statusCode);
            ps.setString(5, userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 500)) : null);
            ps.setString(6, correo);

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error registrando log: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> obtenerLogs(int limite) {
        String sql = "SELECT id, ip, metodo, endpoint, status_code, correo, timestamp "
                + "FROM request_logs ORDER BY timestamp DESC LIMIT ?";

        List<Map<String, Object>> lista = new ArrayList<>();

        try (Connection conn = Conexion.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

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

        } catch (SQLException e) {
            System.err.println("Error obteniendo logs: " + e.getMessage());
        }

        return lista;
    }

    public void bloquearIP(String ip, String razon) {
        String sql = "INSERT INTO ip_bloqueadas (ip, razon, fecha_bloqueo, desbloqueada) "
                + "VALUES (?, ?, NOW(), false)";

        try (Connection conn = Conexion.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, ip);
            ps.setString(2, razon);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error bloqueando IP: " + e.getMessage());
        }
    }

    public boolean estaBloqueada(String ip) {
        String sql = "SELECT 1 FROM ip_bloqueadas WHERE ip=? AND desbloqueada=false LIMIT 1";

        try (Connection conn = Conexion.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, ip);
            ResultSet rs = ps.executeQuery();

            return rs.next();

        } catch (SQLException e) {
            System.err.println("Error verificando IP: " + e.getMessage());
        }

        return false;
    }

    public List<Map<String, Object>> obtenerIPsBloqueadas() {
        String sql = "SELECT ip, razon, fecha_bloqueo FROM ip_bloqueadas WHERE desbloqueada=false ORDER BY fecha_bloqueo DESC";

        List<Map<String, Object>> lista = new ArrayList<>();

        try (Connection conn = Conexion.conectar();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ip", rs.getString("ip"));
                row.put("razon", rs.getString("razon"));
                row.put("fecha_bloqueo", rs.getString("fecha_bloqueo"));
                lista.add(row);
            }

        } catch (SQLException e) {
            System.err.println("Error obteniendo IPs bloqueadas: " + e.getMessage());
        }

        return lista;
    }

    public List<Map<String, Object>> obtenerTopIPs(int limite) {
        String sql = "SELECT ip, COUNT(*) as total "
                + "FROM request_logs GROUP BY ip ORDER BY total DESC LIMIT ?";

        List<Map<String, Object>> lista = new ArrayList<>();

        try (Connection conn = Conexion.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limite);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ip", rs.getString("ip"));
                row.put("total", rs.getInt("total"));
                lista.add(row);
            }

        } catch (SQLException e) {
            System.err.println("Error top IPs: " + e.getMessage());
        }

        return lista;
    }

    public void bloquearIPManual(String ip, String razon) {
        bloquearIP(ip, razon != null ? razon : "Bloqueo manual");
    }

    public void desbloquearIP(String ip) {
        String sql = "UPDATE ip_bloqueadas SET desbloqueada=true WHERE ip=?";

        try (Connection conn = Conexion.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, ip);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error desbloqueando IP: " + e.getMessage());
        }
    }

    public void registrarOActualizarUsuario(String correo, String nombre, String ip) {
        if (correo == null || correo.isBlank()) return;

        try (Connection conn = Conexion.conectar()) {

            PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO usuarios_web (correo, nombre, ip_ultimo) "
                    + "VALUES (?, ?, ?) ON CONFLICT (correo) DO NOTHING");

            ins.setString(1, correo.trim().toLowerCase());
            ins.setString(2, nombre);
            ins.setString(3, ip);
            ins.executeUpdate();

            PreparedStatement upd = conn.prepareStatement(
                    "UPDATE usuarios_web SET total_pedidos=total_pedidos+1, "
                    + "ultimo_pedido=NOW(), ip_ultimo=?, "
                    + "nombre=COALESCE(?, nombre) WHERE correo=?");

            upd.setString(1, ip);
            upd.setString(2, nombre);
            upd.setString(3, correo.trim().toLowerCase());
            upd.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error usuario: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> obtenerUsuarios(int limite) {
        String sql = "SELECT correo, nombre, ip_ultimo, total_pedidos, primer_pedido, ultimo_pedido "
                + "FROM usuarios_web ORDER BY ultimo_pedido DESC LIMIT ?";

        List<Map<String, Object>> lista = new ArrayList<>();

        try (Connection conn = Conexion.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

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

        } catch (SQLException e) {
            System.err.println("Error usuarios: " + e.getMessage());
        }

        return lista;
    }

    public Map<String, Object> obtenerEstadisticas() {

        Map<String, Object> stats = new LinkedHashMap<>();

        try (Connection conn = Conexion.conectar()) {

            stats.put("requests_hoy", queryLong(conn, "SELECT COUNT(*) FROM request_logs WHERE DATE(timestamp)=CURRENT_DATE"));
            stats.put("requests_total", queryLong(conn, "SELECT COUNT(*) FROM request_logs"));
            stats.put("ips_unicas_hoy", queryLong(conn, "SELECT COUNT(DISTINCT ip) FROM request_logs WHERE DATE(timestamp)=CURRENT_DATE"));
            stats.put("pedidos_hoy", queryLong(conn, "SELECT COUNT(*) FROM request_logs WHERE endpoint='/api/pedidos' AND metodo='POST' AND status_code=200 AND DATE(timestamp)=CURRENT_DATE"));
            stats.put("usuarios_total", queryLong(conn, "SELECT COUNT(*) FROM usuarios_web"));
            stats.put("ips_bloqueadas", queryLong(conn, "SELECT COUNT(*) FROM ip_bloqueadas WHERE desbloqueada=false"));
            stats.put("ventas_hoy", queryDouble(conn, "SELECT COALESCE(SUM(precio_unitario*cantidad),0) FROM ventas_rapidas WHERE DATE(fecha)=CURRENT_DATE"));

        } catch (SQLException e) {
            System.err.println("Error stats: " + e.getMessage());
        }

        return stats;
    }

    private long queryLong(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private double queryDouble(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }
}
