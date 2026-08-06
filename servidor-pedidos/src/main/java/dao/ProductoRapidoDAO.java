package dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductoRapidoDAO {

    public List<Object[]> listar() {
        List<Object[]> lista = new ArrayList<>();
        String sql = "SELECT id, nombre, precio, COALESCE(categoria, 'Panadería') AS categoria FROM productos_rapidos ORDER BY id ASC";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String nombre = rs.getString("nombre");
                double precio = rs.getDouble("precio");
                String categoria = rs.getString("categoria");
                lista.add(new Object[]{id, nombre, precio, categoria});
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

    public boolean agregar(String nombre, double precio, String categoria) {
        String sql = "INSERT INTO productos_rapidos (nombre, precio, categoria) VALUES (?, ?, ?)";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, nombre.trim());
            ps.setDouble(2, precio);
            ps.setString(3, categoria != null ? categoria.trim() : "Panadería");
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

    public boolean actualizar(int id, String nombre, double precio, String categoria) {
        String sql = "UPDATE productos_rapidos SET nombre = ?, precio = ?, categoria = ? WHERE id = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, nombre.trim());
            ps.setDouble(2, precio);
            ps.setString(3, categoria != null ? categoria.trim() : "Panadería");
            ps.setInt(4, id);
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

    public boolean eliminar(int id) {
        String sql = "DELETE FROM productos_rapidos WHERE id = ?";
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

    public boolean existeNombre(String nombre) {
        String sql = "SELECT COUNT(*) FROM productos_rapidos WHERE LOWER(nombre) = LOWER(?)";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, nombre.trim());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                boolean existe = rs.getInt(1) > 0;
                rs.close();
                ps.close();
                return existe;
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

    public void modificarPrecio(int id, double nuevoPrecio) {
        String sql = "UPDATE productos_rapidos SET precio = ? WHERE id = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setDouble(1, nuevoPrecio);
            ps.setInt(2, id);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            System.err.println("Error modificando precio: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public void limpiarDuplicados() {
        Connection conn = null;
        try {
            conn = Conexion.conectar();

            // 1) Eliminar duplicados exactos (case-insensitive)
            String sql = "DELETE FROM productos_rapidos WHERE id NOT IN " +
                    "(SELECT MIN(id) FROM productos_rapidos GROUP BY LOWER(nombre))";
            PreparedStatement ps = conn.prepareStatement(sql);
            int eliminados = ps.executeUpdate();
            ps.close();
            if (eliminados > 0) {
                System.out.println("PRODUCTOS_RAPIDOS: eliminados " + eliminados + " duplicados exactos");
            }

            // 2) Eliminar plurales sobrantes: normalizar nombre y comparar
            String selectSql = "SELECT id, nombre FROM productos_rapidos ORDER BY id ASC";
            ps = conn.prepareStatement(selectSql);
            java.sql.ResultSet rs = ps.executeQuery();

            java.util.Map<String, java.util.List<int[]>> grupos = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                int id = rs.getInt("id");
                String nombre = rs.getString("nombre");
                String key = normalizarNombre(nombre);
                grupos.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(new int[]{id});
            }
            rs.close();
            ps.close();

            String deleteSql = "DELETE FROM productos_rapidos WHERE id = ?";
            ps = conn.prepareStatement(deleteSql);
            int plurales = 0;
            for (java.util.List<int[]> ids : grupos.values()) {
                if (ids.size() > 1) {
                    for (int i = 1; i < ids.size(); i++) {
                        ps.setInt(1, ids.get(i)[0]);
                        ps.executeUpdate();
                        plurales++;
                    }
                }
            }
            ps.close();
            if (plurales > 0) {
                System.out.println("PRODUCTOS_RAPIDOS: eliminados " + plurales + " plurales sobrantes");
            }

        } catch (SQLException e) {
            System.err.println("Error limpiando duplicados: " + e.getMessage());
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    private String normalizarNombre(String nombre) {
        if (nombre == null) return "";
        String n = nombre.trim().toLowerCase();
        n = n.replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
        if (n.endsWith("es") && n.length() > 3) {
            n = n.substring(0, n.length() - 2);
        } else if (n.endsWith("s") && n.length() > 2) {
            n = n.substring(0, n.length() - 1);
        }
        return n;
    }
}
