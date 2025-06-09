package com.example.reservafutbol.Modelo;

// Un enum para definir los estados posibles de una reserva.
public enum EstadoReserva {
    PENDIENTE("pendiente"),
    CONFIRMADA("confirmada"), // Podría ser un estado intermedio si se confirma antes de pagar
    PENDIENTE_PAGO_EFECTIVO("pendiente_pago_efectivo"),
    PENDIENTE_PAGO_MP("pendiente_pago_mp"),
    PAGADA("pagada"),
    RECHAZADA_PAGO_MP("rechazada_pago_mp"),
    CANCELADA("cancelada");

    private final String valor;

    EstadoReserva(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }

    // Método estático para convertir un String a un enum EstadoReserva
    public static EstadoReserva fromString(String text) {
        for (EstadoReserva b : EstadoReserva.values()) {
            if (b.valor.equalsIgnoreCase(text)) {
                return b;
            }
        }
        // Si no se encuentra una coincidencia, puedes decidir si lanzar una excepción
        // o devolver un valor por defecto como EstadoReserva.PENDIENTE
        throw new IllegalArgumentException("No se encontró un estado de reserva con el valor: " + text);
    }
}