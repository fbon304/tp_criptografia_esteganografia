package stegobmp.steg;

import java.util.Arrays;

public class Lsb1 implements Steganography{

    private static final int BITS_PER_COMPONENT = 1;
    private static final int COMPONENTS_PER_BYTE = Byte.SIZE / BITS_PER_COMPONENT;
    private static final int MESSAGE_MASK = 0x01;
    private static final int CARRIER_MASK = 0xFE;

    @Override
    public long capacity(byte[] carrier) {
        return carrier.length / COMPONENTS_PER_BYTE;
    }

    @Override
    public byte[] embed(byte[] carrier, byte[] payload) {
        byte[] result = Arrays.copyOf(carrier, carrier.length);
        int index = 0;

        for(byte value : payload) {
            for (int j = Byte.SIZE - 1; j >= 0; j--) {
                int bit = (value >> j) & MESSAGE_MASK;
                result[index] = (byte) ((result[index] & CARRIER_MASK) | bit);
                index++;
            }
        }
        return result;
    }

    @Override
    public byte[] extract(byte[] carrier, int byteCount) {
        byte[] result = new byte[byteCount];

        for (int i = 0; i < byteCount; i++ ) {
            int recoveredByte = 0;
            for (int j = 0; j < COMPONENTS_PER_BYTE; j++){
                int bit = (carrier[COMPONENTS_PER_BYTE * i + j]) & MESSAGE_MASK;
                recoveredByte = ((recoveredByte << BITS_PER_COMPONENT) | bit);
            }
            result[i] = (byte) recoveredByte;
        }
        return result;
    }
}
