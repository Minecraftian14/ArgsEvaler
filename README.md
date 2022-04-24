## Args Utilities

[![](https://jitpack.io/v/Minecraftian14/ArgsEvaler.svg)](https://jitpack.io/#in.mcxiv/ArgsEvaler)
[![](https://img.shields.io/discord/872811194170347520?color=%237289da&logoColor=%23424549)](https://discord.gg/Ar6Zuj2m82)

Parse the arguments received in an application to a Map object.

#### importing in your project

```groovy
repositories {
    // .. other repositories
    maven { url 'https://jitpack.io/#in.mcxiv' }
}

dependencies {
    implementation 'in.mcxiv:ArgsEvaler:0.1'
}
```

### Creating a parser

```groovy
ArgsEvaler parser = new ArgsEvaler.ArgsEvalerBuilder()
        .build();
```

### Adding Indexed Arguments

These arguments are picked up according to their order of appearance.

If one defines the names as "a", "b" and "c"; then no matter what, the first three arguments received will be mapped to
first "a", then "b" and finally "c".

Input arguments: `Hello World !`
<br>
Referencing: `{a="Hello", b="World", c="!"}`

Input arguments: `! World Hello`
<br>
Referencing: `{a="!", b="World", c="Hello"}`

```groovy
ArgsEvaler parser = new ArgsEvaler.ArgsEvalerBuilder()
        .addIndexed("name_a")
        .addIndexed("name_b")
        .addIndexed("name_c")
        .build();
```

### Adding Named Arguments

To parse arguments which are equated with an `=` character and may occur in any order, use the named arguments.

If the names are defined as "a", "b" and "c":
<br>
Input arguments: `c=! a=World b=Hello`
<br>
Referencing: `{a="World", b="Hello", c="!"}`

```groovy
ArgsEvaler parser = new ArgsEvaler.ArgsEvalerBuilder()
        .addNamed("name_a")
        .addNamed("name_b")
        .addNamed("name_b")
        .build();
```

### Adding Tagged Arguments

To parse arguments which are followed by a tag/word before them we use tagged arguments. Note, the tag can be placed
anywhere, and will parse the word right after it, so these arguments can also occur in any order.

If the tags are defined as "-a", "-b" and "--cat":
<br>
Input arguments: `-b Hello -a World --cat !`
<br>
Referencing: `{-a="World", -b="Hello", --cat="!"}`

```groovy
ArgsEvaler parser = new ArgsEvaler.ArgsEvalerBuilder()
        .addTagged("tag_a")
        .addTagged("tag_b")
        .addTagged("tag_b")
        .build();
```

### Adding Word Argument

To simply verify if a specific keyword was passed along with the arguments one can add word arguments. The words can be
placed anywhere, they are not indexed. So these arguments can also occur in any order.

If the words are defined as "java" and "args":
<br>
Input arguments: `Hello java world, args`
<br>
Referencing: `{java="java", args="args", other args}`

```groovy
ArgsEvaler parser = new ArgsEvaler.ArgsEvalerBuilder()
        .addWord("word_a")
        .addWord("word_b")
        .build();
```

### Adding Chained Arguments

When we have to parse a specific order or certain words occurring in an args array, we can proceed to use chain
arguments. These may occur anywhere in the args array, but all words must occur one after another.

Note, by default expression and chained arguments are parsed first, but if the order is changed, other parsers may
extract words occurring inside the chains, and thus cause an unexpected behaviour.

If a chain is defined as "the_chain" = "This", "is", "a", "chain":
<br>
Input arguments: `This is a book This is a chain`
<br>
Referencing: `{the_chain={"This", "is", "a", "chain", other args}`

```groovy
ArgsEvaler parser = new ArgsEvaler.ArgsEvalerBuilder()
        .addChain("name_of_chain", "chain_element_1", "chain_element_2", ...)
        .build();
```

### Adding Expression Arguments

Much similar to chains, expressions takes the complexity a step further. While chain is limited to just parsing a
specific occurrence of words, expression can take help of regex patterns or string predicates to determine a suitable
word. Similar to chains, these may occur anywhere in the args array.

Currently, one can provide the following data for parsing:

* a String - to match the exact word
* a Class - to let Object Resolvers do their duty.
* a Pattern with no groups - to check if the value must match this pattern.
* a Pattern with at least one capturing group - to check if the value must match this pattern and store the value as the
  group 1 match.
* a StringPredicate - to implement custom logic analyzing a string, to keep or not.
* a StringPredResolver - to implement custom logic analyzing a string, and keep it's transformed value if it's valid.

If an expression is defined as "an_expr" = "word", int.class, Pattern.compile("^\\w{5}$"):
<br>
Input arguments: `word 19 fg12h`
<br>
Referencing: `{an_expr={"word", 19, "fg12h"}`

```groovy
ArgsEvaler parser = new ArgsEvaler.ArgsEvalerBuilder()
        .addExpression("expression_name", object_1, object_2, ...)
        .build();
```

### Parsing Variadic Arguments

One may require an array of indefinite length at the end of arguments.

To parse the remaining arguments at the end of the args array, enable this property in ArgsEvalerBuilder.

These may exist only at the end of the array.

```groovy
ArgsEvaler parser = new ArgsEvaler.ArgsEvalerBuilder()
        .setRequireAllIndexedArgsToBeFulfilled(true)
        .build();
```

### Other flags of ArgsEvalerBuilder

* `.setRequireAllIndexedArgsToBeFulfilled(#boolean)`
    * Make the parse require all indexed arguments be fulfilled.
* `.setHasVariadicEnding(#boolean)`
    * Enable/Disable Variadic Arguments.
* `.setMixingEachTypeIsAllowed(#boolean)`
    * Allow/Disallow mixed placement of arguments.
* `.setNameEquatorSyllable(#String)`
    * Alter the string which is used as the equating symbol in Named Arguments.

### Redefining Evaluation order

One can make the parser evaluate the different kinds of arguments in a specific order, or simply ignore a few specific
types. Note that one must not set mixing allowed when creating a new order {`setMixingEachTypeIsAllowed(true)`}.

Indexed and Variadic arguments always lie at the end of evaluation order, so the only configurable types are Named,
Expression, Tagged and Chain.

The default order of execution is Expression>Chain>Tagged>Named>Indexed>?Variadic.

```groovy
ArgsEvaler parser = new ArgsEvaler.ArgsEvalerBuilder()
        .setEvaluationOrder(evaluationOrder1, evaluationOrder2, ...)
        .build();
```

```groovy
ArgsEvaler parser = new ArgsEvaler.ArgsEvalerBuilder()
        .setEvaluationOrder(EvaluationOrder.NAMED, EvaluationOrder.TAGGED)
        .build();
```

### Parsing Arguments and Retrieving Values

[//]: # (@formatter:off)
```groovy
var map = parser.parse(args);
Object object = map.get("name_a");
String string = (String) map.get("name_a");
int num_a = map.getT("some_int_a");
int num_b = map.getT("some_int_c", 1114);

// Get an Optional which can be empty
Optional<Object> opt_a = map.getOpt("some_int_a")
// Get an Optional with a non-null value (default)
Optional<Object> opt_c = map.getOpt("some_int_c", 1114)
```
[//]: # (@formatter:on)

### Specifying Data Types

To parse stuff directly to primitive types like `int`, we can specify it's class type.

```groovy
ArgsEvaler parser = new ArgsEvaler.ArgsEvalerBuilder()
        .addIndexed("num_a", long.class)
        .build();
```

#### Supported Types

```
// Primitive types, both boxed and unboxed. 
boolean.class
byte.class
char.class
short.class
int.class
float.class
long.class
double.class

// Other types
String.class
StringBuilder.class
StringBuffer.class

BigInteger.class
BigDecimal.class
AtomicInteger.class
AtomicLong.class
DoubleAdder.class
LongAdder.class

File.class
Pattern.class
```

#### Adding custom types

Use the add resolver to add parsers for custom types.

```groovy
ArgsEvaler parser = new ArgsEvaler.ArgsEvalerBuilder()
        .addResolver(ByteBuffer.class, (c, s) -> ByteBuffer.wrap(s.getBytes()))
        .build();
```
