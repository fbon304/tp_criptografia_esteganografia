package stegobmp.bmp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class BmpImage {
    private static final int FILE_HEADER_SIZE = 14;
    private static final int DIB_HEADER_SIZE = 40;
    private static final int HEADER_SIZE = FILE_HEADER_SIZE + DIB_HEADER_SIZE;
    private static final int DIB_SIZE_POSITION = 14;
    private static final int WIDTH_POSITION = 18;
    private static final int HEIGHT_POSITION = 22;
    private static final int PLANES_POSITION = 26;
    private static final int BITS_PER_PIXEL_POSITION = 28;
    private static final int COMPRESSION_POSITION = 30;
    private static final int PIXEL_OFFSET_POSITION = 10;
    private static final int BYTES_PER_PIXEL = 3;
    private static final int ROW_ALIGNMENT = 4;

    private final byte[] header;
    private final byte[] body;

    private BmpImage(byte[] header, byte[] body) {
        this.header = header;
        this.body = body;
    }

    public static BmpImage read(Path path) throws IOException {
        byte[] fileBytes = Files.readAllBytes(path);
        validate(fileBytes);

        ByteBuffer metadata = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN);
        long pixelOffset = Integer.toUnsignedLong(metadata.getInt(PIXEL_OFFSET_POSITION));
        if (pixelOffset < HEADER_SIZE || pixelOffset > fileBytes.length) {
            throw new IOException("El offset de pixeles del BMP no es valido");
        }

        long width = metadata.getInt(WIDTH_POSITION);
        long height = Math.abs((long) metadata.getInt(HEIGHT_POSITION));
        long bytesPerRow = ((width * BYTES_PER_PIXEL + ROW_ALIGNMENT - 1)
                / ROW_ALIGNMENT) * ROW_ALIGNMENT;

        if (height > (fileBytes.length - pixelOffset) / bytesPerRow) {
            throw new IOException("El cuerpo del BMP esta incompleto para sus dimensiones");
        }

        byte[] header = Arrays.copyOfRange(fileBytes, 0, (int) pixelOffset);
        byte[] body = Arrays.copyOfRange(fileBytes, (int) pixelOffset, fileBytes.length);
        return new BmpImage(header, body);
    }

    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }

    public void write(Path path, byte[] modifiedBody) throws IOException {
        if (modifiedBody.length != body.length) {
            throw new IllegalArgumentException(
                    "El cuerpo modificado debe conservar la longitud original: " + body.length + " bytes");
        }
        try (OutputStream output = Files.newOutputStream(path)) {
            output.write(header);
            output.write(modifiedBody);
        }
    }

    private static void validate(byte[] fileBytes) throws IOException {
        if (fileBytes.length < HEADER_SIZE) {
            throw new IOException("Archivo BMP incompleto: se requieren al menos 54 bytes de encabezado");
        }
        if (fileBytes[0] != 'B' || fileBytes[1] != 'M') {
            throw new IOException("El archivo no tiene la firma BMP (BM)");
        }

        ByteBuffer header = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN);
        // V3 tiene un encabezado DIB de 40 bytes. V4 y V5 lo extienden (108 y 124 bytes) conservando
        // los mismos primeros 40, así que se aceptan: los píxeles se ubican con el offset del encabezado.
        if (header.getInt(DIB_SIZE_POSITION) < DIB_HEADER_SIZE) {
            throw new IOException("Solo se admiten archivos BMP V3 o posteriores (encabezado DIB de al menos 40 bytes)");
        }
        if (header.getInt(WIDTH_POSITION) <= 0 || header.getInt(HEIGHT_POSITION) == 0
                || header.getInt(HEIGHT_POSITION) == Integer.MIN_VALUE) {
            throw new IOException("Las dimensiones del BMP no son validas");
        }
        if (header.getShort(PLANES_POSITION) != 1) {
            throw new IOException("El BMP debe tener un unico plano de color");
        }
        if (header.getShort(BITS_PER_PIXEL_POSITION) != 24) {
            throw new IOException("Solo se admiten archivos BMP de 24 bits por pixel");
        }
        if (header.getInt(COMPRESSION_POSITION) != 0) {
            throw new IOException("Solo se admiten archivos BMP sin compresion");
        }

    }
}