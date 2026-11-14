package stegobmp.steg;

import java.util.Arrays;

/**
 * LSB Improved (Majeed y Sulaiman): LSB1 con inversión de bits por patrón.
 * <p>
 * Los primeros 4 bytes del portador guardan con LSB1 común (usando los tres canales) si se invirtieron
 * los patrones 00, 01, 10 y 11, en ese orden. El patrón de un byte son su tercer y segundo bit menos
 * significativos. El mensaje se oculta a continuación, un bit por byte, usando solo los canales azul
 * y verde: el rojo (tercer byte de cada píxel, en el orden físico del archivo) se saltea.
 */
public class Lsbi implements Steganography {

    private static final int PATTERN_COUNT = 4;
    private static final int PATTERN_MASK = 0x03;
    private static final int MESSAGE_MASK = 0x01;
    private static final int CARRIER_MASK = 0xFE;
    private static final int BYTES_PER_PIXEL = 3;
    private static final int RED_OFFSET = 2;

    @Override
    public long capacity(byte[] carrier) {
        if (carrier.length < PATTERN_COUNT) {
            return 0;
        }
        // Bytes que no son rojos, menos los 3 no rojos (índices 0, 1 y 3) reservados para los patrones.
        long usable = carrier.length - carrier.length / BYTES_PER_PIXEL - (PATTERN_COUNT - 1);
        return usable / Byte.SIZE;
    }

    @Override
    public byte[] embed(byte[] carrier, byte[] payload) {
        if (payload.length > capacity(carrier)) {
            throw new IllegalArgumentException("El payload no cabe en el portador con LSBI");
        }
        byte[] result = Arrays.copyOf(carrier, carrier.length);
        long[] changed = new long[PATTERN_COUNT];
        long[] unchanged = new long[PATTERN_COUNT];

        // Paso 1: LSB1 común sobre azul y verde, contando por patrón cuántos bytes cambian.
        int index = PATTERN_COUNT;
        for (byte value : payload) {
            for (int j = Byte.SIZE - 1; j >= 0; j--) {
                index = skipRed(index);
                int bit = (value >> j) & MESSAGE_MASK;
                if ((carrier[index] & MESSAGE_MASK) == bit) {
                    unchanged[pattern(carrier[index])]++;
                } else {
                    changed[pattern(carrier[index])]++;
                }
                result[index] = (byte) ((carrier[index] & CARRIER_MASK) | bit);
                index++;
            }
        }
        int end = index;

        // Paso 2: se invierten los patrones en los que cambiaron más bytes de los que quedaron iguales.
        boolean[] inverted = new boolean[PATTERN_COUNT];
        for (int pattern = 0; pattern < PATTERN_COUNT; pattern++) {
            inverted[pattern] = changed[pattern] > unchanged[pattern];
            result[pattern] = (byte) ((carrier[pattern] & CARRIER_MASK) | (inverted[pattern] ? 1 : 0));
        }
        for (int i = skipRed(PATTERN_COUNT); i < end; i = skipRed(i + 1)) {
            if (inverted[pattern(result[i])]) {
                result[i] ^= MESSAGE_MASK;
            }
        }
        return result;
    }

    @Override
    public byte[] extract(byte[] carrier, int byteCount) {
        if (byteCount > capacity(carrier)) {
            throw new IllegalArgumentException("El portador no tiene tantos bytes ocultos con LSBI");
        }
        boolean[] inverted = new boolean[PATTERN_COUNT];
        for (int pattern = 0; pattern < PATTERN_COUNT; pattern++) {
            inverted[pattern] = (carrier[pattern] & MESSAGE_MASK) == 1;
        }

        byte[] result = new byte[byteCount];
        int index = PATTERN_COUNT;
        for (int i = 0; i < byteCount; i++) {
            int recoveredByte = 0;
            for (int j = 0; j < Byte.SIZE; j++) {
                index = skipRed(index);
                int bit = carrier[index] & MESSAGE_MASK;
                if (inverted[pattern(carrier[index])]) {
                    bit ^= MESSAGE_MASK;
                }
                recoveredByte = (recoveredByte << 1) | bit;
                index++;
            }
            result[i] = (byte) recoveredByte;
        }
        return result;
    }

    /** Tercer y segundo bit menos significativos: 00, 01, 10 u 11. El LSB no los modifica. */
    private static int pattern(byte value) {
        return (value >> 1) & PATTERN_MASK;
    }

    private static int skipRed(int index) {
        return index % BYTES_PER_PIXEL == RED_OFFSET ? index + 1 : index;
    }
}