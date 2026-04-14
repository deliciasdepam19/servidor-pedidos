package model;

public class Producto {

    private int id;
    private String nombre;
    private String categoria;
    private double precio;
    private boolean manejaStock;
    private int stock;
    private boolean activo;

    public Producto(int id, String nombre, String categoria, double precio, int stock) {
        this.id = id;
        this.nombre = nombre;
        this.categoria = categoria;
        this.precio = precio;
        this.manejaStock = true;
        this.stock = stock;
        this.activo = true;
    }

    public Producto(int id, String nombre, String categoria, double precio) {
        this.id = id;
        this.nombre = nombre;
        this.categoria = categoria;
        this.precio = precio;
        this.manejaStock = false;
        this.stock = 0;
        this.activo = true;
    }

    // Constructor sin id (para inserción en BD)
    public Producto(String nombre, String categoria, double precio, int stock) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.precio = precio;
        this.manejaStock = true;
        this.stock = stock;
        this.activo = true;
    }

    public Producto(String nombre, String categoria, double precio) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.precio = precio;
        this.manejaStock = false;
        this.stock = 0;
        this.activo = true;
    }

    public boolean descontarStock(int cantidad) {
        if (!manejaStock) {
            return true;
        }
        if (this.stock < cantidad) {
            return false;
        }
        this.stock -= cantidad;
        return true;
    }

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public double getPrecio() {
        return precio;
    }

    public boolean isManejaStock() {
        return manejaStock;
    }

    public int getStock() {
        return stock;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public void setPrecio(double precio) {
        this.precio = precio;
    }

    public void setManejaStock(boolean b) {
        this.manejaStock = b;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    @Override
    public String toString() {
        return "Producto{id=" + id + ", nombre='" + nombre + "', categoria='" + categoria
                + "', precio=" + precio + ", stock=" + (manejaStock ? stock : "N/A") + "}";
    }
}
