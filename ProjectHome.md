**xtrms** (pronounced "extremis") aims to bring finite automata (a.k.a. "state machine") based pattern matching techniques to developers familiar with the "Perl style" expressions supported by the java.util.regex package.

The matching algorithm in **xtrms** is selected based on the requirements of the regex pattern, resulting in the maximum possible execution speed when matching.

This package is aimed at efficient matching of long lived or long running regex patterns. While not intended as a "drop in replacement" for the standard package, the API strives to conform to the standard package as much as possible.

Extensions to the standard API currently include Stream input, named capturing groups and composable, named sub expressions. Some regex features such as backreferences are not supported.

**xtrms** can be understood to mean "eXperimental Tagged Regex - Multi Strategy". (Or it could be understood as the cool-aspiring but contrived acronym that it is).

**Status**: alpha-ish maturity - see "featured download" at right for source, javadocs and pre-built jar. Site development is _in progress_...