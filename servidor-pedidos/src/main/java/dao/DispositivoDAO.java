package dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DispositivoDAO {

    public boolean registrar(String telefono, String expoPushToken, String plataforma) {
        String sql = "INSERT INTO dispositivos (telefono, expo_push_token, plataforma) "
                + "VALUES (?, ?, ?) "
                + "ON CONFLICT (telefono, expo_push_token) DO UPDATE SET "
                + "activo = true, plataforma = EXCLUDED.plataforma";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, telefono);
                ps.setString(2, expoPushToken);
                ps.setString(3, plataforma != null ? plataforma : "android");
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[DispositivoDAO] registrar: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return false;
    }

    public boolean eliminar(String telefono, String expoPushToken) {
        String sql = "UPDATE dispositivos SET activo = false "
                + "WHERE telefono = ? AND expo_push_token = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, telefono);
                ps.setString(2, expoPushToken);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[DispositivoDAO] eliminar: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return false;
    }

    public List<String> obtenerTokensPorTelefono(String telefono) {
        List<String> tokens = new ArrayList<>();
        String sql = "SELECT expo_push_token FROM dispositivos "
                + "WHERE telefono = ? AND activo = true";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, telefono);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tokens.add(rs.getString("expo_push_token"));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[DispositivoDAO] obtenerTokens: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return tokens;
    }

    public void limpiarTokensInvalidos(List<String> tokensInvalidos) {
        if (tokensInvalidos == null || tokensInvalidos.isEmpty()) return;
        String sql = "UPDATE dispositivos SET activo = false "
                + "WHERE expo_push_token = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (String token : tokensInvalidos) {
                    ps.setString(1, token);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("[DispositivoDAO] limpiarTokens: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
    }
}
