package stegobmp;

import picocli.CommandLine;
import stegobmp.cli.CliParser;

import java.io.PrintWriter;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, new PrintWriter(System.out, true), new PrintWriter(System.err, true)));
    }

    static int run(String[] args, PrintWriter out, PrintWriter err) {
        CliParser parser = new CliParser();
        try {
            if (parser.parse(args).isEmpty()) {
                parser.printUsage(out);
            }
            // Por ahora solo se parsean y validan argumentos.
            return CommandLine.ExitCode.OK;
        } catch (CommandLine.ParameterException exception) {
            err.println("Error: " + exception.getMessage());
            parser.printUsage(err);
            return CommandLine.ExitCode.USAGE;
        }
    }
}
