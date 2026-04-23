package dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import model.Pedido;

public class PedidosDAO {

    public static class PedidoBD {

        public int id;
        public int numero;
        public String cliente;
        public String telefono;
        public String detalle;
        public double total;
        public String estado;
        public String franja;
        public String timestamp;
        public String origen;

        public PedidoBD(int id, int numero, String cliente, String telefono,
                String detalle, double total, String estado,
                String franja, String timestamp) {
            this.id = id;
            this.numero = numero;
            this.cliente = cliente;
            this.telefono = telefono;
            this.detalle = detalle;
            this.total = total;
            this.estado = estado;
            this.franja = franja;
            this.timestamp = timestamp;
        }
    }

    public static String formatearNumero(int numero, String origen) {
        String prefijo = "LOCAL".equalsIgnoreCase(origen) ? "L" : "W";
        return "#" + prefijo + String.format("%03d", numero);
    }

    public int[] guardarPedidoAutoNumero(String cliente, String telefono,
            String detalle, double total, String franja, String origen,
            String fechaEntrega) {

        String sqlNum = "SELECT nextval('pedidos_numero_seq')";
        String sqlIns = "INSERT INTO pedidos (numero, cliente, telefono, detalle, total, estado, franja, origen, fecha_entrega) "
                + "VALUES (?, ?, ?, ?, ?, 'PENDIENTE', ?, ?, ?)";

        Connection conn = null;
        try {
            conn = Conexion.conectar();
            conn.setAutoCommit(false);

            int numero = 1;
            try (PreparedStatement psNum = conn.prepareStatement(sqlNum); ResultSet rs = psNum.executeQuery()) {
                if (rs.next()) {
                    numero = rs.getInt(1);
                }
            }

            int id = -1;
            try (PreparedStatement psIns = conn.prepareStatement(sqlIns, Statement.RETURN_GENERATED_KEYS)) {
                psIns.setInt(1, numero);
                psIns.setString(2, cliente);
                psIns.setString(3, telefono);
                psIns.setString(4, detalle);
                psIns.setDouble(5, total);
                psIns.setString(6, franja);
                psIns.setString(7, origen);
                // fecha_entrega puede ser null para pedidos normales
                if (fechaEntrega != null && !fechaEntrega.isBlank()) {
                    psIns.setDate(8, java.sql.Date.valueOf(
                            java.time.LocalDate.parse(fechaEntrega,
                                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
                } else {
                    psIns.setNull(8, java.sql.Types.DATE);
                }
                psIns.executeUpdate();
                try (ResultSet rs = psIns.getGeneratedKeys()) {
                    if (rs.next()) {
                        id = rs.getInt(1);
                    }
                }
            }

            conn.commit();
            conn.setAutoCommit(true);
            return new int[]{id, numero};

        } catch (SQLException e) {
            try {
                if (conn != null) {
                    conn.rollback();

                }
            } catch (SQLException ignored) {
            }
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return new int[]{-1, 1};
    }

    public int guardarPedido(int numero, String cliente, String telefono,
            String detalle, double total, String franja, String origen) {
        String sql = "INSERT INTO pedidos (numero, cliente, telefono, detalle, total, estado, franja, origen) "
                + "VALUES (?, ?, ?, ?, ?, 'PENDIENTE', ?, ?)";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, numero);
                ps.setString(2, cliente);
                ps.setString(3, telefono);
                ps.setString(4, detalle);
                ps.setDouble(5, total);
                ps.setString(6, franja);
                ps.setString(7, origen);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return -1;
    }

    public boolean actualizarEstado(int id, String nuevoEstado) {
        String sql = "UPDATE pedidos SET estado = ? WHERE id = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, nuevoEstado);
                ps.setInt(2, id);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return false;
    }

    public boolean actualizarDetalle(int id, String nuevoDetalle, double nuevoTotal) {
        String sql = "UPDATE pedidos SET detalle = ?, total = ? WHERE id = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, nuevoDetalle);
                ps.setDouble(2, nuevoTotal);
                ps.setInt(3, id);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return false;
    }

    public boolean eliminarPedido(int id) {
        String sql = "DELETE FROM pedidos WHERE id = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return false;
    }

    public List<PedidoBD> cargarPedidosDeHoy() {
        List<PedidoBD> lista = new ArrayList<>();
        String sql = "SELECT id, numero, cliente, telefono, detalle, total, estado, franja, origen, "
                + "fecha_hora "
                + "FROM pedidos "
                + "WHERE fecha_hora::date = CURRENT_DATE "
                + "AND estado NOT IN ('COBRADO', 'ELIMINADO') "
                + "ORDER BY numero ASC";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PedidoBD pb = new PedidoBD(
                            rs.getInt("id"),
                            rs.getInt("numero"),
                            rs.getString("cliente"),
                            rs.getString("telefono"),
                            rs.getString("detalle"),
                            rs.getDouble("total"),
                            rs.getString("estado"),
                            rs.getString("franja"),
                            rs.getString("fecha_hora")
                    );
                    pb.origen = rs.getString("origen");
                    lista.add(pb);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return lista;
    }

    public int siguienteNumeroPedido(String origen) {
        String sql = "SELECT COALESCE(MAX(numero), 0) + 1 FROM pedidos";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return 1;
    }

    public boolean cobrarPedido(int id) {
        String sql = "UPDATE pedidos SET estado = 'COBRADO' WHERE id = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return false;
    }

    public int obtenerProximoNumero(String origen) {
        String sql = "SELECT COALESCE(MAX(numero), 0) + 1 FROM pedidos "
                + "WHERE UPPER(origen) = UPPER(?)";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, origen);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return 1;
    }

    public void registrarCancelacion(Pedido p) {
        String sql = "INSERT INTO pedidos_cancelados "
                + "(pedido_id, numero, cliente, telefono, detalle, total, origen, franja) "
                + "VALUES (?,?,?,?,?,?,?,?)";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, p.id);
                ps.setInt(2, p.numero);
                ps.setString(3, p.cliente);
                ps.setString(4, p.telefono);
                ps.setString(5, p.detalle);
                ps.setDouble(6, p.total);
                ps.setString(7, p.origen != null ? p.origen : "LOCAL");
                ps.setString(8, p.hora);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public void marcarComoProcesado(int id) {
        Connection conn = null;
        try {
            conn = Conexion.conectar();

            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE pedidos SET estado = 'PROCESADO' WHERE id = ?"
            );

            ps.setInt(1, id);
            ps.executeUpdate();

            ps.close();

        } catch (Exception e) {
            System.out.println(" Error al marcar pedido como PROCESADO: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public List<PedidoBD> cargarPedidosPendientesDeHoy() {
        List<PedidoBD> pedidos = new ArrayList<>();
        Connection conn = null;

        try {
            conn = Conexion.conectar();

            PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, numero, cliente, telefono, detalle, total, estado, franja, fecha_hora, origen "
                    + "FROM pedidos "
                    + "WHERE origen = 'WEB' "
                    + "AND (fecha_hora::timestamptz AT TIME ZONE 'America/Santiago')::date = "
                    + "    (CURRENT_TIMESTAMP AT TIME ZONE 'America/Santiago')::date "
                    + "AND estado NOT IN ('COBRADO', 'CANCELADO') "
                    + "ORDER BY fecha_hora ASC"
            );

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                PedidoBD p = new PedidoBD(
                        rs.getInt("id"),
                        rs.getInt("numero"),
                        rs.getString("cliente"),
                        rs.getString("telefono"),
                        rs.getString("detalle"),
                        rs.getDouble("total"),
                        rs.getString("estado"),
                        rs.getString("franja"),
                        rs.getString("fecha_hora")
                );

                p.origen = rs.getString("origen");

                pedidos.add(p);
            }

            rs.close();
            ps.close();

        } catch (Exception e) {
            System.out.println("Error cargando pedidos pendientes: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }

        return pedidos;
    }
}
