package in.mcxiv.args;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The main class which parses the arguments.
 * <p>
 * Please see {@link ArgsEvalerBuilder} to create an instance.
 * Also take a note of {@link ArgumentTypes} to learn about the various types of arguments.
 *
 * @see ArgsEvalerBuilder
 * @see ArgumentTypes
 */
public class ArgsEvaler {
    private static final String VARIADIC_KEY = ArgsEvaler.class + ".VARIADIC_KEY";

    private final HashMap<Class<?>, ObjectResolver> RESOLVERS = new HashMap<>(ObjectResolver.Default.RESOLVERS);

    private final EvaluationOrder[] evaluationOrder;

    private final boolean requireAllIndexedArgsToBeFulfilled;
    private final boolean hasVariadicEnding;
    private final boolean mixingEachTypeIsAllowed;

    private final String nameEquatorSyllable;

    private final ArgsTriplet[] indexed;
    private final ArgsTriplet[] named;
    private final ArgsTriplet[] tagged;
    private final String[][][] chains; // words are basically length 1 chains.
    private final Object[][][] expressions;

    private ArgsEvaler(
            EvaluationOrder[] evaluationOrder,
            boolean requireAllIndexedArgsToBeFulfilled,
            boolean hasVariadicEnding,
            boolean mixingEachTypeIsAllowed,
            String nameEquatorSyllable,
            ArgsTriplet[] indexed,
            ArgsTriplet[] named,
            ArgsTriplet[] tagged,
            String[][][] chains,
            Object[][][] expressions) {

        this.evaluationOrder = evaluationOrder;

        this.requireAllIndexedArgsToBeFulfilled = requireAllIndexedArgsToBeFulfilled;
        this.hasVariadicEnding = hasVariadicEnding;
        this.mixingEachTypeIsAllowed = mixingEachTypeIsAllowed;
        this.nameEquatorSyllable = nameEquatorSyllable;
        this.indexed = indexed;
        this.named = named;
        this.tagged = tagged;
        this.chains = chains;
        this.expressions = expressions;
    }

    /**
     * Add a new Object Resolver.
     *
     * @param clazz    The output class of the resolver
     * @param resolver The Object Resolver
     * @see ObjectResolver
     */
    public void addResolver(Class<?> clazz, ObjectResolver resolver) {
        RESOLVERS.put(clazz, resolver);
    }

    /**
     * Parses the given array of arguments into a Map object.
     * The keys and value types are specified by the {@link ArgsEvalerBuilder} when
     * building an instance of {@link ArgsEvaler}.
     *
     * @param args The arguments to be parsed.
     * @return The Map object mapping the argument names to their values.
     */
    public ResultMap parse(String[] args) {
        LinkedList<String> list = new LinkedList<>();
        Collections.addAll(list, args);
        return parse(list, new ResultMap());
    }

    /**
     * Parses the given array of arguments into a Map object.
     * The keys and value types are specified by the {@link ArgsEvalerBuilder} when
     * building an instance of {@link ArgsEvaler}.
     *
     * @param args The arguments to be parsed.
     * @param map  Provide an existing instance of ResultMap for reusing.
     * @return The Map object mapping the argument names to their values.
     */
    public ResultMap parse(String[] args, ResultMap map) {
        if (map == null) return parse(args);
        LinkedList<String> list = new LinkedList<>();
        Collections.addAll(list, args);
        map.clear();
        return parse(list, map);
    }

    private ResultMap parse(List<String> args, ResultMap map) {


        for (EvaluationOrder order : evaluationOrder)
            switch (order) {
                case EXPRESSION:
                    parseExpressionArguments(args, map);
                    break;
                case CHAINED:
                    parseChainedArguments(args, map);
                    break;
                case TAGGED:
                    parseTaggedArguments(args, map);
                    break;
                case NAMED:
                    parseNamedArguments(args, map);
                    break;
            }

        parseIndexedArguments(args, map);

        if (hasVariadicEnding && args.size() > 0)
            parseVariadicArguments(args, map);

        return map;
    }

