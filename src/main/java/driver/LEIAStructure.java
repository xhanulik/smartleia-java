package driver;

abstract class LEIAStructure {
    public abstract byte[] pack();
    public abstract LEIAStructure unpack(byte[] buffer);
}
