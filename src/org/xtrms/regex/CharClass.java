/*
 * @LICENSE@
 */

package org.xtrms.regex;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.xtrms.regex.Misc.FlagMgr;

import static org.xtrms.regex.Misc.Esc.*;

/**
 * An immutable value class representing character classes. (Not to be confused
 * with <code>class Character</code> - we're talking the regex sense of the
 * term "character class" here.) Immutability is a desireable property for the
 * CharClass objects because it allows them to be safely used as elements of
 * Sets, as well as keys in Maps.
 * <p>
 * The sets of characters are represented as arrays of {@link Interval}s. The
 * {@link Builder} ensures that these arrays are properly sorted and
 * constructed. The Builder allows clients (e.g. the {@link RegexParser}) to
 * incrementally build up CharClasss which respect all the internal invariants.
 * <p>
 * There is a special set of CharClass instances, representing Anchors (\A, \G
 * and \z) and combinations thereof which are required to implement NFAs and
 * DFAs for regular expressions with Anchors. Also, there are two others
 * (epsilon and omega) for working with NFAs created with Thompson's
 * construction {@link TNFA}, which is used for random test generation. No
 * class except CharClas can create these special instances. Factory methods and
 * the {@link Builder} enforce this restriction.
 * <p>
 * There are also static fields representing the empty CharClass and the "dot"
 * (all chars). These are for convenience only; do not count on any Singleton
 * property (i.e. always test using equals(), not '==' identity).
 */
final class CharClass implements Comparable<CharClass>, Serializable { 
    
    private static final long serialVersionUID = -6876307657918534659L;

    static final int CHAR_END = 0x10000;

    static final class Interval implements Comparable<Interval>, Serializable {

        private static final long serialVersionUID = 7757191198870744886L;


        /**
         * <code>begin</code> and <code>end</code> are declared as int for
         * future extension beyond BMP, also useful for encoding special
         * CharClass instances as negative ranges.
         * <p />
         * <code>begin</code> is inclusive.
         */
        final int begin;
        /**
         * <code>end</code> is exclusive.
         */
        final int end;

        /**
         * @param c
         */
        private Interval(int c) {
            this(c, ++c);
        }

        /**
         * Represents the character interval [begin, end).
         * <p />
         * Invariant: begin < end. Therefore, there is no "native"
         * representation for the empty range. The empty char class is
         * represented by a zero length array of <code>Interval</code>s
         * within {@link CharClass} proper.
         * 
         * @param begin
         *            inclusive
         * @param end
         *            exclusive
         */
        private Interval(int begin, int end) {
            assert begin < end && end <= CHAR_END;
            this.begin = begin;
            this.end = end;
        }

        /**
         * Attempt to merge an {@link Interval} with the current instance,
         * returning a new instance.
         * 
         * @param ci
         *            the {@link Interval} to attempt to merge
         * @return a new {@link Interval} representing the merger between
         *         <code>this</code> and <code>ci</code> if possible;
         *         otherwise <code>null</code>.
         */
        private Interval maybeMerge(Interval ci) {
            assert this.compareTo(ci) <= 0 : "arg must be ordered";
            if (this.contains(ci)) {
                return this;
            } else if (end >= ci.begin) {
                return new Interval(begin, ci.end);
            } else {
                return null;
            }
        }

        public int compareTo(Interval ci) {
            int ret = 0;
            if (begin < ci.begin) {
                ret = -1;
            } else if (begin > ci.begin) {
                ret = 1;
            } else if (end < ci.end) {
                ret = -1;
            } else if (end > ci.end) {
                ret = 1;
            }
            assert (ret == 0 ? this.equals(ci) : true);
            return ret;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || !(o instanceof Interval))
                return false;
            final Interval cr = (Interval) o;
            return begin == cr.begin && end == cr.end;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() { // per Bloch
            int result = 17;
            result = 37 * result + begin;
            result = 37 * result + end;
            return result;
        }

        /**
         * For debugging only.
         */
        @Override
        public String toString() {
            if (ALL_INIT.contains(this)) {
                return new CharClass(new Interval[] {this}).toString();
            }
            return "[" + RX.esc(begin) + ',' + RX.esc(end) + ')';
        }

        /**
         * Inner class used to return values from
         * {@link Interval#subtract(org.xtrms.regex.CharClass.Interval, 
         * org.xtrms.regex.CharClass.Interval)}
         */
        private static final class Pair {
            Interval left = null;
            Interval right = null;
        }

