package model;

public class Ingrediente {

    private int id;
    private String nombre;
    private double cantidad;
    private int sacosDisponibles;
    private String unidad;
    private double precioCompra;
    private String nombreProveedor;
    private String fechaIngreso;
    private String fechaAgotado;
    private double kgSaco;

    public Ingrediente(int id, String nombre, double cantidad, int sacosDisponibles,
            String unidad, double precioCompra, String nombreProveedor,
            String fechaIngreso, String fechaAgotado, double kgSaco) {
        this.id = id;
        this.nombre = nombre;
        this.cantidad = cantidad;
        this.sacosDisponibles = sacosDisponibles;
        this.unidad = unidad;
        this.precioCompra = precioCompra;
        this.nombreProveedor = nombreProveedor;
        this.fechaIngreso = fechaIngreso;
        this.fechaAgotado = fechaAgotado;
        this.kgSaco = kgSaco;
    }

    public Ingrediente(String nombre, double cantidad, int sacosDisponibles,
            String unidad, double precioCompra, String nombreProveedor, double kgSaco) {
        this(-1, nombre, cantidad, sacosDisponibles, unidad, precioCompra,
                nombreProveedor, java.time.LocalDate.now().toString(), null, kgSaco);
    }

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public double getCantidad() {
        return cantidad;
    }

    public int getSacosDisponibles() {
        return sacosDisponibles;
    }

    public String getUnidad() {
        return unidad;
    }

    public double getPrecioCompra() {
        return precioCompra;
    }

    public String getNombreProveedor() {
        return nombreProveedor;
    }

    public String getFechaIngreso() {
        return fechaIngreso;
    }

    public String getFechaAgotado() {
        return fechaAgotado != null ? fechaAgotado : "—";
    }

    public double getKgSaco() {
        return kgSaco;
    }

}
