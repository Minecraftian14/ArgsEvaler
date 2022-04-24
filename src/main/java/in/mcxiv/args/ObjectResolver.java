package in.mcxiv.args;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

@FunctionalInterface
public interface ObjectResolver extends BiFunction<Class, String, Object> {

    class Default {

        public static final HashMap<Class<?>, ObjectResolver> RESOLVERS = new HashMap<>();

        static {
            RESOLVERS.put(boolean.class, (c, s) -> Boolean.parseBoolean(s));
            RESOLVERS.put(Boolean.class, (c, s) -> Boolean.parseBoolean(s));
            RESOLVERS.put(byte.class, (c, s) -> Byte.parseByte(s));
            RESOLVERS.put(Byte.class, (c, s) -> Byte.parseByte(s));
            RESOLVERS.put(char.class/* */, (c, s) -> s.charAt(0));
            RESOLVERS.put(Character.class, (c, s) -> s.charAt(0));
            RESOLVERS.put(short.class, (c, s) -> Short.parseShort(s));
            RESOLVERS.put(Short.class, (c, s) -> Short.parseShort(s));
            RESOLVERS.put(int.class/**/, (c, s) -> Integer.parseInt(s));
            RESOLVERS.put(Integer.class, (c, s) -> Integer.parseInt(s));
            RESOLVERS.put(float.class, (c, s) -> Float.parseFloat(s));
            RESOLVERS.put(Float.class, (c, s) -> Float.parseFloat(s));
            RESOLVERS.put(long.class, (c, s) -> Long.parseLong(s));
            RESOLVERS.put(Long.class, (c, s) -> Long.parseLong(s));
            RESOLVERS.put(double.class, (c, s) -> Double.parseDouble(s));
            RESOLVERS.put(Double.class, (c, s) -> Double.parseDouble(s));

            RESOLVERS.put(String.class, (c, s) -> s);
            RESOLVERS.put(StringBuilder.class, (c, s) -> new StringBuilder(s));
            RESOLVERS.put(StringBuffer.class, (c, s) -> new StringBuffer(s));

            RESOLVERS.put(BigInteger.class, (c, s) -> new BigInteger(s));
            RESOLVERS.put(BigDecimal.class, (c, s) -> new BigDecimal(s));
            RESOLVERS.put(AtomicInteger.class, (c, s) -> new AtomicInteger(Integer.parseInt(s)));
            RESOLVERS.put(AtomicLong.class, (c, s) -> new AtomicLong(Long.parseLong(s)));
            RESOLVERS.put(DoubleAdder.class, (c, s) -> {
                DoubleAdder adder = new DoubleAdder();
                adder.add(Double.parseDouble(s));
                return adder;
            });
            RESOLVERS.put(LongAdder.class, (c, s) -> {
                LongAdder adder = new LongAdder();
                adder.add(Long.parseLong(s));
                return adder;
            });

            RESOLVERS.put(File.class, (c, s) -> new File(s));
            RESOLVERS.put(Pattern.class, (c, s) -> Pattern.compile(s));
        }
    }

    @Override
    Object apply(Class objectClass, String s);

    default Object objectify(Class typeClass, String s) {
        return apply(typeClass, s);
    }

    default <ReType> ReType rectify(Class<ReType> typeClass, String s) {
        Object object = objectify(typeClass, s);
        return typeClass.cast(object);
    }

}