        /**
         * Subtracts a {@link Interval} from this instance. When an interval has
         * a wholly contained sub-interval subtracted from it, two (non-
         * contiguous) <code>Intervals</code> will result. Therefore the
         * method returns a {@link Pair} object.
         * <p>
         * The following diagram graphically enumerates the six distinct
         * possible overlap conditions for the two <code>Interval</code>s.
         * The first row depicts a representative interval for <code>this</code>
         * and the subseqent rows illustrate each possible overlap of the
         * <code>ci</code> parameter. The "left" and "right" columns specify
         * what is returned in the <code>Interval.Pair</code> object. The
         * "case" column is used to reference each overlap condition in the
         * source code which deals with that case. <code>
         * Legend: Intervals are speficied by [begin, end) where "begin" is the
         * inclusive lower limit and "end" is the exclusive upper limit.
         *  '[' := begin;  ')' := end;   '?' := begin or end; '...' unbounded 
         *                  
         *       this:  [--------)                  left    right   case
         *                       [??...             this    null    0
         *               [[[[[[[[))...              diff-l  null    1
         *               [??????)                   diff-l  diff-r  2
         *          ...[[))))))))                   null    diff-r  3
         *         ...??)                           null    this    4
         *          ...[[--------))...              null    null    5    
         * </code>
         * 
         * @param ci
         *            The <code>Interval</code> being subtracted
         * @return The <code>Interval.Pair</code> result
         */
        private Pair subtract(Interval ci) {
            Pair ret = new Pair();
            if (ci.begin < end) {
                if (begin < ci.begin) { // cases
                    ret.left = new Interval(begin, ci.begin); // 12
                }
                if (ci.end < end) {
                    if (begin < ci.end) {
                        ret.right = new Interval(ci.end, end); // 23
                    } else {
                        ret.right = this; // 4
                    }
                } // 5
            } else {
                ret.left = this; // 0
            }
            return ret;
        }

        private boolean contains(int c) {
            return begin <= c && c < end;
        }

        private boolean contains(Interval ci) {
            return contains(ci.begin) && contains(ci.end - 1);
        }

        private boolean disjoint(Interval ci) {
            return ci.end <= begin || end <= ci.begin;
        }

        private int size() {
            return end - begin;
        }
    }

    /**
     * This field caches a client-specified or lazily computed
     * <code>String</code> representing the instance. Used for debugging, but
     * also to provide client-friendly representations of the instance - for
     * example, to use the exact idiomatic representation of the instance which
     * the client used to create the instance, as opposed to some canonicalized,
     * computed <code>String</code> expression.
     */

    private transient String s;  // declared first to print prettier on log files
    /**
     * A {@link CharClass} instance is essentially an array of {@link Interval}
     * instances.
     * <p>
     * Field name mnemonic: Char Inverval Array
     */
    private final Interval[] cia;

    /**
     * Allows clients to properly compose (immutable) instances of the
     * {@link CharClass} class. A package protected interface allows simple
     * composition required by clients such as {@link RegexParser}. A more
     * extensive private interface is provided for the implementation of the
     * methods of {@link CharClass}.
     */
    static final class Builder {

        private final ArrayList<Interval> cil = new ArrayList<Interval>();

        private int previousChar = -1;

        /**
         * Creates an empty <code>Builder</code> instance
         */
        Builder() {
        }

        /**
         * Creates a non-empty <code>Builder</code> instance
         * 
         * @param cc
         *            the {@link CharClass} instance used to initialize the
         *            Builder
         */
        Builder(CharClass cc) {
            init(cc);
        }

        private Builder clear() {
            cil.clear();
            return this;
        }

        private boolean isValid() {
            Interval ci = null;
            for (Interval ciNext : cil) {
                if (ci != null) {
                    if (!(ci.compareTo(ciNext) < 0 && ci.end < ciNext.begin)) {
                        return false;
                    }
                }
                ci = ciNext;
            }
            return true;
        }

        /**
         * Clear this code>Builder</code> instance and init with a new
         * {@link CharClass} instance.
         * 
         * @param cc
         *            the {@link CharClass} instance used to initialize the
         *            Builder
         * @return <code>this</code>.
         */
        Builder init(CharClass cc) {
            clear();
            cil.addAll(Arrays.asList(cc.cia));
            assert this.isValid() : this;
            return this;
        }

        /**
         * @param ci
         *            the {@link Interval} to look for
         * @return index - if non-negative, then the CharInterval at this index
         *         <i>equals</i> <code>ci</code>. If negative, then this is
         *         the insertion point (-(i+1)). The CI at the insertion point
         *         (if it exists) must be checked for containment.
         */
        private int indexFor(Interval ci) {
            return Collections.binarySearch(cil, ci);
        }

        /**
         * Attempt to merge two {@link Interval}s.
         * 
         * @param ip
         *            the index in {@link Builder#cil} at which to attempt a
         *            merge between {@link Interval}s.
         * @return <code>true</code> if the {@link Interval} at
         *         <code>ip</code> has been merged with the {@link Interval}
         *         which follows in {@link Builder#cil}, othwise,
         *         <code>false</code>.
         */
        private boolean mergeAt(int ip) {
            if ((0 <= ip && ip < cil.size())
                    && (0 <= ip + 1 && ip + 1 < cil.size())) {
                Interval ciMerge = cil.get(ip).maybeMerge(cil.get(ip + 1));
                if (ciMerge != null) {
                    cil.set(ip, ciMerge);
                    cil.remove(ip + 1);
                    return true;
                }
            }
            return false;
        }

        /**
         * Adds the {@link Interval}, possibly causing {@link Interval}s
         * within the {@link Builder} to be merged.
         * 
         * @param ci
         *            the {@link Interval} to add.
         * @return <code>this</code> instance (for invocation chaining).
         */
        private Builder add(Interval ci) {
            int ip = insertionPoint(indexFor(ci));
            cil.add(ip, ci);
            if (mergeAt(ip - 1))
                --ip; // merge left
            while (mergeAt(ip))
                ; // merge right
            assert this.isValid() : this;
            return this;
        }

        /**
         * Add a single char.
         * 
         * @param c
         *            The char to add.
         * @return <code>this</code> instance.
         */
        Builder add(char c) {
            previousChar = c;
            return add(new Interval(c));
        }

