/* @LICENSE@  
 */

/**
 * 
 */
package org.xtrms.regex;

import static org.xtrms.regex.Misc.EOF;
import static org.xtrms.regex.Misc.LS;
import static org.xtrms.regex.Misc.clear;
import static org.xtrms.regex.Pattern.dollar;
import static org.xtrms.regex.RegexParser.CC_WORD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.MatchResult;

import org.xtrms.regex.Misc.CharScanner;
import org.xtrms.regex.Misc.PushbackIterator;


/**
 * @author ndw
 *
 */
abstract class AbstractMatcher {
    
    protected abstract class Replacer {
        abstract void appendReplacement(Appendable a) throws IOException;
    }
    
    private final class GroupReplacer extends Replacer {
        private final int group;
        GroupReplacer(int group) {
            if (pattern.ncg < group) {
                throw new IndexOutOfBoundsException();
            }
            this.group = group;
        }
        /**
         * see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6715406
         * ...will insert empty string per behavior of java class
         */
        void appendReplacement(Appendable a) throws IOException {
            if (cga.match(group)) {
                a.append(csq, start + cga.start(group), start + cga.end(group));
            }
        }
    }
    
    private final class LiteralReplacer extends Replacer {
        private final int start;
        private final int end;
        LiteralReplacer(int start, int end) {
            this.start = start;
            this.end = end;
        }
        void appendReplacement(Appendable a) throws IOException {
            a.append(rsb, start, end);
        }

    }
    
//    private final class MatchReplacer extends Replacer {
//        void appendReplacement(Appendable a) throws IOException {
//            if (!match) {
//                throw new IllegalStateException("no match");
//            }
//            a.append(csq, start, end);
//        }
//
//    }
//    
    /**
     * Represents an array of capture groups. By encapsulating an entire array
     * of capture groups, a single flat array of <code>int</code>s can be
     * used to store the values, which allows bulk <code>arraycopy</code> and
     * <code>fill</code> operations to be used.
     */
    static final class CGA {
        
        final int length;
        final int ngroups;
        /* private */ final int[] a;    // non-private to allow inlining
        
        CGA(int ngroups) {
            a = new int[ngroups << 1];
            this.length = a.length;
            this.ngroups = ngroups;
            clear();
        }
        
        CGA(int offset, CGA cga) {
            a = new int[cga.length];
            this.length = cga.length;
            this.ngroups = cga.ngroups;
            for (int i=0; i<length; ++i) {
                a[i] = cga.a[i] != -1 ? offset + cga.a[i] : -1;
            }
        }
                
        void copyFrom(CGA src) {
            assert src.a.length == a.length;
            System.arraycopy(src.a, 0, a, 0, a.length);
        }
        
        void copyGroupFrom(CGA src, int group) {
            int i = group << 1;
            a[i] = src.a[i];
            a[i+1] = src.a[i+1];
        }
        
        void clear() {
            Arrays.fill(a, -1);
        }
        
        void clear(int group) {
            a[group << 1] = a[(group << 1) + 1] = -1;
        }
        
        int start(int group) {return a[group << 1];}
        int end(int group)   {return a[(group << 1) + 1];}

        void start(int group, int start) {a[group << 1] = start;}
        void end(int group, int end)     {a[(group << 1) + 1] = end;}
        
        boolean match(int group) {
            boolean ret = a[group << 1] != -1;
            if (ret) {
                assert start(group) <= end(group);
            }
            return ret;
        }
        
        String toString(int group) {
            return new StringBuilder()
                .append('(').append(start(group)).append(',')
                .append(end(group)).append(')')
                .toString();
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int g=0; g<ngroups; ++g) sb.append(toString(g));
            return sb.toString();
        }
        
        static boolean lefterLonger(CGA lhs, CGA rhs) {
            int ls, rs;
            boolean ret = ((ls = lhs.a[0]) == -1) ? false :
                ((rs = rhs.a[0]) == -1 || ls < rs) ? true :
                    (ls == rs && lhs.a[1] > rhs.a[1]) ? true : false;
            return ret;
        }
        
