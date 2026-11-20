package sample.model;



public class Medicine {

    private String name;
    private double price;
    private int quantity;

    public Medicine(String name, double price, int quantity) {
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean sameValuesAs(Medicine other) {
        return other != null
                && name.equals(other.name)
                && Double.compare(price, other.price) == 0
                && quantity == other.quantity;
    }
}
