package stegobmp.cli;

import java.nio.file.Path;

/** Argumentos validados. Las rutas se conservan sin leer ni escribir archivos. */
public record CliArguments(
        Operation operation,
        Path input,
        Path carrier,
        Path output,
        SteganographyAlgorithm steganography,
        CipherAlgorithm cipher,
        CipherMode mode,
        String password
) {
    public enum Operation {
        EMBED, EXTRACT
    }

    public enum SteganographyAlgorithm {
        LSB1, LSB4, LSBI
    }

    public enum CipherAlgorithm {
        AES128, AES192, AES256, TRIPLE_DES
    }

    public enum CipherMode {
        ECB, CFB, OFB, CBC
    }

    /** -a y -m solo tienen efecto si se proporcionó -pass. */
    public boolean encryptionEnabled() {
        return password != null;
    }

    @Override
    public String toString() {
        return "CliArguments[operation=" + operation + ", input=" + input
                + ", carrier=" + carrier + ", output=" + output
                + ", steganography=" + steganography + ", cipher=" + cipher
                + ", mode=" + mode + ", password=" + (password == null ? "null" : "<oculta>") + "]";
    }
}
