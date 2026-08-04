package server;

import java.time.LocalTime;
import java.time.ZoneId;

public class EstadoWeb {

    private static final ZoneId ZONA_CHILE = ZoneId.of("America/Santiago");
    private static final int HORA_APERTURA = 12;
    private static final int HORA_CIERRE = 15;

    // Override manual del admin: null = automático, true/false = override
    private static volatile Boolean overrideManual = null;

    /**
     * Determina si la panadería está abierta.
     * Si hay override manual, usa ese valor.
     * Si no, calcula automáticamente según la hora de Argentina (12:00-15:00).
     */
    public static boolean abierta() {
        if (overrideManual != null) {
            return overrideManual;
        }
        int hora = LocalTime.now(ZONA_CHILE).getHour();
        return hora >= HORA_APERTURA && hora < HORA_CIERRE;
    }

    /**
     * Setea override manual del admin.
     * Pasar null vuelve al modo automático.
     */
    public static void setAbierta(Boolean valor) {
        overrideManual = valor;
    }
}
