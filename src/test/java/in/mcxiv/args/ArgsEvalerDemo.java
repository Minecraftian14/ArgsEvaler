package in.mcxiv.args;

import in.mcxiv.args.ArgsEvaler.ResultMap;

import java.io.File;

import static in.mcxiv.args.ArgsEvaler.pattern;
import static in.mcxiv.args.ArgsEvaler.predicate;

public class ArgsEvalerDemo {

    private static final ArgsEvaler parser = new ArgsEvaler.ArgsEvalerBuilder()
            .addExpression("command sequence",
                    predicate("create delete edit"::contains),
                    pattern("<!(\\d{10})>", long.class),
                    File.class,
                    int.class
            )
            .addTagged("--client", boolean.class)
            .addWord("help")
            .build();

    public static void main(String[] args) throws InterruptedException {
        main2("create <!1234576890> res/cells.db 3".split(" "));
        main2("--client true edit <!1234576890> res/cells_cache.db 4".split(" "));
        main2("delete <!1234576890> compile/cells.db 0 --client false".split(" "));
    }

    public static void main2(String[] args) throws InterruptedException {
        ResultMap result = parser.parse(args);

        result.ifPresent("help", o -> printHelpMessage());

        result.<Boolean>getOptT("--client")
                .ifPresent(ArgsEvalerDemo::setupClientEntry);

        result.ifPresentRunAsync("command sequence",
                o -> runCommandSequence((Object[]) o));

        Thread.sleep(500);
    }

    private static void runCommandSequence(Object[] objects) {
        String command = (String) objects[0];
        long object_id = (long) objects[1];
        File database = (File) objects[2];
        int value = (int) objects[3];
        System.out.printf("Ran a command. %sing an object with the id %d at data center .\\%s with the value %d\n", command, object_id, database, value);
    }

    private static void printHelpMessage() {
        System.out.println("Printing a help message.");
    }

    private static void setupClientEntry(boolean b) {
        System.out.println("Successfully set up client access point.");
        if (b) System.out.println("Client has been granted editing permissions.");
    }
}
