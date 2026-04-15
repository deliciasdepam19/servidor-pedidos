package model;

public class Pedido {

    public int id;
    public int numero;
    public String cliente;
    public String telefono;
    public String detalle;
    public double total;
    public String estado;
    public String hora;
    public String horaExacta;
    public String origen;

    public Pedido(int id, int numero, String cliente, String telefono,
            String detalle, double total, String estado,
            String hora, String origen) {
        this.id = id;
        this.numero = numero;
        this.cliente = cliente;
        this.telefono = telefono;
        this.detalle = detalle;
        this.total = total;
        this.estado = estado;
        this.hora = hora;
        this.horaExacta = hora;
        this.origen = origen;
    }

    public Pedido(int id, int numero, String cliente, String telefono,
            String detalle, double total, String estado, String hora, String horaExacta, String origen) {
        this(id, numero, cliente, telefono, detalle, total, estado, hora, origen);
        this.horaExacta = horaExacta;
    }
}
