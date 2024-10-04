package driver;

abstract class LEIAStructure {
    public abstract byte[] pack();
    public abstract void unpack(byte[] buffer);
}
