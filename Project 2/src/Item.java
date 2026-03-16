/**
 * Utility class representing an item in the data.csv
 */
public class Item {
    private final short id;
    private final String name;
    private final short price;

    public Item(short id, String name, short price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public short getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public short getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return id + " - " + name + " @ $" + price;
    }
}
