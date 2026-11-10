package stegobmp.steg;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Lsb1Test {
    private final Lsb1 algorithm = new Lsb1();

    @Test
    void capacityCountsOnlyCompletePayloadBytes() {
        assertEquals(2L, algorithm.capacity(new byte[23]));
    }

    @Test
    void embedWritesMostSignificantBitFirstAndPreservesCarrier() {
        byte[] carrier = {(byte) 0xA0, (byte) 0xB1, (byte) 0xC2, (byte) 0xD3,
                (byte) 0xE4, (byte) 0xF5, 0x66, 0x77, 0x55};
        byte[] original = carrier.clone();

        byte[] result = algorithm.embed(carrier, new byte[]{(byte) 0xA6});

        assertArrayEquals(new byte[]{(byte) 0xA1, (byte) 0xB0, (byte) 0xC3,
                (byte) 0xD2, (byte) 0xE4, (byte) 0xF5, 0x67, 0x76, 0x55}, result);
        assertArrayEquals(original, carrier);
    }

    @Test
    void extractReadsRequestedBytesFromLeastSignificantBits() {
        byte[] carrier = {(byte) 0xA1, (byte) 0xB0, (byte) 0xC3, (byte) 0xD2,
                (byte) 0xE4, (byte) 0xF5, 0x67, 0x76,
                0x10, 0x21, 0x30, 0x41, 0x50, 0x60, 0x70, (byte) 0x81, 0x01};

        assertArrayEquals(new byte[]{(byte) 0xA6, 0x51}, algorithm.extract(carrier, 2));
    }
}
