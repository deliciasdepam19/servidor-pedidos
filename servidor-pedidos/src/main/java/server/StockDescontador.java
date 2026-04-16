package server;

import dao.ProductoDAO;
import java.util.List;
import model.Producto;

public class StockDescontador {

    private static final ProductoDAO dao = new ProductoDAO();

    public static void descontarDesdeDetalle(String detalle) {
        System.out.println("StockDescontador recibió detalle: [" + detalle + "]");
        if (detalle == null || detalle.isBlank()) {
            return;
        }

        String[] items = detalle.split("\\+");
        for (String item : items) {
            item = item.trim();
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

            String nombreProducto = item.substring(xIdx + 1).trim();
            descontarProducto(nombreProducto, cantidad);
        }
    }

    private static void descontarProducto(String nombreProducto, int cantidad) {
        for (String categoria : new String[]{"empanadas", "churros", "sopaipillas"}) {
            List<Producto> lista = dao.listarPorCategoria(categoria);
            for (Producto p : lista) {
                if (p.getNombre().equalsIgnoreCase(nombreProducto)) {
                    dao.descontarStock(p.getId(), p.getCategoria(), cantidad);
                    return;
                }
            }
        }
    }

    public static String obtenerStockJSON() {

        StringBuilder json = new StringBuilder("{");

        try {

            List<Producto> empanadas = dao.listarPorCategoria("empanadas");
            List<Producto> churros = dao.listarPorCategoria("churros");
            List<Producto> sopaipillas = dao.listarPorCategoria("sopaipillas");

            json.append("\"empanadas\":[");
            for (int i = 0; i < empanadas.size(); i++) {
                Producto p = empanadas.get(i);
                json.append("{")
                        .append("\"nombre\":\"").append(p.getNombre()).append("\",")
                        .append("\"stock\":").append(p.getStock())
                        .append("}");
                if (i < empanadas.size() - 1) json.append(",");
            }
            json.append("],");

            json.append("\"churros\":[");
            for (int i = 0; i < churros.size(); i++) {
                Producto p = churros.get(i);
                json.append("{")
                        .append("\"nombre\":\"").append(p.getNombre()).append("\",")
                        .append("\"stock\":").append(p.getStock())
                        .append("}");
                if (i < churros.size() - 1) json.append(",");
            }
            json.append("],");

            json.append("\"sopaipillas\":[");
            for (int i = 0; i < sopaipillas.size(); i++) {
                Producto p = sopaipillas.get(i);
                json.append("{")
                        .append("\"nombre\":\"").append(p.getNombre()).append("\",")
                        .append("\"stock\":").append(p.getStock())
                        .append("}");
                if (i < sopaipillas.size() - 1) json.append(",");
            }
            json.append("]");

        } catch (Exception e) {
            e.printStackTrace();
        }

        json.append("}");
        return json.toString();
    }
}