        /**
         * Add a range of chars. Note the range specified here is inclusive.
         * 
         * @param first
         *            the first char in the range
         * @param last
         *            the last char in the range
         * @return <code>this</code> instance.
         */
        Builder add(char first, char last) {
            return add(new Interval(first, ((int) last) + 1));
        }

        /**
         * Add a set of ranges represented by an existing {@link CharClass}
         * instance.
         * 
         * @param cc
         *            the instance to add.
         * @return <code>this</code> instance.
         */
        Builder add(CharClass cc) {
            for (Interval ci : cc.cia) {
                add(ci);
            }
            return this;
        }

        private Builder add(int i) {
            return add(new Interval(i));
        }

        private boolean hasSpecial() {
            return cil.size() > 0 && cil.get(0).begin < 0;
        }

        Builder complement() {
            assert !hasSpecial();
            ArrayList<Interval> temp = new ArrayList<Interval>(cil);
            cil.clear();
            int begin = 0;
            for (Interval ci : temp) {
                int end = ci.begin;
                if (begin < end)
                    cil.add(new Interval(begin, end));
                begin = ci.end;
            }
            if (begin < CHAR_END) {
                cil.add(new Interval(begin, CHAR_END));
            }
            assert this.isValid() : this;
            return this;
        }

        boolean isEmpty() {
            return cil.size() == 0;
        }

        /**
         * convenience method - helps when parsing ranges.
         * 
         * @return the previous char added with {@link Builder#add(char)}.
         */
        char previousChar() {
            assert previousChar != -1;
            return (char) previousChar;
        }

        /**
         * Build a {@link CharClass} instance.
         * 
         * @return the (immutable) {@link CharClass} instance which this
         *         {@link Builder} represents.
         */
        CharClass build() {
            return build(null);
        }

        /**
         * @param s
         *            The string to associate with the built CharClass. This
         *            string will be returned by {@link CharClass#toString()}.
         *            The String <code>s</code> is accepted on an "if you say
         *            so, dude" basis (no checking is performed). It is
         *            typically supplied by the parser.
         * @return the resulting {@link CharClass}
         */
        CharClass build(String s) {
            return new CharClass(cil.toArray(new Interval[cil.size()]), s);
        }

        @Override
        public String toString() {
            return build().toString();
        }

        /**
         * Subtract an {@link Interval} from {@link #cil} at a given index. An
         * {@link Interval} in {@link #cil} may be adjusted, or removed
         * entirely, or split, adding an {@link Interval} to {@link #cil}.
         * 
         * @param ip
         *            The index into {@link #cil} specifying which
         *            {@link Interval} to attempt to subtract from.
         * @param ci
         *            The {@link Interval} to subtract.
         * @return <code>true</code> if an entire {@link Interval} in
         *         {@link #cil} was removed.
         */
        private boolean munch(int ip, Interval ci) {
            assert 0 <= ip && ip < cil.size();
            boolean ret = false;
            final Interval.Pair cip = cil.get(ip).subtract(ci);
            if (cip.left != null) {
                cil.set(ip, cip.left);
                if (cip.right != null) {
                    cil.add(ip + 1, cip.right);
                }
            } else if (cip.right != null) {
                cil.set(ip, cip.right);
            } else {
                ret = true;
                cil.remove(ip);
            }
            return ret;
        }

        /**
         * Subtract an {@link Interval} from the current instance. Note that
         * potentially <i>all</i> the {@link Interval}s in {@link #cil} could
         * be wiped out.
         * 
         * @param ci
         *            the {@link Interval} to subtract.
         */
        private void subtract(Interval ci) {
            int ip = insertionPoint(indexFor(ci));
            assert ip - 1 < cil.size();
            /*
             * ci could take chunk out of Interval _before_ ip (if there is one)
             */
            if (0 < ip) {
                while (ip - 1 < cil.size() && munch(ip - 1, ci))
                    ;
            }
            /*
             * Now need to check for munches at the original insertion point.
             */
            while (ip < cil.size() && munch(ip, ci))
                ;
            assert this.isValid() : this;
        }

        private Builder subtract(CharClass cc) {
            for (Interval ci : cc.cia) {
                subtract(ci);
            }
            return this;
        }

        private static Interval next(Iterator<Interval> i) {
            return i.hasNext() ? i.next() : null;
        }

        /**
         * @param cc
         * @return <code>this</code> {@link Builder}, possibly trimmed such
         *         that <code>cc.</code>{@link CharClass#contains(CharClass)}
         *         <code>(this.</code>{@link #build()}<code>)</code>
         */
        private Builder intersect(CharClass cc) {
            ArrayList<Interval> temp = new ArrayList<Interval>(cil);
            cil.clear();
            Iterator<Interval> i = temp.iterator();
            Iterator<Interval> j = Arrays.asList(cc.cia).iterator();
            Interval ci = next(i);
            Interval cj = next(j);
            while (ci != null && cj != null) {
                if (ci.equals(cj)) {
                    cil.add(ci);
                    ci = next(i);
                    cj = next(j);
                } else if (ci.disjoint(cj)) {
                    if (ci.compareTo(cj) < 0) {
                        ci = next(i);
                    } else {
                        cj = next(j);
                    }
                } else if (ci.contains(cj)) {
                    cil.add(cj);
                    cj = next(j);
                } else if (cj.contains(ci)) {
                    cil.add(ci);
                    ci = next(i);
                } else if (cj.begin < ci.end && cj.begin > ci.begin) {
                    cil.add(new Interval(cj.begin, ci.end));
                    ci = next(i);
                } else {
                    assert ci.begin < cj.end;
                    cil.add(new Interval(ci.begin, cj.end));
                    cj = next(j);
                }
            }
            assert this.isValid() : this;
            return this;
        }
    }

