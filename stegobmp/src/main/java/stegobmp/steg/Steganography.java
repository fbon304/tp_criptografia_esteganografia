package stegobmp.steg;

public interface Steganography {
    long capacity(byte[] carrier);

    byte[] embed(byte[] carrier, byte[] payload);

    byte[] extract(byte[] carrier, int byteCount);
}
