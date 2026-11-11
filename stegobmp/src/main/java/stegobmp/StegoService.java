package stegobmp;

import stegobmp.bmp.BmpImage;
import stegobmp.cli.CliArguments;
import stegobmp.crypto.CryptoException;
import stegobmp.crypto.CryptoService;
import stegobmp.payload.PayloadCodec;
import stegobmp.steg.Lsb1;
import stegobmp.steg.Lsb4;
import stegobmp.steg.Steganography;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class StegoService {
    private static final int MINIMUM_EXTENSION_LENGTH = 2;
    private final PayloadCodec codec = new PayloadCodec();
    private final CryptoService cryptoService = new CryptoService();

    public void execute(CliArguments arguments) throws IOException {
        Steganography algorithm = switch (arguments.steganography()) {
            case LSB1 -> new Lsb1();
            case LSB4 -> new Lsb4();
            // TODO: Conectar la implementación de LSBI cuando esté disponible.
            case LSBI -> throw new UnsupportedOperationException("LSBI todavía no está implementado");
        };
        BmpImage image = BmpImage.read(arguments.carrier());
        byte[] carrier = image.body();

        switch (arguments.operation()) {
            case EMBED -> embed(arguments, image, carrier, algorithm);
            case EXTRACT -> extract(arguments, carrier, algorithm);
        }
    }

    private void embed(CliArguments arguments, BmpImage image, byte[] carrier, Steganography algorithm) throws IOException {
        String filename = arguments.input().getFileName().toString();
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new IllegalArgumentException("El archivo a ocultar debe tener una extensión");
        }
        byte[] payload = codec.encodePlain(Files.readAllBytes(arguments.input()), filename.substring(dot));
        if (arguments.encryptionEnabled()) {
            byte[] ciphertext = cryptoService.encrypt(payload, arguments.cipher(),
                    arguments.mode(), arguments.password());
            payload = codec.encodeEncrypted(ciphertext);
        }
        long capacity = algorithm.capacity(carrier);
        if (payload.length > capacity) {
            throw new IllegalArgumentException("El archivo no cabe en el portador. Capacidad máxima: "
                    + capacity + " bytes de payload; se necesitan " + payload.length + " bytes");
        }
        image.write(arguments.output(), algorithm.embed(carrier, payload));
    }

    private void extract(CliArguments arguments, byte[] carrier, Steganography algorithm) throws IOException {
        long capacity = algorithm.capacity(carrier);
        if (capacity < Integer.BYTES) {
            throw new IllegalArgumentException("El portador no tiene capacidad para el tamaño del payload");
        }
        long size = codec.readSize(algorithm.extract(carrier, Integer.BYTES));
        PayloadCodec.DecodedPayload payload = arguments.encryptionEnabled()
                ? extractEncrypted(arguments, carrier, algorithm, capacity, size)
                : extractPlain(carrier, algorithm, capacity, size);

        String extension = payload.extension();
        if (extension.indexOf('/') >= 0 || extension.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("La extensión oculta no puede contener separadores de ruta");
        }
        Path output = arguments.output();
        Path destination = output.resolveSibling(output.getFileName().toString() + extension);
        Files.write(destination, payload.content());
    }

    /** El tamaño oculto es el del cifrado; al descifrar se obtiene tamaño real || datos || extensión. */
    private PayloadCodec.DecodedPayload extractEncrypted(CliArguments arguments, byte[] carrier,
                                                         Steganography algorithm, long capacity, long size) {
        if (Integer.BYTES + size > capacity) {
            throw new IllegalArgumentException("El tamaño cifrado excede la capacidad del portador");
        }
        byte[] ciphertext = codec.decodeEncrypted(algorithm.extract(carrier, Integer.BYTES + (int) size));
        byte[] plain = cryptoService.decrypt(ciphertext, arguments.cipher(), arguments.mode(),
                arguments.password());
        try {
            return codec.decodePlain(plain);
        } catch (IllegalArgumentException exception) {
            // En CFB y OFB no hay padding: una contraseña incorrecta se detecta recién acá.
            throw new CryptoException(CryptoException.DECRYPTION_FAILED, exception);
        }
    }

    private PayloadCodec.DecodedPayload extractPlain(byte[] carrier, Steganography algorithm,
                                                     long capacity, long size) {
        if (Integer.BYTES + size + MINIMUM_EXTENSION_LENGTH > capacity) {
            throw new IllegalArgumentException("El tamaño oculto excede la capacidad del portador");
        }
        byte[] recovered = algorithm.extract(carrier, (int) capacity);
        int extensionEnd = Integer.BYTES + (int) size;
        while (extensionEnd < recovered.length && recovered[extensionEnd] != 0) {
            extensionEnd++;
        }
        if (extensionEnd == recovered.length) {
            throw new IllegalArgumentException("La extensión oculta no tiene terminador NUL");
        }
        return codec.decodePlain(Arrays.copyOf(recovered, extensionEnd + 1));
    }
}