    /**
     * Most general constructor for non-"special" instances.
     * @param cia The array of {@link Interval}s.
     * @param s The String to assign as a label.
     */
    private CharClass(Interval[] cia, String s) {
        this.cia = cia;
        this.s = s;
        assert ALL_LEGAL == null // static init dependence cycle
                || ALL_LEGAL.contains(this);
    }

    /**
     * Constructs a CharClass without an assigned label. The label will be 
     * computed lazily if required.
     * @param cia The array of {@link Interval}s.
     */
    private CharClass(Interval[] cia) {
        this(cia, null);
    }

    /**
     * Constructor for "special" CharClass instances.
     * @param s The String to assign as a label.
     * @param id the "special" ID - must be negative.
     */
    private CharClass(String s, int id) {
        this(new Interval[] {new Interval(id, id + 1)}, s);
        assert id < 0 : id;
    }

    /**
     * This method saves the overhead of using a {@link Builder}. The common
     * case of a single char literal is thus very lightweight.
     * 
     * @param c
     *            the char which the CharClass will represent
     * @return the CharClass representing <code>c</code>
     */
    static CharClass newSingleChar(char c) {
        return new CharClass(new Interval[] {
            new Interval(c)
        });
    }
    
    static CharClass newSingleChar(char c, boolean foldcase, boolean unicode) {
        if (foldcase && unicode && Character.isLetter(c)) {
            return new Builder()
                .add(Character.toLowerCase(c))  // TODO: check this naivite.
                .add(Character.toUpperCase(c))
                .add(Character.toTitleCase(c))
                .build();
        } else if (foldcase && ('A' <= c && c <= 'Z' || 'a' <= c && c <= 'z')) {
            char f = (char) (c ^ 0x20);
            return new CharClass(new Interval[] {
                    new Interval(c < f ? c : f),
                    new Interval(c < f ? f : c)
            });
        } else return newSingleChar(c);
    }

    /**
     * A normal <code>CharClass</code> instance representing the empty set of
     * characters.
     */
    static final CharClass EMPTY = new CharClass(new Interval[0], "");

    /**
     * A special CharClass marking end - often "#' in the literature...
     */
    static final CharClass ACCEPT = new CharClass("_#_", -'#');

    /**
     * A normal <code>CharClass</code> instance representing all legal
     * "normal" (non-special) characters.
     */
    static final CharClass DOT_ALL = EMPTY.complement();
    /**
     * A normal <code>CharClass</code> instance reperesting the UNIX line
     * separator. ('\n')
     */
    static final CharClass LS_UNIX = new CharClass(new Interval[] {
        new Interval('\n')
    });
    /**
     * A normal <code>CharClass</code> instance representing all UNICODE line
     * separators.
     */
    static final CharClass LS_UNICODE =
            new Builder(LS_UNIX).add('\r').add('\u0085').add('\u2028').add(
                '\u2029').build();
    /**
     * A special <code>CharClass</code> instance representing the '\Z' anchor.
     */
    static final CharClass BIGZED = new CharClass("\\Z", -'Z');

    /**
     * A special <code>CharClass</code> instance representing the '\z' anchor.
     * The internal representation of this instance is a single range of a
     * single <code>int</code>, -1, which is convenient when performing match
     * operations, as -1 is used byt the stream classes to signal EOF.
     */
    static final CharClass EOF = new CharClass("\\z", Misc.EOF);

    /**
     * Signifies EOF or EOL for UNIX.
     */
    static final CharClass DOLLAR_UNIX =
            new Builder(EOF).add(LS_UNIX).build("$_UX");

    /**
     * Signifies EOF or EOL for UNICODE.
     */
    static final CharClass DOLLAR_UNICODE =
            new Builder(EOF).add(LS_UNICODE).build("$_UC");

    /**
     * A special <code>CharClass</code> instance used to signifiy the
     * non-deterministic arc transitions for Thompson construction NFAs.
     * Typically these will not be found in the graphs used for evaluation of
     * regex, which are "epsilon free".
     */
    static final CharClass EPSILON = new CharClass("_epsilon_", -'e');

    /**
     * A special <code>CharClass</code> instance used to signifiy the
     * terminal transition for Thompson construction NFAs.
     */
    static final CharClass OMEGA =
            new Builder(EOF).add(DOT_ALL).build("_omega_");
    /*
     * init anchor flags
     */
    private static final FlagMgr flagGen = new FlagMgr();

    /**
     * Flag constant used to construct pseudo character indicating that the
     * "beginning of file" (\A) condition is true at the start of the sequence
     * to be matched.
     */
    static final int BOF_FLAG = flagGen.next("A");

    /**
     * Flag constant used to construct pseudo character indicating that the
     * "beginning of line" (^) condition is true at the start of the sequence
     * to be matched.
     */
    static final int BOL_FLAG = flagGen.next("^");

    /**
     * Flag constant used to construct pseudo character indicating that the
     * "previous match" (\G) condition is true at the start of the sequence
     * to be matched.
     */
    static final int MATCH_FLAG = flagGen.next("G");

