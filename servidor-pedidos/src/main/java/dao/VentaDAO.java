package dao;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class VentaDAO {

    private static final String CACHE_PREFIX = "venta_";


    public double totalDelDia(String fecha) {
        return queryDouble(
                "SELECT COALESCE(SUM(total),0) FROM ventas WHERE fecha=? AND tipo_pago!='PENDIENTE'", fecha)
                + queryDouble(
                        "SELECT COALESCE(SUM(subtotal),0) FROM ventas_rapidas WHERE fecha=? AND grupo_venta_id IS NULL",
                        fecha);
    }

    public double totalEfectivoDelDia(String fecha) {
        return queryDouble(
                "SELECT COALESCE(SUM(total),0) FROM ventas WHERE fecha=? AND tipo_pago='EFECTIVO'", fecha)
                + queryDouble(
                        "SELECT COALESCE(SUM(subtotal),0) FROM ventas_rapidas WHERE fecha=? AND tipo_pago='EFECTIVO' AND grupo_venta_id IS NULL",
                        fecha);
    }

    public double totalTransferenciaDelDia(String fecha) {
        return queryDouble(
                "SELECT COALESCE(SUM(total),0) FROM ventas WHERE fecha=? AND tipo_pago='TRANSFERENCIA'", fecha)
                + queryDouble(
                        "SELECT COALESCE(SUM(subtotal),0) FROM ventas_rapidas WHERE fecha=? AND tipo_pago='TRANSFERENCIA' AND grupo_venta_id IS NULL",
                        fecha);
    }

    public double totalPendienteDelDia(String fecha) {
        return PendientesDAO.totalPendientePorFecha(fecha);
    }

    public double totalPendienteDelDia() {
        return totalPendienteDelDia(hoy());
    }

    public Map<String, Integer> resumenDelDia(String fecha) {
        Map<String, Integer> resumen = new LinkedHashMap<>();

        try (Connection conn = Conexion.conectar()) {

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT d.nombre, SUM(d.cantidad) FROM ventas v "
                            + "JOIN detalle_ventas d ON v.id=d.venta_id "
                            + "WHERE v.fecha=? AND v.tipo_pago!='PENDIENTE' "
                            + "GROUP BY d.nombre")) {

                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    resumen.put(rs.getString(1), rs.getInt(2));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT nombre, SUM(cantidad) FROM ventas_rapidas "
                            + "WHERE fecha=? GROUP BY nombre")) {

                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    resumen.merge(rs.getString(1), rs.getInt(2), Integer::sum);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return resumen;
    }


    public int contarVentasDelDia(String fecha) {

        String cacheKey = CACHE_PREFIX + "count_" + fecha;
        Object cached = Conexion.getCached(cacheKey);

        if (cached instanceof Integer) {
            return (Integer) cached;
        }

        int total = 0;

        try (Connection conn = Conexion.conectar()) {

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ventas WHERE fecha=? AND tipo_pago!='PENDIENTE'")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                if (rs.next())
                    total += rs.getInt(1);
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ventas_rapidas WHERE fecha=? AND grupo_venta_id IS NULL")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                if (rs.next())
                    total += rs.getInt(1);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        Conexion.cacheResult(cacheKey, total);
        return total;
    }

    public int contarVentasDelDia() {
        return contarVentasDelDia(hoy());
    }



    public boolean registrarVentaConRapidos(
            Map<Integer, Integer> items,
            Map<Integer, String> categorias,
            Map<Integer, String> nombres,
            Map<Integer, Double> precios,
            String tipoPago,
            String nombreCliente,
            Map<String, Integer> rapidosCant,
            Map<String, Double> rapidosPrecios,
            Map<String, String> rapidosNombres) {

        Connection conn = null;

        try {
            conn = Conexion.conectar();
            conn.setAutoCommit(false);

            double totalVenta = 0;

            for (Map.Entry<Integer, Integer> e : items.entrySet()) {
                totalVenta += precios.get(e.getKey()) * e.getValue();
            }

            if (rapidosCant != null) {
                for (String k : rapidosCant.keySet()) {
                    totalVenta += rapidosPrecios.get(k) * rapidosCant.get(k);
                }
            }

            int ventaId = -1;

            if (!items.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO ventas (fecha,total,tipo_pago,nombre_cliente) VALUES (?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {

                    ps.setString(1, hoy());
                    ps.setDouble(2, totalVenta);
                    ps.setString(3, tipoPago);
                    ps.setString(4, nombreCliente);
                    ps.executeUpdate();

                    ResultSet rs = ps.getGeneratedKeys();
                    if (rs.next()) {
                        ventaId = rs.getInt(1);
                    }
                }
            }

            if (rapidosCant != null) {
                for (String k : rapidosCant.keySet()) {

                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO ventas_rapidas (fecha,nombre,cantidad,precio_unitario,subtotal,tipo_pago,grupo_venta_id) "
                                    + "VALUES (?,?,?,?,?,?,?)")) {

                        ps.setString(1, hoy());
                        ps.setString(2, rapidosNombres.get(k));
                        ps.setInt(3, rapidosCant.get(k));
                        ps.setDouble(4, rapidosPrecios.get(k));
                        ps.setDouble(5, rapidosPrecios.get(k) * rapidosCant.get(k));
                        ps.setString(6, tipoPago);

                        if (ventaId > 0) {
                            ps.setInt(7, ventaId);
                        } else {
                            ps.setNull(7, Types.INTEGER);
                        }

                        ps.executeUpdate();
                    }
                }
            }

            conn.commit();
            Conexion.invalidateCache(CACHE_PREFIX);
            return true;

        } catch (Exception e) {

            try {
                if (conn != null)
                    conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }

            e.printStackTrace();
            return false;

        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    Conexion.devolver(conn);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }


    public void guardarPedido(int numero, String cliente, String telefono, String detalle, double total) {

        String sql = "INSERT INTO pedidos (numero, cliente, telefono, detalle, total, fecha_hora, origen) "
                + "VALUES (?, ?, ?, ?, ?, NOW(), 'WEB')";

        try (Connection conn = Conexion.conectar();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, numero);
            ps.setString(2, cliente);
            ps.setString(3, telefono);
            ps.setString(4, detalle);
            ps.setDouble(5, total);

            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int obtenerSiguienteNumeroPedido() {

        try (Connection conn = Conexion.conectar();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COALESCE(MAX(numero),0)+1 FROM pedidos")) {

            ResultSet rs = ps.executeQuery();

            if (rs.next())
                return rs.getInt(1);

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 1;
    }


    private String hoy() {
        return java.time.LocalDate.now().toString();
    }

    public boolean registrarVentaRapida(String nombre, int cantidad, double precioUnitario, String tipoPago) {
    Connection conn = null;
    try {
        conn = Conexion.conectar();

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ventas_rapidas (fecha, nombre, cantidad, precio_unitario, subtotal, tipo_pago, grupo_venta_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, NULL)")) {

            ps.setString(1, java.time.LocalDate.now().toString());
            ps.setString(2, nombre);
            ps.setInt(3, cantidad);
            ps.setDouble(4, precioUnitario);
            ps.setDouble(5, precioUnitario * cantidad);
            ps.setString(6, tipoPago);

            int filas = ps.executeUpdate();
            Conexion.invalidateCache("venta_");

            return filas > 0;
        }

    } catch (SQLException e) {
        System.err.println("Error en registrarVentaRapida: " + e.getMessage());
        e.printStackTrace();
        return false;

    } finally {
        if (conn != null) {
            Conexion.devolver(conn);
        }
    }
}

    private double queryDouble(String sql, String param) {

        try (Connection conn = Conexion.conectar();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, param);
            ResultSet rs = ps.executeQuery();

            if (rs.next())
                return rs.getDouble(1);

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }
}
