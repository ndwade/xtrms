/* @LICENSE@  
 */
/* $Id$ */

package org.xtrms.regex.test;

import org.xtrms.regex.AbstractRxTestCase;
import org.xtrms.regex.Matcher;
import org.xtrms.regex.Pattern;


//import java.util.regex.Matcher;
//import java.util.regex.Pattern;

import static org.xtrms.regex.RegexAssert.*;


public class AnchorsTestCase extends AbstractRxTestCase {
    

    public static void main(String[] args) {
        junit.textui.TestRunner.run(AnchorsTestCase.class);
    }

    public AnchorsTestCase(String arg0) {
        super(arg0);
    }

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testAnchorA() {        

        // this is how Java behaves

        assertFind("\\Afoo|bar", "foo", "(0,3)", "");
        assertFind("\\Afoo|bar", "bar", "(0,3)", "");
        assertFind("\\Afoo|bar", "foobar", "(0,3)","(3,6)", "");
        
        Matcher m = Pattern.compile("\\Afoo|bar").matcher("");        
        m.reset("barfoo");
        assertTrue(m.lookingAt());
        assertMatch(m, "(0, 3)");
        m.region(m.end(), 6);
        assertTrue(m.lookingAt());     // because region bound is anchor
        assertMatch(m, "(3, 6)");
        
        /*
         * test interaction of lookingAt() and find()
         */
        
        m.reset("clembarbar");
        m.useAnchoringBounds(true);
        assertTrue(m.find());           // first bar
        assertMatch(m, "(4, 7)");
        
        assertFalse(m.lookingAt());     // starts from beginning of region!
        assertTrue(m.find());           // second bar
        assertMatch(m, "(7, 10)");
        
        assertFalse(m.find());          // no more bars

        
        m.reset("foobarbar");
        
        assertTrue(m.find());           // foo
        assertMatch(m, "(0, 3)");
        assertTrue(m.lookingAt());      // foo again
        assertMatch(m, "(0, 3)");
        
        assertTrue(m.find());           // first bar
        assertMatch(m, "(3, 6)");
        assertTrue(m.lookingAt());      // foo again
        assertMatch(m, "(0, 3)");
        
        assertTrue(m.find());           // first bar again
        assertMatch(m, "(3, 6)");
        assertTrue(m.find());           // second bar
        assertMatch(m, "(6, 9)");
        assertFalse(m.find());          // no more bars
    }
    
    public void testAnchorG() {
        
        /*
         * \\G matches at the beginning of a sequence as well as the last
         * end offset which was part of a match
         */
        
        String regex = "\\Afoo|\\Gbar";
        CharSequence csq = "barbarfoo"; 
        
        assertFind(regex, csq, "(0,3)", "(3,6)", "");
        
        Matcher m = Pattern.compile(regex).matcher("");
        m.reset(csq);
        
        assertFalse(m.find(6));
        assertTrue(m.find(3));  // \G is true whenever Matcher is reset.
        assertTrue(m.find(0));
        assertTrue(m.find());   // \G is also true if prev match was find(int)
        assertFalse(m.find());
        
        /*
         * interaction of region() and find(int)
         * RTFM - find(int) _resets the matcher_ - i.e. no region()
         */
        m.region(4, 9);
        assertTrue(m.find(3));        
        /*
         * anchoring bounds
         */
        m.region(6, 9);
        assertTrue(m.find());
        
        /*
         * behavior of \G and lookingAt() - lookingAt() records last match
         */
        m.usePattern(Pattern.compile("\\Gbar"));
        m.reset("bar");
        assertTrue(m.lookingAt());
        assertFalse(m.lookingAt()); // BOF && !MATCH 

        m.usePattern(Pattern.compile("\\Afoo|\\Abar"));
        m.reset("foobar");
        assertTrue(m.lookingAt());
        assertTrue(m.lookingAt()); // BOF && !MATCH.
        
        /*
         * looks like \\G behaves the way Perl does now.
         */
        assertFind("\\Ga?", "xyz", "(0,0)", "");

        // FIXME: reintroduce when anchor nfa transformations are done 
        
//        m.usePattern(Pattern.compile("xy\\z", EngineStyle.DFA_INTERPRETED));
//        m.reset("xyz");
//        assertFalse(m.lookingAt());
//        m.region(0, 2);
//        assertTrue(m.lookingAt());

        m.usePattern(Pattern.compile("xy\\z"));
        m.reset("xyz");
        assertFalse(m.lookingAt());
        m.region(0, 2);
        assertTrue(m.lookingAt());

        // TODO: interaction of matches() and find()
        // case: matches() -> lookingAt() returns true but doesn't match
        // whole region; where does find() pick up?
        // case: lookingAt() returns true for zero length match, where
        // does find() pick up - is there a zedBump? (Don't think so).
        
    }
    