    private void parseExpressionArguments(List<String> args, ResultMap map) {

        for (int argsIdx = 0, argsS = args.size(); argsIdx < argsS; argsIdx++) {

            boolean wasAnArgFound = false;

            CHAIN_ITERATOR:
            for (Object[][] pair : expressions) {

                String name = (String) pair[0][0];
                Object[] expression = pair[1];

                Object[] objects = new Object[expression.length];

                if (expression.length > argsS - argsIdx) continue;

                for (int exprIdx = 0; exprIdx < expression.length; exprIdx++) {

                    String args_value = args.get(argsIdx + exprIdx);
                    Object expr = expression[exprIdx];

                    if (expr instanceof String) {
                        if (!Objects.equals(expr, args_value))
                            continue CHAIN_ITERATOR;

                    } else if (expr instanceof Class) {
                        if (!RESOLVERS.containsKey(expr))
                            continue CHAIN_ITERATOR;

                    } else {
                        Matcher matcher;
                        if (expr instanceof Pattern && (matcher = ((Pattern) expr).matcher(args_value)).matches()) {
                            if (matcher.groupCount() >= 1)
                                objects[exprIdx] = matcher.group(1);
                            else objects[exprIdx] = matcher.group();

                        } else if (expr instanceof StringPredicate && ((StringPredicate) expr).test(args_value)) {
                            objects[exprIdx] = args_value;

                        } else if (expr instanceof StringPredicateResolver && ((StringPredicateResolver) expr).test(args_value)) {
                            objects[exprIdx] = ((StringPredicateResolver) expr).apply(args_value);

                        } else if (expr instanceof StringPatternResolver && (matcher = ((StringPatternResolver) expr).pattern.matcher(args_value)).matches()) {
                            String match;
                            if (matcher.groupCount() >= 1)
                                match = matcher.group(1);
                            else match = matcher.group();
                            objects[exprIdx] = RESOLVERS.get(((StringPatternResolver) expr).clazz).objectify(((StringPatternResolver) expr).clazz, match);

                        } else
                            continue CHAIN_ITERATOR;
                    }
                }

                // If the expression didn't match completely, this part wont be ran.

                for (int exprIdx = 0; exprIdx < expression.length; exprIdx++) {

                    Object expr = expression[exprIdx];

                    if (expr instanceof String) {
                        objects[exprIdx] = expr;

                    } else if (expr instanceof Class) {
                        objects[exprIdx] = RESOLVERS.get(expr).objectify((Class) expr, args.get(argsIdx + exprIdx));

                    } else {
                        // There's nothing to do yet.
                    }
                }

                map.put(name, objects);

                for (Object ignored : expression)
                    args.remove(argsIdx);
                argsIdx -= expression.length;
                argsS -= expression.length;

                wasAnArgFound = true;

                break;
            }

            if (!mixingEachTypeIsAllowed)
                if (!wasAnArgFound)
                    break;
        }
    }

    private void parseChainedArguments(List<String> args, ResultMap map) {

        for (int argsIdx = 0, argsS = args.size(); argsIdx < argsS; argsIdx++) {

            boolean wasAnArgFound = false;

            CHAIN_ITERATOR:
            for (String[][] pair : chains) {

                String name = pair[0][0];
                String[] chain = pair[1];

                if (chain.length > argsS - argsIdx) continue;

                for (int chainIdx = 0; chainIdx < chain.length; chainIdx++)
                    if (!Objects.equals(chain[chainIdx], args.get(argsIdx + chainIdx)))
                        continue CHAIN_ITERATOR;

                // If the chain didn't match completely, this part wont be ran.

                map.put(name, chain);

                for (Object ignored : chain)
                    args.remove(argsIdx);
                argsIdx -= chain.length;
                argsS -= chain.length;

                wasAnArgFound = true;

                break;
            }

            if (!mixingEachTypeIsAllowed)
                if (!wasAnArgFound)
                    break;
        }
    }

    private void parseTaggedArguments(List<String> args, ResultMap map) {

        for (int argsIdx = 0, argsS = args.size(); argsIdx < argsS; argsIdx++) {

            String name = args.get(argsIdx);

            boolean wasAnArgFound = false;

            for (ArgsTriplet triplet : tagged) {


                if (Objects.equals(triplet.name, name)) {
                    map.put(triplet.name, RESOLVERS.get(triplet.clazz).objectify(triplet.clazz, args.get(argsIdx + 1)));

                    args.remove(argsIdx); // The name
                    args.remove(argsIdx); // The value
                    argsIdx -= 1; // TODO: test it
                    argsS -= 2;

                    wasAnArgFound = true;

                    break; // Skip to next argument

                }
            }

            if (!mixingEachTypeIsAllowed)
                if (!wasAnArgFound)
                    break;
        }
    }

