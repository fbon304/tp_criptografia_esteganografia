package stegobmp;

import picocli.CommandLine;
import stegobmp.cli.CliParser;

import java.io.PrintWriter;
import java.io.IOException;
import stegobmp.cli.CliArguments;
import java.util.Optional;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, new PrintWriter(System.out, true), new PrintWriter(System.err, true)));
    }

    static int run(String[] args, PrintWriter out, PrintWriter err) {
        CliParser parser = new CliParser();
        try {
            Optional<CliArguments> arguments = parser.parse(args);
            if (arguments.isEmpty()) {
                parser.printUsage(out);
                return CommandLine.ExitCode.OK;
            }
            new StegoService().execute(arguments.get());
            return CommandLine.ExitCode.OK;
        } catch (CommandLine.ParameterException exception) {
            err.println("Error: " + exception.getMessage());
            parser.printUsage(err);
            return CommandLine.ExitCode.USAGE;
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException exception) {
            err.println("Error: " + exception.getMessage());
            err.flush();
            return CommandLine.ExitCode.SOFTWARE;
        }
    }
}
