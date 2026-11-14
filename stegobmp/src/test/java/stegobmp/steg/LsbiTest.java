package stegobmp.steg;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LsbiTest {
    private final Lsbi algorithm = new Lsbi();

    @Test
    void capacityExcludesPatternBytesAndRedChannel() {
        assertEquals(0L, algorithm.capacity(new byte[3]));
        // 16 bytes: 4 de patrones; de los 12 restantes, 4 son rojos (5, 8, 11, 14).
        assertEquals(1L, algorithm.capacity(new byte[16]));
        assertEquals(0L, algorithm.capacity(new byte[15]));
        // 640x480: (921600 - 307200 - 3) / 8.
        assertEquals(76799L, algorithm.capacity(new byte[921600]));
    }

    @Test
    void embedInvertsPatternsWithMoreChangesAndSkipsRed() {
        // Los 8 bytes usados tienen patrón 00 y LSB 0; ocultar 0xFE cambiaría 7 de 8, así que se invierte.
        byte[] carrier = new byte[16];
        carrier[5] = 0x55;
        carrier[8] = 0x55;

        byte[] result = algorithm.embed(carrier, new byte[]{(byte) 0xFE});

        // Patrones 00, 01, 10, 11 → solo se invirtió 00. Tras invertir, solo cambia el último byte.
        assertArrayEquals(new byte[]{1, 0, 0, 0,
                0, 0x55, 0, 0, 0x55, 0, 0, 0, 0, 0, 0, 1}, result);
        assertArrayEquals(new byte[]{(byte) 0xFE}, algorithm.extract(result, 1));
    }

    @Test
    void embedKeepsPatternsWithFewerChanges() {
        byte[] carrier = new byte[16];

        byte[] result = algorithm.embed(carrier, new byte[]{0x01});

        assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}, result);
    }

    @Test
    void roundTripNeverChangesMoreBytesThanPlainLsb() {
        Random random = new Random(7204);
        byte[] carrier = new byte[4096];
        random.nextBytes(carrier);
        byte[] payload = new byte[(int) algorithm.capacity(carrier)];
        random.nextBytes(payload);

        byte[] result = algorithm.embed(carrier, payload);

        assertArrayEquals(payload, algorithm.extract(result, payload.length));
        int changed = 0;
        for (int i = 4; i < carrier.length; i++) {
            assertEquals(carrier[i] & 0xFE, result[i] & 0xFE);
            if (i % 3 == 2) {
                assertEquals(carrier[i], result[i]);
            } else if (carrier[i] != result[i]) {
                changed++;
            }
        }
        assertTrue(changed <= payload.length * Byte.SIZE / 2);
    }

    @Test
    void rejectsPayloadLargerThanCapacity() {
        assertThrows(IllegalArgumentException.class, () -> algorithm.embed(new byte[16], new byte[2]));
        assertThrows(IllegalArgumentException.class, () -> algorithm.extract(new byte[16], 2));
    }
}