    private void parseNamedArguments(List<String> args, ResultMap map) {

        for (int argsIdx = 0, argsS = args.size(); argsIdx < argsS; argsIdx++) {

            String pair = args.get(argsIdx);
            if (!pair.contains(nameEquatorSyllable)) {
                if (mixingEachTypeIsAllowed) continue;
                else break;
            }

            boolean wasAnArgFound = false;

            for (ArgsTriplet triplet : named) {
                if (pair.startsWith(triplet.name)) {

                    String[] strings = pair.split(nameEquatorSyllable, 2);

                    if (!strings[0].equals(triplet.name)) continue;

                    map.put(triplet.name, RESOLVERS.get(triplet.clazz).objectify(triplet.clazz, strings[1]));

                    args.remove(argsIdx);
                    argsIdx--;
                    argsS--;

                    wasAnArgFound = true;

                    break;
                }
            }

            if (!mixingEachTypeIsAllowed)
                if (!wasAnArgFound)
                    break;
        }
    }

    private void parseIndexedArguments(List<String> args, ResultMap map) {
        if (requireAllIndexedArgsToBeFulfilled && indexed.length > args.size())
            throw new IllegalArgumentException("Too few indexed arguments.");

        for (int i = 0, s = Math.min(indexed.length, args.size()); i < s; i++)
            map.put(indexed[i].name, RESOLVERS.get(indexed[i].clazz).objectify(indexed[i].clazz, args.get(i)));

        for (int i = 0, s = Math.min(indexed.length, args.size()); i < s; i++)
            args.remove(0);
    }

    private void parseVariadicArguments(List<String> args, ResultMap map) {
        map.put(VARIADIC_KEY, args.toArray(new String[0]));
    }

    /**
     * A list of all the various types of arguments which {@link ArgsEvaler} can parse.
     * Note that this list does not contain Indexed Arguments and Variadic Arguments as
     * they can only exist at the end of the execution order.
     */
    public enum EvaluationOrder {
        /**
         * @see ArgumentTypes#NAMED
         */
        NAMED,
        /**
         * @see ArgumentTypes#TAGGED
         */
        TAGGED,
        /**
         * @see ArgumentTypes#CHAINED
         */
        CHAINED,
        /**
         * @see ArgumentTypes#EXPRESSION
         */
        EXPRESSION
    }

    /**
     * A class illustrating the various types of arguments which are supported.
     */
    public enum ArgumentTypes {
        /**
         * A simple array or strings passed as arguments which are differentiated only
         * using the order in which they are placed are called Indexed Arguments.
         */
        INDEXED,
        /**
         * If a string is a composition of the form "somekey=somevalue", it is called a
         * Named Argument. They can be placed in order, and are solely identified using
         * the former part (somekey).
         * <p>
         * Note, the symbol used for equation can be changed as required.
         *
         * @see ArgsEvalerBuilder#setNameEquatorSyllable(String)
         */
        NAMED,
        /**
         * Is an argument is identified using another string occurring right before it, it
         * is called a Tagged Argument.
         * <p>
         * For instance, {@code gcc my_app.c -o my_app.exe}, here my_app.exe is identified as
         * the output file because there's a tag -o right before it.
         */
        TAGGED,
        /**
         * A Word Argument is a string of interest which may be placed anywhere in the provided
         * arguments (except as parts of expression or chains!).
         */
        WORD,
        /**
         * A chain is a series of specific strings occurring in an ordered fashion.
         * Chain Arguments are used to capture these strings separately.
         */
        CHAINED,
        /**
         * An extension of chained arguments, except that here an element can be passed through
         * a conditional check before accepting it as an argument. Further, it can optionally be
         * transformed into another object using resolvers.
         */
        EXPRESSION,
        /**
         * In the context of {@link ArgsEvaler} these are just the remaining unparsed arguments
         * left at the end of the args array.
         * <p>
         * If the args must contain an array of indefinite size, they are called Variadic Arguments.
         */
        VARIADIC
    }

    private static class ArgsTriplet {
        private final String name;
        private final Class<?> clazz;

        public ArgsTriplet(String name, Class<?> clazz) {
            this.name = name;
            this.clazz = clazz;
        }
    }

    /**
     * A utility class used in Expression Arguments.
     * <p>
     * Implement this interface to specify a condition for the argument value to satisfy before
     * becoming a part of the expression.
     *
     * @see ArgsEvaler#predicate(StringPredicate)
     */
    @FunctionalInterface
    public interface StringPredicate extends Predicate<String> {
    }