    /**
     * Flag constant used to construct pseudo character indicating that the
     * "beginning of word" (\b) condition is true at the start of the sequence
     * to be matched.
     */
    static final int WORD_B_FLAG = flagGen.next("b");

    /**
     * Flag constant used to construct pseudo character indicating that the "not
     * beginning of word" (\b) condition is true at the start of the sequence to
     * be matched.
     */
    static final int WORD_NB_FLAG = flagGen.next("B");
    
    /**
     * Flag constant used to construct pseudo character indicating that the 
     * <code>Matcher</code> has initiated a find() operation which will 
     * loop through the input searching for the pattern.
     */
    static final int LOOP_FLAG = flagGen.next("L");

    static final int NUM_INIT_ANCHORS = flagGen.freezeAndCount();

    static final int MAX_INIT_COMBOS = 1 << CharClass.NUM_INIT_ANCHORS;

    /**
     * Factory method to construct a "special" instance representing an Anchor
     * present at the beginning of a {@link Pattern}.
     * <p>
     * The {@link Matcher} classes employ <i>pseudo characters</i> (really an
     * <i>int</i>) which signal to the automata certain conditions. These
     * pseudo characters always have bit 31 set - i.e. they are negative.
     * <p>
     * The CharClass instances constructed by this method will
     * {@linkplain #contains(int) contain} any pseudo character which has <i>any
     * combination</i> of init anchor flags set in addition to the specified
     * <code>flag</code>. CharClass instances constructed with this factory
     * method are used within automata to check for the presence of the
     * speficied init conditions.
     * 
     * @param flag
     *            the flag to ensure containment of
     * @param s
     *            a label for this special instance
     * @return the special CharClass instance
     */
    private static CharClass newInitAnchor(int flag, String s) {
        int mask = flag - 1;                  // set all bits below flag
        assert (flag & mask) == 0 && flag < MAX_INIT_COMBOS : flag;
        Builder b = new Builder();
        for (int n = 0; n < MAX_INIT_COMBOS / 2; ++n) {
            b.add(Integer.MIN_VALUE | ((~mask & n) << 1) | flag | (mask & n));
        }
        return b.build(s);
    }

    /**
     * A special CharClass instance which will
     * {@linkplain #contains(int) contain} any pseudo character which has
     * BOF_FLAG set.
     */
    static final CharClass BOF = newInitAnchor(CharClass.BOF_FLAG, "\\A");
    /**
     * A special CharClass instance which will
     * {@linkplain #contains(int) contain} any pseudo character which has
     * MATCH_FLAG set.
     */
    static final CharClass MATCH = newInitAnchor(CharClass.MATCH_FLAG, "\\G");
    /**
     * A special CharClass instance which will
     * {@linkplain #contains(int) contain} any pseudo character which has
     * BOL_FLAG set.
     */
    static final CharClass CARET = newInitAnchor(CharClass.BOL_FLAG, "^");
    /**
     * A special CharClass instance which will
     * {@linkplain #contains(int) contain} any pseudo character which has
     * WORD_B_FLAG set.
     */
    static final CharClass WORD_B = newInitAnchor(CharClass.WORD_B_FLAG, "\\b");
    /**
     * A special CharClass instance which will
     * {@linkplain #contains(int) contain} any pseudo character which has
     * WORD_NB_FLAG set.
     */
    static final CharClass WORD_NB =
            newInitAnchor(CharClass.WORD_NB_FLAG, "\\B");

    /**
     * A special CharClass instance which will
     * {@linkplain #contains(int) contain} any pseudo character which has
     * LOOP_FLAG set.
     */
    static final CharClass LOOP = 
            newInitAnchor(CharClass.LOOP_FLAG, "\\L");
    
    static final CharClass ALL_INIT = new CharClass(new Interval[] {
        new Interval(Integer.MIN_VALUE, 
            Integer.MIN_VALUE | CharClass.MAX_INIT_COMBOS)
    });
    static final CharClass[] initAnchorCombos = new CharClass[MAX_INIT_COMBOS];
    private static final SortedMap<Integer, CharClass> initAnchorMap =
            initAnchorMap();
    static final CharClass[] initAnchors =
            initAnchorMap.values().toArray(new CharClass[initAnchorMap.size()]);

//    private static final Set<CharClass> DYNAMIC_BOUNDARIES =
//        new HashSet<CharClass>();
//
//    static {
//        DYNAMIC_BOUNDARIES.add(BOF);
//        DYNAMIC_BOUNDARIES.add(MATCH);
//        DYNAMIC_BOUNDARIES.add(CARET);
//        DYNAMIC_BOUNDARIES.add(WORD_B);
//        DYNAMIC_BOUNDARIES.add(WORD_NB);
//        DYNAMIC_BOUNDARIES.add(BIGZED);
//        DYNAMIC_BOUNDARIES.add(DOLLAR_UNICODE);
//        DYNAMIC_BOUNDARIES.add(DOLLAR_UNIX);
//        DYNAMIC_BOUNDARIES.add(EOF);
//        DYNAMIC_BOUNDARIES.add(LOOP);
//    }
    
    private static final Map<CharClass, Void> DYNAMIC_BOUNDARIES =
        new IdentityHashMap<CharClass, Void>();
    