    public void testAnchorZ() {
        
        /**
         * TODO: document and file bug.
         * the following test shows up a bug in Matcher.requireEnd() according
         * to the spec, <blockquote>If this method returns true, and a match was
         * found, then more input could cause the match to be lost. If this
         * method returns false and a match was found, then more input might
         * change the match but the match won't be lost. If a match was not
         * found, then requireEnd has no meaning.</blockquote> if the initial
         * input is "a", and then "a\\z|abc" will match. If the next char is
         * 'x', it won't; the match will be "lost". Therefore, requireEnd()
         * should return true. This shows the value of a hitEnd and requireEnd
         * definition which is grounded in Automata theory.
         */
        // checkLookingAt("a\\z|abc", "a");
        
        assertTrue(Pattern.matches("a\\z|abc", "a"));
        assertJavaLookingAt("a\\z|abc", "axyz");
        assertJavaLookingAt("a\\z|abc", "abc");
        assertJavaLookingAt("a\\\\z|abc", "a\\z");
        assertJavaLookingAt("a|abc", "axyz");
    }
    
    public void testWordBoundary() {
        
//        logRxXHTML();
  
        assertFind(
            "\\bfoo\\b|^bar$", Pattern.MULTILINE,
        //   0         1         2
        //   01234567890123456789012345
        //   ---        ---        ---
            "foo foobar foo barfoo foo",
            "(0,3)",   "(11,14)", "(22,25)", "");
    }
    public void testWordBoundary2() {
        
//        logRxXHTML();
        
        assertFind("(?:\\bfoo\\b.+)+", Pattern.DOTALL,
        //  0         1         2         3
        //  0123456789012345678901234567890
           "foo foobar foo barfoo foo  foo",
           "(0,30)", "");
    }
    public void testWordBoundaryHacked() {
        
//        logRxXHTML();
        
        assertFind("(?:\\bfoo\\b.+)+", Pattern.DOTALL,
        //  0         1         2         3
        //  0123456789012345678901234567890
           "foo foobar foo barfoo foo  foo",
            "(0,30)", "");
    }
    /*
     * nota bene... the (.+) is _greedy_...
     */
    public void testWordBoundary3() {
        
//        logRxXHTML();
        
        assertFind("(?:\\b(foo)\\b(.+))+", Pattern.DOTALL,
        //  0         1         2         3
        //  0123456789012345678901234567890
           "foo foobar foo barfoo foo  foo",
             "(0,30) (0,3) (3,30)", "");
    }
    
    public void testZeroLengthAltAnchorCapture() {
        // "multiple possible zero length match" issue which CK raised 01/07:
        // http://laurikari.net/pipermail/tre-general/2007-January/000082.html
        assertFind("foo(\\b|\\B)bar", "foobar", "(0,6)(3,3)", "");
        assertFind("foo(\\B|\\b)bar", "foobar", "(0,6)(3,3)", "");
    }
    
    public void testBatshitCrazy() {
        
        assertFind("foo(\\b)?.*", "foo bar", "(0,7)(3,3)", "");
        assertFind("foo(\\b)?.*", "foobar", "(0,6)(?,?)", "");
        assertFind(".(\\b)?.", "a ", "(0,2)(1,1)", "");
        assertFind(".(\\b)?.", "ab", "(0,2)(?,?)", "");
        /*
         * here's another scoop of crazy:
         * how completely awesome - a conjuction of boundarys is order
         * insensitive. Note that \\z is not in naive last pos here...
         */
        assertFind("foo(\\b)(\\z)", "foo", "(0,3)(3,3)(3,3)", "");
        assertFind("foo(\\z)(\\b)", "foo", "(0,3)(3,3)(3,3)", "");
    }
    
