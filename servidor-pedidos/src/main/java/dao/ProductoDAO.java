package dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import model.Producto;

public class ProductoDAO {

    private static final String CACHE_KEY_ALL = "productos_todos";
    private static final String CACHE_KEY_CATEGORIA = "productos_cat_";

    public List<Producto> listarPorCategoria(String categoria) {
        String cacheKey = CACHE_KEY_CATEGORIA + categoria;

        Object cached = Conexion.getCached(cacheKey);
        if (cached instanceof List) {
            return (List<Producto>) cached;
        }

        List<Producto> resultado = switch (categoria.toLowerCase()) {
            case "churros" ->
                listarDesde("churros", "Churro", true);
            case "sopaipillas" ->
                listarDesdeSopaipillas();
            default ->
                listarDesde("empanadas", "Empanada", true);
        };

        Conexion.cacheResult(cacheKey, resultado);
        return resultado;
    }

    public List<Producto> listarPorCategoriaSinCache(String categoria) {
        return switch (categoria.toLowerCase()) {
            case "churros" ->
                listarDesde("churros", "Churro", true);
            case "sopaipillas" ->
                listarDesdeSopaipillas();  // ← Método especial para sopaipillas
            default ->
                listarDesde("empanadas", "Empanada", true);
        };
    }

    private List<Producto> listarDesdeSopaipillas() {
        List<Producto> lista = new ArrayList<>();
        String sql = "SELECT id, precio, stock FROM sopaipillas ORDER BY id";

        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                lista.add(new Producto(
                        rs.getInt("id"),
                        "Sopaipilla",
                        "Sopaipilla",
                        rs.getDouble("precio"),
                        rs.getInt("stock")
                ));
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            System.err.println("Error en listarDesdeSopaipillas: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return lista;
    }

    private List<Producto> listarDesde(String tabla, String categoria, boolean tieneTipo) {
        List<Producto> lista = new ArrayList<>();
        String sql = "SELECT id, tipo, precio, stock FROM " + tabla + " ORDER BY id";

        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String nombre = tieneTipo
                        ? categoria + " " + rs.getString("tipo")
                        : categoria;
                lista.add(new Producto(
                        rs.getInt("id"),
                        nombre,
                        categoria,
                        rs.getDouble("precio"),
                        rs.getInt("stock")
                ));
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            System.err.println("Error en listarDesde(" + tabla + "): " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return lista;
    }

    public List<Producto> listarTodosConStock() {
        Object cached = Conexion.getCached(CACHE_KEY_ALL);
        if (cached instanceof List) {
            return (List<Producto>) cached;
        }

        List<Producto> todos = new ArrayList<>();
        Connection conn = null;

        try {
            conn = Conexion.conectar();

            todos.addAll(listarDesdeConConexion(conn, "empanadas", "Empanada", true));
            todos.addAll(listarDesdeConConexion(conn, "churros", "Churro", true));
            todos.addAll(listarDesdeConConexion(conn, "sopaipillas", "Sopaipilla", false));

            todos.removeIf(p -> p.getStock() <= 0);

            Conexion.cacheResult(CACHE_KEY_ALL, todos);

        } catch (SQLException e) {
            System.err.println("Error en listarTodosConStock: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }

        return todos;
    }

    private List<Producto> listarDesdeConConexion(Connection conn, String tabla, String categoria, boolean tieneTipo) {
        List<Producto> lista = new ArrayList<>();

        String sql = tieneTipo
                ? "SELECT id, tipo, precio, stock FROM " + tabla + " ORDER BY id"
                : "SELECT id, precio, stock FROM " + tabla + " ORDER BY id";

        try {
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String nombre = tieneTipo
                        ? categoria + " " + rs.getString("tipo")
                        : categoria;
                lista.add(new Producto(
                        rs.getInt("id"),
                        nombre,
                        categoria,
                        rs.getDouble("precio"),
                        rs.getInt("stock")
                ));
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            System.err.println("❌ Error en listarDesdeConConexion: " + e.getMessage());
        }

        return lista;
    }

    public void agregar(Producto p) {
        String tabla = tablaDesdeCategoria(p.getCategoria());
        boolean tieneTipo = !p.getCategoria().equals("Sopaipilla");
        String sql = tieneTipo
                ? "INSERT INTO " + tabla + " (tipo, precio, stock) VALUES (?, ?, ?)"
                : "INSERT INTO " + tabla + " (precio, stock) VALUES (?, ?)";

        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement stmt = conn.prepareStatement(sql);

            if (tieneTipo) {
                String tipo = p.getNombre().replaceFirst("^\\S+\\s*", "");
                stmt.setString(1, tipo);
                stmt.setDouble(2, p.getPrecio());
                stmt.setInt(3, p.getStock());
            } else {
                stmt.setDouble(1, p.getPrecio());
                stmt.setInt(2, p.getStock());
            }
            stmt.executeUpdate();
            stmt.close();

            Conexion.invalidateCache(CACHE_KEY_CATEGORIA + "empanadas");
            Conexion.invalidateCache(CACHE_KEY_CATEGORIA + "churros");
            Conexion.invalidateCache(CACHE_KEY_CATEGORIA + "sopaipillas");
            Conexion.invalidateCache(CACHE_KEY_ALL);
            System.out.println("✓ Producto guardado y caché invalidado");

        } catch (SQLException e) {
            System.err.println("Error agregando producto: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public void aumentarStock(int id, String categoria, int cantidad) {
        ejecutarUpdateStock(id, categoria, "stock = stock + ?", cantidad);
    }

    public void descontarStock(int id, String categoria, int cantidad) {
        ejecutarUpdateStock(id, categoria, "stock = stock - ?", cantidad);
    }

    private void ejecutarUpdateStock(int id, String categoria, String expr, int cantidad) {
        String tabla = tablaDesdeCategoria(categoria);
        String sql = "UPDATE " + tabla + " SET " + expr + " WHERE id = ?";

        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, cantidad);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            stmt.close();

            Conexion.invalidateCache(CACHE_KEY_CATEGORIA + "empanadas");
            Conexion.invalidateCache(CACHE_KEY_CATEGORIA + "churros");
            Conexion.invalidateCache(CACHE_KEY_CATEGORIA + "sopaipillas");
            Conexion.invalidateCache(CACHE_KEY_ALL);

        } catch (SQLException e) {
            System.err.println("❌ Error actualizando stock: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public void actualizarStock(int id, String categoria, int nuevoStock) {
        String tabla = tablaDesdeCategoria(categoria);
        String sql = "UPDATE " + tabla + " SET stock = ? WHERE id = ?";

        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, nuevoStock);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            stmt.close();

            Conexion.invalidateCache(CACHE_KEY_CATEGORIA + "empanadas");
            Conexion.invalidateCache(CACHE_KEY_CATEGORIA + "churros");
            Conexion.invalidateCache(CACHE_KEY_CATEGORIA + "sopaipillas");
            Conexion.invalidateCache(CACHE_KEY_ALL);

        } catch (SQLException e) {
            System.err.println("Error actualizando stock: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public void actualizarPrecio(int id, String categoria, double precio) {
        String tabla = tablaDesdeCategoria(categoria);
        String sql = "UPDATE " + tabla + " SET precio = ? WHERE id = ?";

        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setDouble(1, precio);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            stmt.close();

            Conexion.invalidateCache(CACHE_KEY_CATEGORIA + "empanadas");
            Conexion.invalidateCache(CACHE_KEY_CATEGORIA + "churros");
            Conexion.invalidateCache(CACHE_KEY_CATEGORIA + "sopaipillas");
            Conexion.invalidateCache(CACHE_KEY_ALL);

        } catch (SQLException e) {
            System.err.println("Error actualizando precio: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public void eliminar(int id, String categoria) {
        String tabla = tablaDesdeCategoria(categoria);
        String sql = "DELETE FROM " + tabla + " WHERE id = ?";

        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, id);
            stmt.executeUpdate();
            stmt.close();

            Conexion.invalidateCache(CACHE_KEY_CATEGORIA + "empanadas");
            Conexion.invalidateCache(CACHE_KEY_CATEGORIA + "churros");
            Conexion.invalidateCache(CACHE_KEY_CATEGORIA + "sopaipillas");
            Conexion.invalidateCache(CACHE_KEY_ALL);

        } catch (SQLException e) {
            System.err.println("Error eliminando producto: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
    }

    public void sopaipillaAgregarOActualizar(double precio, int cantidad) {

        List<Producto> lista = listarPorCategoriaSinCache("sopaipillas");
        if (lista.isEmpty()) {
            Producto p = new Producto("Sopaipilla", "Sopaipilla", precio, cantidad);
            agregar(p);
        } else {
            Producto s = lista.get(0);
            aumentarStock(s.getId(), "Sopaipilla", cantidad);
            actualizarPrecio(s.getId(), "Sopaipilla", precio);
        }
    }

    public boolean existeTipo(String tipo, String categoria) {
        String tabla = tablaDesdeCategoria(categoria);
        String sql = "SELECT COUNT(*) FROM " + tabla + " WHERE LOWER(tipo) = LOWER(?)";

        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, tipo);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                boolean existe = rs.getInt(1) > 0;
                rs.close();
                stmt.close();
                return existe;
            }
        } catch (SQLException e) {
            System.err.println("Error verificando tipo: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }
        return false;
    }

    public static String tablaDesdeCategoria(String categoria) {
        return switch (categoria.toLowerCase()) {
            case "churro" ->
                "churros";
            case "sopaipilla" ->
                "sopaipillas";
            default ->
                "empanadas";
        };
    }
}