        static void propagate(int len, CGA src, boolean[] tags, CGA dst) {
            assert src.length == dst.length && src.length == tags.length;
            for (int i=0; i < src.length; ++i) {
                dst.a[i] = tags[i] ? len : src.a[i];
            }
        }
    }
    
    /**
     * Represents a Dynamic Boundary Checker on {@link NFA.Arc}s. Each enum
     * constant overrides a
     * {@linkplain DBC#check(EnumSet, AbstractMatcher) check} method which
     * {@link Engine}s can use to test the boundary condition.
     */
    enum DBC {
        BOF(CharClass.BOF) {
            @Override
            boolean check(AbstractMatcher m) {
                return m.atBof();
            }
        },
        MATCH(CharClass.MATCH) {
            @Override
            boolean check(AbstractMatcher m) {
                return m.afterLastMatch();
            }
        },
        CARET(CharClass.CARET) {
            @Override
            boolean check(AbstractMatcher m) {
                return m.atBofOrAfterLs();
            }
        },
        WORD_B(CharClass.WORD_B) {
            @Override
            boolean check(AbstractMatcher m) {
                return m.atWordBoundary();
            }
        },
        WORD_NB(CharClass.WORD_NB) {
            @Override
            boolean check(AbstractMatcher m) {
                return !m.atWordBoundary();
            }
        },
        DOLLAR_UNICODE(CharClass.DOLLAR_UNICODE) {
            @Override
            boolean check(AbstractMatcher m) {
                return m.atEofOrLs();
            }
        },
        DOLLAR_UNIX(CharClass.DOLLAR_UNIX) {
            @Override
            boolean check(AbstractMatcher m) {
                // FIXME: make the checks specific to flags - don't
                // freeking keep retesting to the same static shit!
                return m.atEofOrLs();
            }
        },
        BIGZED(CharClass.BIGZED) {
            @Override
            boolean check(AbstractMatcher m) {
                return m.atEofOrLastLs();
            }
        },
        EOF(CharClass.EOF) {
            @Override
            boolean check(AbstractMatcher m) {
                return m.atEof();
            }
        },
        LOOP(CharClass.LOOP) {
            @Override
            boolean check(AbstractMatcher m) {
                return m.loop;
            }
        };
        final CharClass cc;
        private DBC(CharClass cc) {
            this.cc = cc;
        }
        static DBC DBCofCC(CharClass cc) {
            for (DBC dbc : DBC.values()) {
                if (cc.equals(dbc.cc)) {
                    return dbc;
                }
            }
            assert false; // should never be called for non DBC CC
            return null;
        }
        abstract boolean check(AbstractMatcher m);
        static boolean check(EnumSet<DBC> dbcs, AbstractMatcher m) {
            for (DBC dbc : dbcs) {
                if (!dbc.check(m)) return false;
            }
            return true;
        }
        @Override
        public String toString() {
            return cc.toString();
        }
    }
    
   
    /*
     * CCLT_UNICODE must not contain '\r' because of the test for BOL - 
     * @see #eval() where initStatus is set
     */
    private static final CharClass CCLT_UNICODE =
            CharClass.LS_UNICODE.difference(CharClass.newSingleChar('\r'));
    private static final CharClass CCLT_UNIX = CharClass.LS_UNIX;
    
    protected final List<Replacer> replacers = new ArrayList<Replacer>();
    private final StringBuilder rsb = new StringBuilder();
    protected Appendable result = null;
    protected int appendPosition = 0;
    private String replacement = null;
    protected String replacement() {
        return replacement;
    }
    
    protected void installReplacement(String r) {
        if (r == replacement) {
            return;
        }
        replacement = r;
        clear(rsb);
        int mark = 0;
        PushbackIterator<Integer> ii = new CharScanner(r).iterator();
        while(ii.hasNext()) {
            int c = ii.next();
            if (c == '$' && ii.hasNext()) {
                if ('0' <= (c = ii.next()) && c <= '9') {
                    if (rsb.length() > mark) {
                        replacers.add(
                            new LiteralReplacer(mark, rsb.length()));
                        mark = rsb.length();
                    }
                    replacers.add(new GroupReplacer(c - '0'));
                } else if (c == '<') {
                    StringBuilder sb = new StringBuilder();
                    while ((c = ii.next()) != '>') {
                        if (!RegexParser.CC_NAME.contains(c)) {
                            throw new IllegalArgumentException(
                                "bad char in capture group name: " + (char) c);
                        }
                        sb.append((char) c);
                    }
                    String name = sb.toString();
                    replacers.add(new GroupReplacer(pattern.cgNames.get(name)));
                } else {
                    ii.pushback();
                }
            } else {
                rsb.append((char) c);
            }
        }
        if (rsb.length() > mark) {
            replacers.add(new LiteralReplacer(mark, rsb.length()));
        }
    }

    protected AbstractMatcher doAppendReplacement() {
        checkFound();
        int matchStart = start + cga.start(0);
        assert appendPosition <= matchStart;
        try {
            result.append(csq, appendPosition, matchStart);
            for (Replacer r : replacers) {
                r.appendReplacement(result);
            }
        } catch (IOException e) {
            iox = e;
        }
        appendPosition = end;
        return this;
    }
    
    protected final Appendable doAppendTail() {
        assert appendPosition <= csq.length();
        try {
            result.append(csq, appendPosition, csq.length());
        } catch (IOException e) {
            iox = e;
        }
        return result;
    }
    
    protected Pattern pattern;
    protected Engine engine;
    
    /**
     * <code>mls</code> := matcher local storage.<p>
     * <code>Engine</code> objects use the <code>mls</code> as a kind of
     * thread local storage. <code>Engine</code> are part of
     * <code>Patterns</code>, which must be immutable and thread safe. So all
     * state must be stored as automatic or heap allocated objects local to the
     * eval() method, or, if that is hopelessly inefficient, may be stored in
     * the <code>mls</code>.
     * <p>
     * Note that the <code>Matcher</code> is not thread safe; the
     * <code>mls</code> effectively becomes part of the
     * <code>Matcher</code> state.
     */
    protected Object mls;

    protected CharSequence csq = null;
    protected int start = -1;
    protected int end = -1;
    protected int initStatus = CharClass.BOF_FLAG | CharClass.BOL_FLAG | CharClass.MATCH_FLAG;
    protected boolean match;
    
    protected int regionStart = 0;
    protected int regionEnd = 0;
    
    protected boolean hitEnd = false;
    protected boolean requireEnd = false;
    protected boolean found;
    protected int zedBump = 0;
    protected int matchEnd = 0;
    
    /**
     * invariant: we have read i characters from csq.
     */
    protected int i = 0;
    protected final StringBuilder sb = new StringBuilder();
    protected IOException iox = null;
    /**
     * assigned by the implementation of {@link Engine#eval(AbstractMatcher)} to
     * return the longest prefixed matched, or left at -1 to indicate no match.
     */
    private CharClass cclt = CCLT_UNICODE;
    private int cr = '\r';
    CGA cga;
    protected AbstractMatcher(Pattern pattern) {
        doUsePattern(pattern);
    }
    
    protected abstract boolean moreInput();
    /*
     * invariant: csq.charAt(i) always returns the next character,
     * even though peekChar() can rewind i to the previous start position,
     * due to the call to moreInput().
     */

    
    /*
     * currChar(0) gives the last char returned by nextChar()
     * if nextChar() not called yet then returns '\n'
     * because if nextChar() not called yet, then start == i
     */
    private int currChar(int offset) {
        assert start == i && 0 <= offset || start < i && -1 <= offset;
        int j = (i - 1 + offset); 
        if (j < 0) {
            return '\n';
        } else {
            while ((j = i - 1 + offset) >= regionEnd && moreInput());
            return j <  regionEnd ? csq.charAt(j) : EOF;
        }
    }
    
    protected final int nextChar() {
        return i < regionEnd || moreInput() ? csq.charAt(i++) : eof(i++);
    }
    
    private static int eof(int dummy) { // just need an expression...
        return EOF;
    }

    private boolean afterAllButLastLS(int ci, int cj) {
        return (cj != EOF && (cclt.contains(ci) || (ci == cr && cj != '\n')));
    }
    /**
     * Invoked by '\A' anchor
     * {@linkplain NFA.DynamicBoundaryCheck dynamic boundary check}.
     * 
     * @return true at the beginning of input
     */
    private final boolean atBof() {
        /*
         * n.b.: i == 1 will hold only after the very first char, even
         * if moreInput is called - after at least one char is consumed,
         * moreInput will never rewind i back to 0 (only back to 1).
         */
        return start == regionStart && i == start + 1;
    }
    /**
     * Invoked by '\G' anchor
     * {@linkplain NFA.DynamicBoundaryCheck dynamic boundary check}.
     * 
     * @return true if the start of this eval() is immediately following 
     * the last match.
     */
    private boolean afterLastMatch() {
        return matchEnd == start && i == start + 1;
    }
    /**
     * Invoked by '^' anchor
     * {@linkplain NFA.DynamicBoundaryCheck dynamic boundary check}.
     * 
     * @return true if at the beggining of input or after a complete line term,
     * except for the last line term before the end of input.
     */
    private boolean atBofOrAfterLs() {
        return atBof() || afterAllButLastLS(currChar(-1), currChar(0));
    }
    /**
     * Invoked by '\b' and '\B word 
     * {@linkplain NFA.DynamicBoundaryCheck dynamic boundary check}s.
     * 
     * @return true if the char previously returned by nextChar() is on a word
     *         boundary, that is, if it and the previous char constitute a
     *         sequence matching \W\w or \w\W.
     */
    private boolean atWordBoundary() {
        return CC_WORD.contains(currChar(-1)) ^ CC_WORD.contains(currChar(0));
    }
    /**
     * Invoked by '$' anchor
     * {@linkplain NFA.DynamicBoundaryCheck dynamic boundary check}.
     * 
     * @return true if the next char is EOF or the first char of a line
     * terminator.
     */
    private boolean atEofOrLs() {
        int c = currChar(0);
        return (!dollar(pattern.flags).contains(c)) ? false 
                : !((pattern.flags & Pattern.UNIX_LINES) == 0
                        && currChar(-1) == '\r' && c == '\n');  
    }
    /**
     * Invoked by '\Z' anchor
     * {@linkplain NFA.DynamicBoundaryCheck dynamic boundary check}.
     * 
     * @return true the end of input, or before the line terminator immediately
     * preceeding the end of input.
     */
    private boolean atEofOrLastLs() {
        if (!atEofOrLs()) return false;
        int c = currChar(0);
        int c1;
        switch(c) {
        case EOF:
            return true;
        case '\n':
            return currChar(1) == EOF;
        case '\r':
            return (c1 = currChar(1)) == EOF ? true
                    : c1 == '\n' && currChar(2) == EOF;
        default:
            return ((Boolean) null);    // boxing is punkd
        }
    }
    /**
     * Invoked by '\z' anchor
     * {@linkplain NFA.DynamicBoundaryCheck dynamic boundary check}.
     * 
     * @return true if the next char is EOF.
     */
    private boolean atEof() {
        return currChar(0) == EOF;
    }
    
    private boolean loop;
    
    protected final void evalProlog(boolean loop) {
        
        this.loop = loop;
        
        i = start;
        
        initStatus = Integer.MIN_VALUE;
        if (start == regionStart) {
            initStatus |= CharClass.BOF_FLAG;
        }
        if (matchEnd == start) {
            initStatus |= CharClass.MATCH_FLAG;
        }
        // atBofOrAfterLsInit
        if (start == regionStart 
                || afterAllButLastLS(currChar(0), currChar(1))) {
            initStatus |= CharClass.BOL_FLAG;
        }
        if (CC_WORD.contains(currChar(0)) ^ CC_WORD.contains(currChar(1))) {
            initStatus |= CharClass.WORD_B_FLAG;
        } else {
            initStatus |= CharClass.WORD_NB_FLAG;
        }
        if (loop) {
            initStatus |= CharClass.LOOP_FLAG;
        }
        
        cga.clear(0);
        hitEnd = requireEnd = false;
    }
    
    protected final void evalEpilog() {
        
        match = cga.match(0);
        if (match) {
            end = matchEnd = start + cga.end(0);
        }
    }
    
    protected final void checkFound() {
        if (!found) {
            throw new IllegalStateException("no prevous match from find()");
        }        
    }
    protected boolean engineHasFindLoop;

    private void doUsePattern(Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException();
        }
        this.pattern = pattern; 
        engine = pattern.engine;
        engineHasFindLoop = 
            engine.style.capabilities().contains(Pattern.Feature.FIND_LOOP);
        mls = null;
        if ((pattern.flags & Pattern.UNIX_LINES) != 0) {
            cclt = CCLT_UNIX;
            cr = (char) -2;
        } else {
            cclt = CCLT_UNICODE;
            cr = '\r';
        }
        /*
         * bizzare behavior which java class lib exhibits: when changing
         * Patterns, the old match state is retained for group[0] and
         * cleared for all other groups.
         */
        CGA cga2 = new CGA(pattern.ncg + 1);
        if (cga != null) {
            cga2.copyGroupFrom(cga, 0);
        }
        cga = cga2;
    }
    
    /**
     * Overridden by subclasses for the purpose of implementing covariance.
     * Subclasses should override this only to invoke using <code>super</code>,
     * and return the exact subclass type.
     * @param newPattern
     * @return This AbstractMatcher. Subclasses should return their own exact 
     * type.
     */
    protected AbstractMatcher usePattern(Pattern newPattern) {
        doUsePattern(newPattern);
        return this;
    }
    
    public final Pattern pattern() {
        return pattern;
    }
    
    public final MatchResult toMatchResult() {
        return MatchResultImpl.newMatchResult(this);
    }

    
    public static final String quoteReplacement(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (c == '$' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
    
    @Override
    public String toString() {
        clear(sb);
        sb.append("pattern=").append(pattern).append(LS);
        sb.append("csq=").append(csq).append(LS);
        sb.append("start=").append(start).append(',');
        sb.append("end=").append(end).append(',');
        sb.append("regionStart=").append(regionStart).append(',');
        sb.append("regionEnd=").append(regionEnd).append(',');
        sb.append("hitEnd=").append(hitEnd).append(',');
        sb.append("requireEnd=").append(requireEnd).append(',');
        sb.append("init=").append(initStatus).append(LS);
        return sb.toString();
    }

    public int start() {
        return start(0);   
    }

    public int end() {
        return end(0);
    }

    public final String group() {
        return group(0);
    }

    public final String group(int group) {
        checkMatch();
        if (!cga.match(group)) {
            return null;
        } else {
            return csq.subSequence(start(group), end(group)).toString();
        }
    }
    
    private int indexOfName(String name) {
        Integer i = pattern.cgNames.get(name);
        if (i == null) throw new IllegalArgumentException(
            "unknown group name: " + name);
        return i;
    }
    public final String group(String name) {
        return group(indexOfName(name));
    }

    public final int groupCount() {
        return pattern.ncg;
    }

    public int start(int group) {
        checkMatch();
        int start = cga.start(group);
        return start == -1 ? -1 : this.start + start; 
    }
    
    public int start(String name) {
        return start(indexOfName(name));
    }

    public int end(int group) {
        checkMatch();
        int end = cga.end(group);
        return end == -1 ? -1 : this.start + end; 
    }
    
    public int end(String name) {
        return end(indexOfName(name));
    }

    protected final void checkMatch() {
        if (!match) {
            throw new IllegalStateException("no match");
        }
    }

}
