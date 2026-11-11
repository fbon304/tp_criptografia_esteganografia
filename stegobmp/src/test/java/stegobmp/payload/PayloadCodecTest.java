package stegobmp.payload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PayloadCodecTest {
    private final PayloadCodec codec = new PayloadCodec();

    @Test
    void plainEncodingMatchesWireFormat() {
        assertArrayEquals(new byte[]{0, 0, 0, 3, 0, (byte) 255, 42, '.', 'b', 'i', 'n', 0},
                codec.encodePlain(new byte[]{0, (byte) 255, 42}, ".bin"));
    }

    @Test
    void plainDecodingReadsIndependentFixture() {
        var decoded = codec.decodePlain(new byte[]{0, 0, 0, 2, 0, (byte) 255, '.', 'x', 0});
        assertArrayEquals(new byte[]{0, (byte) 255}, decoded.content());
        assertEquals(".x", decoded.extension());
    }

    @Test
    void emptyFileRoundTrips() {
        var decoded = codec.decodePlain(codec.encodePlain(new byte[0], ".txt"));
        assertArrayEquals(new byte[0], decoded.content());
        assertEquals(".txt", decoded.extension());
    }

    @Test
    void plainDecodingRejectsMalformedSizesAndExtensions() {
        byte[][] invalid = {
                {}, {0, 0, 0},
                {0, 0, 0, 10, '.', 'x', 0},
                {(byte) 255, (byte) 255, (byte) 255, (byte) 255, '.', 0},
                {0, 0, 0, 0, '.', 'x'},
                {0, 0, 0, 0, 'x', 0},
                {0, 0, 0, 0, '.', (byte) 128, 0},
                {0, 0, 0, 0, '.', 0, 'x', 0},
                {0, 0, 0, 0, '.', 'x', 0, 42}
        };
        for (byte[] payload : invalid) {
            assertThrows(IllegalArgumentException.class, () -> codec.decodePlain(payload));
        }
    }

    @Test
    void plainEncodingRejectsInvalidExtensions() {
        for (String extension : new String[]{"", "txt", ".téxt", ".x\0"}) {
            assertThrows(IllegalArgumentException.class, () -> codec.encodePlain(new byte[0], extension));
        }
    }

    @Test
    void encryptedEncodingAndDecodingUseOnlySizeAndCiphertext() {
        byte[] ciphertext = {0, (byte) 255, 42};
        byte[] wire = {0, 0, 0, 3, 0, (byte) 255, 42};
        assertArrayEquals(wire, codec.encodeEncrypted(ciphertext));
        assertArrayEquals(ciphertext, codec.decodeEncrypted(wire));
        assertArrayEquals(new byte[0], codec.decodeEncrypted(new byte[4]));
    }

    @Test
    void encryptedDecodingRejectsTruncationAndTrailingBytes() {
        for (byte[] payload : new byte[][]{{}, {0, 0, 0}, {0, 0, 0, 2, 42}, {0, 0, 0, 0, 42},
                {(byte) 255, (byte) 255, (byte) 255, (byte) 255}}) {
            assertThrows(IllegalArgumentException.class, () -> codec.decodeEncrypted(payload));
        }
    }

    @Test
    void sizeIsUnsignedBigEndianAndRequiresExactlyFourBytes() {
        assertEquals(0, codec.readSize(new byte[4]));
        assertEquals(256, codec.readSize(new byte[]{0, 0, 1, 0}));
        assertEquals(4294967295L, codec.readSize(new byte[]{-1, -1, -1, -1}));
        for (int length : new int[]{0, 3, 5}) {
            assertThrows(IllegalArgumentException.class, () -> codec.readSize(new byte[length]));
        }
    }

    @Test
    void publicMethodsRejectNullInputs() {
        assertThrows(NullPointerException.class, () -> codec.encodePlain(null, ".txt"));
        assertThrows(NullPointerException.class, () -> codec.encodePlain(new byte[0], null));
        assertThrows(NullPointerException.class, () -> codec.decodePlain(null));
        assertThrows(NullPointerException.class, () -> codec.encodeEncrypted(null));
        assertThrows(NullPointerException.class, () -> codec.decodeEncrypted(null));
        assertThrows(NullPointerException.class, () -> codec.readSize(null));
    }
}
