package dao;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PendientesDAO {

    public static boolean registrar(String nombreCliente, double total, String detalle) {
        String sql = "INSERT INTO ventas_pendientes (nombre_cliente, total, fecha_venta, detalle, estado) "
                + "VALUES (?, ?, ?, ?, 'PENDIENTE')";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, nombreCliente);
            ps.setDouble(2, total);
            ps.setString(3, LocalDate.now().toString());
            ps.setString(4, detalle);
            ps.executeUpdate();
            ps.close();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public static List<String[]> listarPendientes() {
        List<String[]> lista = new ArrayList<>();
        String sql = "SELECT id, nombre_cliente, total, fecha_venta, detalle "
                + "FROM ventas_pendientes WHERE estado = 'PENDIENTE' ORDER BY fecha_venta ASC";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                lista.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("nombre_cliente"),
                    String.valueOf(rs.getDouble("total")),
                    rs.getString("fecha_venta"),
                    rs.getString("detalle")
                });
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return lista;
    }

    public static boolean liquidar(int id, String tipoPago) {
        String sqlGet = "SELECT nombre_cliente, total, fecha_venta, detalle FROM ventas_pendientes WHERE id = ?";
        String sqlUpdatePendiente = "UPDATE ventas_pendientes SET estado = 'PAGADO', fecha_pago = ?, "
                + "tipo_pago_liquidacion = ? WHERE id = ?";
        String sqlUpdateVenta = "UPDATE ventas SET tipo_pago = ? "
                + "WHERE id = ("
                + "  SELECT id FROM ventas "
                + "  WHERE nombre_cliente = ? AND fecha = ? AND tipo_pago = 'PENDIENTE' "
                + "  ORDER BY id DESC LIMIT 1"
                + ")";

        String sqlActualizarReporte = "UPDATE reportes SET "
                + "total_pendiente = CASE WHEN total_pendiente - ? < 0 THEN 0 ELSE total_pendiente - ? END, "
                + "total_efectivo = total_efectivo + CASE WHEN ? = 'EFECTIVO' THEN ? ELSE 0 END, "
                + "total_transferencia = total_transferencia + CASE WHEN ? = 'TRANSFERENCIA' THEN ? ELSE 0 END, "
                + "total = total + ? "
                + "WHERE fecha = ?";

        Connection conn = null;
        try {
            conn = Conexion.conectar();
            conn.setAutoCommit(false);

            String nombreCliente, fechaVenta, detalle;
            double total;

            try (PreparedStatement ps = conn.prepareStatement(sqlGet)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    conn.rollback();
                    return false;
                }
                nombreCliente = rs.getString("nombre_cliente");
                total = rs.getDouble("total");
                fechaVenta = rs.getString("fecha_venta");
                detalle = rs.getString("detalle");
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlUpdatePendiente)) {
                ps.setString(1, LocalDate.now().toString());
                ps.setString(2, tipoPago);
                ps.setInt(3, id);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlUpdateVenta)) {
                ps.setString(1, tipoPago);
                ps.setString(2, nombreCliente);
                ps.setString(3, fechaVenta);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlActualizarReporte)) {
                ps.setDouble(1, total);
                ps.setDouble(2, total);
                ps.setString(3, tipoPago);
                ps.setDouble(4, total);
                ps.setString(5, tipoPago);
                ps.setDouble(6, total);
                ps.setDouble(7, total);
                ps.setString(8, fechaVenta);
                ps.executeUpdate();
            }

            conn.commit();
            conn.setAutoCommit(true);
            return true;

        } catch (SQLException e) {
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ignored) {
            }
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public static boolean eliminar(int id) {
        String sql = "DELETE FROM ventas_pendientes WHERE id = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ps.executeUpdate();
            ps.close();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public static double totalPendiente() {
        String sql = "SELECT COALESCE(SUM(total),0) FROM ventas_pendientes WHERE estado = 'PENDIENTE'";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                return rs.getDouble(1);
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return 0;
    }

    public static double totalPendientePorFecha(String fecha) {
        String sql = "SELECT COALESCE(SUM(total),0) FROM ventas_pendientes "
                + "WHERE estado = 'PENDIENTE' AND fecha_venta = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, fecha);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getDouble(1);
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return 0;
    }
}
