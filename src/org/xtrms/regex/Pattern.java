/*
 * @LICENSE@
 */

package org.xtrms.regex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xtrms.regex.AST.Node;
import org.xtrms.regex.EngineStyle.ConstructionException;
import org.xtrms.regex.Misc.FlagMgr;

/**
 * A compiled representation of a regular expression; analog to the
 * {@link java.util.regex.Pattern} class. Like the Pattern class of the standard
 * library, intances of this Pattern class are immutable and thread safe. The
 * regex syntax accepted by this Pattern class is limited compared to that
 * accepted by the standard Pattern class. Differences with the standard package
 * are highighted below.
 * <p>
 * <strong>Unicode support:</strong> This class <em>intended</em> to be in
 * conformance with Level 1 of <a
 * href="http://www.unicode.org/reports/tr18/">Unicode Technical Standard #18:
 * Unicode Regular Expression Guidelines</a>. Compared to the Java regex
 * library, there is no support for the RL2.1 Canonical Equivalents, nor support
 * for code points outside the Basic Multilingual Plane (in other words,
 * <code>char</code>s only.) (In plain English: basically the whole package
 * works on raw <code>char</code>s; this may change soon).
 * <p>
 * <strong>Character escape codes</strong>: All the specified codes, such as
 * \t, \n, unicode escapes sequences (\\uXXXX), etc, work as they do in the
 * standard package.
 * <p>
 * <strong>Character classes:</strong> All the features of the standard
 * package, including negation, ranges, union, interesection and subtraction,
 * are supported with the standard syntax. Note, however, that there is a <a
 * href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6609854">bug</a> in
 * the standard package parsing nested char classes when top level char class is
 * negated, which has been "fixed" here.
 * <p>
 * <strong>Predefined character classes:</strong> The simple predefined
 * classes, as well as POSIX and java.lang.Character classes, and classes for
 * Unicode blocks and categories, are all supported with the same syntax as the
 * standard package.
 * <p>
 * <strong>Boundary matchers:</strong> The standard Boundary matchers
 * <code>(^, \A,
 * \G, \b, \B, $, \Z, \z)</code> are all supported.
 * <p>
 * <strong>Quantifiers:</strong> Greedy quantifiers are supported, including
 * bounded quantifiers (<code>a{3,5}</code> etc). Reluctant quantifiers are
 * supported, including bounded reluctant quantifiers (e.g. (<code>a{3,5}?</code>).
 * Possessive quantifiers are <em>not</em> supported.
 * <p>
 * <strong>Logical Operators</strong>, including capturing groups, are
 * supported.
 * <p>
 * <strong>Back References</strong> are <em>not</em> supported.
 * <p>
 * <strong>Quotation</strong> is supported.
 * <p>
 * <strong>Special constructs:</strong> The construct for a non-capturing group
 * is supported - <code>(?:X)</code> matches <code>X</code> as a
 * non-capturing group. No other special constructs are currently supported.
 * <p>
 * <strong>Additional features:</strong>
 * <ul>
 * <li>Named capturing groups, using the syntax of the extensions slated for
 * inclusion in Java 1.7</li>
 * <li>Named sub-expressions, parsed and encapsulated via the
 * {@link Expression} class, and passed into the Pattern factory methods as
 * varargs.</li>
 * <li><a href="http://en.wiktionary.org/wiki/eponymous">Eponymous</a>
 * sub-expression capture. When named sub-expression is captured as a named
 * capturing group, and it is desired that the name of the capturing group be
 * the same as the name of the sub-expression, the following syntax may be used
 * for a sub expression "foo": {@code (?<><foo>)}.
 * <li>Hierarchical capture group names: named capture groups which are part of
 * named sub expressions pick up their sub-expression name as a prefix. For
 * example, a capturing group "bar", part of a sub-expression named "foo", will
 * be accessed as "foo.bar" at the top level. This is the default behavior,
 * intended to reduce name collisions, and may be overridden with the
 * {@link Pattern#X_FLAT_CG_NAMES} flag.
 * </ul>
 * See the documentation for the {@link Expression} class</li>
 * for more examples of the interaction between named sub-expressions and named
 * capturing groups.
 */
public final class Pattern {

    private static final Logger logger = Logger.getLogger("org.xtrms.regex");
    private static final Level level = Level.FINEST;

