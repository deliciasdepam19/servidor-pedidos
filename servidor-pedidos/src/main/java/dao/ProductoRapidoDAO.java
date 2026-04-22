package dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductoRapidoDAO {

    public List<Object[]> listar() {
        List<Object[]> lista = new ArrayList<>();
        String sql = "SELECT id, nombre, precio FROM productos_rapidos ORDER BY id ASC";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String nombre = rs.getString("nombre");
                double precio = rs.getDouble("precio");
                System.out.println("PROD: id=" + id + " nombre=" + nombre + " precio=" + precio);
                lista.add(new Object[]{id, nombre, precio});
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

    public boolean agregar(String nombre, double precio) {
        String sql = "INSERT INTO productos_rapidos (nombre, precio) VALUES (?, ?)";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, nombre.trim());
            ps.setDouble(2, precio);
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

    public boolean actualizar(int id, String nombre, double precio) {
        String sql = "UPDATE productos_rapidos SET nombre = ?, precio = ? WHERE id = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, nombre.trim());
            ps.setDouble(2, precio);
            ps.setInt(3, id);
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
}