    // FIXME: several bugs in here, esp with \Z
    public void testAnchors() {
        
        assertFind("a?\\Ab", "ba", "(0, 1)");
//      assertFind("a?\\Ab", "ab", "(0, 2)");  The Impossible \\A kills match.
        assertFind("a?\\Ab", "ab", "");
        assertFind("a?\\Ab", "ab", "");
        assertFind("a?\\Ab", "xb", "");
        
        
        assertFind("^", "\n\n", "(0,0)", "");
        
        /*
         * ^ refuses to match after the line term if the line term
         * is the last character in the input. This is per the spec: "If
         * MULTILINE mode is activated then ^ matches at the beginning of input
         * and after any line terminator except at the end of input."
         */
        assertFind("^", Pattern.MULTILINE, 
            "\n\n\n",
            "(0,0)", "(1,1)", "(2,2)", "");

        assertFind("^", Pattern.MULTILINE,
            "\n\r\n",   // note termination sequence \r\n
            "(0,0)", "(1,1)", "");    

        assertFind("^", Pattern.MULTILINE, 
            "\n\r\nx",   // note termination sequence \r\n
            "(0,0)", "(1,1)", "(3,3)", "");
        /*
         * ...yet $ matches just fine before the line term when it is the 
         * _first_ char in the file.
         */
        assertFind("$", Pattern.MULTILINE,
            "\n\n\n", 
            "(0, 0)", "(1, 1)", "(2, 2)", "(3, 3)", "");


        /*
         * TODO: file Java bug in the case where bump-along from a zero length
         * match pushes find() past the end of the input; 
         * a subsequent find() returns false, but then start() returns
         * -1 instead of throwing.
         */

        /*
         * \\Z works as expected. (clientRoot)(\r|\n|\r\n)\z
         */
        assertFind("\\Z", "x\n\n", "(2, 2)");
        assertFind("\\Z", "x\r\r", "(2, 2)");
        assertFind("\\Z", "x\r\n", "(1, 1)"); 
        
        /*
         * stupid terminal anchor tricks with  $ and \\Z
         */
        assertFind("foo\\Z(.?.?)", Pattern.DOTALL, 
            "foo\r\n", "(0,5) (3,5)", "");

        assertFind("foo\\Z(.?.?)", Pattern.DOTALL,
            "foo\n", "(0,4) (3,4)", "");
        
        assertFind("foo\\Z(.?.?)", Pattern.DOTALL, 
            "foo", "(0,3) (3,3)", "");

        assertFind("foo$(.?.?)", Pattern.DOTALL | Pattern.MULTILINE, 
            "foo\r\nbar", "(0,5) (3,5)", "");

        assertFind("foo$(.?.?)", Pattern.DOTALL | Pattern.MULTILINE, 
            "foo\nbar", "(0,5) (3,5)", "");

        assertFind("foo$(.?.?)", Pattern.DOTALL | Pattern.MULTILINE, 
            "foo", "(0,3) (3,3)", "");

        assertFind("foo\\z(.?.?)", Pattern.DOTALL | Pattern.MULTILINE, 
            "foo", "(0,3) (3,3)", "");

        assertFind("(.?)\\A\\Gfoo\\z(.?.?)", Pattern.DOTALL | Pattern.MULTILINE,
            "foo", "(0,3) (0,0) (3,3)", "");

        assertFind("(foo^bar$clem)*", 
            "foo^bar$clem", 
            "(0,0) (?,?)", 
            "(1,1) (?,?)"); // etc... bump along will try every position

        assertFind("foo^bar$clem", Pattern.LITERAL, 
            "foo^bar$clem", "(0,12)", "");
        
        // FIXME: why is _this_ one commented out?
//        m = find("foo\\$(\n)", "foo\n"); 
//        assertMatch(m, "(0,4) (3,4)");
//        assertGroups(m, "foo\n", "\n");
        
        assertFind("\\A\\b\\z", "", "");
        
        /*
         * the ^ anchor does not match the last line term before EOF...
         */
        assertFind("\n^", "", "");
        /*
         * but it _does_ match the BOF, even if the sequence is empty. 
         */
        assertFind("^", "", "(0,0)");

        // test MULTILINE
        assertFind("foo$", /* Pattern.MULTILINE | Pattern.UNIX_LINES, */
            "foo\r\nbar", "");
        assertFind("foo$", Pattern.MULTILINE, /* | Pattern.UNIX_LINES, */
            "foo\r\nbar", "(0,3)");
        
        assertFind("foo$", Pattern.MULTILINE /* | Pattern.UNIX_LINES */, 
            "foo\u2028foo", "(0,3)");
        
        /**
         * The behavior of the java regex Pattern class does not match
         * the documentation:
         * <p>
         * By default, the regular expressions ^ and $ ignore line terminators
         * and only match at the beginning and the end, respectively, of the
         * entire input sequence. If MULTILINE mode is activated then ^ matches
         * at the beginning of input and after any line terminator except at the
         * end of input. When in MULTILINE mode $ matches just before a line
         * terminator or the end of the input sequence.
         * <p>
         * The problem is that $ behaves like \Z, not \z, by default.
         * TODO: document.
         */
        // does $ match LS by default? Yes it does.
//        assertFind("foo$", /* Pattern.MULTILINE | Pattern.UNIX_LINES, */
//            "foo\r\n", "(0,3)");
        /*
         * This is how it should be according to docs:
         */
        assertFind("foo$", Pattern.MULTILINE,
            "foo\r\n", "(0,3)");

        // test UNIX_LINES
        assertFind("foo\\Z", /* Pattern.MULTILINE | */ Pattern.UNIX_LINES,
            "foo\r\n", "");
        
        
        // The \b word boundary seems to correspond to \w, not \p{L}
        // so Friedl's data appears to be old.
        assertFind("\\bfoobar\\b", "   foobar   ", "(3, 9)");
        assertFind("\\bƒ¿¿∫Œ¨\\b", "   ƒ¿¿∫Œ¨   ", "");
        
        /*
         * control character parsing IS case sensitive
         */
        assertFind("\\cA\\cZ", "\u0001\u001A", "(0,2)");
        assertFind("\\ca\\cz", "\u0001\u001A", "(0,2)");    // TODO: recheck regex
        
    }


}