    /**
     * Enumeration of characteristics of {@linkplain Pattern}s which determine
     * the selection of a matching algorithm, as represented by the
     * {@link EngineStyle} class. Each Pattern object fills a set of
     * {@linkplain Pattern#requirements() requirements} with these enumeration
     * constants, and a matching algorithm is selected based on the
     * {@linkplain EngineStyle#capabilities() capabilities} of the algorithm.
     */
    public enum Feature {
        /**
         * Indicates the presence of capturing parenthesis (capturing groups) in
         * the regular expression. The {@link Matcher#group(int) group(0)} group
         * does not consitute a capturing group with respect to this element.
         */
        CAPTURING_GROUPS,
        /**
         * Indicates the presence of a at least one of {\A, \G, ^, \b, \B, \z,
         * \Z, $} in the regex parameter to the
         * {@link Pattern#compile(String, Expression...)} method. Matching
         * algorithms which support capturing groups can be slower than those
         * which do not.
         */
        DYNAMIC_BOUNDARIES,
        /**
         * Like {@link #DYNAMIC_BOUNDARIES}, but where the boundaries are
         * confined to the spot before the first position of a match. Regex's
         * with boundaries confined to this spot can take advantage of certain
         * static optimizations which allow certain classes of fast matching
         * algorithms to be used.
         */
        LOOP_DBC,
        /**
         * Indicates the presence of reluctant quantifiers (e.g. ([a-z])*?).
         * Currently all matching algorthms which can support capturing groups
         * also support reluctant qualifiers, although this may change in future
         * releases.
         */
        RELUCTANT_QUANTIFIERS,
        /**
         * Indicates the presense of possessive quantifiers (e.g. ([a-z]*+).
         * Currently, no matching algorithm supports possessive quantifiers,
         * although this may change in a future release.
         */
        POSSESSIVE_QUANTIFIERS,
        /**
         * An enumerated capability, indicates a matching algorithm can directly
         * execute the loop implicit in the {@link Matcher#find()} method
         * without iteration external to the matching algorithm. This constant
         * is used only as a {@linkplain EngineStyle#capabilities() capability},
         * and not as a {@linkplain Pattern#requirements() requirement}.
         */
        FIND_LOOP,
        /**
         * Indicates the {@link Matcher} should return the first match, not the
         * longest - this is the default behavior, overridden by the
         * {@link Pattern#X_LEFTMOST_LONGEST} flag. Using the X_LEFTMOST_LONGEST
         * flag will eliminate the need for Engines supporting this feature
         * (e.g. DFA based Engines), and may result in faster matching.
         */
        LEFTMOST_FIRST;
    }

    private static final FlagMgr flagMgr = new FlagMgr();

    /**
     * This flag is not implemented. Breath holding is not recommended.
     */
    public static final int CANON_EQ = flagMgr.next("CANON_EQ");

    public static final int CASE_INSENSITIVE = flagMgr.next("CASE_INSENSITIVE");

    /**
     * This flag is not yet implemented.
     */
    public static final int COMMENTS = flagMgr.next("COMMENTS");

    public static final int DOTALL = flagMgr.next("DOTALL");

    public static final int LITERAL = flagMgr.next("LITERAL");

    public static final int MULTILINE = flagMgr.next("MULTILINE");

    public static final int UNICODE_CASE = flagMgr.next("UNICODE_CASE");

    public static final int UNIX_LINES = flagMgr.next("UNIX_LINES");

    /**
     * Forces the match to be as long as possible - specifically, the longest
     * match that occurs first (leftmost) in the input string. This is a
     * nonstandard flag with respect to the standard Java API.
     */
    public static final int X_LEFTMOST_LONGEST = flagMgr.next("X_LEFTMOST_LONGEST");

    /**
     * Suppresses default hierarchical prefixing of named capturing groups. This
     * is a nonstandard flag.
     */
    public static final int X_FLAT_CG_NAMES = flagMgr.next("X_FLAT_CG_NAMES");

    /**
     * Strips out all capturing groups (groupings delimited with simple
     * parenthesis are treated as non-capturing groups). This feature can be
     * used to maintain both capturing and non-capturing versions of the same
     * regular expression. Since {@linkplain EngineStyle engines} which don't support capturing
     * can be faster, this dual capturing / non-capturing strategy can be used to efficiently
     * find rare expressions ("needle in a haystack") and then run the capturing regex to get the
     * groups.
     */
    public static final int X_STRIP_CG = flagMgr.next("X_STRIP_CG");

    static final int FLAG_COUNT =
            flagMgr.setImplemented(
                DOTALL | MULTILINE | UNIX_LINES | LITERAL | CASE_INSENSITIVE | UNICODE_CASE
                        | X_LEFTMOST_LONGEST | X_FLAT_CG_NAMES | X_STRIP_CG).freezeAndCount();

    final String regex;
    final int flags;
    final EngineStyle style;
    final int ncg;
    final Map<String, Integer> cgNames;
    private final Set<Feature> requirements;
    final Engine engine;

    private Pattern(String regex, int flags, EngineStyle style, RegexParser.Result r) {

        flagMgr.check(flags);
        this.regex = regex;
        logger.log(level, "regex: " + regex);
        this.ncg = r.ncg;
        logger.log(level, "ncg: " + ncg, ncg);
        this.cgNames = r.cgNames;
        this.flags = flags;
        logger.log(level, "flags: " + flagMgr.stringFrom(flags));
        this.style = style;
        NFA nfa = new NFA(this, r.root);
        this.requirements = nfa.requirements;
        this.engine = style.newEngine(nfa);
    }

