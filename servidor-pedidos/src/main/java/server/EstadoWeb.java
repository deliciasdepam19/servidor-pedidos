package server;

public class EstadoWeb {

    // Override manual del admin: null = automático (abierta por defecto), true/false = override
    private static volatile Boolean overrideManual = null;

    /**
     * Determina si la web está habilitada para recibir pedidos.
     * Este flag es el interruptor GENERAL de la tienda (no de una categoría en particular).
     * El horario específico de cada categoría (panadería 12-15, pastelería 12-22,
     * general 18-22, etc.) se valida aparte en PedidosServer.calcularFranjaActual().
     *
     * Si hay override manual del admin, se respeta ese valor.
     * Si no, por defecto la tienda queda ABIERTA (el filtro real de horario
     * lo hace calcularFranjaActual por categoría).
     */
    public static boolean abierta() {
        if (overrideManual != null) {
            return overrideManual;
        }
        return true;
    }

    /**
     * Setea override manual del admin.
     * Pasar null vuelve al modo automático (abierta por defecto).
     */
    public static void setAbierta(Boolean valor) {
        overrideManual = valor;
    }
}
