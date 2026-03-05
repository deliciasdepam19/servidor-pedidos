package server;

import dao.ProductoDAO;
import java.util.List;
import model.Producto;

public class StockDescontador {

    private static final ProductoDAO dao = new ProductoDAO();

    public static void descontarDesdeDetalle(String detalle) {
        if (detalle == null || detalle.isBlank()) {
            return;
        }

        String[] items = detalle.split("\\+");
        for (String item : items) {
            item = item.trim();
            // Espera formato: "Nx Nombre Producto"
            if (!item.matches("\\d+x .+")) {
                continue;
            }

            int xIdx = item.indexOf('x');
            int cantidad;
            try {
                cantidad = Integer.parseInt(item.substring(0, xIdx).trim());
            } catch (NumberFormatException e) {
                continue;
            }

            // Nombre completo del producto, ej: "Empanada Pino"
            String nombreProducto = item.substring(xIdx + 1).trim();

            descontarProducto(nombreProducto, cantidad);
        }
    }

    private static void descontarProducto(String nombreProducto, int cantidad) {
        // Buscar en empanadas, churros y sopaipillas
        for (String categoria : new String[]{"empanadas", "churros", "sopaipillas"}) {
            List<Producto> lista = dao.listarPorCategoria(categoria);
            for (Producto p : lista) {
                if (p.getNombre().equalsIgnoreCase(nombreProducto)) {
                    if (p.getStock() >= cantidad) {
                        dao.descontarStock(p.getId(), p.getCategoria(), cantidad);
                        System.out.println("📦 Stock descontado: " + nombreProducto
                                + " -" + cantidad + " (stock era " + p.getStock() + ")");
                    } else {
                        // Descontar lo que queda (llega a 0, no negativo)
                        dao.descontarStock(p.getId(), p.getCategoria(), p.getStock());
                        System.out.println("⚠️ Stock insuficiente para: " + nombreProducto
                                + " — descontado a 0");
                    }
                    return;
                }
            }
        }
        System.out.println("⚠️ Producto no encontrado en inventario: " + nombreProducto);
    }
}
