package in.mcxiv.args;

import in.mcxiv.args.ArgsEvaler.EvaluationOrder;
import in.mcxiv.args.ArgsEvaler.ResultMap;
import in.mcxiv.args.ArgsEvaler.StringPredicate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ArgsEvalerTest {

    public static void main(String[] args) {
        System.out.println(Arrays.toString(args));
    }

    @Test
    void testArgsEvaler() {
        ArgsEvaler parser = new ArgsEvaler.ArgsEvalerBuilder()
                .addIndexed("name_a")
                .addIndexed("name_b")
                .addIndexed("some_int_a", int.class)
                .addIndexed("some_float_a", float.class)
                .addIndexed("big_int", BigInteger.class)
                .addNamed("value")
                .addNamed("path", File.class)
                .addNamed("bytes", ByteBuffer.class)
                .addTagged("--tag")
                .addTagged("-t")
                .addChain("a chain", "this", "is", "interesting")
                .addExpression("an expression", "kub", "timeout", Pattern.compile("<!\\d{10}.*>"), Pattern.compile("<!(\\d{10}).*>"), int.class)
                .addExpression("another expression", "kub", "profile", ((StringPredicate) s -> s.length() % 2 == 0), ArgsEvaler.resolve(s -> s.length() % 2 == 1, s -> s.substring(s.length() / 2)))
                .addResolver(ByteBuffer.class, (c, s) -> ByteBuffer.wrap(s.getBytes()))
                .build();

        ResultMap map = parser.parse(new String[]{
                "path=this/lol",
                "hello", "world",
                "bytes=someBytes", "135",
                "--tag", "a tagged value",
                "67.0", "123456787654321",
                "this", "is", "cool",
                "this", "is", "interesting",
                "-t", "another tagged value",
                "kub", "timeout", "<!2345678901>", "<!2345678901>", "14",
                "kub", "profile", "evenLength", "oddLength",
                "value=something"});

        Assertions.assertEquals("hello", map.get("name_a"));
        Assertions.assertEquals("world", map.get("name_b"));
        Assertions.assertEquals(135, map.get("some_int_a"));
        Assertions.assertEquals(67f, map.get("some_float_a"));
        Assertions.assertEquals(new BigInteger("123456787654321"), map.get("big_int"));

        Assertions.assertEquals("something", map.get("value"));
        Assertions.assertEquals(new File("this/lol"), map.get("path"));
        Assertions.assertTrue(map.get("bytes") instanceof ByteBuffer);
        Assertions.assertEquals(9, ((ByteBuffer) map.get("bytes")).array().length);

        Assertions.assertEquals("a tagged value", map.get("--tag"));
        Assertions.assertEquals("another tagged value", map.get("-t"));

        Assertions.assertArrayEquals(new Object[]{"this", "is", "interesting"}, (Object[]) map.get("a chain"));

        Assertions.assertArrayEquals(new Object[]{"kub", "timeout", "<!2345678901>", "2345678901", 14}, (Object[]) map.get("an expression"));
        Assertions.assertArrayEquals(new Object[]{"kub", "profile", "evenLength", "ength"}, (Object[]) map.get("another expression"));

        map.forEach((s, o) -> System.out.printf("%-20s\t%s%n", s, Arrays.deepToString(new Object[]{o})));

        // Just curious
        System.out.println(((File) map.get("path")).getAbsolutePath());

        ResultMap resultMap = parser.parse(new String[]{"ooof", "ooof", "1114"});

        int name = resultMap.getT("some_int_a");
        Assertions.assertEquals(1114, name);

        name = resultMap.getT("some_int_c", 1114);
        Assertions.assertEquals(1114, name);

        Optional<Object> opt = resultMap.getOpt("some_int_a");
        Assertions.assertTrue(opt.isPresent());

        resultMap.getOpt("some_int_c")
                .ifPresent(integer -> Assertions.fail());

        (opt = resultMap.getOpt("some_int_a", 1114))
                .map(o -> (int) o)
                .ifPresent(System.out::println);
        Assertions.assertTrue(opt.isPresent());
    }

    @Test
    void testConfigurableParameters() {
        ResultMap resultMap;

        final ArgsEvaler parser1 = new ArgsEvaler.ArgsEvalerBuilder()
                .addIndexed("A").addIndexed("B").addIndexed("C")
                .setRequireAllIndexedArgsToBeFulfilled(true)
                .build();

        assertThrows(Throwable.class, () -> parser1.parse(args("a", "b")));
        assertDoesNotThrow(() -> parser1.parse(args("a", "b", "c")));
        assertDoesNotThrow(() -> parser1.parse(args("a", "b", "c", "d")));

        final ArgsEvaler parser2 = new ArgsEvaler.ArgsEvalerBuilder()
                .addIndexed("A").addIndexed("B")
                .setHasVariadicEnding(true)
                .build();

        assertArrayEquals(new String[]{"c", "d"}, parser2.parse(args("a", "b", "c", "d")).getVariadic());

        final ArgsEvaler parser3 = new ArgsEvaler.ArgsEvalerBuilder()
                .addNamed("X")
                .addIndexed("A").addIndexed("B")
                .setMixingEachTypeIsAllowed(false)
                .build();

        resultMap = parser3.parse(args("X=x", "a", "b"));
        assertEquals("x", resultMap.getT("X"));
        assertEquals("a", resultMap.getT("A"));
        assertEquals("b", resultMap.getT("B"));
        resultMap = parser3.parse(args("a", "X=x", "b"));
        assertNull(resultMap.getT("X"));
        assertEquals("a", resultMap.getT("A"));
        assertEquals("X=x", resultMap.getT("B"));

        final ArgsEvaler parser4 = new ArgsEvaler.ArgsEvalerBuilder()
                .addTagged("-t")
                .addNamed("X")
                .addIndexed("A")
                .setNameEquatorSyllable("-Hello-")
                .setMixingEachTypeIsAllowed(false)
                .build();

        resultMap = parser4.parse(args("-t", "T", "X-Hello-x", "a"));
        assertEquals("T", resultMap.getT("-t"));
        assertEquals("x", resultMap.getT("X"));
        assertEquals("a", resultMap.getT("A"));
        resultMap = parser4.parse(args("-t", "T", "a", "X-Hello-x"));
        assertEquals("T", resultMap.getT("-t"));
        assertNull(resultMap.getT("X"));
        assertEquals("a", resultMap.getT("A"));

        final ArgsEvaler parser5 = new ArgsEvaler.ArgsEvalerBuilder()
                .addTagged("-t")
                .addNamed("X")
                .addNamed("Y")
                .addIndexed("A")
                .setEvaluationOrder(EvaluationOrder.NAMED, EvaluationOrder.TAGGED, EvaluationOrder.NAMED)
                .build();

        resultMap = parser5.parse(args("X=x", "-t", "T", "Y=y", "a"));
        System.out.println(resultMap);
        assertEquals("T", resultMap.getT("-t"));
        assertEquals("x", resultMap.getT("X"));
        assertEquals("y", resultMap.getT("Y"));
        assertEquals("a", resultMap.getT("A"));
    }

    private static String[] args(String... args) {
        return args;
    }
}