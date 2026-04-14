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
                    "SELECT COUNT(*) FROM request_logs WHERE date(timestamp) = date('now','localtime')")) {
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
                    "SELECT COUNT(DISTINCT ip) FROM request_logs WHERE date(timestamp) = date('now','localtime')")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    stats.put("ips_unicas_hoy", rs.getLong(1));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM request_logs WHERE endpoint='/api/pedidos' "
                    + "AND metodo='POST' AND status_code=200 "
                    + "AND date(timestamp) = date('now','localtime')")) {
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
                    "SELECT COUNT(*) FROM ip_bloqueadas WHERE desbloqueada=0")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    stats.put("ips_bloqueadas", rs.getLong(1));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(precio_unitario * cantidad), 0) FROM ventas_rapidas "
                    + "WHERE date(fecha) = date('now','localtime')")) {
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

    public boolean estaBloqueada(String ip) {
        String sql = "SELECT bloqueada_hasta, permanente FROM ip_bloqueadas "
                + "WHERE ip = ? AND desbloqueada = 0";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, ip);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int permanente = rs.getInt("permanente");
                if (permanente == 1) {
                    return true;
                }
                String hasta = rs.getString("bloqueada_hasta");
                rs.close();
                ps.close();
                if (hasta != null) {

                    try {
                        java.time.LocalDateTime bloqHasta = java.time.LocalDateTime.parse(
                                hasta.replace(" ", "T"));
                        if (java.time.LocalDateTime.now().isBefore(bloqHasta)) {
                            return true;
                        }
                    } catch (Exception ignored) {
                    }
                }
                autoDesbloquear(ip, conn);
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error verificando bloqueo: " + e.getMessage());
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return false;
    }

    private void autoDesbloquear(String ip, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ip_bloqueadas SET desbloqueada=1 WHERE ip=?")) {
            ps.setString(1, ip);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error auto-desbloqueando: " + e.getMessage());
        }
    }

    public void bloquearIP(String ip, String razon) {
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement sel = conn.prepareStatement(
                    "SELECT id, reincidencias FROM ip_bloqueadas WHERE ip=?");
            sel.setString(1, ip);
            ResultSet rs = sel.executeQuery();

            if (rs.next()) {
                int id = rs.getInt("id");
                int reinc = rs.getInt("reincidencias") + 1;
                rs.close();
                sel.close();

                boolean permanente = reinc >= 3;
                String hasta = permanente ? null : calcularHasta(reinc == 1 ? 3_600_000L : 21_600_000L);

                PreparedStatement upd = conn.prepareStatement(
                        "UPDATE ip_bloqueadas SET reincidencias=?, permanente=?, bloqueada_hasta=?, "
                        + "desbloqueada=0, bloqueada_en=datetime('now','localtime'), razon=? WHERE id=?");
                upd.setInt(1, reinc);
                upd.setInt(2, permanente ? 1 : 0);
                upd.setString(3, hasta);
                upd.setString(4, razon);
                upd.setInt(5, id);
                upd.executeUpdate();
                upd.close();
                System.out.println("IP " + ip + " bloqueada (reincidencia " + reinc + ", permanente=" + permanente + ")");

            } else {
                rs.close();
                sel.close();
                String hasta = calcularHasta(3_600_000L);
                PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO ip_bloqueadas (ip, razon, reincidencias, permanente, bloqueada_hasta) "
                        + "VALUES (?, ?, 1, 0, ?)");
                ins.setString(1, ip);
                ins.setString(2, razon);
                ins.setString(3, hasta);
                ins.executeUpdate();
                ins.close();
                System.out.println("IP " + ip + " bloqueada 1 hora (primera vez)");
            }
        } catch (SQLException e) {
            System.err.println("Error bloqueando IP: " + e.getMessage());
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public void bloquearIPManual(String ip, String razon) {
        Connection conn = null;
        try {
            conn = Conexion.conectar();

            PreparedStatement ins = conn.prepareStatement(
                    "INSERT OR IGNORE INTO ip_bloqueadas (ip, razon, reincidencias, permanente) "
                    + "VALUES (?, ?, 99, 1)");
            ins.setString(1, ip);
            ins.setString(2, razon != null ? razon : "Bloqueado manualmente");
            ins.executeUpdate();
            ins.close();

            PreparedStatement upd = conn.prepareStatement(
                    "UPDATE ip_bloqueadas SET permanente=1, desbloqueada=0, razon=?, "
                    + "bloqueada_en=datetime('now','localtime') WHERE ip=?");
            upd.setString(1, razon != null ? razon : "Bloqueado manualmente");
            upd.setString(2, ip);
            upd.executeUpdate();
            upd.close();

        } catch (SQLException e) {
            System.err.println("Error bloqueando IP manual: " + e.getMessage());
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public void desbloquearIP(String ip) {
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ip_bloqueadas SET desbloqueada=1 WHERE ip=?");
            ps.setString(1, ip);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            System.err.println("Error desbloqueando IP: " + e.getMessage());
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public List<Map<String, Object>> obtenerIPsBloqueadas() {
        String sql = "SELECT ip, razon, reincidencias, permanente, bloqueada_hasta, bloqueada_en, desbloqueada "
                + "FROM ip_bloqueadas ORDER BY bloqueada_en DESC";
        List<Map<String, Object>> lista = new ArrayList<>();
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ip", rs.getString("ip"));
                row.put("razon", rs.getString("razon"));
                row.put("reincidencias", rs.getInt("reincidencias"));
                row.put("permanente", rs.getInt("permanente") == 1);
                row.put("bloqueada_hasta", rs.getString("bloqueada_hasta"));
                row.put("bloqueada_en", rs.getString("bloqueada_en"));
                row.put("desbloqueada", rs.getInt("desbloqueada") == 1);
                lista.add(row);
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("Error obteniendo IPs: " + e.getMessage());
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return lista;
    }

    public List<Map<String, Object>> obtenerTopIPs(int limite) {
        String sql = "SELECT ip, COUNT(*) as total, "
                + "SUM(CASE WHEN date(timestamp) = date('now','localtime') THEN 1 ELSE 0 END) as hoy "
                + "FROM request_logs GROUP BY ip ORDER BY total DESC LIMIT ?";
        List<Map<String, Object>> lista = new ArrayList<>();
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, limite);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ip", rs.getString("ip"));
                row.put("total", rs.getLong("total"));
                row.put("hoy", rs.getLong("hoy"));
                lista.add(row);
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("Error top IPs: " + e.getMessage());
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return lista;
    }

    public void registrarOActualizarUsuario(String correo, String nombre, String ip) {
        if (correo == null || correo.isBlank()) {
            return;
        }
        Connection conn = null;
        try {
            conn = Conexion.conectar();

            PreparedStatement ins = conn.prepareStatement(
                    "INSERT OR IGNORE INTO usuarios_web (correo, nombre, ip_ultimo) VALUES (?, ?, ?)");
            ins.setString(1, correo.trim().toLowerCase());
            ins.setString(2, nombre);
            ins.setString(3, ip);
            ins.executeUpdate();
            ins.close();

            PreparedStatement upd = conn.prepareStatement(
                    "UPDATE usuarios_web SET total_pedidos=total_pedidos+1, "
                    + "ultimo_pedido=datetime('now','localtime'), ip_ultimo=?, "
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

    private String calcularHasta(long millis) {
        java.time.LocalDateTime hasta = java.time.LocalDateTime.now()
                .plus(millis, java.time.temporal.ChronoUnit.MILLIS);
        return hasta.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