    /**
     * A utility method to neatly create instances of {@link StringPredicate} when using Expression Arguments.
     *
     * @param predicate The predicate, created using lambda.
     * @return The same predicate.
     * @see StringPredicate
     */
    public static StringPredicate predicate(StringPredicate predicate) {
        return predicate;
    }

    /**
     * A utility class used in Expression Arguments.
     * <p>
     * Implement this interface to specify a condition for the argument value to satisfy before
     * becoming a part of the expression along with an optional transformation as a preprocessing step.
     *
     * @see ArgsEvaler#predicate(StringPredicate)
     */
    public interface StringPredicateResolver extends Predicate<String>, Function<String, Object> {
    }

    /**
     * A utility method to neatly create instances of {@link StringPredicateResolver} when using Expression Arguments.
     *
     * @param predicate The condition an argument must satisfy before becoming a part of Expression Argument.
     * @param resolver  A resolver to transform the argument into a suitable object.
     * @return An instance of {@link StringPredicateResolver}.
     * @see StringPredicateResolver
     */
    public static StringPredicateResolver resolve(Predicate<String> predicate, Function<String, Object> resolver) {
        return new StringPredicateResolver() {
            @Override
            public boolean test(String s) {
                return predicate.test(s);
            }

            @Override
            public Object apply(String s) {
                return resolver.apply(s);
            }
        };
    }

    /**
     * A utility class used in Expression Arguments.
     * <p>
     * Implement this interface to specify a pattern which the argument value must match before
     * becoming a part of the expression along with the class of the final type, where it should be resolved into.
     *
     * @see ArgsEvaler#pattern(Pattern, Class)
     * @see ObjectResolver
     * @see ArgsEvaler#addResolver(Class, ObjectResolver)
     * @see ArgsEvalerBuilder#addResolver(Class, ObjectResolver)
     */
    public static class StringPatternResolver {

        private final Pattern pattern;
        private final Class<?> clazz;

        public StringPatternResolver(Pattern pattern, Class<?> clazz) {
            this.pattern = pattern;
            this.clazz = clazz;
        }
    }


    /**
     * A utility method to neatly create instances of {@link StringPatternResolver} when using Expression Arguments.
     *
     * @param pattern The pattern an argument must satisfy before becoming a part of Expression Argument.
     * @param clazz   The class type which the argument should be resolved into.
     * @return An instance of {@link StringPatternResolver}.
     * @see StringPatternResolver
     */
    public static StringPatternResolver pattern(Pattern pattern, Class<?> clazz) {
        return new StringPatternResolver(pattern, clazz);
    }

    /**
     * ResultMap is a utility class which casts the values to required return type as required.
     * It represents a collection of the parsed arguments, a mapping from their name to their value.
     */
    public static class ResultMap extends AbstractMap<String, Object> {

        private final HashMap<String, Object> map;

        private ResultMap() {
            map = new HashMap<>();
        }

        @Override
        public Object put(String key, Object value) {
            return map.put(key, value);
        }

        @Override
        public Object get(Object key) {
            return map.get(key);
        }

        /**
         * If the object required is available, it is returned.
         * If the object is null or if the key is not present, the default value supplied is returned instead.
         *
         * @param key The key of the object required.
         * @param def The default value to be used in place.
         * @return The object required or the default values supplied.
         */
        public Object get(Object key, Object def) {
            Object o = get(key);
            return o != null ? o : def;
        }

        /**
         * If the object required is available, it is returned.
         * If the object is null or if the key is not present, the default supplier is invoked
         * to yield a default value.
         *
         * @param key The key of the object required.
         * @param def A supplier for the default value to be used in place.
         * @return The object required or the default values supplied.
         */
        public Object get(Object key, Supplier<Object> def) {
            Object o = get(key);
            return o != null ? o : def.get();
        }

        /**
         * @see ResultMap#get(Object)
         */
        public <ReType> ReType getT(String name, Class<ReType> clazz) {
            return clazz.cast(get(name));
        }

        /**
         * @see ResultMap#get(Object, Object)
         */
        public <ReType> ReType getT(String name, Class<ReType> clazz, ReType def) {
            return clazz.cast(get(name, def));
        }

        /**
         * @see ResultMap#get(Object, Supplier)
         */
        public <ReType> ReType getT(String name, Class<ReType> clazz, Supplier<ReType> def) {
            return clazz.cast(get(name, def));
        }

