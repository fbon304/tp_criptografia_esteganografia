package stegobmp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import stegobmp.cli.CliArguments;
import stegobmp.cli.CliArguments.*;
import stegobmp.crypto.CryptoException;
import stegobmp.crypto.CryptoService;
import stegobmp.payload.PayloadCodec;
import stegobmp.steg.Lsb1;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class StegoServiceTest {
    @TempDir Path directory;
    private final StegoService service = new StegoService();

    @Test
    void exactCapacityAndEmptyFilesRoundTripWithEveryAlgorithm() throws Exception {
        for (SteganographyAlgorithm algorithm : SteganographyAlgorithm.values()) {
            // 192 carrier bytes hold 24 payload bytes in LSB1, 96 in LSB4 and 15 in LSBI.
            int capacity = switch (algorithm) {
                case LSB1 -> 24;
                case LSB4 -> 96;
                case LSBI -> 15;
            };
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
    void reportsMissingCarrier() {
        Path missing = directory.resolve("missing.bmp");
        Path output = directory.resolve("out");
        assertThrows(IOException.class, () -> service.execute(arguments(Operation.EXTRACT,
                null, missing, output, SteganographyAlgorithm.LSBI, null)));
        assertThrows(IOException.class, () -> service.execute(arguments(Operation.EXTRACT,
                null, missing, output, SteganographyAlgorithm.LSB1, null)));
        assertThrows(IOException.class, () -> service.execute(arguments(Operation.EXTRACT,
                null, missing, output, SteganographyAlgorithm.LSB1, "secret")));
        assertFalse(Files.exists(output));
    }

    @Test
    void encryptedRoundTripWithEveryCipherModeAndAlgorithm() throws Exception {
        byte[] content = "contenido secreto".getBytes(StandardCharsets.US_ASCII);
        Path input = directory.resolve("secreto.txt");
        Files.write(input, content);
        Path carrier = writeBitmap(new byte[4096]);
        for (SteganographyAlgorithm algorithm : SteganographyAlgorithm.values()) {
            for (CipherAlgorithm cipher : CipherAlgorithm.values()) {
                for (CipherMode mode : CipherMode.values()) {
                    Path embedded = directory.resolve("embedded.bmp");
                    service.execute(new CliArguments(Operation.EMBED, input, carrier, embedded, algorithm,
                            cipher, mode, "oculto"));
                    Path output = directory.resolve("recovered");
                    service.execute(new CliArguments(Operation.EXTRACT, null, embedded, output, algorithm,
                            cipher, mode, "oculto"));
                    Path recovered = directory.resolve("recovered.txt");
                    assertArrayEquals(content, Files.readAllBytes(recovered), cipher + " " + mode);
                    Files.delete(recovered);
                }
            }
        }
    }

    @Test
    void encryptedPayloadHidesCiphertextSizeFollowedByCiphertext() throws Exception {
        // Ejemplo de la consigna: 4 + 414 + ".txt\0" = 423 bytes, que con padding 3DES quedan en 424.
        byte[] content = new byte[414];
        Path input = directory.resolve("saco.txt");
        Files.write(input, content);
        Path carrier = writeBitmap(new byte[4096]);
        Path embedded = directory.resolve("embedded.bmp");
        service.execute(new CliArguments(Operation.EMBED, input, carrier, embedded, SteganographyAlgorithm.LSB1,
                CipherAlgorithm.TRIPLE_DES, CipherMode.CBC, "oculto"));

        byte[] body = Arrays.copyOfRange(Files.readAllBytes(embedded), 54, 54 + 4096);
        byte[] hidden = new Lsb1().extract(body, 4 + 424);
        assertEquals(424, new PayloadCodec().readSize(Arrays.copyOf(hidden, 4)));
        byte[] plain = new CryptoService().decrypt(Arrays.copyOfRange(hidden, 4, hidden.length),
                CipherAlgorithm.TRIPLE_DES, CipherMode.CBC, "oculto");
        assertArrayEquals(new PayloadCodec().encodePlain(content, ".txt"), plain);
    }

    @Test
    void wrongPasswordFailsWithClearMessageWithoutWritingOutput() throws Exception {
        Path input = directory.resolve("secreto.txt");
        Files.write(input, "contenido secreto".getBytes(StandardCharsets.US_ASCII));
        Path carrier = writeBitmap(new byte[4096]);
        for (CipherMode mode : CipherMode.values()) {
            Path embedded = directory.resolve("embedded.bmp");
            service.execute(new CliArguments(Operation.EMBED, input, carrier, embedded, SteganographyAlgorithm.LSB1,
                    CipherAlgorithm.AES256, mode, "oculto"));
            Path output = directory.resolve("recovered");
            CryptoException exception = assertThrows(CryptoException.class, () -> service.execute(
                    new CliArguments(Operation.EXTRACT, null, embedded, output, SteganographyAlgorithm.LSB1,
                            CipherAlgorithm.AES256, mode, "incorrecta")), mode.toString());
            assertEquals(CryptoException.DECRYPTION_FAILED, exception.getMessage());
            assertFalse(Files.exists(directory.resolve("recovered.txt")));
        }
    }

    @Test
    void encryptedPayloadLargerThanCarrierReportsCapacity() throws Exception {
        Path input = directory.resolve("secreto.txt");
        Files.write(input, new byte[12]);
        Path carrier = writeBitmap(new byte[192]);
        Path output = directory.resolve("output.bmp");
        // 4 + 12 + 5 = 21 bytes en claro caben en 24, pero cifrados con AES-CBC ocupan 4 + 32.
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.execute(
                new CliArguments(Operation.EMBED, input, carrier, output, SteganographyAlgorithm.LSB1,
                        CipherAlgorithm.AES128, CipherMode.CBC, "oculto")));
        assertTrue(exception.getMessage().contains("Capacidad mÃ¡xima: 24"));
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