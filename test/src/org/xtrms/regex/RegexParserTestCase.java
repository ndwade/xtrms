/* @LICENSE@  
 */
/* $Id$ */


package org.xtrms.regex;

import java.util.regex.PatternSyntaxException;

import org.xtrms.regex.Matcher;
import org.xtrms.regex.Pattern;
import org.xtrms.regex.RegexParser;
import org.xtrms.regex.AST.Node;


import static org.xtrms.regex.RegexAssert.*;

public class RegexParserTestCase extends AbstractRxTestCase {

    private Pattern p;
    private Matcher m; 
    
    public static void main(String[] args) {
        junit.swingui.TestRunner.run(RegexParserTestCase.class);
    }

    public RegexParserTestCase(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }
    
    private static void assertThrows(String regex) {
        try {
            @SuppressWarnings("unused") 
            Pattern pattern = Pattern.compile(regex);
            fail("should throw");
        } catch (PatternSyntaxException e) {}
    }
    
    public void testSimple1() {
        // TODO: revisit testing the toString() methods in light of 
        // "rootless" Pattern refactoring.
//        String regex = "ab(c|d)?|ef*(gh(i|j.))+[lmno0-9a-fxyz](p|pp)*";     
//        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
//        String normalizedRegex = pattern.toString();
//        Pattern testPattern = Pattern.compile(normalizedRegex);
//        assertEquals(pattern.toTreeString(), testPattern.toTreeString());
//        System.out.println(pattern);
//        System.out.println(testPattern);
//        System.out.println(pattern.toTreeString());
    }
    
    public void testSyntax() {
        assertThrows("ab(c|d)?|ef*(gh(i|j.)+[lmno0-9a-fxyz](p|pp)*");   
        assertThrows("ab[fo(x|b*)");            // OK not so simple
        assertThrows("ab[fo7-6](x|b*)");        // OK not so simple
        assertThrows("a{9876543210}");          // from att tests: basic.dat
        assertThrows("(a)*)b");                 // unbalanced ')' at top level
    }
    
    
    // TODO: test other chars ($ and . etc)
    public void testEscape() {
        Pattern pattern = Pattern.compile("\\)\\(\\]\\[\\*");
        assertEquals("\\)\\(]\\[*", rootOf(pattern).toString());
    }

    public void testCaptureGroupParens() {
        
        RegexParser rxp = new RegexParser();
        Node root;
        
        root = rxp.parse("(a|b)*", 0).root;
        assertEquals("(a|b)*", root.toString());
        
        root = rxp.parse("(?:a|b)*", 0).root;
        assertEquals("(?:a|b)*", root.toString());
        
        root = rxp.parse("((?:a|b)*)", 0).root;
        assertEquals("((?:a|b)*)", root.toString());
        
        root = rxp.parse("((?:ab)*)xyz", 0).root;
        assertEquals("((?:ab)*)xyz", root.toString());
        
        root = rxp.parse("((?:(a)b)*)xyz", 0).root;
        assertEquals("((?:(a)b)*)xyz", root.toString());
        
        root = rxp.parse("(?:(?:(?:a)b)*)xyz", 0).root;      // useless ngc removed
        assertEquals("(?:ab)*xyz", root.toString());
        
        root = rxp.parse("(?:(?:(?:a)b)*)|xyz", 0).root;     // 'Or' adds paren
        assertEquals("(?:(?:ab)*|xyz)", root.toString());
        
    }
    
    public void testSimpleExactQuantifier() {

        p = Pattern.compile("a{3}");
        
        m = p.matcher("aa");
        assertFalse(m.toString(), m.matches());
        
        m.reset("aaa");
        assertTrue(p.toString(), m.matches());

        m.reset("aaaa");
        assertFalse(p.toString(), m.matches());
        
        
        p = Pattern.compile("a{3,3}");
        
        m = p.matcher("aa");
        assertFalse(m.toString(), m.matches());
        
        m.reset("aaa");
        assertTrue(p.toString(), m.matches());

        m.reset("aaaa");
        assertFalse(p.toString(), m.matches());
        
        p = Pattern.compile("a{0,}");
        
        m = p.matcher("");
        assertTrue(p.toString(), m.matches());
        
        m.reset("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertTrue(p.toString(), m.matches());
        
        
        p = Pattern.compile("a{1,}");
        
        m = p.matcher("");
        assertFalse(p.toString(), m.matches());
        
        m = p.matcher("a");
        assertTrue(p.toString(), m.matches());
        
        m.reset("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertTrue(p.toString(), m.matches());
        
        
        p = Pattern.compile("a{3,5}");
        
        m = p.matcher("aa");
        assertFalse(p.toString(), m.matches());
        
        m.reset("aaa");
        assertTrue(p.toString(), m.matches());
        
        m.reset("aaaa");
        assertTrue(p.toString(), m.matches());
        
        m.reset("aaaaa");
        assertTrue(p.toString(), m.matches());
        
        m.reset("aaaaaa");
        assertFalse(p.toString(), m.matches());
        
    }
    
    public void testComposeCharClass() {
        
        p = Pattern.compile("[a-z&&[^aeiou]]+");     // no vowels!
        
        m = p.matcher("nvvhvhwvhwghqwprklmdfr");
        assertTrue(p.toString(), m.matches());
        
        m.reset("qwrtypsdfghjklzxcvbnmab");
        assertFalse(p.toString(), m.matches());
        
        p = Pattern.compile("[a-z&&[^aeiou]&&[^x-z]]+");
        m = p.matcher("wdghyplmndsxz");
        assertFalse(p.toString(), m.matches());
        
        m = p.matcher("wdghplmnds");
        assertTrue(p.toString(), m.matches());
        
        // ha! what was taken away can be restored!
        p = Pattern.compile("[a-z&&[^aeiou]&&[^x-z][w-z]]+");
        m = p.matcher("wdghyplmndsxz");
        assertTrue(p.toString(), m.matches());

        // ...but not at the same level???
        p = Pattern.compile("[a-z&&[^aeiou]&&[^x-z]w-z]+");
        m = p.matcher("wdghyplmndsxz");
        assertFalse(p.toString(), m.matches());
        /*
         * conclusion: keep track of nested char clases at same level,
         * as well as whether nested char class is intersection or union,
         * and apply each nested op in order after top level char class
         * is completely parsed.
         */        
        
    }
    
    public void testCharClassCornerCases() {
        p = Pattern.compile("[-z]+");     // not a range!

        m = p.matcher("-z---zzz-z-zz--z");
        assertTrue(p.toString(), m.matches());

        m.reset("qwrtypsdfghjklzxcvbnmab");
        assertFalse(p.toString(), m.matches());
       
    }
    
    public void testEscapeSequences() {
        p = Pattern.compile("\\cA\\cZ\\c4");
        m = p.matcher("\u0001\u001Ac4");
        assertTrue(m.matches());
        assertMatch(m, "(0,4)");
    }
    
    /**
     * This implementation fixes 
     * {@linkplain http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6609854 this java bug}.
     */
    public void testNegNestedCharClass() {
        assertFind("[c]", "c", "(0,1)");
        assertFind("[^c]", "c", "");
        assertFind("[[c]]", "c", "(0,1)");
        assertFind("[^[c]]", "c", "");
    }    
    
    public void testBackrefPuke() {
        try {
            Pattern.compile("\\b(\\w+)(\\s+\\1)+\\b");  // from Tusker.org microbenchmark
            fail("don't silently accept stuff you can't handle!!!");
        } catch (PatternSyntaxException e) {}
    }
}