    static {
        DYNAMIC_BOUNDARIES.put(BOF, null);
        DYNAMIC_BOUNDARIES.put(MATCH, null);
        DYNAMIC_BOUNDARIES.put(CARET, null);
        DYNAMIC_BOUNDARIES.put(WORD_B, null);
        DYNAMIC_BOUNDARIES.put(WORD_NB, null);
        DYNAMIC_BOUNDARIES.put(BIGZED, null);
        DYNAMIC_BOUNDARIES.put(DOLLAR_UNICODE, null);
        DYNAMIC_BOUNDARIES.put(DOLLAR_UNIX, null);
        DYNAMIC_BOUNDARIES.put(EOF, null);
        DYNAMIC_BOUNDARIES.put(LOOP, null);
    }

    boolean contains(CharClass cc) {
        for (Interval ci : cc.cia) {
            if (!contains(ci)) {
                return false;
            }
        }
        return true;
    }

    boolean contains(int c) {
        // OMG! 
        // http://googleresearch.blogspot.com/2006/06/extra-extra-read-all-about-it-nearly.html
        assert 1 <= cia.length;

        int lo = 0;
        int hi = cia.length;
        int m = -1;
        boolean ret = false;
        while (lo < hi) {
            m = (lo + hi) >>> 1; // invariant: m <= length - 1;
            if (cia[m].begin <= c) {
                if (m+1 == cia.length || c < cia[m+1].begin) {
                    ret = c < cia[m].end;
                    break;
                } else {
                    lo = m+1;
                }
            } else {
                hi = m;
            }
        }
        return ret;
    }

    boolean contains(Interval ci) {
        /*
         * need to check both the insertion point and the entry before.
         */
        int ip = insertionPoint(indexFor(ci));
        boolean ret = containmentAt(ip, ci);
        if (!ret)
            ret = containmentAt(--ip, ci);
        return ret;
    }

    CharClass complement() {
        return new Builder(this).complement().build();
    }

    CharClass intersection(CharClass cc) {
        return new Builder(this).intersect(cc).build();
    }

    CharClass difference(CharClass cc) {
        Builder b = new Builder(this);
        b.subtract(cc);
        return b.build();
    }

    CharClass union(CharClass cc) {
        return new Builder(this).add(cc).build();
    }

    Interval[] intervals() {
        Interval[] ret = new Interval[cia.length];
        System.arraycopy(cia, 0, ret, 0, cia.length);
        return ret;
    }

    Interval interval(int i) {
        return cia[i];
    }

    int nIntervals() {
        return cia.length;
    }

    boolean isSpecial() {
        return cia.length > 0 && cia[0].begin < 0;
    }

    boolean isDynamicBoundary() {
        return DYNAMIC_BOUNDARIES.containsKey(this);
    }

    /**
     * create a set of disjoint CharClasss from a (possibly) non-disjoint set of
     * CharClasss. invariant: result set (and temp set) are always disjoint.
     * <p>
     * For each element of the input set, clear a temp set, test against each
     * element in the result set, putting intersection and difference pieces in
     * the temp set, and keeping track of the leftover piece of the input set.
     * Put the leftover piece of the input set into the temp set, swap temp and
     * result. TODO: try to make this lighter weight, maybe using Builders.
     * 
     * @param ccs
     *            The (likely non-disjoint) set of CharClass objects
     * @return the disjoint partition of CharClasses
     */
    static SortedSet<CharClass> partition(SortedSet<CharClass> ccs) {
        SortedSet<CharClass> ret = new TreeSet<CharClass>();
        SortedSet<CharClass> temp = new TreeSet<CharClass>();
        if (ccs.isEmpty()) {
            return ret;
        }
        CharClass cc0 = ccs.first();
        ccs.remove(cc0);
        ret.add(cc0);
        for (CharClass cc : ccs) {
            temp.clear();
            CharClass dcc = cc;
            for (CharClass rcc : ret) {
                CharClass icc = cc.intersection(rcc);
                if (!icc.equals(EMPTY)) {
                    temp.add(icc);
                }
                CharClass ccx = rcc.difference(icc);
                if (!ccx.equals(EMPTY)) {
                    temp.add(ccx);
                }
                dcc = dcc.difference(icc);
            }
            if (!dcc.equals(EMPTY)) {
                temp.add(dcc);
            }
            SortedSet<CharClass> swap = ret;
            ret = temp;
            temp = swap;
        }
        assert CharClass.isDisjoint(ret) : ret;
        return ret;
    }
    
