package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PedidosServer {

    private static final int PUERTO = System.getenv("PORT") != null
            ? Integer.parseInt(System.getenv("PORT"))
            : 8888;

    private HttpServer servidor;
    private List<PedidoListener> listeners = new CopyOnWriteArrayList<>();
    private List<Pedido> historicoPedidos = new CopyOnWriteArrayList<>();
    private Map<String, Integer> stockMemoria = new ConcurrentHashMap<>();

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
            this.timestamp = LocalDateTime.now(ZoneId.of("America/Santiago")).format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    @FunctionalInterface
    public interface PedidoListener {
        void onNuevoPedido(Pedido pedido);
    }

    public PedidosServer() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("0.0.0.0", PUERTO), 0);

        // ── POST /api/pedidos ─────────────────────────────────────────────────
        servidor.createContext("/api/pedidos", exchange -> {
            agregarCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body     = readBody(exchange);
                    String cliente  = extraerValor(body, "cliente");
                    String telefono = extraerValor(body, "telefono");
                    String detalle  = extraerValor(body, "detalle");
                    double total    = extraerDouble(body, "total");

                    // ── Hora real de Chile ────────────────────────────────────
                    LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Santiago"));
                    int hora = ahora.getHour();

                    // ── Detección de panadería en todo el body ────────────────
                    String bodyLower = body.toLowerCase();
                    boolean esPanaderia =
                            bodyLower.contains("panaderia") ||
                            bodyLower.contains("panadería");

                    System.out.println(">>> HORA SANTIAGO: " + hora
                            + " | ES_PANADERIA: " + esPanaderia
                            + " | DETALLE: " + detalle);

                    // ── Validación de horario ─────────────────────────────────
                    boolean fueraHorario;
                    String  mensajeHorario;

                    if (esPanaderia) {
                        // Panadería: solo de 12:00 a 18:00
                        fueraHorario   = hora < 12 || hora >= 18;
                        mensajeHorario = "Los pedidos de Panadería se reciben entre las 12:00 y las 18:00 hrs.";
                    } else {
                        // Resto: solo de 18:00 a 22:00
                        fueraHorario   = hora < 18 || hora >= 22;
                        mensajeHorario = "Los pedidos se reciben entre las 18:00 y las 22:00 hrs.";
                    }

                    if (fueraHorario) {
                        System.err.println(">>> PEDIDO RECHAZADO horario ("
                                + hora + ":xx) esPanaderia=" + esPanaderia);
                        enviarRespuesta(exchange, 403,
                                "{\"exito\":false,\"error\":\"" + mensajeHorario + "\"}");
                        return;
                    }
                    // ─────────────────────────────────────────────────────────

                    System.out.println("TEST body: " + body);

                    int numeroPedido = historicoPedidos.size() + 1;
                    Pedido pedido = new Pedido(numeroPedido, cliente, telefono, detalle, total);
                    historicoPedidos.add(pedido);

                    descontarStock(detalle);

                    for (PedidoListener listener : listeners) {
                        listener.onNuevoPedido(pedido);
                    }

                    enviarRespuesta(exchange, 200,
                            "{\"exito\":true,\"mensaje\":\"Pedido recibido\",\"numero\":" + numeroPedido + "}");

                } catch (Exception e) {
                    e.printStackTrace();
                    enviarRespuesta(exchange, 400,
                            "{\"exito\":false,\"error\":\"" + e.getMessage() + "\"}");
                }
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

        // ── GET /api/pedidos/historico ────────────────────────────────────────
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
                    if (i < historicoPedidos.size() - 1) json.append(",");
                }
                json.append("]");
                enviarRespuesta(exchange, 200, json.toString());
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

        // ── DELETE /api/pedidos/eliminar ──────────────────────────────────────
        servidor.createContext("/api/pedidos/eliminar", exchange -> {
            agregarCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("DELETE".equals(exchange.getRequestMethod())) {
                try {
                    String body = readBody(exchange);
                    int numero  = (int) extraerDouble(body, "numero");
                    boolean eliminado = historicoPedidos.removeIf(p -> p.numero == numero);
                    if (eliminado) {
                        System.out.println("Pedido #" + numero + " eliminado.");
                        enviarRespuesta(exchange, 200,
                                "{\"exito\":true,\"numero\":" + numero + "}");
                    } else {
                        enviarRespuesta(exchange, 404,
                                "{\"exito\":false,\"error\":\"Pedido no encontrado\"}");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    enviarRespuesta(exchange, 400,
                            "{\"exito\":false,\"error\":\"" + e.getMessage() + "\"}");
                }
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

        // ── DELETE /api/pedidos/limpiar ───────────────────────────────────────
        servidor.createContext("/api/pedidos/limpiar", exchange -> {
            agregarCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("DELETE".equals(exchange.getRequestMethod())) {
                int cantidad = historicoPedidos.size();
                historicoPedidos.clear();
                System.out.println("Se eliminaron " + cantidad + " pedidos.");
                enviarRespuesta(exchange, 200,
                        "{\"exito\":true,\"cantidad\":" + cantidad + "}");
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

        // ── POST /api/stock/actualizar ────────────────────────────────────────
        servidor.createContext("/api/stock/actualizar", exchange -> {
            agregarCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = readBody(exchange);
                    body = body.replace("{", "").replace("}", "").trim();
                    for (String par : body.split(",")) {
                        String[] kv = par.split(":");
                        if (kv.length == 2) {
                            String key = kv[0].trim().replace("\"", "");
                            try {
                                stockMemoria.put(key, Integer.parseInt(kv[1].trim()));
                            } catch (Exception ignored) {}
                        }
                    }
                    System.out.println("Stock actualizado: " + stockMemoria);
                    enviarRespuesta(exchange, 200, "{\"exito\":true}");
                } catch (Exception e) {
                    e.printStackTrace();
                    enviarRespuesta(exchange, 400,
                            "{\"exito\":false,\"error\":\"" + e.getMessage() + "\"}");
                }
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

        // ── GET /api/stock ────────────────────────────────────────────────────
        servidor.createContext("/api/stock", exchange -> {
            agregarCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                StringBuilder json = new StringBuilder("{");
                stockMemoria.forEach((k, v) ->
                        json.append("\"").append(k).append("\":").append(v).append(","));
                if (json.charAt(json.length() - 1) == ',')
                    json.deleteCharAt(json.length() - 1);
                json.append("}");
                enviarRespuesta(exchange, 200, json.toString());
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

        // ── GET / — Health check ──────────────────────────────────────────────
        servidor.createContext("/", exchange -> {
            agregarCorsHeaders(exchange);
            enviarRespuesta(exchange, 200,
                    "{\"status\":\"ok\",\"puerto\":" + PUERTO + "}");
        });

        servidor.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        System.out.println("Servidor iniciado en puerto " + PUERTO);
    }

    // ── Descontar stock en memoria cuando llega un pedido ────────────────────
    private void descontarStock(String detalle) {
        if (detalle == null || detalle.isBlank()) return;
        for (String item : detalle.split("\\+")) {
            item = item.trim();
            if (!item.matches("\\d+x .+")) continue;
            int xIdx = item.indexOf('x');
            int cantidad;
            try { cantidad = Integer.parseInt(item.substring(0, xIdx).trim()); }
            catch (NumberFormatException e) { continue; }
            String nombre = item.substring(xIdx + 1).trim()
                    .replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
            stockMemoria.computeIfPresent(nombre, (k, v) -> Math.max(0, v - cantidad));
            System.out.println("Stock descontado: " + nombre + " -" + cantidad);
        }
    }

    public void iniciar() { servidor.start(); }

    public void detener() {
        servidor.stop(0);
        System.out.println("Servidor detenido");
    }

    public void registrarListener(PedidoListener listener) { listeners.add(listener); }
    public void removerListener(PedidoListener listener)   { listeners.remove(listener); }

    private String readBody(HttpExchange exchange) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private String extraerValor(String json, String clave) {
        String patron = "\"" + clave + "\":\"";
        int inicio = json.indexOf(patron);
        if (inicio == -1) return "-";
        inicio += patron.length();
        int fin = json.indexOf("\"", inicio);
        return fin == -1 ? "-" : json.substring(inicio, fin);
    }

    private double extraerDouble(String json, String clave) {
        String patron = "\"" + clave + "\":";
        int inicio = json.indexOf(patron);
        if (inicio == -1) return 0.0;
        inicio += patron.length();
        int fin = json.indexOf(",", inicio);
        if (fin == -1) fin = json.indexOf("}", inicio);
        if (fin == -1) return 0.0;
        try { return Double.parseDouble(json.substring(inicio, fin).trim()); }
        catch (Exception e) { return 0.0; }
    }

    private String escaparJson(String texto) {
        if (texto == null) return "";
        return texto.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
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
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    public static void main(String[] args) throws IOException {
        PedidosServer server = new PedidosServer();
        server.iniciar();
    }
}
