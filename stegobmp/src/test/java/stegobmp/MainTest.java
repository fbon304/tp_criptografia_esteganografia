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
    void passwordAndLsbiFailExplicitlyWithoutWritingOutput() {
        for (String[] options : new String[][]{{"-steg", "LSB1", "-pass", "secret"}, {"-steg", "LSBI"}}) {
            String[] arguments = {"-extract", "-p", "missing.bmp", "-out", directory.resolve("out").toString()};
            String[] combined = Arrays.copyOf(arguments, arguments.length + options.length);
            System.arraycopy(options, 0, combined, arguments.length, options.length);
            assertNotEquals(0, run(combined));
        }
        assertFalse(Files.exists(directory.resolve("out")));
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
