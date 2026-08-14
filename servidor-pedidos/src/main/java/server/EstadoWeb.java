package server;

public class EstadoWeb {

    private static volatile Boolean overrideManual = null;

    public static boolean abierta() {
        if (overrideManual != null) {
            return overrideManual;
        }
        return false;
    }

    public static void setAbierta(Boolean valor) {
        overrideManual = valor;
    }
}