    /**
     * Useful for making table driven {@link Engine}s.
     * 
     * @param <T>
     *            Type of Object mapped to the CharClass.
     * @param sigmap
     *            Map from CharClass to T. The elements of the key set must be
     *            disjoint.
     * @return a sorted mapping from intervals to objects of type T.
     */
    static <T> SortedMap<Interval, T> intervalMapFrom(Map<CharClass, T> sigmap) {
//        assert isDisjoint(new TreeSet<CharClass>(sigmap.keySet()));
//        SortedMap<Interval, T> ret = new TreeMap<Interval, T>();
//        for (Map.Entry<CharClass, T> e : sigmap.entrySet()) {
//            for (Interval iv : e.getKey().cia) {
//                T old = ret.put(iv, e.getValue());
//                assert old == null;
//            }
//        }
        assert isDisjoint(new TreeSet<CharClass>(sigmap.keySet()));
        SortedMap<Interval, T> temp = new TreeMap<Interval, T>();
        for (Map.Entry<CharClass, T> e : sigmap.entrySet()) {
            for (Interval iv : e.getKey().cia) {
                T old = temp.put(iv, e.getValue());
                assert old == null;
            }
        }   
        // collapses mappings from adjacent intervals to objects
        SortedMap<Interval, T> ret = new TreeMap<Interval, T>();
        Interval iv = null;
        T t = null;
        for (Map.Entry<Interval, T> e : temp.entrySet()) {
            if (iv == null) {
                iv = e.getKey();
                t = e.getValue();
                continue;
            } else if (iv.end == e.getKey().begin && t.equals(e.getValue())) {
                iv = new Interval(iv.begin, e.getKey().end);
            } else {
                assert iv.end < e.getKey().begin || !t.equals(e.getValue());
                ret.put(iv, t);
                iv = e.getKey();
                t = e.getValue();
            }
        }
        if (!temp.isEmpty()) {
            assert iv != null && t != null;
            ret.put(iv, t);
        }
        return ret;
    }