        /**
         * @see ResultMap#get(Object)
         */
        @SuppressWarnings("unchecked")
        public <ReType> ReType getT(String name) {
            Object o = get(name);
            if (o == null) return null;
            return (ReType) o;
        }

        /**
         * @see ResultMap#get(Object, Object)
         */
        public <ReType> ReType getT(String name, ReType def) {
            ReType o = getT(name);
            return o != null ? o : def;
        }

        /**
         * @see ResultMap#get(Object, Supplier)
         */
        public <ReType> ReType getT(String name, Supplier<ReType> def) {
            ReType o = getT(name);
            return o != null ? o : def.get();
        }

        /**
         * Returns an Optional of the required value.
         *
         * @see ResultMap#get(Object)
         */
        public Optional<Object> getOpt(String name) {
            return Optional.ofNullable(get(name));
        }

        /**
         * Returns an Optional of the required value.
         *
         * @see ResultMap#get(Object, Object)
         */
        public Optional<Object> getOpt(String name, Object def) {
            return Optional.of(get(name, def));
        }

        /**
         * Returns an Optional of the required value.
         *
         * @see ResultMap#get(Object, Supplier)
         */
        public Optional<Object> getOpt(String name, Supplier<Object> def) {
            return Optional.of(get(name, def));
        }

        /**
         * @see ResultMap#getOpt(String)
         */
        public <ReType> Optional<ReType> getOptT(String name, Class<ReType> clazz) {
            return getOpt(name).map(clazz::cast);
        }

        /**
         * @see ResultMap#getOpt(String, Object)
         */
        public <ReType> Optional<ReType> getOptT(String name, Class<ReType> clazz, ReType def) {
            return getOpt(name, def).map(clazz::cast);
        }

        /**
         * @see ResultMap#getOpt(String, Supplier)
         */
        public <ReType> Optional<ReType> getOptT(String name, Class<ReType> clazz, Supplier<ReType> def) {
            return getOpt(name, def).map(clazz::cast);
        }

        /**
         * @see ResultMap#getOpt(String)
         */
        public <ReType> Optional<ReType> getOptT(String name) {
            return Optional.ofNullable(getT(name));
        }

        /**
         * @see ResultMap#getOpt(String, Object)
         */
        public <ReType> Optional<ReType> getOptT(String name, ReType def) {
            ReType o = getT(name);
            return Optional.of(o != null ? o : def);
        }

        /**
         * @see ResultMap#getOpt(String, Supplier)
         */
        public <ReType> Optional<ReType> getOptT(String name, Supplier<ReType> def) {
            ReType o = getT(name);
            return Optional.of(o != null ? o : def.get());
        }

        /**
         * To get the array of strings which represent the Variadic Arguments if enabled.
         *
         * @return the variadic arguments.
         * @see ArgumentTypes#VARIADIC
         */
        public String[] getVariadic() {
            return getT(VARIADIC_KEY);
        }

        /**
         * Runs the provided call back in case the value represented by the given key
         * exists (is non-null).
         *
         * @param name        The key
         * @param thenRunThis The call back.
         */
        public void ifPresent(String name, Consumer<Object> thenRunThis) {
            if (map.containsKey(name))
                thenRunThis.accept(get(name));
        }

        /**
         * Runs the provided call back in case the value represented by the given key
         * exists (is non-null). Runs the second call back instead, if the value was
         * null.
         *
         * @param name        The key
         * @param thenRunThis The success call back.
         * @param elseRunThis The failure call back.
         */
        public void ifPresent(String name, Consumer<Object> thenRunThis, Runnable elseRunThis) {
            if (map.containsKey(name))
                thenRunThis.accept(get(name));
            else elseRunThis.run();
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return map.entrySet();
        }

    }

    /**
     * A utility class to help creating a {@link ArgsEvaler}.
     *
     * @see ArgsEvaler
     * @see ArgumentTypes
     */
    public static class ArgsEvalerBuilder {

        private final List<EvaluationOrder> evaluationOrder = new ArrayList<>();

        {
            evaluationOrder.add(EvaluationOrder.EXPRESSION);
            evaluationOrder.add(EvaluationOrder.CHAINED);
            evaluationOrder.add(EvaluationOrder.TAGGED);
            evaluationOrder.add(EvaluationOrder.NAMED);
        }

