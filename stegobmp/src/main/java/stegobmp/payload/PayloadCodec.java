package stegobmp.payload;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public final class PayloadCodec {
    private static final long MAX_UNSIGNED_INT = 0xFFFF_FFFFL;
    private static final int MAX_ASCII_CHARACTER = 0x7F;
    private static final int MINIMUM_EXTENSION_LENGTH = 2;

    public record DecodedPayload(byte[] content, String extension) {}

    public byte[] encodePlain(byte[] content, String extension) {
        Objects.requireNonNull(content, "El contenido no puede ser null");
        validateExtension(extension);

        long payloadSize = (long) Integer.BYTES + content.length + extension.length() + 1;
        if (payloadSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("El payload es demasiado grande para un array de bytes");
        }

        ByteBuffer buffer = ByteBuffer.allocate((int) payloadSize);
        writeSize(buffer, content.length);
        buffer.put(content);
        buffer.put(extension.getBytes(StandardCharsets.US_ASCII));
        buffer.put((byte) '\0');
        return buffer.array();
    }

    public DecodedPayload decodePlain(byte[] payload) {
        Objects.requireNonNull(payload, "El payload no puede ser null");
        if (payload.length < Integer.BYTES) {
            throw new IllegalArgumentException("El payload es demasiado pequeño para extraer");
        }

        long size = readSize(Arrays.copyOfRange(payload, 0, Integer.BYTES));

        long minimumLength = Integer.BYTES + size + MINIMUM_EXTENSION_LENGTH;
        if (minimumLength > payload.length) {
            throw new IllegalArgumentException("Payload incompleto");
        }

        int extensionStart = Integer.BYTES + (int) size;
        int extensionEnd = payload.length - 1;
        if (payload[extensionEnd] != 0) {
            throw new IllegalArgumentException("La extensión no tiene terminador NUL final");
        }

        String extension = new String(payload, extensionStart, extensionEnd - extensionStart,
                StandardCharsets.US_ASCII);
        validateExtension(extension);

        return new DecodedPayload(
                Arrays.copyOfRange(payload, Integer.BYTES, extensionStart),
                extension
        );
    }

    public byte[] encodeEncrypted(byte[] ciphertext) {
        Objects.requireNonNull(ciphertext, "El cifrado no puede ser null");

        long payloadSize = (long) Integer.BYTES + ciphertext.length;
        if (payloadSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("El payload es demasiado grande para un array de bytes");
        }

        ByteBuffer buffer = ByteBuffer.allocate((int) payloadSize);
        writeSize(buffer, ciphertext.length);
        buffer.put(ciphertext);
        return buffer.array();
    }

    public byte[] decodeEncrypted(byte[] payload) {
        Objects.requireNonNull(payload, "El payload no puede ser null");
        if (payload.length < Integer.BYTES) {
            throw new IllegalArgumentException("El payload es demasiado pequeño para extraer");
        }

        long size = readSize(Arrays.copyOfRange(payload, 0, Integer.BYTES));

        if (size != payload.length - Integer.BYTES) {
            throw new IllegalArgumentException("El tamaño declarado no coincide con el cifrado");
        }

        return Arrays.copyOfRange(payload, Integer.BYTES, payload.length);
    }

    public long readSize(byte[] sizeBytes) {
        Objects.requireNonNull(sizeBytes, "Los bytes del tamaño no pueden ser null");
        if (sizeBytes.length != Integer.BYTES) {
            throw new IllegalArgumentException("El tamaño debe estar representado por exactamente 4 bytes");
        }
        return Integer.toUnsignedLong(
                ByteBuffer.wrap(sizeBytes).order(ByteOrder.BIG_ENDIAN).getInt());
    }

    private void writeSize(ByteBuffer buffer, long size) {
        Objects.requireNonNull(buffer, "El buffer no puede ser null");
        if (size < 0 || size > MAX_UNSIGNED_INT) {
            throw new IllegalArgumentException("El tamaño debe estar entre 0 y 4.294.967.295 bytes");
        }
        if (buffer.remaining() < Integer.BYTES) {
            throw new IllegalArgumentException("El buffer debe tener al menos 4 bytes disponibles");
        }
        buffer.order(ByteOrder.BIG_ENDIAN).putInt((int) size);
    }

    private void validateExtension(String extension) {
        Objects.requireNonNull(extension, "La extensión no puede ser null");
        if (!extension.startsWith(".")) {
            throw new IllegalArgumentException("La extensión debe comenzar con un punto");
        }
        for (int i = 0; i < extension.length(); i++) {
            char character = extension.charAt(i);
            if (character == '\0' || character > MAX_ASCII_CHARACTER) {
                throw new IllegalArgumentException(
                        "La extensión debe contener solo caracteres ASCII y no incluir el terminador NUL");
            }
        }
    }

}
