package dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import model.Ingrediente;

public class InventarioDAO {

    public List<Ingrediente> listar() {
        List<Ingrediente> lista = new ArrayList<>();
        String sql = "SELECT id, nombre, cantidad, sacos_disponibles, unidad, precio_compra, "
                + "nombre_proveedor, fecha_ingreso, fecha_agotado, kg_saco FROM inventario ORDER BY id";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                lista.add(new Ingrediente(
                        rs.getInt("id"),
                        rs.getString("nombre"),
                        rs.getDouble("cantidad"),
                        rs.getInt("sacos_disponibles"),
                        rs.getString("unidad"),
                        rs.getDouble("precio_compra"),
                        rs.getString("nombre_proveedor"),
                        rs.getString("fecha_ingreso"),
                        rs.getString("fecha_agotado"),
                        rs.getDouble("kg_saco")
                ));
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
        return lista;
    }

    public int agregar(Ingrediente ing) {
        String sql = "INSERT INTO inventario (nombre, cantidad, sacos_disponibles, unidad, precio_compra, nombre_proveedor, kg_saco) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (nombre) DO UPDATE "
                + "SET sacos_disponibles = inventario.sacos_disponibles + EXCLUDED.sacos_disponibles, "
                + "    precio_compra     = EXCLUDED.precio_compra, "
                + "    nombre_proveedor  = EXCLUDED.nombre_proveedor, "
                + "    kg_saco           = EXCLUDED.kg_saco, "
                + "    fecha_agotado     = NULL "
                + "RETURNING id";
        Connection conn = null;
        int idResultante = -1;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, ing.getNombre());
            ps.setDouble(2, ing.getCantidad());
            ps.setInt(3, ing.getSacosDisponibles());
            ps.setString(4, ing.getUnidad());
            ps.setDouble(5, ing.getPrecioCompra());
            ps.setString(6, ing.getNombreProveedor());
            ps.setDouble(7, ing.getKgSaco());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                idResultante = rs.getInt("id");
            }
            rs.close();
            ps.close();

            if (idResultante > 0) {
                registrarSacosNuevos(conn, idResultante, ing.getNombre(),
                        ing.getKgSaco(), ing.getSacosDisponibles() + 1);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return idResultante;
    }

    public void actualizarCantidad(int id, double nuevaCantidad) {
        String sql = nuevaCantidad <= 0
                ? "UPDATE inventario SET cantidad = 0, fecha_agotado = CURRENT_DATE WHERE id = ?"
                : "UPDATE inventario SET cantidad = ?, fecha_agotado = NULL WHERE id = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            if (nuevaCantidad <= 0) {
                ps.setInt(1, id);
            } else {
                ps.setDouble(1, nuevaCantidad);
                ps.setInt(2, id);
            }
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public void eliminar(int id) {
        String sql = "DELETE FROM inventario WHERE id = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public void agregarSacos(int id, double gramosSaco, int sacosReserva) {
        String nombre = "";
        for (Ingrediente ing : listar()) {
            if (ing.getId() == id) {
                nombre = ing.getNombre();
                break;
            }
        }
        final String nombreFinal = nombre;

        String sql = "UPDATE inventario SET "
                + "sacos_disponibles = sacos_disponibles + ?, "
                + "fecha_agotado     = NULL "
                + "WHERE id = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, sacosReserva);
            ps.setInt(2, id);
            ps.executeUpdate();
            ps.close();

            registrarSacosNuevos(conn, id, nombreFinal,
                    gramosSaco / 1000.0, sacosReserva);

            registrarMovimiento(conn, id, nombreFinal, "INGRESO",
                    gramosSaco * sacosReserva, sacosReserva, "reabastecimiento");

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public List<String> getAlertasStock() {
        List<String> alertas = new ArrayList<>();
        for (Ingrediente ing : listar()) {
            int reserva = ing.getSacosDisponibles();
            double cantidad = ing.getCantidad();
            double kgSaco = ing.getKgSaco() * 1000.0;

            if (reserva == 1) {
                alertas.add("⚠ Stock bajo: " + ing.getNombre() + " Queda solo 1 saco en reserva");
            }

            if (reserva == 0) {
                alertas.add("🔴 Sin reserva: " + ing.getNombre() + " Último saco abierto");
            }

            if (kgSaco > 0 && cantidad <= kgSaco / 2.0) {
                alertas.add("⚠ Último saco abierto a la mitad: " + ing.getNombre()
                        + " (" + (int) (cantidad / 1000) + " kg restantes)");
            }
        }
        return alertas;
    }

    private void registrarMovimiento(Connection conn, int idIngrediente,
            String nombre, String tipo, double cantidad, int sacos, String motivo) {
        String sql = "INSERT INTO inventario_movimientos "
                + "(id_ingrediente, nombre_ingrediente, tipo, cantidad, sacos, motivo) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, idIngrediente);
            ps.setString(2, nombre);
            ps.setString(3, tipo);
            ps.setDouble(4, cantidad);
            ps.setInt(5, sacos);
            ps.setString(6, motivo);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            System.err.println("registrarMovimiento omitido: " + e.getMessage());
        }
    }

    public void descontarStock(int idIngrediente, double gramosADescontar) {
        String nombre = "";
        for (Ingrediente ing : listar()) {
            if (ing.getId() == idIngrediente) {
                nombre = ing.getNombre();
                break;
            }
        }
        final String nombreFinal = nombre;

        String sql = "UPDATE inventario SET "
                + "cantidad = CASE "
                + "  WHEN cantidad - ? >= 0 THEN cantidad - ? "
                + "  WHEN sacos_disponibles > 0 THEN GREATEST((kg_saco * 1000 - (? - cantidad)), 0) "
                + "  ELSE 0 "
                + "END, "
                + "sacos_disponibles = CASE "
                + "  WHEN cantidad - ? < 0 AND sacos_disponibles > 0 THEN sacos_disponibles - 1 "
                + "  ELSE sacos_disponibles "
                + "END, "
                + "fecha_agotado = CASE "
                + "  WHEN cantidad - ? <= 0 AND sacos_disponibles = 0 THEN CURRENT_DATE "
                + "  ELSE NULL "
                + "END "
                + "WHERE id = ?";

        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setDouble(1, gramosADescontar);
            ps.setDouble(2, gramosADescontar);
            ps.setDouble(3, gramosADescontar);
            ps.setDouble(4, gramosADescontar);
            ps.setDouble(5, gramosADescontar);
            ps.setInt(6, idIngrediente);
            ps.executeUpdate();
            ps.close();

            String checkSql = "SELECT cantidad FROM inventario WHERE id = ?";
            PreparedStatement check = conn.prepareStatement(checkSql);
            check.setInt(1, idIngrediente);
            ResultSet checkRs = check.executeQuery();
            boolean agotado = false;
            if (checkRs.next()) {
                agotado = checkRs.getDouble("cantidad") <= 0;
            }
            checkRs.close();
            check.close();

            if (agotado) {
                cerrarSacoAgotado(conn, idIngrediente);
            }

            registrarMovimiento(conn, idIngrediente, nombreFinal, "DESCUENTO",
                    gramosADescontar, 0, "venta");

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public void registrarSacosNuevos(Connection conn, int idIngrediente, String nombre,
            double kgSaco, int cantidad) {
        boolean tieneAbiertoExistente = false;
        try {
            String checkSql = "SELECT COUNT(*) FROM inventario_sacos "
                    + "WHERE id_ingrediente = ? AND estado = 'ABIERTO'";
            PreparedStatement checkPs = conn.prepareStatement(checkSql);
            checkPs.setInt(1, idIngrediente);
            ResultSet checkRs = checkPs.executeQuery();
            if (checkRs.next()) {
                tieneAbiertoExistente = checkRs.getInt(1) > 0;
            }
            checkRs.close();
            checkPs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String sql = "INSERT INTO inventario_sacos "
                + "(id_ingrediente, nombre_ingrediente, kg_saco, estado) "
                + "VALUES (?, ?, ?, ?)";
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            for (int i = 0; i < cantidad; i++) {
                ps.setInt(1, idIngrediente);
                ps.setString(2, nombre);
                ps.setDouble(3, kgSaco);

                String estado = (!tieneAbiertoExistente && i == 0) ? "ABIERTO" : "RESERVA";
                ps.setString(4, estado);
                ps.executeUpdate();
            }
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void registrarSacosNuevos(int idIngrediente, String nombre,
            double kgSaco, int cantidad) {
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            registrarSacosNuevos(conn, idIngrediente, nombre, kgSaco, cantidad);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public void cerrarSacoAgotado(Connection conn, int idIngrediente) {
        try {
            String sqlCerrar = "UPDATE inventario_sacos SET "
                    + "estado = 'AGOTADO', "
                    + "fecha_agotado = CURRENT_DATE, "
                    + "dias_duracion = CURRENT_DATE - fecha_ingreso "
                    + "WHERE id = ("
                    + "  SELECT id FROM inventario_sacos "
                    + "  WHERE id_ingrediente = ? AND estado = 'ABIERTO' "
                    + "  ORDER BY fecha_ingreso ASC LIMIT 1"
                    + ")";
            PreparedStatement ps1 = conn.prepareStatement(sqlCerrar);
            ps1.setInt(1, idIngrediente);
            ps1.executeUpdate();
            ps1.close();

            String sqlAbrir = "UPDATE inventario_sacos SET "
                    + "estado = 'ABIERTO', "
                    + "fecha_ingreso = CURRENT_DATE "
                    + "WHERE id = ("
                    + "  SELECT id FROM inventario_sacos "
                    + "  WHERE id_ingrediente = ? AND estado = 'RESERVA' "
                    + "  ORDER BY fecha_ingreso ASC LIMIT 1"
                    + ")";
            PreparedStatement ps2 = conn.prepareStatement(sqlAbrir);
            ps2.setInt(1, idIngrediente);
            int promovidos = ps2.executeUpdate();
            ps2.close();

            if (promovidos > 0) {
                String sqlRecargar = "UPDATE inventario SET "
                        + "cantidad = (SELECT kg_saco * 1000 FROM inventario_sacos "
                        + "            WHERE id_ingrediente = ? AND estado = 'ABIERTO' "
                        + "            ORDER BY fecha_ingreso ASC LIMIT 1), "
                        + "fecha_agotado = NULL "
                        + "WHERE id = ?";
                PreparedStatement ps3 = conn.prepareStatement(sqlRecargar);
                ps3.setInt(1, idIngrediente);
                ps3.setInt(2, idIngrediente);
                ps3.executeUpdate();
                ps3.close();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void cerrarSacoAgotado(int idIngrediente) {
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            cerrarSacoAgotado(conn, idIngrediente);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public List<Object[]> listarSacos(int idIngrediente) {
        List<Object[]> lista = new ArrayList<>();
        String sql = "SELECT kg_saco, fecha_ingreso, fecha_agotado, "
                + "dias_duracion, estado "
                + "FROM inventario_sacos "
                + "WHERE id_ingrediente = ? "
                + "ORDER BY fecha_ingreso DESC";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, idIngrediente);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                lista.add(new Object[]{
                    rs.getDouble("kg_saco") + " kg",
                    rs.getString("fecha_ingreso"),
                    rs.getString("fecha_agotado") != null ? rs.getString("fecha_agotado") : "—",
                    rs.getString("dias_duracion") != null ? rs.getString("dias_duracion") + " días" : "—",
                    rs.getString("estado")
                });
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
        return lista;
    }
}
