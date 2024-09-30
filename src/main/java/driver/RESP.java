package driver;

public class RESP extends LEIAStructure {
    @Override
    public byte[] pack() {
        return new byte[0];
    }

    @Override
    public LEIAStructure unpack(byte[] buffer) {
        return null;
    }
}