        private boolean requireAllIndexedArgsToBeFulfilled = false;
        private boolean hasVariadicEnding = false;
        private boolean mixingEachTypeIsAllowed = true;

        private String nameEquatorSyllable = "=";

        private final List<ArgsTriplet> indexed = new ArrayList<>();
        private final List<ArgsTriplet> named = new ArrayList<>();
        private final List<ArgsTriplet> tagged = new ArrayList<>();
        private final List<String[][]> chains = new ArrayList<>();
        private final List<Object[][]> expressions = new ArrayList<>();

        private final HashMap<Class<?>, ObjectResolver> objectResolvers = new HashMap<>();

        /**
         * Define a new execution order of parsing of the various types of arguments.
         * Note, that Indexed and Variadic always lie at the end.
         * <p>
         * Use this function to reorder or remove unneeded components of the parser.
         * <p>
         * The default execution order is:
         * <ul>
         *     <li>Expression</li>
         *     <li>Chains or Words</li>
         *     <li>Tagged</li>
         *     <li>Named</li>
         * </ul>
         *
         * @param orders The new execution order.
         * @return this, for Fluent API
         * @see ArgumentTypes
         */
        public ArgsEvalerBuilder setEvaluationOrder(EvaluationOrder... orders) {
            setMixingEachTypeIsAllowed(false);
            evaluationOrder.clear();
            evaluationOrder.addAll(Arrays.asList(orders));
            return this;
        }

        /**
         * Sets the parser to allow parsing less values than provided for Indexed arguments.
         * <p>
         * By default, it's set to false.
         *
         * @param requireAllIndexedArgsToBeFulfilled should the parser parse all indexed arguments?
         * @return this, for Fluent API
         */
        public ArgsEvalerBuilder setRequireAllIndexedArgsToBeFulfilled(boolean requireAllIndexedArgsToBeFulfilled) {
            this.requireAllIndexedArgsToBeFulfilled = requireAllIndexedArgsToBeFulfilled;
            return this;
        }

        /**
         * Sets the parser to also parse Variadic Arguments.
         * <p>
         * Variadic Arguments are basically the remaining arguments which were left unaffected by the parser.
         * By default, it is disabled. Enable it to parse indefinite lengths of possible arguments.
         *
         * @param hasVariadicEnding Should the parser parse variadic arguments.
         * @return this, for Fluent API
         */
        public ArgsEvalerBuilder setHasVariadicEnding(boolean hasVariadicEnding) {
            this.hasVariadicEnding = hasVariadicEnding;
            return this;
        }

        /**
         * Sets the parser to allow mixing the different types of arguments.
         * <p>
         * When it's allowed, {@code "name=value", "-tag", "tagged_value"} will be parsed into one tagged value
         * and one named value. But if it's not allowed, it will not see any tagged values after named values.
         * So it will only parse one named value (and 2 variadic values if enabled).
         *
         * <b>Dont use it along with a custom execution order.</b>
         *
         * @param mixingEachTypeIsAllowed Should the parser allow mixing of the types?
         * @return this, for Fluent API
         */
        public ArgsEvalerBuilder setMixingEachTypeIsAllowed(boolean mixingEachTypeIsAllowed) {
            this.mixingEachTypeIsAllowed = mixingEachTypeIsAllowed;
            return this;
        }

        /**
         * Changes the equation symbol as used in named arguments.
         * <p>
         * The default value is '='.
         *
         * @param nameEquatorSyllable The new string or character.
         * @return this, for Fluent API
         */
        public ArgsEvalerBuilder setNameEquatorSyllable(String nameEquatorSyllable) {
            this.nameEquatorSyllable = nameEquatorSyllable;
            return this;
        }

        /**
         * Adds a new Indexed Argument with the given name and the default value type, String.class.
         *
         * @param name The name of this indexed argument.
         * @return this, for Fluent API
         */
        public ArgsEvalerBuilder addIndexed(String name) {
            return addIndexed(name, String.class);
        }

        /**
         * Adds a new Indexed Argument with the given name and the given value type.
         * <p>
         * Note, if the given class is not one of the pre-defined resolvers, the one
         * should add a new Obhect Resolver using {@link ArgsEvalerBuilder#addResolver(Class, ObjectResolver)}
         * before creation or {@link ArgsEvaler#addResolver(Class, ObjectResolver)} after creation.
         *
         * @param name  The name of this indexed argument.
         * @param clazz The class type of this indexed argument.
         * @return this, for Fluent API
         * @see ObjectResolver
         */
        public ArgsEvalerBuilder addIndexed(String name, Class<?> clazz) {
            return addTo(indexed, name, clazz);
        }

