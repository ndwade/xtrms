/*
 * @LICENSE@
 */

/**
 * <h3><b>xtrms</b> - A finite automata based Java regex package with adaptive
 * algorithm selection.</h3>
 * <p>
 * <h4>Motivation.</h4>
 * <p>
 * <b>xtrms</b> aims to bring finite automata (a.k.a. "state machine") based
 * pattern matching techniques - widely used in everything from CSV file
 * scanning to the "lexer" component of compilers - to developers familiar with
 * the "Perl style" expressions supported by the
 * {@linkplain java.util.regex regex} package. While not intended as a "drop in
 * replacement" for the standard package, the API strives to conform to the
 * standard package as much as possible.
 * <p>
 * The matching algorithm in <b>xtrms</b> (pronounced "extremis") is selected
 * based on the requirements of the regex pattern, resulting in the maximum
 * possible execution speed when matching.
 * <p>
 * This package is aimed at efficient matching of long lived or long running
 * regex patterns, and supports most of the syntax and API of the standard
 * java.util.regex package, plus extensions. Extensions to the standard API
 * currently include Stream input, named capturing groups and composable, named
 * sub expressions. Some regex features such as backreferences are not
 * supported.
 * <p>
 * The <b>xtrms</b> regex package makes a conscious tradeoff of compile time
 * for match time. The analysis of the regex pattern and the construction of the
 * finite automata used for matching can take longer than the "compilation"
 * implemented by the standard java.util.regex package. But for long lived (same
 * pattern used for a great many short strings) or long running (pattern may be
 * a one-off, but is matched against a very long Stream) - or both! - the use of
 * "extremis" is well motivated.
 * <p>
 * In addition, certain well known "pathalogical patterns" (link to java "bug"
 * here) which "hang" the standard regex package are exectuted extremely
 * efficiently using finite automata based packages such as <b>xtrms</b>. This
 * could be very important for certain applications - e.g. web or text editing
 * apps where the pattern is input by the end user.
 * <p>
 * <h4>Requirements based matching algorithm selection.</h4>
 * <p>
 * <b>xtrms</b> stands for "eXperimental Tagged Regex - Multiple Strategy". The
 * central architectural premis of <b>xtrms</b> is that "engines are cheap" -
 * the code which matches compiled regex patterns against input is relatively
 * short, and <b>xtrms</b> implements multiple "engines" which are selected
 * based on the requirements of the regex pattern. Patterns for which DFA based
 * Engines are appropriate will use them; otherwise an NFA based Engine is
 * selected.
 * <p>
 * Certain transformations are performed which attempt to eliminate the
 * requirement for (typically slower) NFA based Engines where that requirement
 * is driven by Boundary (^, $, \z, etc) matching. Capturing groups are
 * supported by the ("tagged") NFA based engine. A flag is provided to turn off
 * capturing groups for a pattern; for applications which require a long search
 * for a short pattern ("needle in a haystack"), it may make sense to construct
 * two patterns - one DFA based without capturing groups, and NFA based with the
 * capturing groups, and run the NFA based pattern on the results of the DFA
 * based pattern match in order to recover the capturing groups.
 * <p>
 * The engine framework is extensible, and more exotic engine implementations
 * (e.g. implementations which compile an automaton down to java byte code)
 * could be implemented. Currently, some regex features such as back references
 * are not supported. Also, a backtracking engine could be integrated, and the
 * full (Perl based) Java regex syntax - including backreferences - thereby
 * supported.
 * <p>
 * <h4>References and Acknowledgements:</h4>
 * <ul>
 * <li>Since this package is modeled on the standard Java regex package,
 * learning the basics of the standard package is a good place to start. See,
 * e.g., <a href="http://www.oreilly.com/catalog/regex2/">this tutorial</a> at
 * the O'Reilly site.
 * <li>For a comprehensive introduction to regular expressions from the point
 * of view of a programmer wishing to use them, see <a
 * href="http://www.oreilly.com/catalog/regex2/">Mastering Regular Expressions
 * by Jeffrey Freidl.</a></li>
 * <li>For an introduction to the theory behind regular expression and thier
 * implementation as automata, see the first chapters of the <a
 * href="http://en.wikipedia.org/wiki/Compilers:_Principles,_Techniques,_and_Tools">Dragon
 * Book.</a>
 * <li>I am in debt to Russ Cox, who wrote something of a <a
 * href="http://swtch.com/~rsc/regexp/regexp1.html">manifesto</a> for the use
 * of finite automata to imeplement regular expression matching. Russ explains
 * in detail the advantages of automaton based regular expression evaluation, as
 * compared to the ubiquitous backtracking implementations.</li>
 * <li>Ville Laurikari published algorithms and theory behind submatch
 * addressing (capturing groups) for automata based regular expresion
 * evaluation, for both <a
 * href="http://laurikari.net/ville/regex-submatch.pdf">NFA</a> and <a
 * href="http://laurikari.net/ville/spire2000-tnfa.pdf">DFA</a>. The NFA
 * algorithms are implemented in his popular 'C' language Posix regex library <a
 * href="http://laurikari.net/tre/">TRE</a>. The <b>xtrms</b> package extends
 * Laurikari's algorithms for constructing "tagged" NFA's from regular
 * expressions, but uses the algorithms of Russ Cox and Rob Pike to do the
 * actual matching.</li>
 * <li>For an alternative NFA/DFA based regular expression package in Java,
 * check out the <a href="http://www.brics.dk/automaton/">dk.brics.automaton</a>
 * package by Anders Møller. That package has a different focus; while not
 * offering features such as anchors and capturing groups, it implements other
 * features such as intersection and negation of regular expressions.
 * </ul>
 * <h4>Updates:</h4>
 * See the project <a href="http://code.google.com/p/xtrms/">web site</a>.
 * 
 * @author <a href="mailto:nicholas.d.wade@gmail.com">Nick Wade</a>, Panavista
 *         Technologies LLC.
 *         <p>
 */
package org.xtrms.regex;