    @Override
    public String toString() {
        if (s == null) {
            if (!isSpecial()) {
                s = stringFrom(cia);
            } else {
                StringBuilder sb = new StringBuilder();
                CharClass ccNormal = intersection(DOT_ALL);
                CharClass ccSpecial = difference(DOT_ALL);
                if (!ccNormal.equals(EMPTY)) { // no empty brackets for
                    // specials
                    sb.append(stringFrom(ccNormal.cia));
                    if (sb.length() > 0) {
                        sb.append('|');
                    }
                }
                if (ALL_INIT.contains(ccSpecial)) {
                    List<byte[]> l = new LinkedList<byte[]>();
                    for (int flags = 0; flags < MAX_INIT_COMBOS; ++flags) {
                        if (ccSpecial.contains(Integer.MIN_VALUE | flags)) {
                            addMinimize(l, byteArrayFrom(flags));
                        }
                    }
                    sb.append("init:{").append(stringFrom(l)).append('}');
                } else {
                    for (CharClass scc : nonInitSpecials) {
                        if (ccSpecial.contains(scc)) {
                            sb.append(scc.toString());
                        }
                    }
                }
                s = sb.toString();
            }
        }
        return s;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CharClass))
            return false;
        CharClass cc = (CharClass) o;
        boolean ret = Arrays.equals(cia, cc.cia);
        return ret;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(cia);
    }

    public int compareTo(CharClass cc) {
        int ret = 0;
        int i = 0;
        int j = 0;
        while (i < cia.length) {
            if (j < cc.cia.length) {
                ret = cia[i++].compareTo(cc.cia[j++]);
                if (ret != 0)
                    return ret;
            } else {
                return 1;
            }
        }
        return (j < cc.cia.length) ? -1 : ret;
    }

    private static final CharClass[] nonInitSpecials;
    private static final CharClass ALL_LEGAL;

    private static SortedMap<Integer, CharClass> initAnchorMap() {

        final SortedMap<Integer, CharClass> ret =
                new TreeMap<Integer, CharClass>();

        ret.put(CharClass.BOF_FLAG, BOF);
        ret.put(CharClass.BOL_FLAG, CARET);
        ret.put(CharClass.MATCH_FLAG, MATCH);
        ret.put(CharClass.WORD_B_FLAG, WORD_B);
        ret.put(CharClass.WORD_NB_FLAG, WORD_NB);

        return Collections.unmodifiableSortedMap(ret);
    }

    private static boolean checkContainment(int i, CharClass cc) {
        if (!cc.contains(Integer.MIN_VALUE | i))
            return false;
        if (!ALL_INIT.contains(Integer.MIN_VALUE | i))
            return false;
        for (int f : initAnchorMap.keySet()) {
            CharClass cca = initAnchorMap.get(f);
            if ((i & f) != 0 && !cca.contains(Integer.MIN_VALUE | i)) {
                return false;
            }
        }
        return true;
    }

    static {

        nonInitSpecials = new CharClass[] {
                // WORD_B, WORD_NB,
                BIGZED, DOLLAR_UNIX, DOLLAR_UNICODE, EOF, EPSILON, OMEGA, ACCEPT
        };

        Builder b = new Builder();
        for (CharClass cc : nonInitSpecials) {
            b.add(cc);
        }
        b.add(ALL_INIT);
        b.add(DOT_ALL);
        ALL_LEGAL = b.build(); // for assertion checking

        for (int i = 0; i < MAX_INIT_COMBOS; ++i) {
            int begin = Integer.MIN_VALUE | i;
            initAnchorCombos[i] = new CharClass(new Interval[] {
                new Interval(begin, begin + 1)
            });
            assert checkContainment(i, initAnchorCombos[i]) : "" + i + " "
                    + initAnchorCombos[i];
        }
    }

    /*
     * yes, apparently we have to minimize boolean expressions to print labels
     * nicely for the init anchors. Sigh.
     */
    private static String stringFrom(List<byte[]> l) {
        StringBuilder sb = new StringBuilder();
        for (byte[] x : l) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(stringFrom(x));
        }
        return sb.toString();
    }

    private static String stringFrom(byte[] x) {
        assert x.length == CharClass.NUM_INIT_ANCHORS;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < x.length; ++i) {
            int flag = 1 << i;
            String label = flagGen.stringFrom(flag);
            if (x[i] == 0) {
                sb.append('!').append(label);
            } else if (x[i] == 1) {
                sb.append(label);
            } else
                assert x[i] == 2;
        }
        return sb.toString();
    }

    private static byte[] byteArrayFrom(int flags) {
        assert flags < MAX_INIT_COMBOS : flags;
        byte[] ret = new byte[CharClass.NUM_INIT_ANCHORS];
        for (int i = 0; i < CharClass.NUM_INIT_ANCHORS; ++i) {
            if ((flags & (1 << i)) != 0) {
                ret[i] = 1;
            }
        }
        return ret;
    }

    /*
     * I think this is Quine / McClusky - too lazy to look up...
     */
    private static void addMinimize(List<byte[]> l, byte[] x) {
        Iterator<byte[]> i = l.iterator();
        while (i.hasNext()) {
            byte[] y = i.next();
            byte[] z = maybeMerge(x, y);
            if (z == null) { // cannot be merged - try next on list
                continue;
            } else if (z == y) { // completely absorbed - done
                return;
            } else { // new element replaces y
                i.remove(); // buh bye, y
                addMinimize(l, z);
                return;
            }
        }
        l.add(x);
    }

    /*
     * if/else case analysis: x vs y (2 means "don't care")
     *  
     *     0 1 2 
     *    +-+-+-+ 
     *  0| A C B 
     *  1| C A B 
     *  2| B B A
     *  
     * returns: null if no merge, z == y if perfect merge, new z if imperfect
     * merge
     */
    private static byte[] maybeMerge(byte[] x, byte[] y) {
        assert x.length == y.length;
        byte[] cover = null;
        int diff = -1;
        boolean xdc, ydc;
        for (int i = 0; i < x.length; ++i) {
            if (x[i] == y[i]) { // A
                continue;
            } else if ((xdc = x[i] == 2) ^ (ydc = y[i] == 2)) { // B
                if (xdc && cover == y || ydc && cover == x) {
                    return null;
                } else if (cover == null) {
                    cover = xdc ? x : y;
                }
                continue;
            } else { // C
                if (diff != -1) {
                    return null;
                }
                diff = i;
            }
        }
        if (cover != null && diff == -1) {
            return cover;
        } else if (diff != -1) {
            byte[] z = new byte[x.length];
            System.arraycopy((cover != null) ? cover : x, 0, z, 0, x.length);
            z[diff] = 2;
            return z;
        } else {
            assert false;
            return null;
        }
    }

    /*
     * when we don't get a client-provided string at construction time
     */
    private static String stringFrom(Interval[] cia) {
        final boolean betterAsComp =
                cia.length > 0 && cia[0].begin == 0
                        && cia[cia.length - 1].end == CHAR_END;
        if (betterAsComp) {
            cia = new Builder(new CharClass(cia)).complement().build().cia;
        }
        final boolean isInCc =
                betterAsComp || cia.length == 0 || cia.length > 1
                        || cia.length == 1 && cia[0].size() > 1;
        final StringBuilder sb = new StringBuilder();
        for (Interval ci : cia) {
            char begin = (char) ci.begin;
            char end = (char) ci.end;
            if (ci.size() == 1) {
                sb.append(isInCc ? RXCC.esc(begin) : RXP.esc(begin));
            } else if (ci.size() == 2) {
                assert isInCc;
                sb.append(RXCC.esc(begin));
                sb.append(RXCC.esc(--end));
            } else {
                assert isInCc;
                sb.append(RXCC.esc(begin));
                sb.append('-');
                sb.append(RXCC.esc(--end));
            }
        }
        if (isInCc) {
            sb.insert(0, betterAsComp ? "[^" : "[").append(']');
        }
        return sb.toString();
    }

    private static int insertionPoint(int i) {
        return i < 0 ? -i - 1 : i;
    }

    private static boolean isDisjoint(SortedSet<CharClass> ccs) {
        SortedSet<CharClass> tcss = new TreeSet<CharClass>(ccs);
        while (!tcss.isEmpty()) {
            CharClass cc0 = tcss.first();
            tcss.remove(cc0);
            for (CharClass cc : tcss) {
                if (!cc0.disjoint(cc)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean containmentAt(int i, Interval ci) {
        return (0 <= i && i < cia.length) ? cia[i].contains(ci) : false;
    }

    /**
     * @param ci
     *            the CharInterval to look for
     * @return index - if positive, then the CharInterval at this index
     *         <i>equals</i> <code>ci</code>. If negative, then this is the
     *         insertion point (-(i+1)). The CI at the insertion point (if it
     *         exists) must be checked for containment.
     */
    private int indexFor(Interval ci) {
        return Arrays.binarySearch(cia, ci);
    }

    private boolean disjoint(Interval ci) {
        boolean ret = true;

        final int ip = insertionPoint(indexFor(ci));
        assert ip >= 0 : ip;
        if (ip < cia.length) {
            ret &= cia[ip].disjoint(ci);
        }
        final int i = ip - 1;
        assert i < cia.length : i;
        if (0 <= i) {
            ret &= cia[i].disjoint(ci);
        }
        return ret;
    }

    /*
     * TODO: a faster algorithm for this which narrows the range searched after
     * each ci.
     */
    private boolean disjoint(CharClass cc) {
        for (Interval ci : cc.cia) {
            if (!disjoint(ci))
                return false;
        }
        return true;
    }
}