    public static Pattern compile(String regex, Expression... exprs) {
        return compile(regex, 0, EngineStyle.DYNAMIC, exprs);
    }

    /**
     * This factory method allows direct selection of a
     * {@linkplain EngineStyle matching algorithm}.
     * 
     * @param regex
     *            the regular expression to be compiled.
     * @param style
     *            The EngineStyle specified.
     * @return the pattern.
     * @throws EngineStyle#
     *             {@link ConstructionException} if the
     *             {@linkplain EngineStyle#capabilities() capabilities} of the
     *             matching algorithm are incompatable with the
     *             {@linkplain Pattern#requirements() requirements} of the
     *             Pattern.
     */
    public static Pattern compile(String regex, EngineStyle style, Expression... exprs) {
        return compile(regex, 0, style, exprs);
    }

    public static Pattern compile(String regex, int flags, Expression... exprs) {
        return compile(regex, flags, EngineStyle.DYNAMIC, exprs);
    }

    /**
     * This factory method allows direct selection of a
     * {@linkplain EngineStyle matching algorithm}.
     * 
     * @param regex
     *            the regular expression to be compiled.
     * @param flags
     *            the specified flags.
     * @param style
     *            The EngineStyle specified.
     * @return the pattern.
     * @throws EngineStyle#
     *             {@link ConstructionException} if the
     *             {@linkplain EngineStyle#capabilities() capabilities} of the
     *             matching algorithm are incompatable with the
     *             {@linkplain Pattern#requirements() requirements} of the
     *             Pattern.
     */
    public static Pattern compile(String regex, int flags, EngineStyle style, Expression... exprs) {
        RegexParser.Result result = new RegexParser().parse(regex, flags, exprs);
        return new Pattern(regex, flags, style, result);
    }

    public int flags() {
        return flags;
    }

    /**
     * The set of {@linkplain Feature features} which matching algorithms using
     * this pattern must implement. The set is
     * {@linkplain Collections#unmodifiableSet(Set) unmodifiable}.
     * 
     * @return the set of required features.
     */
    public Set<Feature> requirements() {
        return requirements;
    }

    /**
     * The matching algorithm, as represented by the {@link EngineStyle} class,
     * selected for use with this Pattern instance.
     * 
     * @return the matching algorithm.
     */
    public EngineStyle style() {
        return engine.style;
    }

    public final Matcher matcher(CharSequence csq) {
        return new Matcher(this, csq);
    }

    public static boolean matches(String regex, CharSequence input) {
        return Pattern.compile(regex).matcher(input).matches();
    }

    public String pattern() {
        return toString();
    }

    public static String quote(String s) {
        return "\\Q" + s + "\\E";
    }

    public String[] split(CharSequence input) {
        return split(input, 0);
    }

    public String[] split(CharSequence input, int limit) {
        List<String> result = new ArrayList<String>();
        final Matcher m = matcher(input);
        int start = 0;
        int n = 0;
        boolean found;
        do {
            found = m.find();
            final int end = found ? m.start() : input.length();
            result.add(input.subSequence(start, end).toString());
            start = found ? m.end() : -1;
            
        } while (found && ++n != limit);
        if ((limit == 0)) {
            ListIterator<String> li = result.listIterator(result.size());
            while (li.hasPrevious() && li.previous().length() == 0) li.remove();
        }
        return result.toArray(new String[result.size()]);
    }

    @Override
    public String toString() {
        return regex;
    }

    /**
     * For testability: create an NFA from a Pattern instance.
     * 
     * @param p
     *            the Pattern instance
     * @return the NFA
     */
    static NFA NFAfor(Pattern p) {
        return new NFA(p, new RegexParser().parse(p.toString(), p.flags).root);
    }

    /**
     * Simple {@link Node} based factory for testability
     */
    static final Pattern fromNode(Node root, EngineStyle style) {
        return new Pattern(root.toString(), 0, style, new RegexParser.Result(root, 0, Collections
            .unmodifiableMap(new HashMap<String, Integer>())));
    }

    /**
     * The {@link CharClass} instance representing the '$' anchor for a set of
     * <code>flags.</code> The {@linkplain Pattern#flags flags} settings
     * determine if the Unix or the Unicode variant is used; this is just a
     * convenience method to avoid repeated bit twiddling with the flags.
     * 
     * @return the ccDollar
     */
    static CharClass dollar(int flags) {
        return (flags & UNIX_LINES) != 0 ? CharClass.DOLLAR_UNIX : CharClass.DOLLAR_UNICODE;
    }
}
