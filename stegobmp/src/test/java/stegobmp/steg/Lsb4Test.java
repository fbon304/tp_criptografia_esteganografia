package stegobmp.steg;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Lsb4Test {
    private final Lsb4 algorithm = new Lsb4();

    @Test
    void capacityCountsOnlyCompletePayloadBytes() {
        assertEquals(2L, algorithm.capacity(new byte[5]));
    }

    @Test
    void embedWritesHighNibbleFirstAndPreservesCarrier() {
        byte[] carrier = {(byte) 0xA1, (byte) 0xB2, (byte) 0xC3, (byte) 0xD4, 0x55};
        byte[] original = carrier.clone();

        byte[] result = algorithm.embed(carrier, new byte[]{(byte) 0xA6, 0x51});

        assertArrayEquals(new byte[]{(byte) 0xAA, (byte) 0xB6,
                (byte) 0xC5, (byte) 0xD1, 0x55}, result);
        assertArrayEquals(original, carrier);
    }

    @Test
    void extractReadsRequestedBytesFromLowNibbles() {
        byte[] carrier = {(byte) 0xAA, (byte) 0xB6, (byte) 0xC5, (byte) 0xD1, 0x5F};

        assertArrayEquals(new byte[]{(byte) 0xA6, 0x51}, algorithm.extract(carrier, 2));
    }
}
