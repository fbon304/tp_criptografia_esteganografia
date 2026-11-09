package stegobmp.cli;

import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.TypeConversionException;
import stegobmp.cli.CliArguments.CipherAlgorithm;
import stegobmp.cli.CliArguments.CipherMode;
import stegobmp.cli.CliArguments.Operation;
import stegobmp.cli.CliArguments.SteganographyAlgorithm;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Optional;

public final class CliParser {
    /**
     * Devuelve los argumentos validados, o un Optional vacío si se pidió ayuda.
     * No imprime mensajes ni accede a los archivos indicados.
     *
     * @throws ParameterException si los argumentos no cumplen la sintaxis de la consigna
     */
    public Optional<CliArguments> parse(String... args) {
        Options options = new Options();
        CommandLine commandLine = commandLine(options);
        if (commandLine.parseArgs(args).isUsageHelpRequested()) {
            return Optional.empty();
        }

        if (options.operation.embed && options.input == null) {
            throw new ParameterException(commandLine, "La opción -in es obligatoria con -embed.");
        }
        if (options.operation.extract && options.input != null) {
            throw new ParameterException(commandLine, "La opción -in solo se permite con -embed.");
        }
        if (options.password != null && options.password.isEmpty()) {
            throw new ParameterException(commandLine, "La opción -pass requiere una contraseña no vacía.");
        }

        return Optional.of(new CliArguments(
                options.operation.embed ? Operation.EMBED : Operation.EXTRACT,
                options.input, options.carrier, options.output, options.steganography,
                options.cipher, options.mode, options.password
        ));
    }

    public void printUsage(PrintWriter writer) {
        commandLine(new Options()).usage(writer);
        writer.flush();
    }

    private static CommandLine commandLine(Options options) {
        return new CommandLine(options)
                .setExpandAtFiles(false)
                .setPosixClusteredShortOptionsAllowed(false)
                .setOverwrittenOptionsAllowed(false)
                .registerConverter(Path.class, value -> {
                    if (value.isEmpty()) {
                        throw new TypeConversionException("La ruta no puede estar vacía.");
                    }
                    return Path.of(value);
                })
                .registerConverter(CipherAlgorithm.class, value -> switch (value) {
                    case "aes128" -> CipherAlgorithm.AES128;
                    case "aes192" -> CipherAlgorithm.AES192;
                    case "aes256" -> CipherAlgorithm.AES256;
                    case "3des" -> CipherAlgorithm.TRIPLE_DES;
                    default -> throw new TypeConversionException("Valores válidos: aes128, aes192, aes256, 3des.");
                })
                .registerConverter(CipherMode.class, value -> switch (value) {
                    case "ecb" -> CipherMode.ECB;
                    case "cfb" -> CipherMode.CFB;
                    case "ofb" -> CipherMode.OFB;
                    case "cbc" -> CipherMode.CBC;
                    default -> throw new TypeConversionException("Valores válidos: ecb, cfb, ofb, cbc.");
                });
    }

    @Command(
            name = "stegobmp",
            description = "Parser de argumentos de stegobmp.",
            synopsisHeading = "Uso:%n",
            customSynopsis = {
                    "stegobmp -embed -in file -p bitmapfile -out bitmapfile -steg <LSB1|LSB4|LSBI> [opciones]",
                    "stegobmp -extract -p bitmapfile -out file -steg <LSB1|LSB4|LSBI> [opciones]"
            },
            optionListHeading = "%nOpciones:%n",
            footer = "Sin -pass no se cifra ni descifra, aunque se indiquen -a o -m.",
            sortOptions = false
    )
    private static final class Options {
        @ArgGroup(exclusive = true, multiplicity = "1")
        private OperationOptions operation;

        @Option(names = "-in", paramLabel = "file", description = "Archivo a ocultar; obligatorio solo con -embed.")
        private Path input;

        @Option(names = "-p", required = true, paramLabel = "bitmapfile", description = "Archivo BMP portador.")
        private Path carrier;

        @Option(names = "-out", required = true, paramLabel = "file", description = "Ruta del archivo de salida.")
        private Path output;

        @Option(names = "-steg", required = true, paramLabel = "<LSB1|LSB4|LSBI>",
                description = "Algoritmo de esteganografiado (respetar mayúsculas).")
        private SteganographyAlgorithm steganography;

        @Option(names = "-a", defaultValue = "aes128", paramLabel = "<aes128|aes192|aes256|3des>",
                description = "Algoritmo de cifrado. Por defecto: aes128.")
        private CipherAlgorithm cipher;

        @Option(names = "-m", defaultValue = "cbc", paramLabel = "<ecb|cfb|ofb|cbc>",
                description = "Modo de cifrado. Por defecto: cbc.")
        private CipherMode mode;

        @Option(names = "-pass", paramLabel = "password", description = "Contraseña de cifrado o descifrado.")
        private String password;

        @Option(names = {"-h", "--help"}, usageHelp = true, arity = "0", description = "Mostrar esta ayuda.")
        private boolean help;
    }

    private static final class OperationOptions {
        @Option(names = "-embed", arity = "0", description = "Ocultar información (excluye -extract).")
        private boolean embed;

        @Option(names = "-extract", arity = "0", description = "Extraer información (excluye -embed).")
        private boolean extract;
    }
}
