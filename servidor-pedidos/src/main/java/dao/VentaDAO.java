package dao;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class VentaDAO {

    private static final String CACHE_PREFIX = "venta_";

    public double totalDelDia(String fecha) {
        return queryDouble("SELECT COALESCE(SUM(total), 0) FROM ventas WHERE fecha = ? AND tipo_pago != 'PENDIENTE'", fecha)
                + queryDouble("SELECT COALESCE(SUM(subtotal), 0) FROM ventas_rapidas WHERE fecha = ? AND grupo_venta_id IS NULL", fecha);
    }

    public double totalEfectivoDelDia(String fecha) {
        return queryDouble("SELECT COALESCE(SUM(total), 0) FROM ventas WHERE fecha = ? AND tipo_pago = 'EFECTIVO'", fecha)
                + queryDouble("SELECT COALESCE(SUM(subtotal), 0) FROM ventas_rapidas WHERE fecha = ? AND tipo_pago = 'EFECTIVO' AND grupo_venta_id IS NULL", fecha);
    }

    public double totalTransferenciaDelDia(String fecha) {
        return queryDouble("SELECT COALESCE(SUM(total), 0) FROM ventas WHERE fecha = ? AND tipo_pago = 'TRANSFERENCIA'", fecha)
                + queryDouble("SELECT COALESCE(SUM(subtotal), 0) FROM ventas_rapidas WHERE fecha = ? AND tipo_pago = 'TRANSFERENCIA' AND grupo_venta_id IS NULL", fecha);
    }

    public double totalPendienteDelDia() {
        return PendientesDAO.totalPendientePorFecha(hoy());
    }

    public double totalPendienteDelDia(String fecha) {
        return PendientesDAO.totalPendientePorFecha(fecha);
    }

    public Map<String, Integer> resumenDelDia() {
        Map<String, Integer> resumen = new LinkedHashMap<>();
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT d.nombre, SUM(d.cantidad) as total "
                    + "FROM ventas v JOIN detalle_ventas d ON v.id = d.venta_id "
                    + "WHERE v.fecha = ? AND v.tipo_pago != 'PENDIENTE' "
                    + "GROUP BY d.nombre ORDER BY d.producto_tipo, d.nombre");
            stmt.setString(1, hoy());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                resumen.put(rs.getString("nombre"), rs.getInt("total"));
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            System.err.println("❌ Error en resumenDelDia: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        resumenRapidoDelDia().forEach((nombre, cantidad) -> resumen.merge(nombre, cantidad, Integer::sum));
        return resumen;
    }

    public Map<String, Integer> resumenDelDia(String fecha) {
        Map<String, Integer> resumen = new java.util.LinkedHashMap<>();
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT d.nombre, SUM(d.cantidad) as total FROM ventas v "
                    + "JOIN detalle_ventas d ON v.id = d.venta_id "
                    + "WHERE v.fecha = ? AND v.tipo_pago != 'PENDIENTE' "
                    + "GROUP BY d.nombre ORDER BY d.producto_tipo, d.nombre")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    resumen.put(rs.getString("nombre"), rs.getInt("total"));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT nombre, SUM(cantidad) as total FROM ventas_rapidas "
                    + "WHERE fecha = ? GROUP BY nombre ORDER BY nombre")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    resumen.merge(rs.getString("nombre"), rs.getInt("total"), Integer::sum);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return resumen;
    }

    public Map<String, Integer> resumenRapidoDelDia() {
        Map<String, Integer> resumen = new LinkedHashMap<>();
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT nombre, SUM(cantidad) as total FROM ventas_rapidas "
                    + "WHERE fecha = ? GROUP BY nombre ORDER BY nombre");
            stmt.setString(1, hoy());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                resumen.put(rs.getString("nombre"), rs.getInt("total"));
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            System.err.println("❌ Error en resumenRapidoDelDia: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return resumen;
    }

    public int contarVentasDelDia() {
        String cacheKey = CACHE_PREFIX + "count_" + hoy();
        Object cached = Conexion.getCached(cacheKey);
        if (cached instanceof Integer) {
            return (Integer) cached;
        }

        int ventas = 0;
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ventas WHERE fecha = ? AND tipo_pago != 'PENDIENTE'")) {
                ps.setString(1, hoy());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    ventas += rs.getInt(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ventas_rapidas WHERE fecha = ? AND grupo_venta_id IS NULL")) {
                ps.setString(1, hoy());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    ventas += rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error en contarVentasDelDia: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        Conexion.cacheResult(cacheKey, ventas);
        return ventas;
    }

    public int contarVentasDelDia(String fecha) {
        int ventas = 0;
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ventas WHERE fecha = ? AND tipo_pago != 'PENDIENTE'")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    ventas += rs.getInt(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ventas_rapidas WHERE fecha = ? AND grupo_venta_id IS NULL")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    ventas += rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error en contarVentasDelDia(fecha): " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return ventas;
    }

    public boolean registrarVenta(Map<Integer, Integer> items,
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

            // Migración automática columna grupo_venta_id
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE ventas_rapidas ADD COLUMN grupo_venta_id INTEGER DEFAULT NULL");
            } catch (SQLException ignored) {
            }

            // ── Verificar stock ───────────────────────────────────────────────
            for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
                int id = entry.getKey();
                int cantidad = entry.getValue();
                String tabla = ProductoDAO.tablaDesdeCategoria(categorias.get(id));
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT stock FROM " + tabla + " WHERE id = ?")) {
                    ps.setInt(1, id);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next() || rs.getInt("stock") < cantidad) {
                        conn.rollback();
                        return false;
                    }
                }
            }

            // ── Calcular total ────────────────────────────────────────────────
            double totalVenta = 0;
            for (Map.Entry<Integer, Integer> e : items.entrySet()) {
                totalVenta += precios.get(e.getKey()) * e.getValue();
            }
            if (rapidosCant != null) {
                for (String key : rapidosCant.keySet()) {
                    totalVenta += rapidosPrecios.get(key) * rapidosCant.get(key);
                }
            }

            // ── Insertar venta principal (si hay items con stock) ─────────────
            int ventaId = -1;
            if (!items.isEmpty()) {
                try (PreparedStatement psVenta = conn.prepareStatement(
                        "INSERT INTO ventas (fecha, total, tipo_pago, nombre_cliente) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    psVenta.setString(1, hoy());
                    psVenta.setDouble(2, totalVenta);
                    psVenta.setString(3, tipoPago);
                    psVenta.setString(4, nombreCliente);
                    psVenta.executeUpdate();
                    ResultSet keys = psVenta.getGeneratedKeys();
                    if (!keys.next()) {
                        conn.rollback();
                        return false;
                    }
                    ventaId = keys.getInt(1);
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
                    try (PreparedStatement psR = conn.prepareStatement(
                            "INSERT INTO ventas_rapidas (fecha, nombre, cantidad, precio_unitario, subtotal, tipo_pago, grupo_venta_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                        psR.setString(1, hoy());
                        psR.setString(2, rapidosNombres.get(key));
                        psR.setInt(3, rapidosCant.get(key));
                        psR.setDouble(4, rapidosPrecios.get(key));
                        psR.setDouble(5, rapidosPrecios.get(key) * rapidosCant.get(key));
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
            System.err.println("❌ Error registrando venta: " + e.getMessage());
            e.printStackTrace();
            try {
                if (conn != null) {
                    conn.rollback();

                }
            } catch (SQLException e2) {
                e2.printStackTrace();
            }
            return false;
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    Conexion.devolver(conn);
                }
            } catch (SQLException e) {
                System.err.println("❌ Error reseteando AutoCommit: " + e.getMessage());
            }
        }
    }

    public boolean registrarVentaRapida(String nombre, int cantidad, double precioUnitario, String tipoPago) {
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE ventas_rapidas ADD COLUMN grupo_venta_id INTEGER DEFAULT NULL");
            } catch (SQLException ignored) {
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ventas_rapidas (fecha, nombre, cantidad, precio_unitario, subtotal, tipo_pago, grupo_venta_id) "
                    + "VALUES (?, ?, ?, ?, ?, ?, NULL)")) {
                ps.setString(1, hoy());
                ps.setString(2, nombre);
                ps.setInt(3, cantidad);
                ps.setDouble(4, precioUnitario);
                ps.setDouble(5, precioUnitario * cantidad);
                ps.setString(6, tipoPago);
                int filas = ps.executeUpdate();
                Conexion.invalidateCache(CACHE_PREFIX);
                return filas > 0;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error en registrarVentaRapida: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public int guardarReporteYReiniciar(double total, double totalEfectivo,
            double totalTransferencia, Map<String, Integer> resumen, String generadoPor) {
        return guardarReporteYReiniciar(total, totalEfectivo, totalTransferencia, resumen, generadoPor, hoy());
    }

    public int guardarReporteYReiniciar(double total, double totalEfectivo,
            double totalTransferencia, Map<String, Integer> resumen,
            String generadoPor, String fecha) {

        double totalPendiente = PendientesDAO.totalPendientePorFecha(fecha);
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            conn.setAutoCommit(false);

            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE reportes ADD COLUMN generado_por TEXT DEFAULT ''");
            } catch (SQLException ignored) {
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE reportes ADD COLUMN detalle_categorias TEXT DEFAULT ''");
            } catch (SQLException ignored) {
            }

            try (PreparedStatement psCheck = conn.prepareStatement(
                    "SELECT COUNT(*) FROM reportes WHERE fecha = ?")) {
                psCheck.setString(1, fecha);
                ResultSet rsCheck = psCheck.executeQuery();
                if (rsCheck.next() && rsCheck.getInt(1) > 0) {
                    conn.rollback();
                    return 0;
                }
            }

            double totalEmp = 0, efecEmp = 0, transfEmp = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(d.subtotal),0), "
                    + "COALESCE(SUM(CASE WHEN v.tipo_pago='EFECTIVO' THEN d.subtotal ELSE 0 END),0), "
                    + "COALESCE(SUM(CASE WHEN v.tipo_pago='TRANSFERENCIA' THEN d.subtotal ELSE 0 END),0) "
                    + "FROM detalle_ventas d JOIN ventas v ON d.venta_id=v.id "
                    + "WHERE v.fecha=? AND LOWER(d.producto_tipo) LIKE '%empanada%' AND v.tipo_pago!='PENDIENTE'")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    totalEmp = rs.getDouble(1);
                    efecEmp = rs.getDouble(2);
                    transfEmp = rs.getDouble(3);
                }
            }

            StringBuilder detalleEmp = new StringBuilder();
            String masVendida = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT d.nombre, SUM(d.cantidad) as total_cant "
                    + "FROM detalle_ventas d JOIN ventas v ON d.venta_id=v.id "
                    + "WHERE v.fecha=? AND LOWER(d.producto_tipo) LIKE '%empanada%' AND v.tipo_pago!='PENDIENTE' "
                    + "GROUP BY d.nombre ORDER BY total_cant DESC")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String nombre = rs.getString("nombre");
                    int cant = rs.getInt("total_cant");
                    if (masVendida == null) {
                        masVendida = nombre;
                    }
                    detalleEmp.append(nombre).append(": ").append(cant).append(" uds. | ");
                }
            }

            int conteoWeb = 0, conteoLocal = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM pedidos WHERE DATE(fecha_hora)=? AND UPPER(origen)='WEB'")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    conteoWeb = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM pedidos WHERE DATE(fecha_hora)=? AND UPPER(origen)='LOCAL'")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    conteoLocal = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ventas WHERE fecha=? AND tipo_pago!='PENDIENTE'")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    conteoLocal += rs.getInt(1);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ventas_rapidas WHERE fecha=? AND grupo_venta_id IS NULL")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    conteoLocal += rs.getInt(1);
                }
            }

            StringBuilder detalle = new StringBuilder();
            for (Map.Entry<String, Integer> e : resumen.entrySet()) {
                detalle.append(e.getKey()).append(": ").append(e.getValue()).append(" uds. | ");
            }

            double totalSopa = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(d.subtotal),0) FROM detalle_ventas d JOIN ventas v ON d.venta_id=v.id "
                    + "WHERE v.fecha=? AND LOWER(d.producto_tipo) LIKE '%sopaipilla%' AND v.tipo_pago!='PENDIENTE'")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    totalSopa = rs.getDouble(1);
                }
            }

            double totalRapido = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(subtotal),0), COALESCE(SUM(precio_unitario*cantidad),0) "
                    + "FROM ventas_rapidas WHERE fecha=?")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    totalRapido = Math.max(rs.getDouble(1), rs.getDouble(2));
                }
            }

            String detalleCategorias = "EMPANADAS:" + (long) totalEmp
                    + "|SOPAIPILLAS:" + (long) totalSopa
                    + "|PRODUCTOS_RAPIDOS:" + (long) totalRapido;

            try (PreparedStatement psR = conn.prepareStatement(
                    "INSERT INTO reportes (fecha, total, total_efectivo, total_transferencia, "
                    + "total_pendiente, detalle, pedidos_web, pedidos_local, generado_por, detalle_categorias) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?)")) {
                psR.setString(1, fecha);
                psR.setDouble(2, total);
                psR.setDouble(3, totalEfectivo);
                psR.setDouble(4, totalTransferencia);
                psR.setDouble(5, totalPendiente);
                psR.setString(6, detalle.toString());
                psR.setInt(7, conteoWeb);
                psR.setInt(8, conteoLocal);
                psR.setString(9, generadoPor);
                psR.setString(10, detalleCategorias);
                psR.executeUpdate();
            }

            if (totalEmp > 0) {
                try (PreparedStatement psEmp = conn.prepareStatement(
                        "INSERT INTO reportes_empanadas (fecha, total, total_efectivo, total_transferencia, detalle, empanada_mas_vendida) "
                        + "VALUES (?,?,?,?,?,?)")) {
                    psEmp.setString(1, fecha);
                    psEmp.setDouble(2, totalEmp);
                    psEmp.setDouble(3, efecEmp);
                    psEmp.setDouble(4, transfEmp);
                    psEmp.setString(5, detalleEmp.toString());
                    psEmp.setString(6, masVendida);
                    psEmp.executeUpdate();
                }
            }

            double totalRapEfec = 0, totalRapTransf = 0;
            StringBuilder detalleRap = new StringBuilder();
            String masVendidoRap = null;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(CASE WHEN tipo_pago='EFECTIVO' THEN subtotal ELSE 0 END),0), "
                    + "COALESCE(SUM(CASE WHEN tipo_pago='TRANSFERENCIA' THEN subtotal ELSE 0 END),0) "
                    + "FROM ventas_rapidas WHERE fecha=?")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    totalRapEfec = rs.getDouble(1);
                    totalRapTransf = rs.getDouble(2);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT nombre, SUM(cantidad) as total_cant "
                    + "FROM ventas_rapidas WHERE fecha=? "
                    + "GROUP BY nombre ORDER BY total_cant DESC")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String nombre = rs.getString("nombre");
                    int cant = rs.getInt("total_cant");
                    if (masVendidoRap == null) {
                        masVendidoRap = nombre;
                    }
                    detalleRap.append(nombre).append(": ").append(cant).append(" uds. | ");
                }
            }

            if (totalRapido > 0) {
                try (PreparedStatement psRap = conn.prepareStatement(
                        "INSERT INTO reportes_rapidos (fecha, total, total_efectivo, "
                        + "total_transferencia, detalle, producto_mas_vendido) "
                        + "VALUES (?,?,?,?,?,?)")) {
                    psRap.setString(1, fecha);
                    psRap.setDouble(2, totalRapido);
                    psRap.setDouble(3, totalRapEfec);
                    psRap.setDouble(4, totalRapTransf);
                    psRap.setString(5, detalleRap.toString());
                    psRap.setString(6, masVendidoRap != null ? masVendidoRap : "—");
                    psRap.executeUpdate();
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM detalle_ventas WHERE venta_id IN "
                    + "(SELECT id FROM ventas WHERE fecha=? AND tipo_pago!='PENDIENTE')")) {
                ps.setString(1, fecha);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM ventas WHERE fecha=? AND tipo_pago!='PENDIENTE'")) {
                ps.setString(1, fecha);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM ventas_rapidas WHERE fecha=?")) {
                ps.setString(1, fecha);
                ps.executeUpdate();
            }

            conn.commit();
            Conexion.invalidateCache(CACHE_PREFIX);
            return 1;

        } catch (SQLException e) {
            System.err.println("❌ Error en guardarReporteYReiniciar: " + e.getMessage());
            e.printStackTrace();
            try {
                if (conn != null) {
                    conn.rollback();

                }
            } catch (SQLException e2) {
                e2.printStackTrace();
            }
            return -1;
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    Conexion.devolver(conn);
                }
            } catch (SQLException e) {
                System.err.println("❌ Error reseteando AutoCommit: " + e.getMessage());
            }
        }
    }

    public boolean registrarVentaDesdeCarrita(Map<String, Integer> items,
            Map<String, String> categorias,
            Map<String, String> nombres,
            Map<String, Double> precios,
            Map<String, Integer> ids,
            String tipoPago,
            String nombreCliente) {

        Map<Integer, Integer> itemsInt = new java.util.LinkedHashMap<>();
        Map<Integer, String> categoriasInt = new java.util.LinkedHashMap<>();
        Map<Integer, String> nombresInt = new java.util.LinkedHashMap<>();
        Map<Integer, Double> preciosInt = new java.util.LinkedHashMap<>();
        Map<String, Integer> rapidosCant = new java.util.LinkedHashMap<>();
        Map<String, Double> rapidosPrecios = new java.util.LinkedHashMap<>();
        Map<String, String> rapidosNombres = new java.util.LinkedHashMap<>();

        for (String key : items.keySet()) {
            int id = ids.getOrDefault(key, 0);
            if (id == 0 || esCategoriRapida(categorias.get(key))) {
                rapidosCant.put(key, items.get(key));
                rapidosPrecios.put(key, precios.getOrDefault(key, 0.0));
                rapidosNombres.put(key, nombres.get(key));
            } else {
                itemsInt.put(id, items.get(key));
                categoriasInt.put(id, categorias.get(key));
                nombresInt.put(id, nombres.get(key));
                preciosInt.put(id, precios.get(key));
            }
        }

        return registrarVentaConRapidos(itemsInt, categoriasInt, nombresInt, preciosInt,
                tipoPago, nombreCliente,
                rapidosCant.isEmpty() ? null : rapidosCant,
                rapidosPrecios.isEmpty() ? null : rapidosPrecios,
                rapidosNombres.isEmpty() ? null : rapidosNombres);
    }

    private String hoy() {
        return java.time.LocalDate.now().toString();
    }

    private double queryDouble(String sql, String param) {
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, param);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                double result = rs.getDouble(1);
                rs.close();
                stmt.close();
                return result;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error en queryDouble: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return 0;
    }

    public java.time.LocalDate fechaConVentasSinCierre() {
        String hoy = hoy();
        java.time.LocalDate fechaVentas = null, fechaRapidas = null;
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT MIN(fecha) FROM ventas WHERE fecha < ? AND tipo_pago != 'PENDIENTE'")) {
                ps.setString(1, hoy);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getString(1) != null) {
                    fechaVentas = java.time.LocalDate.parse(rs.getString(1));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT MIN(fecha) FROM ventas_rapidas WHERE fecha < ?")) {
                ps.setString(1, hoy);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getString(1) != null) {
                    fechaRapidas = java.time.LocalDate.parse(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        if (fechaVentas == null) {
            return fechaRapidas;
        }
        if (fechaRapidas == null) {
            return fechaVentas;
        }
        return fechaVentas.isBefore(fechaRapidas) ? fechaVentas : fechaRapidas;
    }

    public void invalidarCache() {
        Conexion.invalidateCache(CACHE_PREFIX);
    }

    public int[] resumenCategorias(String fecha) {
        int[] totales = new int[3];
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(d.cantidad),0) FROM detalle_ventas d JOIN ventas v ON d.venta_id=v.id "
                    + "WHERE v.fecha=? AND LOWER(d.producto_tipo) LIKE '%empanada%' AND v.tipo_pago!='PENDIENTE'")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    totales[0] = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(d.cantidad),0) FROM detalle_ventas d JOIN ventas v ON d.venta_id=v.id "
                    + "WHERE v.fecha=? AND LOWER(d.producto_tipo) LIKE '%sopaipilla%' AND v.tipo_pago!='PENDIENTE'")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    totales[1] = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(cantidad),0) FROM ventas_rapidas WHERE fecha=?")) {
                ps.setString(1, fecha);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    totales[2] = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return totales;
    }

    private String normalizarClave(String nombre) {
        if (nombre == null) {
            return "";
        }
        String[] p = nombre.split(" ", 2);
        String primera = p[0];
        if (primera.equalsIgnoreCase("Empanadas")) {
            primera = "Empanada";
        } else if (primera.equalsIgnoreCase("Churros")) {
            primera = "Churro";
        } else if (primera.equalsIgnoreCase("Sopaipillas")) {
            primera = "Sopaipilla";
        }
        return p.length > 1 ? primera + " " + p[1] : primera;
    }

    private boolean esCategoriRapida(String cat) {
        if (cat == null) {
            return false;
        }
        String normalizada = java.text.Normalizer
                .normalize(cat, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .trim();
        return normalizada.equals("rapido");
    }
}
