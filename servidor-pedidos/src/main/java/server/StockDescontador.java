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
            System.out.println("Item parseado: [" + item + "]");
            if (!item.matches("\\d+x .+")) {
                System.out.println("No matchea formato: [" + item + "]");
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
            System.out.println("Buscando en BD: [" + nombreProducto + "]");
            descontarProducto(nombreProducto, cantidad);
        }
    }

    private static void descontarProducto(String nombreProducto, int cantidad) {
        for (String categoria : new String[]{"empanadas", "churros", "sopaipillas"}) {
            List<Producto> lista = dao.listarPorCategoria(categoria);
            for (Producto p : lista) {
                String nombreBD = p.getNombre();
                String nombreBDSinPrefijo = nombreBD.replaceFirst("^\\S+\\s+", "");

                if (nombreBD.equalsIgnoreCase(nombreProducto)
                        || nombreBDSinPrefijo.equalsIgnoreCase(nombreProducto)
                        || nombreBD.equalsIgnoreCase(normalizarNombre(nombreProducto))
                        || nombreBDSinPrefijo.equalsIgnoreCase(normalizarNombre(nombreProducto))) {
                    if (p.getStock() >= cantidad) {
                        dao.descontarStock(p.getId(), p.getCategoria(), cantidad);
                    } else {
                        dao.descontarStock(p.getId(), p.getCategoria(), p.getStock());
                    }
                    return;
                }
            }
        }
        System.out.println(" Producto no encontrado: [" + nombreProducto + "]");
    }

    private static String quitarPrefijo(String nombre) {
        if (nombre == null) {
            return "";
        }
        String[] prefijos = {"Empanada ", "Sopaipilla ", "Churro "};
        for (String pref : prefijos) {
            if (nombre.toLowerCase().startsWith(pref.toLowerCase())) {
                return nombre.substring(pref.length()).trim();
            }
        }
        return nombre;
    }

    private static String normalizarNombre(String nombre) {
        if (nombre == null) {
            return "";
        }
        String[] palabras = nombre.split(" ", 2);
        if (palabras.length == 0) {
            return nombre;
        }
        String primera = palabras[0];
        if (primera.endsWith("as")) {
            primera = primera.substring(0, primera.length() - 1);
        } else if (primera.endsWith("os")) {
            primera = primera.substring(0, primera.length() - 1);
        } else if (primera.endsWith("s") && primera.length() > 3) {
            primera = primera.substring(0, primera.length() - 1);
        }
        return palabras.length > 1 ? primera + " " + palabras[1] : primera;
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
                if (i < empanadas.size() - 1) {
                    json.append(",");
                }
            }
            json.append("],");

            json.append("\"churros\":[");
            for (int i = 0; i < churros.size(); i++) {
                Producto p = churros.get(i);
                json.append("{")
                        .append("\"nombre\":\"").append(p.getNombre()).append("\",")
                        .append("\"stock\":").append(p.getStock())
                        .append("}");
                if (i < churros.size() - 1) {
                    json.append(",");
                }
            }
            json.append("],");

            json.append("\"sopaipillas\":[");
            for (int i = 0; i < sopaipillas.size(); i++) {
                Producto p = sopaipillas.get(i);
                json.append("{")
                        .append("\"nombre\":\"").append(p.getNombre()).append("\",")
                        .append("\"stock\":").append(p.getStock())
                        .append("}");
                if (i < sopaipillas.size() - 1) {
                    json.append(",");
                }
            }
            json.append("]");

        } catch (Exception e) {
            e.printStackTrace();
        }

        json.append("}");
        return json.toString();
    }

}
