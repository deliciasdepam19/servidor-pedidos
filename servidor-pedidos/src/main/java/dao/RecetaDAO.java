package dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RecetaDAO {

    public List<RecetaItem> obtenerPorProducto(int idProducto, String tipo) {
        List<RecetaItem> items = new ArrayList<>();
        String sql = "SELECT id_ingrediente, cantidad_g FROM receta "
                + "WHERE id_producto_tipo = ? "
                + "AND (id_producto = ? OR id_producto = 0) "
                + "AND nombre_producto IS NULL "
                + "ORDER BY id_producto DESC";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, tipo);
            ps.setInt(2, idProducto);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                items.add(new RecetaItem(
                        rs.getInt("id_ingrediente"),
                        rs.getDouble("cantidad_g")));
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
        return items;
    }

    public List<RecetaItem> obtenerPorNombre(String nombreProducto) {
        List<RecetaItem> items = new ArrayList<>();
        String sql = "SELECT id_ingrediente, cantidad_g FROM receta "
                + "WHERE id_producto_tipo = 'rapido' "
                + "AND LOWER(nombre_producto) = LOWER(?)";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, nombreProducto.trim());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(new RecetaItem(
                        rs.getInt("id_ingrediente"),
                        rs.getDouble("cantidad_g")));
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
        return items;
    }
}
