/*@LICENSE@
 */
package org.xtrms.regex.test;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.xtrms.regex.AbstractRxTestCase;
import org.xtrms.regex.Matcher;
import org.xtrms.regex.Pattern;



import static org.xtrms.regex.RegexAssert.*;


public class CaptureGroupTestCase extends AbstractRxTestCase {
    
    Logger logger = Logger.getLogger("org.xtrms.regex");
    Level level = Level.FINEST;

    public CaptureGroupTestCase(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
//        logRxXHTML();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }
    
    public void testLaurikarisThesisExample() {
        
//        logRxXHTML();
        
        // flags are just to make the diagram prettier... ;)
        //                            1
        //                  0123456789012
        CharSequence csq = "pqabbaabbxyz";
        assertFind(
            "(((a)|(b))*)(abb)", 
            Pattern.MULTILINE | Pattern.UNIX_LINES | Pattern.DOTALL,    
            csq, "(2,9)(2,6)(5,6)(5,6)(4,5)(6,9)", "");
    }
    
    public void testAltPrio() {
        
        assertFind("([ab])|([ac])", "a", "(0,1)(0,1)(-1,-1)", "");
        assertFind("(ab|a)(bc|c)", "abc",  "(0,3)(0,2)(2,3)");
        
        assertFind("(?:(foo)|(.*))(bar)", Pattern.DOTALL, "foobar",
            "(0,6)(0,3)(-1,-1)(3,6)", "");
        
        assertFind("(.*)(?:(foo)|(.*))(bar)", Pattern.DOTALL, "---foobar",
            "(0,9)(0,6)(-1,-1)(6,6)(6,9)", "");
        

    }
    
    public void testGreedy() {
        
        // shameless theft of a few tests from http://www.haskell.org/haskellwiki/Regex_Posix

        assertFind(
            "(()|.)(b)", Pattern.X_LEFTMOST_LONGEST, 
            "ab",
            "(0,2)(0,1)(-1,-1)(1,2)", "");

        assertFind(
            "((a)|(b)){2,}", Pattern.X_LEFTMOST_LONGEST, 
            "ab",
            "(0,2)(1,2)(0,1)(1,2)", "");    // note: leftover capture from first loop.

        assertFind(
            "(a*)(b|abc)", 
            "abc",
            "(0,2)(0,1)(1,2)", "");

        assertFind(
            "(a*)(b|abc)", Pattern.X_LEFTMOST_LONGEST, 
            "abc",
            "(0,3)(0,0)(0,3)", "");

        assertFind(
            "(a?)((ab)?)", Pattern.X_LEFTMOST_LONGEST, 
            "ab",
            "(0,2)(0,0)(0,2)(0,2)", "(2,2)(2,2)(2,2)(-1,-1)", "");  // hmmm....

        assertFind(
            "(a?)((ab)?)", Pattern.X_LEFTMOST_LONGEST, 
            "",
            "(0,0)(0,0)(0,0)(-1,-1)", "");  // ...multiple nil captures are somewhat troubling...
    }

    public void testCaptureGroupFromPreviousRep() {
        assertFind("((a)|(b))*", "ab", 
            "(0,2)(1,2)(0,1)(1,2)", 
            "(2,2)(?,?)(?,?)(?,?)", 
            "");
    }
    public void testAnchorCapture() {
        
//        logRxXHTML();
        
        assertFind("(\\Afoo)|(\\Gbar)|(clem\\z)", "foobarclem",
            "(0,3)  (0,3) (?,?) (?,?)",
            "(3,6)  (?,?) (3,6) (?,?)",
            "(6,10) (?,?) (?,?) (6,10)",
            "");
    }
    
    public void testAnchorCapture2() {
        
        CharSequence csq = new String("ab");
        
        Pattern pA = Pattern.compile("a");
        Pattern pAnchor = Pattern.compile("(\\A)|(\\G)|()");
        
        Matcher m = pAnchor.matcher(csq);
        m.lookingAt();
        assertMatch(m, "(0, 0) (0, 0) (-1, -1) (-1, -1)");
        
        m.usePattern(pA);
        m.reset();
        assertTrue(m.find());        
        
        m.usePattern(pAnchor);
        assertTrue(m.find());        
        assertMatch(m, "(1, 1) (-1, -1) (1, 1) (-1, -1)");
        
        m.usePattern(pA);
        assertFalse(m.find());        
        
        m.usePattern(pAnchor);
        assertTrue(m.find());   
        assertMatch(m, "(2, 2) (-1, -1) (-1, -1) (2, 2)");
        // assertMatch(m, "(1, 1) (-1, -1) (1, 1) (-1, -1)"); (what java does)

        // NOTE: java.util.regex behavior is different here - and difficult
        // to fathom. I will defend the above as being correct.
        
        /* 
         * what java.util.regex does:
         */
        
//      CharSequence csq = new String("ab");
//      
//      Pattern pA = Pattern.compile("a");
//      Pattern pB = Pattern.compile("b");
//      Pattern pAnchor = Pattern.compile("(\\A)|(\\G)|()");
//      
//      Matcher m = pAnchor.matcher(csq);
//      m.lookingAt();
//      assertMatch(m, "(0, 0) (0, 0) (-1, -1) (-1, -1)");
//      
//      m.usePattern(pA);
//      m.reset();
//      assertTrue(m.find());
//      assertMatch(m, "(0, 1)");
//      
//      m.usePattern(pAnchor);
//      assertTrue(m.find());        
//      assertMatch(m, "(1, 1) (-1, -1) (1, 1) (-1, -1)");
//      
////    assertMatch(m, "(1,2)"); OK, doesn't find 'b' at position 1...
//      m.usePattern(pB); assertFalse(m.find());
//
//      /*
//       * ...but using pB and then failing the match effectivel 
//       * cancels the bump-along! WTF?
//       */
//      m.usePattern(pAnchor);
//      assertTrue(m.find());   
////      assertMatch(m, "(2, 2) (-1, -1) (-1, -1) (2, 2)");
//      assertMatch(m, "(1, 1) (-1, -1) (1, 1) (-1, -1)");
    }
    
    public void testQuantifiedAnchorCapture() {
        /*
         * tricky - the anchor is effectively bypassable; the match will 
         * always be zero width but the anchor captures at the beginning 
         * of the string but does not capture after that.
         * 
         * Note: this does not match behavior of java.util.regex in the
         * (^)* case, but that behavior has multiple bugs in addition to 
         * being nonsensical. I'll defend the behavior below as being correct.
         */
        assertFind("(^)?", "-", "(0,0)(0,0)", "(1,1)(?,?)", "");
        assertFind("(^)*", "-", "(0,0)(0,0)", "(1,1)(?,?)", "");
        
        /* 
         * what java.util.regex does:
         */
//      // Java bug: inconsistent start() vs start(0) 
//      // (but only in a case where it should throw, anyway)
////      assertFind("(^)?", "-", "(0,0)(0,0)", "(1,1)(?,?)", "(?,?)(?,?)");
//      m = Pattern.compile("(^)?", 0).matcher("-");
//      assertTrue(m.find());
//      assertMatch(m, "(0,0)(0,0)");   // (^) is captured
//      assertTrue(m.find());
//      assertMatch(m, "(1,1)(?,?)");
//      assertFalse(m.find());
//      assertFalse(m.start() == m.start(0));   // and in any case, this should throw!
//
//      // (^)? captures; (^)* doesn't - why the difference?
//      // arguably (^)* means "zero or more '^' anchors" and should capture
//      // at the beginning of input.
////      assertFind("(^)*", "-", "(0,0)(?,?)", "(1,1)(?,?)", "(?,?)(?,?)");
//      m = Pattern.compile("(^)*", 0).matcher("-");
//      assertTrue(m.find());
//      assertMatch(m, "(0,0)(?,?)");   // doesn't capture!
//      assertTrue(m.find());
//      assertMatch(m, "(1,1)(?,?)");
//      assertFalse(m.find());
//      assertFalse(m.start() == m.start(0));   // and in any case, this should throw!

    }
    
    
    public void testBoundedRep() {
        Pattern p = Pattern.compile("(?:([A-Z][0-9]+)\\s+){3,5}");
        Matcher m;

        m = p.matcher("F22 M16 ");
        assertFalse(m.find());
                    // 0         1         2         3
                    // 0123456789012345678901234567890123456789
        m = p.matcher("F22 M16 A1 ");
        assertTrue(m.find());
        assertMatch(m, "(0, 11) (8,10)");
                    // 0         1         2         3
                    // 0123456789012345678901234567890123456789
        m = p.matcher("F22 M16 A1 PT109 AK47");
        assertTrue(m.find());
        assertMatch(m, "(0, 11) (8,10)");
                    // 0         1         2         3
                    // 0123456789012345678901234567890123456789
        m = p.matcher("F22 M16 A1 X33 ");
        assertTrue(m.find());
        assertMatch(m, "(0, 15) (11,14)");

                    // 0         1         2         3
                    // 0123456789012345678901234567890123456789
        m = p.matcher("F22 M16 A1 X33 B29     ");
        assertTrue(m.find());
        assertMatch(m, "(0, 23) (15,18)");

                    // 0         1         2         3
                    // 0123456789012345678901234567890123456789
        m = p.matcher("F22 M16 A1 X33 B29 A10 ");
        assertTrue(m.find());
        assertMatch(m, "(0, 19) (15,18)");
    }

    public void testBoundedRepReluctant() {
        Pattern p = Pattern.compile("(?:([A-Z][0-9]+)\\s+){3,5}?");
        Matcher m;

        m = p.matcher("F22 M16 ");
        assertFalse(m.find());
                    // 0         1         2         3
                    // 0123456789012345678901234567890123456789
        m = p.matcher("F22 M16 A1 ");
        assertTrue(m.find());
        assertMatch(m, "(0, 11) (8,10)");
                    // 0         1         2         3
                    // 0123456789012345678901234567890123456789
        m = p.matcher("F22 M16 A1 PT109 AK47");
        assertTrue(m.find());
        assertMatch(m, "(0, 11) (8,10)");
                    // 0         1         2         3
                    // 0123456789012345678901234567890123456789
        m = p.matcher("F22 M16 A1 X33 ");
        assertTrue(m.find());
        assertMatch(m, "(0, 11) (8,10)");

                    // 0         1         2         3
                    // 0123456789012345678901234567890123456789
        m = p.matcher("F22 M16 A1 X33 B29     ");
        assertTrue(m.find());
        assertMatch(m, "(0, 11) (8,10)");

                    // 0         1         2         3
                    // 0123456789012345678901234567890123456789
        m = p.matcher("F22 M16 A1 X33 B29 A10 ");
        assertTrue(m.find());
        assertMatch(m, "(0, 11) (8,10)");
    }

    /**
     * This test will "hang" the standard java library - see
     * {@linkplain http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6693451 this bug}
     */
    public void testExpBTtortureCase() {
        Pattern p = Pattern.compile("^(\\s*foo\\s*)*$");
        Matcher m = p.matcher(
            "foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo " +
            "foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo " +
            "foo foo foo foo foo foo foo fo");  // ending in "fo", not "foo"
        assertFalse(m.find());
        m = p.matcher(
            "foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo " +
            "foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo " +
            "foo foo foo foo foo foo foo foo");  // ending in "foo" this time
        assertTrue(m.find());
    }
    
    public void testEmptyCapture() {
        CharSequence csq = new String("ab");
        
        Pattern pA = Pattern.compile("a");
        Pattern pB = Pattern.compile("b");
        Pattern pEmpty = Pattern.compile("()");
        
        Matcher m = pEmpty.matcher(csq);
        m.lookingAt();
        assertMatch(m, "(0,0)(0,0)");
        
        m.usePattern(pA);
        m.reset();
        assertTrue(m.find());
        assertMatch(m, "(0,1)");
        
        m.usePattern(pEmpty);
        assertTrue(m.find());        
        assertMatch(m, "(1,1)(1,1)");
        
        m.usePattern(pB); assertFalse(m.find());
//        assertMatch(m, "(1,2)"); OK, doesn't find 'b' at position 1...

        /*
         * bump-along NOT cancelled; empty match found at 2.
         * This is what makes sense to me.
         * TODO: document the weird java.util.regex behavior and move on.
         */
        m.usePattern(pEmpty);
        assertTrue(m.find());   
//        assertMatch(m, "(1,1)(1,1)");
        assertMatch(m, "(2,2)(2,2)");
    }
    public void testEmptyCapture2() {
//        logRxXHTML();
        assertFind("((a)?)?", "", "(0,0)(0,0)(?,?)", "");
    }
    
    public void testReluctantQuantifier() {
//        logRxXHTML();
        
        assertFind("(a+?b*)((?:a|b)+)", "aaab", "(0,4)(0,1)(1,4)");
        assertFind("(a+?b*)((?:a|b)+)", "abbb", "(0,4)(0,3)(3,4)");
        
        assertFind("a(.*?)b(.*?)ab", "abbbbbab", "(0,8)(1,1)(2,6)");
        
        assertFind("(a)?", "a", "(0,1)(0,1)");
        assertFind("(a)??", "a", "(0,0)(?,?)");
        assertFind("((a)?)??", "a", "(0,0)(?,?)(?,?)");
        assertFind("((a)??)?", "a", "(0,0)(0,0)(?,?)");

        assertFind("(?:(a)?)(?:(a)??)", "a","(0,1)(0,1)(?,?)");
        assertFind("(?:(a)?)*?(?:(a)??)*", "a", "(0,1)(?,?)(0,1)");

    }
    
    public void testHeReWithLpDbc() {
        assertFind("foo($)?", "foo", "(0,3)(3,3){tf}");
        assertFind("foo($)?", "fool", "(0,3)(?,?){ff}");
    }
    
}