        /**
         * Adds a new Named Argument with the given name and the default value type, String.class.
         *
         * @param name The name of this named argument.
         * @return this, for Fluent API
         */
        public ArgsEvalerBuilder addNamed(String name) {
            return addNamed(name, String.class);
        }

        /**
         * Adds a new Named Argument with the given name and the given value type.
         *
         * @param name  The name of this named argument.
         * @param clazz The class type of this named argument.
         * @return this, for Fluent API
         */
        public ArgsEvalerBuilder addNamed(String name, Class<?> clazz) {
            return addTo(named, name, clazz);
        }

        /**
         * Adds a new Tagged Argument with the given name and the default value type, String.class.
         *
         * @param name The name of this tagged argument.
         * @return this, for Fluent API
         */
        public ArgsEvalerBuilder addTagged(String name) {
            return addTagged(name, String.class);
        }

        /**
         * Adds a new Tagged Argument with the given name and the given value type.
         *
         * @param name  The name of this tagged argument.
         * @param clazz The class type of this tagged argument.
         * @return this, for Fluent API
         */
        public ArgsEvalerBuilder addTagged(String name, Class<?> clazz) {
            return addTo(tagged, name, clazz);
        }

        private ArgsEvalerBuilder addTo(List<ArgsTriplet> list, String name, Class<?> clazz) {
            list.add(new ArgsTriplet(name, clazz));
            return this;
        }

        /**
         * Add a new object resolver, it's used to simply String value to the given Object types when a enpression,
         * named, tagged or indexed argument is received.
         *
         * @param clazz          The class type that this new resolver returns.
         * @param objectResolver The new resolver.
         * @return this, for Fluent API
         * @see ArgsEvaler#addResolver(Class, ObjectResolver)
         */
        public ArgsEvalerBuilder addResolver(Class<?> clazz, ObjectResolver objectResolver) {
            objectResolvers.put(clazz, objectResolver);
            return this;
        }

        /**
         * Adds a new Word Argument with the given name.
         *
         * @param word The name and value of this word argument.
         * @return this, for Fluent API
         */
        public ArgsEvalerBuilder addWord(String word) {
            return addWord(word, word);
        }

        /**
         * Adds a new Word Argument with the given name and value.
         *
         * @param name The name of this word argument.
         * @param word The value of this word argument.
         * @return this, for Fluent API
         */
        public ArgsEvalerBuilder addWord(String name, String word) {
            return addChain(name, word);
        }

        /**
         * Adds a new Chain Argument with the given name and value.
         *
         * @param name  The name of this chain argument.
         * @param words The value of this chain argument.
         * @return this, for Fluent API
         */
        public ArgsEvalerBuilder addChain(String name, String... words) {
            if (words.length < 1)
                throw new IllegalArgumentException("The chain must have at least 1 element.");
            chains.add(new String[][]{{name}, words});
            return this;
        }

        /**
         * Adds a new Expression Argument with the given name and value.
         *
         * @param name       The name of this chain argument.
         * @param expression The value of this chain argument.
         * @return this, for Fluent API
         */
        public ArgsEvalerBuilder addExpression(String name, Object... expression) {
            if (expression.length < 1)
                throw new IllegalArgumentException("The expression must have at least 1 element.");
            expressions.add(new Object[][]{{name}, expression});
            return this;
        }

        /**
         * Creats a {@link ArgsEvaler} instance with the configured values.
         *
         * @return An instance of {@link ArgsEvaler}.
         */
        public ArgsEvaler build() {
            ArgsEvaler argsEvaler = new ArgsEvaler(
                    evaluationOrder.toArray(new EvaluationOrder[0]),
                    requireAllIndexedArgsToBeFulfilled,
                    hasVariadicEnding,
                    mixingEachTypeIsAllowed,
                    nameEquatorSyllable,
                    indexed.toArray(new ArgsTriplet[0]),
                    named.toArray(new ArgsTriplet[0]),
                    tagged.toArray(new ArgsTriplet[0]),
                    chains.toArray(new String[0][][]),
                    expressions.toArray(new Object[0][][]));
            objectResolvers.forEach(argsEvaler::addResolver);
            return argsEvaler;
        }
    }
}
