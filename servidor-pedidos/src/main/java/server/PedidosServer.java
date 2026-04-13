package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dao.VentaDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PedidosServer {

    private final VentaDAO ventaDAO = new VentaDAO();

    private static final int PUERTO = System.getenv("PORT") != null
            ? Integer.parseInt(System.getenv("PORT"))
            : 8888;
    private HttpServer servidor;
    private List<PedidoListener> listeners = new CopyOnWriteArrayList<>();
    private List<Pedido> historicoPedidos = new CopyOnWriteArrayList<>();

    public static class Pedido {

        public int numero;
        public String cliente;
        public String telefono;
        public String detalle;
        public double total;
        public String timestamp;

        public Pedido(int numero, String cliente, String telefono, String detalle, double total) {
            this.numero = numero;
            this.cliente = cliente;
            this.telefono = telefono;
            this.detalle = detalle;
            this.total = total;
            this.timestamp = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    @FunctionalInterface
    public interface PedidoListener {

        void onNuevoPedido(Pedido pedido);
    }

    public PedidosServer() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("0.0.0.0", PUERTO), 0);

        servidor.createContext("/api/pedidos", exchange -> {
            agregarCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = readBody(exchange);

                    String cliente = extraerValor(body, "cliente");
                    String telefono = extraerValor(body, "telefono");
                    String detalle = extraerValor(body, "detalle");
                    double total = extraerDouble(body, "total");

                    String tipoPago = extraerValor(body, "tipoPago");
                    if ("-".equals(tipoPago) || tipoPago.isBlank()) {
                        tipoPago = "EFECTIVO";
                    }

                    System.out.println("TEST body: " + body);

                    int numeroPedido = historicoPedidos.size() + 1;
                    Pedido pedido = new Pedido(numeroPedido, cliente, telefono, detalle, total);
                    historicoPedidos.add(pedido);
                
                    StockDescontador.descontarDesdeDetalle(detalle);

                    registrarVentaDesdeWeb(detalle, total, tipoPago, cliente);

                    for (PedidoListener listener : listeners) {
                        listener.onNuevoPedido(pedido);
                    }

                    String respuesta = "{\"exito\":true,\"mensaje\":\"Pedido recibido\",\"numero\":" + numeroPedido + "}";
                    enviarRespuesta(exchange, 200, respuesta);

                } catch (Exception e) {
                    e.printStackTrace();
                    String error = "{\"exito\":false,\"error\":\"" + e.getMessage() + "\"}";
                    enviarRespuesta(exchange, 400, error);
                }
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

        servidor.createContext("/api/pedidos/historico", exchange -> {
            agregarCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < historicoPedidos.size(); i++) {
                    Pedido p = historicoPedidos.get(i);
                    json.append("{")
                            .append("\"numero\":").append(p.numero).append(",")
                            .append("\"cliente\":\"").append(escaparJson(p.cliente)).append("\",")
                            .append("\"telefono\":\"").append(escaparJson(p.telefono)).append("\",")
                            .append("\"detalle\":\"").append(escaparJson(p.detalle)).append("\",")
                            .append("\"total\":").append(p.total).append(",")
                            .append("\"timestamp\":\"").append(p.timestamp).append("\"")
                            .append("}");
                    if (i < historicoPedidos.size() - 1) {
                        json.append(",");
                    }
                }
                json.append("]");
                enviarRespuesta(exchange, 200, json.toString());
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

        servidor.createContext("/api/pedidos/eliminar", exchange -> {
            agregarCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("DELETE".equals(exchange.getRequestMethod())) {
                try {
                    String body = readBody(exchange);
                    int numero = (int) extraerDouble(body, "numero");

                    boolean eliminado = historicoPedidos.removeIf(p -> p.numero == numero);

                    if (eliminado) {
                        System.out.println("Pedido #" + numero + " eliminado del servidor.");
                        enviarRespuesta(exchange, 200, "{\"exito\":true,\"mensaje\":\"Pedido eliminado\",\"numero\":" + numero + "}");
                    } else {
                        enviarRespuesta(exchange, 404, "{\"exito\":false,\"error\":\"Pedido no encontrado\"}");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    enviarRespuesta(exchange, 400, "{\"exito\":false,\"error\":\"" + e.getMessage() + "\"}");
                }
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

        servidor.createContext("/api/pedidos/limpiar", exchange -> {
            agregarCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("DELETE".equals(exchange.getRequestMethod())) {
                int cantidad = historicoPedidos.size();
                historicoPedidos.clear();
                System.out.println("Se eliminaron " + cantidad + " pedidos del servidor.");
                enviarRespuesta(exchange, 200, "{\"exito\":true,\"mensaje\":\"Todos los pedidos eliminados\",\"cantidad\":" + cantidad + "}");
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

private Map<String, Integer> stockMemoria = new java.util.concurrent.ConcurrentHashMap<>();

servidor.createContext("/api/stock/actualizar", exchange -> {
    agregarCorsHeaders(exchange);
    if ("OPTIONS".equals(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(204, -1); return;
    }
    if ("POST".equals(exchange.getRequestMethod())) {
        String body = readBody(exchange);
        body = body.replace("{","").replace("}","").trim();
        for (String par : body.split(",")) {
            String[] kv = par.split(":");
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"","");
                try { stockMemoria.put(key, Integer.parseInt(kv[1].trim())); }
                catch (Exception ignored) {}
            }
        }
        System.out.println("Stock actualizado: " + stockMemoria);
        enviarRespuesta(exchange, 200, "{\"exito\":true}");
    } else {
        enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
    }
});

servidor.createContext("/api/stock", exchange -> {
    agregarCorsHeaders(exchange);
    if ("OPTIONS".equals(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(204, -1); return;
    }
    if ("GET".equals(exchange.getRequestMethod())) {
        StringBuilder json = new StringBuilder("{");
        stockMemoria.forEach((k,v) -> json.append("\"").append(k).append("\":").append(v).append(","));
        if (json.charAt(json.length()-1) == ',') json.deleteCharAt(json.length()-1);
        json.append("}");
        enviarRespuesta(exchange, 200, json.toString());
    } else {
        enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
    }
});

    
        servidor.createContext("/", exchange -> {
            agregarCorsHeaders(exchange);
            String response = "{\"status\":\"ok\",\"puerto\":" + PUERTO + "}";
            enviarRespuesta(exchange, 200, response);
        });

        servidor.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        System.out.println("🚀 Servidor de Pedidos iniciado en puerto " + PUERTO);
    }

    private void registrarVentaDesdeWeb(String detalle, double totalPedido,
            String tipoPago, String cliente) {
        if (detalle == null || detalle.isBlank()) {
            return;
        }
        
        dao.ProductoDAO productoDAO = new dao.ProductoDAO();
        java.util.List<model.Producto> todosProductos = new java.util.ArrayList<>();
        for (String cat : new String[]{"empanadas", "sopaipillas", "churros", "rapidos"}) {
            todosProductos.addAll(productoDAO.listarPorCategoria(cat));
        }

        String[] items = detalle.split("\\+");
        boolean registroIndividual = false;

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

            String nombreWeb = item.substring(xIdx + 1).trim();
            String nombreNorm = normalizarNombreWeb(nombreWeb);

            double precioUnitario = buscarPrecioEnBD(todosProductos, nombreWeb, nombreNorm);

            if (precioUnitario == 0 && totalPedido > 0) {
                precioUnitario = totalPedido / cantidad;
                System.out.println("⚠️ Precio no encontrado en BD para [" + nombreWeb
                        + "], usando total/cantidad: $" + precioUnitario);
            }

            ventaDAO.registrarVentaRapida(nombreNorm, cantidad, precioUnitario, tipoPago);
            registroIndividual = true;

            System.out.println("Venta web registrada: " + cantidad + "x "
                    + nombreNorm + " | $" + (precioUnitario * cantidad) + " | " + tipoPago);
        }

        if (!registroIndividual && totalPedido > 0) {
            ventaDAO.registrarVentaRapida(
                    "Pedido Web (" + detalle + ")", 1, totalPedido, tipoPago);
            System.out.println("Venta web registrada (fallback): " + detalle + " | $" + totalPedido);
        }
    }

    private String normalizarNombreWeb(String nombre) {
        if (nombre == null) {
            return "";
        }
        String[] prefijos = {"Panadería ", "Panaderia ", "Empanadas ", "Sopaipillas ", "Churros ", "Rápidos ", "Rapidos "};
        for (String pref : prefijos) {
            if (nombre.startsWith(pref)) {
                return nombre.substring(pref.length()).trim();
            }
        }
        return nombre;
    }

    private double buscarPrecioEnBD(java.util.List<model.Producto> productos,
            String nombreWeb, String nombreNorm) {

        for (model.Producto p : productos) {
            if (p.getNombre().equalsIgnoreCase(nombreWeb)
                    || p.getNombre().equalsIgnoreCase(nombreNorm)) {
                System.out.println("✅ Precio en inventario: [" + p.getNombre() + "] = $" + p.getPrecio());
                return p.getPrecio();
            }
        }
        double precio = buscarUltimoPrecioRapido(nombreWeb, nombreNorm);
        if (precio > 0) {
            return precio;
        }

        System.out.println("Precio no encontrado para: [" + nombreWeb + "] / [" + nombreNorm + "]");
        return 0;
    }

    private double buscarUltimoPrecioRapido(String nombreWeb, String nombreNorm) {
        String sql = "SELECT precio_unitario FROM ventas_rapidas "
                + "WHERE LOWER(nombre) = LOWER(?) OR LOWER(nombre) = LOWER(?) "
                + "ORDER BY id DESC LIMIT 1";
        try (java.sql.Connection conn = dao.Conexion.conectar(); java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombreWeb);
            ps.setString(2, nombreNorm);
            java.sql.ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                double precio = rs.getDouble(1);
                System.out.println("Precio en ventas_rapidas: [" + nombreNorm + "] = $" + precio);
                return precio;
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Error buscando precio en ventas_rapidas: " + e.getMessage());
        }
        return 0;
    }

    public void iniciar() {
        servidor.start();
    }

    public void detener() {
        servidor.stop(0);
        System.out.println("Servidor detenido");
    }

    public void registrarListener(PedidoListener listener) {
        listeners.add(listener);
    }

    public void removerListener(PedidoListener listener) {
        listeners.remove(listener);
    }

    private String readBody(HttpExchange exchange) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    private String extraerValor(String json, String clave) {
        String patron = "\"" + clave + "\":\"";
        int inicio = json.indexOf(patron);
        if (inicio == -1) {
            return "-";
        }
        inicio += patron.length();
        int fin = json.indexOf("\"", inicio);
        if (fin == -1) {
            return "-";
        }
        return json.substring(inicio, fin);
    }

    private double extraerDouble(String json, String clave) {
        String patron = "\"" + clave + "\":";
        int inicio = json.indexOf(patron);
        if (inicio == -1) {
            return 0.0;
        }
        inicio += patron.length();
        int fin = json.indexOf(",", inicio);
        if (fin == -1) {
            fin = json.indexOf("}", inicio);
        }
        if (fin == -1) {
            return 0.0;
        }
        try {
            return Double.parseDouble(json.substring(inicio, fin).trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String escaparJson(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private void agregarCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void enviarRespuesta(HttpExchange exchange, int codigo, String respuesta) throws IOException {
        byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void main(String[] args) throws IOException {
        PedidosServer server = new PedidosServer();
        server.iniciar();
    }
}
