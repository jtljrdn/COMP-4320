public class QuantityCodePair {
    private final short quantity;
    private final short code;

    public QuantityCodePair(short quantity, short code) {
        this.quantity = quantity;
        this.code = code;
    }

    public short getQuantity() {
        return quantity;
    }

    public short getCode() {
        return code;
    }

    @Override
    public String toString() {
        return "QuantityCodePair{quantity=" + quantity + ", code=" + code + "}";
    }
}
