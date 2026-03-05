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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PedidosServer {

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

        // Endpoint: POST /api/pedidos - Recibir nuevo pedido
        servidor.createContext("/api/pedidos", exchange -> {
            agregarCorsHeaders(exchange);

            // Manejo de preflight OPTIONS
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

                    int numeroPedido = historicoPedidos.size() + 1;
                    Pedido pedido = new Pedido(numeroPedido, cliente, telefono, detalle, total);
                    historicoPedidos.add(pedido);
                    StockDescontador.descontarDesdeDetalle(detalle);

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

        // Endpoint: GET /api/pedidos/historico - Obtener histórico
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

        // Endpoint: DELETE /api/pedidos/eliminar - Eliminar pedido por número
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
                        System.out.println("🗑️  Pedido #" + numero + " eliminado del servidor.");
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

        // Endpoint: DELETE /api/pedidos/limpiar - Eliminar TODOS los pedidos
        servidor.createContext("/api/pedidos/limpiar", exchange -> {
            agregarCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("DELETE".equals(exchange.getRequestMethod())) {
                int cantidad = historicoPedidos.size();
                historicoPedidos.clear();
                System.out.println("🧹 Se eliminaron " + cantidad + " pedidos del servidor.");
                enviarRespuesta(exchange, 200, "{\"exito\":true,\"mensaje\":\"Todos los pedidos eliminados\",\"cantidad\":" + cantidad + "}");
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

        // Endpoint: GET / - Health check
        servidor.createContext("/", exchange -> {
            agregarCorsHeaders(exchange);
            String response = "{\"status\":\"ok\",\"puerto\":" + PUERTO + "}";
            enviarRespuesta(exchange, 200, response);
        });

        servidor.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        System.out.println("🚀 Servidor de Pedidos iniciado en puerto " + PUERTO);
    }

    public void iniciar() {
        servidor.start();
    }

    public void detener() {
        servidor.stop(0);
        System.out.println("⛔ Servidor detenido");
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
