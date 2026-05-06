package dao;

public class RecetaItem {

    private final int idIngrediente;
    private final double cantidadG;

    public RecetaItem(int idIngrediente, double cantidadG) {
        this.idIngrediente = idIngrediente;
        this.cantidadG = cantidadG;
    }

    public int getIdIngrediente() {
        return idIngrediente;
    }

    public double getCantidadG() {
        return cantidadG;
    }
}
