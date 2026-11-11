package stegobmp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import stegobmp.cli.CliArguments;
import stegobmp.cli.CliArguments.*;
import stegobmp.steg.Lsb1;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class StegoServiceTest {
    @TempDir Path directory;
    private final StegoService service = new StegoService();

    @Test
    void exactCapacityAndEmptyFilesRoundTripWithBothAlgorithms() throws Exception {
        for (SteganographyAlgorithm algorithm : new SteganographyAlgorithm[]{SteganographyAlgorithm.LSB1,
                SteganographyAlgorithm.LSB4}) {
            // 192 carrier bytes hold 24 payload bytes in LSB1 and 96 in LSB4.
            int capacity = algorithm == SteganographyAlgorithm.LSB1 ? 24 : 96;
            for (int length : new int[]{0, capacity - 9}) {
                byte[] content = new byte[length];
                Arrays.fill(content, (byte) 255);
                Path input = directory.resolve("original.data.bin");
                Files.write(input, content);
                Path carrier = writeBitmap(new byte[192]);
                Path embedded = directory.resolve("embedded.bmp");
                service.execute(arguments(Operation.EMBED, input, carrier, embedded, algorithm, null));
                assertArrayEquals(Arrays.copyOf(Files.readAllBytes(carrier), 54),
                        Arrays.copyOf(Files.readAllBytes(embedded), 54));
                Path output = directory.resolve("recovered");
                service.execute(arguments(Operation.EXTRACT, null, embedded, output, algorithm, null));
                assertArrayEquals(content, Files.readAllBytes(directory.resolve("recovered.bin")));
            }
        }
    }

    @Test
    void rejectsFilesWithoutExtensionAndInsufficientCapacity() throws Exception {
        Path carrier = writeBitmap(new byte[192]);
        for (String name : new String[]{"noextension", "trailing.", "large.bin"}) {
            Path input = directory.resolve(name);
            Files.write(input, new byte[100]);
            Path output = directory.resolve("output.bmp");
            assertThrows(IllegalArgumentException.class, () -> service.execute(arguments(Operation.EMBED,
                    input, carrier, output, SteganographyAlgorithm.LSB1, null)));
            assertFalse(Files.exists(output));
        }
    }

    @Test
    void rejectsMalformedHiddenPayloadsWithoutWritingOutput() throws Exception {
        byte[] noTerminator = new byte[24];
        Arrays.fill(noTerminator, (byte) 'x');
        Arrays.fill(noTerminator, 0, 4, (byte) 0);
        noTerminator[4] = '.';
        byte[][] payloads = {
                {-1, -1, -1, -1}, noTerminator,
                {0, 0, 0, 0, 'x', 0},
                {0, 0, 0, 0, '.', '/', 'x', 0},
                {0, 0, 0, 0, '.', '\\', 'x', 0}
        };
        for (byte[] payload : payloads) {
            Path carrier = writeBitmap(new Lsb1().embed(new byte[192], payload));
            Path output = directory.resolve("recovered");
            assertThrows(IllegalArgumentException.class, () -> service.execute(arguments(Operation.EXTRACT,
                    null, carrier, output, SteganographyAlgorithm.LSB1, null)));
            assertFalse(Files.exists(output));
        }
    }

    @Test
    void rejectsCarrierTooSmallForSizeHeader() throws Exception {
        Path carrier = writeBitmap(new byte[4]);
        assertThrows(IllegalArgumentException.class, () -> service.execute(arguments(Operation.EXTRACT,
                null, carrier, directory.resolve("out"), SteganographyAlgorithm.LSB1, null)));
    }

    @Test
    void reportsUnavailableFeaturesAndMissingCarrier() {
        Path missing = directory.resolve("missing.bmp");
        Path output = directory.resolve("out");
        assertThrows(UnsupportedOperationException.class, () -> service.execute(arguments(Operation.EXTRACT,
                null, missing, output, SteganographyAlgorithm.LSB1, "secret")));
        assertThrows(UnsupportedOperationException.class, () -> service.execute(arguments(Operation.EXTRACT,
                null, missing, output, SteganographyAlgorithm.LSBI, null)));
        assertThrows(IOException.class, () -> service.execute(arguments(Operation.EXTRACT,
                null, missing, output, SteganographyAlgorithm.LSB1, null)));
        assertFalse(Files.exists(output));
    }

    private CliArguments arguments(Operation operation, Path input, Path carrier, Path output,
                                   SteganographyAlgorithm algorithm, String password) {
        return new CliArguments(operation, input, carrier, output, algorithm,
                CipherAlgorithm.AES128, CipherMode.CBC, password);
    }

    private Path writeBitmap(byte[] body) throws IOException {
        // One pixel per row has 3 channel bytes plus one padding byte.
        ByteBuffer bmp = ByteBuffer.allocate(54 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        bmp.put((byte) 'B').put((byte) 'M');
        bmp.putInt(2, bmp.capacity());
        bmp.putInt(10, 54);
        bmp.putInt(14, 40);
        bmp.putInt(18, 1);
        bmp.putInt(22, body.length / 4);
        bmp.putShort(26, (short) 1);
        bmp.putShort(28, (short) 24);
        bmp.position(54);
        bmp.put(body);
        Path path = directory.resolve("carrier.bmp");
        Files.write(path, bmp.array());
        return path;
    }
}
