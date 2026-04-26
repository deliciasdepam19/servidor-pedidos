package dao;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class VentaDAO {

    private static final String CACHE_PREFIX = "venta_";

    public double totalDelDia(String fecha) {
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            java.sql.Date sqlFecha = java.sql.Date.valueOf(fecha);

            return queryDouble(conn,
                    "SELECT COALESCE(SUM(total), 0) FROM ventas WHERE fecha::date = ? AND tipo_pago != 'PENDIENTE'",
                    sqlFecha)
                    + queryDouble(conn,
                            "SELECT COALESCE(SUM(subtotal), 0) FROM ventas_rapidas WHERE fecha::date = ? AND grupo_venta_id IS NULL",
                            sqlFecha);

        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        } finally {
            cerrarConexion(conn);
        }
    }

    public double totalEfectivoDelDia(String fecha) {
        return totalDelDiaPorTipo(fecha, "EFECTIVO");
    }

    public double totalTransferenciaDelDia(String fecha) {
        return totalDelDiaPorTipo(fecha, "TRANSFERENCIA");
    }

    private double totalDelDiaPorTipo(String fecha, String tipoPago) {
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            java.sql.Date sqlFecha = java.sql.Date.valueOf(fecha);

            return queryDouble(conn,
                    "SELECT COALESCE(SUM(total), 0) FROM ventas WHERE fecha::date = ? AND tipo_pago = ?",
                    sqlFecha, tipoPago)
                    + queryDouble(conn,
                            "SELECT COALESCE(SUM(subtotal), 0) FROM ventas_rapidas WHERE fecha::date = ? AND tipo_pago = ? AND grupo_venta_id IS NULL",
                            sqlFecha, tipoPago);

        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        } finally {
            cerrarConexion(conn);
        }
    }

    public double totalPendienteDelDia() {
        return PendientesDAO.totalPendientePorFecha(hoy());
    }

    public double totalPendienteDelDia(String fecha) {
        return PendientesDAO.totalPendientePorFecha(fecha);
    }

    public Map<String, Integer> resumenDelDia() {
        return resumenDelDia(hoy());
    }

    public Map<String, Integer> resumenDelDia(String fecha) {
        Map<String, Integer> resumen = new LinkedHashMap<>();
        Connection conn = null;

        try {
            conn = Conexion.conectar();
            java.sql.Date sqlFecha = java.sql.Date.valueOf(fecha);

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT d.nombre, d.producto_tipo, SUM(d.cantidad) as total "
                    + "FROM ventas v "
                    + "JOIN detalle_ventas d ON v.id = d.venta_id "
                    + "WHERE v.fecha::date = ? AND v.tipo_pago != 'PENDIENTE' "
                    + "GROUP BY d.nombre, d.producto_tipo")) {

                ps.setDate(1, sqlFecha);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        resumen.put(
                                rs.getString("nombre"),
                                rs.getInt("total")
                        );
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT nombre, SUM(cantidad) as total "
                    + "FROM ventas_rapidas "
                    + "WHERE fecha::date = ? "
                    + "GROUP BY nombre")) {

                ps.setDate(1, sqlFecha);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        resumen.merge(rs.getString("nombre"), rs.getInt("total"), Integer::sum);
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Error en resumenDelDia: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cerrarConexion(conn);
        }

        return resumen;
    }

    public Map<String, Integer> resumenRapidoDelDia() {
        Map<String, Integer> resumen = new LinkedHashMap<>();
        Connection conn = null;

        try {
            conn = Conexion.conectar();

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT nombre, SUM(cantidad) as total FROM ventas_rapidas WHERE fecha::date = ? GROUP BY nombre ORDER BY nombre")) {

                stmt.setDate(1, java.sql.Date.valueOf(hoy()));

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        resumen.put(rs.getString("nombre"), rs.getInt("total"));
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Error en resumenRapidoDelDia: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cerrarConexion(conn);
        }

        return resumen;
    }

    public int contarVentasDelDia() {
        return contarVentasDelDia(hoy());
    }

    public int contarVentasDelDia(String fecha) {
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            return contarVentasDelDia(conn, fecha);
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        } finally {
            cerrarConexion(conn);
        }
    }

    public int contarVentasDelDia(Connection conn, String fecha) throws SQLException {
        int ventas = 0;
        java.sql.Date sqlFecha = java.sql.Date.valueOf(fecha);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM ventas WHERE fecha::date = ? AND tipo_pago != 'PENDIENTE'")) {
            ps.setDate(1, sqlFecha);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ventas += rs.getInt(1);
                }
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM ventas_rapidas WHERE fecha::date = ? AND grupo_venta_id IS NULL")) {
            ps.setDate(1, sqlFecha);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ventas += rs.getInt(1);
                }
            }
        }

        return ventas;
    }

    public boolean registrarVenta(
            Map<Integer, Integer> items,
            Map<Integer, String> categorias,
            Map<Integer, String> nombres,
            Map<Integer, Double> precios,
            String tipoPago,
            String nombreCliente) {

        return registrarVentaConRapidos(items, categorias, nombres, precios,
                tipoPago, nombreCliente, null, null, null);
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

            for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
                int id = entry.getKey();
                int cantidad = entry.getValue();
                String tabla = ProductoDAO.tablaDesdeCategoria(categorias.get(id));

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT stock FROM " + tabla + " WHERE id = ?")) {

                    ps.setInt(1, id);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || rs.getInt("stock") < cantidad) {
                            conn.rollback();
                            return false;
                        }
                    }
                }
            }

            double totalVenta = 0;

            for (Map.Entry<Integer, Integer> e : items.entrySet()) {
                totalVenta += precios.get(e.getKey()) * e.getValue();
            }

            if (rapidosCant != null && rapidosPrecios != null) {
                for (String key : rapidosCant.keySet()) {
                    totalVenta += rapidosPrecios.getOrDefault(key, 0.0)
                            * rapidosCant.getOrDefault(key, 0);
                }
            }

            int ventaId = -1;
            java.sql.Date hoy = java.sql.Date.valueOf(java.time.LocalDate.now());

            if (!items.isEmpty()) {
                try (PreparedStatement psVenta = conn.prepareStatement(
                        "INSERT INTO ventas (fecha, total, tipo_pago, nombre_cliente) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {

                    psVenta.setDate(1, hoy);
                    psVenta.setDouble(2, totalVenta);
                    psVenta.setString(3, tipoPago);
                    psVenta.setString(4, nombreCliente);

                    psVenta.executeUpdate();

                    try (ResultSet keys = psVenta.getGeneratedKeys()) {
                        if (!keys.next()) {
                            conn.rollback();
                            return false;
                        }
                        ventaId = keys.getInt(1);
                    }
                }

                for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
                    int id = entry.getKey();
                    int cantidad = entry.getValue();
                    String categoria = categorias.get(id);
                    String tabla = ProductoDAO.tablaDesdeCategoria(categoria);

                    try (PreparedStatement psDet = conn.prepareStatement(
                            "INSERT INTO detalle_ventas (venta_id, producto_tipo, producto_id, nombre, cantidad, subtotal) "
                            + "VALUES (?, ?, ?, ?, ?, ?)")) {

                        psDet.setInt(1, ventaId);
                        psDet.setString(2, categoria.toLowerCase());
                        psDet.setInt(3, id);
                        psDet.setString(4, nombres.get(id));
                        psDet.setInt(5, cantidad);
                        psDet.setDouble(6, precios.get(id) * cantidad);

                        psDet.executeUpdate();
                    }

                    try (PreparedStatement psStock = conn.prepareStatement(
                            "UPDATE " + tabla + " SET stock = stock - ? WHERE id = ?")) {

                        psStock.setInt(1, cantidad);
                        psStock.setInt(2, id);
                        psStock.executeUpdate();
                    }
                }
            }

            if (rapidosCant != null && !rapidosCant.isEmpty()) {
                for (String key : rapidosCant.keySet()) {
                    int cantidad = rapidosCant.getOrDefault(key, 0);
                    double precioUnit = rapidosPrecios != null ? rapidosPrecios.getOrDefault(key, 0.0) : 0.0;
                    String nombreRapido = rapidosNombres != null ? rapidosNombres.getOrDefault(key, "") : "";

                    try (PreparedStatement psR = conn.prepareStatement(
                            "INSERT INTO ventas_rapidas (fecha, nombre, cantidad, precio_unitario, subtotal, tipo_pago, grupo_venta_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {

                        psR.setDate(1, hoy);
                        psR.setString(2, nombreRapido);
                        psR.setInt(3, cantidad);
                        psR.setDouble(4, precioUnit);
                        psR.setDouble(5, precioUnit * cantidad);
                        psR.setString(6, tipoPago);

                        if (ventaId > 0) {
                            psR.setInt(7, ventaId);
                        } else {
                            psR.setNull(7, Types.INTEGER);
                        }

                        psR.executeUpdate();
                    }
                }
            }

            conn.commit();
            Conexion.invalidateCache(CACHE_PREFIX);
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            return false;
        } finally {
            cerrarConexion(conn);
        }
    }

    public java.time.LocalDate fechaConVentasSinCierre() {

        java.time.LocalDate hoy = java.time.LocalDate.now();
        java.time.LocalDate fechaVentas = null;
        java.time.LocalDate fechaRapidas = null;
        Connection conn = null;

        try {
            conn = Conexion.conectar();

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT MIN(fecha) FROM ventas WHERE fecha::date < ? AND tipo_pago != 'PENDIENTE'")) {

                ps.setDate(1, java.sql.Date.valueOf(hoy));

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getDate(1) != null) {
                        fechaVentas = rs.getDate(1).toLocalDate();
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT MIN(fecha) FROM ventas_rapidas WHERE fecha::date < ?")) {

                ps.setDate(1, java.sql.Date.valueOf(hoy));

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getDate(1) != null) {
                        fechaRapidas = rs.getDate(1).toLocalDate();
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            cerrarConexion(conn);
        }

        if (fechaVentas == null) {
            return fechaRapidas;
        }
        if (fechaRapidas == null) {
            return fechaVentas;
        }

        return fechaVentas.isBefore(fechaRapidas) ? fechaVentas : fechaRapidas;
    }

    public int[] resumenCategorias(String fecha) {
        int[] result = {0, 0, 0};
        Connection conn = null;

        try {
            conn = Conexion.conectar();
            java.sql.Date sqlFecha = java.sql.Date.valueOf(fecha);

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT d.producto_tipo, SUM(d.cantidad) as total "
                    + "FROM ventas v "
                    + "JOIN detalle_ventas d ON v.id = d.venta_id "
                    + "WHERE v.fecha::date = ? AND v.tipo_pago != 'PENDIENTE' "
                    + "GROUP BY d.producto_tipo")) {

                ps.setDate(1, sqlFecha);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tipo = rs.getString("producto_tipo");
                        int total = rs.getInt("total");
                        if (tipo != null) {
                            String t = tipo.toLowerCase().trim();
                            if (t.contains("empanada")) {
                                result[0] += total;
                            } else if (t.contains("sopaipilla")) {
                                result[1] += total;
                            }
                        }
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(cantidad), 0) FROM ventas_rapidas WHERE fecha::date = ? AND grupo_venta_id IS NULL")) {

                ps.setDate(1, sqlFecha);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        result[2] = rs.getInt(1);
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Error en resumenCategorias: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cerrarConexion(conn);
        }

        return result;
    }

    public int contarVentasWeb(String fecha) {
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            java.sql.Date sqlFecha = java.sql.Date.valueOf(fecha);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ventas WHERE fecha::date = ? AND tipo_pago != 'PENDIENTE' AND origen = 'WEB'")) {
                ps.setDate(1, sqlFecha);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        } finally {
            cerrarConexion(conn);
        }
    }

    public int contarVentasLocal(String fecha) {
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            java.sql.Date sqlFecha = java.sql.Date.valueOf(fecha);

            int desdeVentas = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ventas WHERE fecha::date = ? AND tipo_pago != 'PENDIENTE' AND origen = 'LOCAL'")) {
                ps.setDate(1, sqlFecha);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        desdeVentas = rs.getInt(1);
                    }
                }
            }

            int desdeRapidas = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ventas_rapidas WHERE fecha::date = ? AND grupo_venta_id IS NULL")) {
                ps.setDate(1, sqlFecha);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        desdeRapidas = rs.getInt(1);
                    }
                }
            }

            return desdeVentas + desdeRapidas;

        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        } finally {
            cerrarConexion(conn);
        }
    }

    public int guardarReporteYReiniciar(
            double total,
            double totalEfectivo,
            double totalTransferencia,
            Map<String, Integer> resumen,
            String usuario,
            String fecha) {

        Connection conn = null;
        try {
            conn = Conexion.conectar();
            conn.setAutoCommit(false);

            try (PreparedStatement psCheck = conn.prepareStatement(
                    "SELECT COUNT(*) FROM reportes WHERE fecha::date = ?")) {
                psCheck.setDate(1, java.sql.Date.valueOf(fecha));
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        conn.rollback();
                        return 0;
                    }
                }
            }

            StringBuilder detalleStr = new StringBuilder();
            for (Map.Entry<String, Integer> entry : resumen.entrySet()) {
                detalleStr.append(entry.getKey()).append(":").append(entry.getValue()).append(" uds.|");
            }

            // ── Categorías ────────────────────────────────────────────────────────
            int[] cats = resumenCategorias(fecha);
            StringBuilder detalleCats = new StringBuilder();
            if (cats[0] > 0) {
                detalleCats.append("EMPANADAS:").append(cats[0]).append("|");
            }
            if (cats[1] > 0) {
                detalleCats.append("SOPAIPILLAS:").append(cats[1]).append("|");
            }
            if (cats[2] > 0) {
                detalleCats.append("RAPIDOS:").append(cats[2]).append("|");
            }

            // ── Conteos ───────────────────────────────────────────────────────────
            int conteoVentas = contarVentasDelDia(conn, fecha);
            double pendiente = totalPendienteDelDia(fecha);
            int pedidosWeb = contarVentasWeb(fecha);
            int pedidosLocal = contarVentasLocal(fecha);

            // ── Pendientes liquidados antes del cierre ────────────────────────────
            double liquidadoEfectivo = 0;
            double liquidadoTransferencia = 0;

            try (PreparedStatement psLiq = conn.prepareStatement(
                    "SELECT COALESCE(SUM(total), 0), tipo_pago_liquidacion "
                    + "FROM ventas_pendientes "
                    + "WHERE fecha_venta = ? AND estado = 'PAGADO' "
                    + "GROUP BY tipo_pago_liquidacion")) {
                psLiq.setDate(1, java.sql.Date.valueOf(fecha));
                ResultSet rsLiq = psLiq.executeQuery();
                while (rsLiq.next()) {
                    String tipo = rsLiq.getString("tipo_pago_liquidacion");
                    double monto = rsLiq.getDouble(1);
                    if ("EFECTIVO".equals(tipo)) {
                        liquidadoEfectivo += monto; 
                    }else if ("TRANSFERENCIA".equals(tipo)) {
                        liquidadoTransferencia += monto;
                    }
                }
            }

            // ── INSERT reporte ────────────────────────────────────────────────────
            try (PreparedStatement psReporte = conn.prepareStatement(
                    "INSERT INTO reportes (fecha, total, total_efectivo, total_transferencia, "
                    + "total_pendiente, generado_por, detalle, pedidos_web, pedidos_local, detalle_categorias) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

                psReporte.setDate(1, java.sql.Date.valueOf(fecha));
                psReporte.setDouble(2, total + liquidadoEfectivo + liquidadoTransferencia);
                psReporte.setDouble(3, totalEfectivo + liquidadoEfectivo);
                psReporte.setDouble(4, totalTransferencia + liquidadoTransferencia);
                psReporte.setDouble(5, pendiente);
                psReporte.setString(6, usuario);
                psReporte.setString(7, detalleStr.toString());
                psReporte.setInt(8, pedidosWeb);
                psReporte.setInt(9, pedidosLocal);
                psReporte.setString(10, detalleCats.toString());
                psReporte.executeUpdate();
            }

            // ── Eliminar ventas del día ───────────────────────────────────────────
            try (PreparedStatement psDel1 = conn.prepareStatement(
                    "DELETE FROM detalle_ventas WHERE venta_id IN "
                    + "(SELECT id FROM ventas WHERE fecha::date = ?)")) {
                psDel1.setDate(1, java.sql.Date.valueOf(fecha));
                psDel1.executeUpdate();
            }

            try (PreparedStatement psDel2 = conn.prepareStatement(
                    "DELETE FROM ventas WHERE fecha::date = ?")) {
                psDel2.setDate(1, java.sql.Date.valueOf(fecha));
                psDel2.executeUpdate();
            }

            try (PreparedStatement psDel3 = conn.prepareStatement(
                    "DELETE FROM ventas_rapidas WHERE fecha::date = ?")) {
                psDel3.setDate(1, java.sql.Date.valueOf(fecha));
                psDel3.executeUpdate();
            }

            conn.commit();
            Conexion.invalidateCache(CACHE_PREFIX);
            return 1;

        } catch (SQLException e) {
            System.err.println("Error en guardarReporteYReiniciar: " + e.getMessage());
            e.printStackTrace();
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            return -1;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
            cerrarConexion(conn);
        }
    }

    public boolean registrarVentaDesdeCarrita(
            Map<String, Integer> carrito,
            Map<String, String> categorias,
            Map<String, String> nombres,
            Map<String, Double> precios,
            Map<String, Integer> ids,
            String tipoPago,
            String nombreCliente) {

        Map<Integer, Integer> items = new java.util.LinkedHashMap<>();
        Map<Integer, String> categoriasById = new java.util.LinkedHashMap<>();
        Map<Integer, String> nombresById = new java.util.LinkedHashMap<>();
        Map<Integer, Double> preciosById = new java.util.LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry : carrito.entrySet()) {
            String key = entry.getKey();
            Integer id = ids.get(key);
            if (id == null) {
                continue;
            }

            items.put(id, entry.getValue());
            categoriasById.put(id, categorias.get(key));
            nombresById.put(id, nombres.get(key));
            preciosById.put(id, precios.get(key));
        }

        return registrarVenta(items, categoriasById, nombresById, preciosById, tipoPago, nombreCliente);
    }

    private String hoy() {
        return java.time.LocalDate.now().toString();
    }

    private double queryDouble(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                Object p = params[i];

                if (p instanceof java.sql.Date) {
                    stmt.setDate(i + 1, (java.sql.Date) p);
                } else if (p instanceof String) {
                    stmt.setString(i + 1, (String) p);
                } else if (p instanceof Integer) {
                    stmt.setInt(i + 1, (Integer) p);
                } else if (p instanceof Double) {
                    stmt.setDouble(i + 1, (Double) p);
                } else {
                    stmt.setObject(i + 1, p);
                }
            }

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        }
    }

    private void cerrarConexion(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            Conexion.devolver(conn);
        } catch (Exception e) {
            System.err.println("Error devolviendo conexión: " + e.getMessage());
        }
    }

    public void invalidarCache() {
        Conexion.invalidateCache(CACHE_PREFIX);
    }

    public boolean registrarVentaSinDescontarStock(
            Map<String, Integer> carrito,
            Map<String, String> categorias,
            Map<String, String> nombres,
            Map<String, Double> precios,
            Map<String, Integer> ids,
            String tipoPago,
            String nombreCliente) {

        Connection conn = null;
        try {
            conn = Conexion.conectar();
            conn.setAutoCommit(false);

            double totalVenta = 0;
            for (Map.Entry<String, Integer> entry : carrito.entrySet()) {
                Double precio = precios.get(entry.getKey());
                if (precio != null) {
                    totalVenta += precio * entry.getValue();
                }
            }

            java.sql.Date hoy = java.sql.Date.valueOf(java.time.LocalDate.now());
            int ventaId = -1;

            boolean tieneConId = ids.values().stream().anyMatch(id -> id != null && id > 0);

            if (tieneConId) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO ventas (fecha, total, tipo_pago, nombre_cliente, origen) VALUES (?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setDate(1, hoy);
                    ps.setDouble(2, totalVenta);
                    ps.setString(3, tipoPago);
                    ps.setString(4, nombreCliente);
                    ps.setString(5, "LOCAL");
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            conn.rollback();
                            return false;
                        }
                        ventaId = keys.getInt(1);
                    }
                }

                for (Map.Entry<String, Integer> entry : carrito.entrySet()) {
                    String key = entry.getKey();
                    Integer prodId = ids.get(key);
                    if (prodId == null || prodId <= 0) {
                        continue;
                    }

                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO detalle_ventas (venta_id, producto_tipo, producto_id, nombre, cantidad, subtotal) "
                            + "VALUES (?, ?, ?, ?, ?, ?)")) {
                        ps.setInt(1, ventaId);
                        ps.setString(2, categorias.getOrDefault(key, "").toLowerCase());
                        ps.setInt(3, prodId);
                        ps.setString(4, nombres.get(key));
                        ps.setInt(5, entry.getValue());
                        ps.setDouble(6, precios.get(key) * entry.getValue());
                        ps.executeUpdate();
                    }
                }
            }

            for (Map.Entry<String, Integer> entry : carrito.entrySet()) {
                String key = entry.getKey();
                Integer prodId = ids.get(key);
                if (prodId != null && prodId > 0) {
                    continue;
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO ventas_rapidas (fecha, nombre, cantidad, precio_unitario, subtotal, tipo_pago, grupo_venta_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setDate(1, hoy);
                    ps.setString(2, nombres.get(key));
                    ps.setInt(3, entry.getValue());
                    ps.setDouble(4, precios.get(key));
                    ps.setDouble(5, precios.get(key) * entry.getValue());
                    ps.setString(6, tipoPago);
                    if (ventaId > 0) {
                        ps.setInt(7, ventaId);
                    } else {
                        ps.setNull(7, Types.INTEGER);
                    }
                    ps.executeUpdate();
                }
            }

            conn.commit();
            Conexion.invalidateCache(CACHE_PREFIX);
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            try {
                if (conn != null) {
                    conn.rollback();

                }
            } catch (SQLException ignored) {
            }
            return false;
        } finally {
            cerrarConexion(conn);
        }
    }
}
