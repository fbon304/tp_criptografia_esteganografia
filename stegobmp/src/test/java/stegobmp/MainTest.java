package stegobmp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @TempDir
    Path directory;

    @Test
    void roundTripThroughParserAndMainForBothAlgorithms() throws Exception {
        byte[] bmp = bitmap();
        Path carrier = directory.resolve("carrier.bmp");
        Files.write(carrier, bmp);
        Path input = directory.resolve("message.bin");
        byte[] content = {0, (byte) 255, 42, 0, 10};
        Files.write(input, content);

        for (String algorithm : new String[]{"LSB1", "LSB4"}) {
            Path embedded = directory.resolve(algorithm + ".bmp");
            Path output = directory.resolve(algorithm);
            assertEquals(0, run("-embed", "-in", input.toString(), "-p", carrier.toString(),
                    "-out", embedded.toString(), "-steg", algorithm));
            assertArrayEquals(Arrays.copyOf(bmp, 54), Arrays.copyOf(Files.readAllBytes(embedded), 54));
            assertEquals(0, run("-extract", "-p", embedded.toString(), "-out", output.toString(),
                    "-steg", algorithm));
            assertArrayEquals(content, Files.readAllBytes(directory.resolve(algorithm + ".bin")));
        }
    }

    @Test
    void insufficientCapacityReportsMaximumAndDoesNotWriteOutput() throws Exception {
        Path carrier = directory.resolve("carrier.bmp");
        Files.write(carrier, bitmap());
        Path input = directory.resolve("large.bin");
        Files.write(input, new byte[100]);
        Path output = directory.resolve("output.bmp");
        StringWriter errors = new StringWriter();
        int status = Main.run(new String[]{"-embed", "-in", input.toString(), "-p", carrier.toString(),
                "-out", output.toString(), "-steg", "LSB1"},
                new PrintWriter(new StringWriter()), new PrintWriter(errors));
        assertNotEquals(0, status);
        assertTrue(errors.toString().contains("Capacidad máxima: 24"));
        assertFalse(Files.exists(output));
    }

    @Test
    void lsbiFailsExplicitlyWithoutWritingOutput() {
        assertNotEquals(0, run("-extract", "-p", "missing.bmp", "-out", directory.resolve("out").toString(),
                "-steg", "LSBI"));
        assertFalse(Files.exists(directory.resolve("out")));
    }

    @Test
    void encryptedRoundTripWithStatementSyntaxAndDefaults() throws Exception {
        Path carrier = directory.resolve("imagen1.bmp");
        Files.write(carrier, bitmap());
        Path input = directory.resolve("mensaje1.txt");
        byte[] content = "hola".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        Files.write(input, content);
        String[][] cipherOptions = {
                {"-a", "3des", "-m", "cbc", "-pass", "oculto"},
                {"-pass", "oculto"},
                {"-a", "aes256", "-pass", "oculto"},
                {"-m", "ofb", "-pass", "oculto"}
        };
        for (String[] options : cipherOptions) {
            Path embedded = directory.resolve("imagenmas1.bmp");
            Path output = directory.resolve("mensaje1");
            assertEquals(0, run(concat(new String[]{"-embed", "-in", input.toString(), "-p", carrier.toString(),
                    "-out", embedded.toString(), "-steg", "LSB4"}, options)), String.join(" ", options));
            assertEquals(0, run(concat(new String[]{"-extract", "-p", embedded.toString(),
                    "-out", output.toString(), "-steg", "LSB4"}, options)), String.join(" ", options));
            Path recovered = directory.resolve("mensaje1.txt");
            assertArrayEquals(content, Files.readAllBytes(recovered));
        }
    }

    @Test
    void wrongPasswordReportsErrorAndFails() throws Exception {
        Path carrier = directory.resolve("imagen1.bmp");
        Files.write(carrier, bitmap());
        Path input = directory.resolve("mensaje1.bin");
        Files.write(input, new byte[]{1, 2, 3});
        Path embedded = directory.resolve("imagenmas1.bmp");
        assertEquals(0, run("-embed", "-in", input.toString(), "-p", carrier.toString(),
                "-out", embedded.toString(), "-steg", "LSB4", "-pass", "oculto"));
        StringWriter errors = new StringWriter();
        int status = Main.run(new String[]{"-extract", "-p", embedded.toString(),
                        "-out", directory.resolve("salida").toString(), "-steg", "LSB4", "-pass", "otra"},
                new PrintWriter(new StringWriter()), new PrintWriter(errors));
        assertNotEquals(0, status);
        assertTrue(errors.toString().contains("contraseña, algoritmo o modo incorrectos"));
    }

    private static String[] concat(String[] first, String[] second) {
        String[] combined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    private int run(String... arguments) {
        return Main.run(arguments, new PrintWriter(new StringWriter()), new PrintWriter(new StringWriter()));
    }

    private byte[] bitmap() {
        ByteBuffer buffer = ByteBuffer.allocate(54 + 192).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 'B').put((byte) 'M');
        buffer.putInt(2, buffer.capacity());
        buffer.putInt(10, 54);
        buffer.putInt(14, 40);
        buffer.putInt(18, 8);
        buffer.putInt(22, 8);
        buffer.putShort(26, (short) 1);
        buffer.putShort(28, (short) 24);
        return buffer.array();
    }
}
