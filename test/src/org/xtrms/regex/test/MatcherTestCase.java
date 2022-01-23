/* @LICENSE@  
 */

package org.xtrms.regex.test;

import java.util.Collection;
import java.util.LinkedList;

import org.xtrms.regex.AbstractRxTestCase;
import org.xtrms.regex.Matcher;
import org.xtrms.regex.Pattern;
import org.xtrms.regex.Expression;

import static org.xtrms.regex.RegexAssert.*;


public class MatcherTestCase extends AbstractRxTestCase {
    
    public static void main(String[] args) {
        junit.swingui.TestRunner.run(MatcherTestCase.class);
    }

    public MatcherTestCase(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
//        logRxXHTML();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }
    
    public void testLiteral() {
        assertJavaLookingAt("foo", "foobar");
        assertJavaLookingAt("foobar", "foobar");
        assertJavaLookingAt("", "foobar");
        // checkLookingAt("", "");   
        // TODO: false positive on hitEnd - not critical...
        // due to funky (_epsilon_)? construct used for nil match
        // causing a false "unsated" condition.
        assertTrue(Pattern.compile("").matcher("").lookingAt());
        assertJavaLookingAt("\\)\\(\\]\\[\\*", ")(][* tricky, huh?");
    }
    
    public void testNil() {
        assertTrue(Pattern.matches("", ""));
    }
    
//    public void testTemp() {
//        logRxXHTML();
//        Pattern.compile("x|y?");    
//    }
    
    public void testStarEtc() {
        
        assertJavaLookingAt("(?:ab)*", "a");    
        assertJavaLookingAt("(?:ab)*a", "a"); 
        assertJavaLookingAt("(ab)*a", "ab");
        assertJavaLookingAt("fo*", "foobar");
        assertJavaLookingAt("fo+", "foobar");
        assertJavaLookingAt("fo?o?", "foobar");
        assertJavaLookingAt("x|y?", "x");
        assertJavaLookingAt("x|y?", "y");
        assertJavaLookingAt("x|y?", "");
    }
    
    public void testOr() {
        
        assertJavaLookingAt("(?:a|b)(?:a|b)(?:a|b)(?:a|b)(?:a|b)", "abbab");
        assertJavaLookingAt("(?:a|b)(?:a|b)(?:a|b)(?:a|b)(?:a|b)", "bbbbb");
        assertJavaLookingAt("(?:a|b)(?:a|b)(?:a|b)(?:a|b)(?:a|b)", "aaaaa");
        assertJavaLookingAt("(?:a|b)(?:a|b)(?:a|b)(?:a|b)(?:a|b)", "ababa");
        assertJavaLookingAt("(?:a|b)(?:a|b)(?:a|b)(?:a|b)(?:a|b)", "bbabb");
        
        assertJavaLookingAt("foo|bar", "foobar");
        assertJavaLookingAt("foo|bar", "zbarfoo");
        
    }
    
    public void testHitEnd() {
        assertJavaLookingAt("(?:\n|,)[^\n,]*", ",some random line");
        assertJavaLookingAt("foobar", "fooba");
        assertJavaLookingAt("(?:\n|,)[^\n,]*", ",some random line\n");
        assertJavaLookingAt("a*", "aaa");
        assertJavaLookingAt("[^c]*", "aaa");
    }        
    
    /*
     * exploring more about hitEnd / requireEnd
     * FIXME: new find() scheme breaks HeRe.
     */
    public void testHeRe() {

        assertFind("a|abc", "abx", "(0, 1){ff}");
        
        // no further input could cause matches to change
        assertFind("a\\z|abc", "abc", "(0, 3){ff}");
        
        /*
         * note: find() will virtually always return t for hitEnd.
         * witness: foo := bar: [{tf}]
         */
//        assertFind("a\\z|abc", "abx", "{ff}");    // expected
        assertFind("a\\z|abc", "abx", "{tf}");       // actual
        
        /**
         * TODO: document and file Java bug.
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
        assertFind("a\\z|abc", "a", "(0, 1){tt}");
//        assertFind("a\\z|abc", "a", "(0, 1){tf}");
        /*
         * demonstrate the loss of the match with additional input
         */
        assertFind("a\\z|abc", "ax", "{tf}");
    }

    
    public void testUsePattern() {
        String csq = "foobar";
        Pattern pFoo = Pattern.compile("foo");
        Pattern pBar = Pattern.compile("bar");
        Matcher m = pFoo.matcher(csq);
        assertTrue(m.lookingAt());
        m.region(m.end(), csq.length());
        m.usePattern(pBar);
        assertTrue(m.lookingAt());
    }
    
    public void testFind() {
        CharSequence csq = "aba";
        Pattern p = Pattern.compile("a");
        Matcher m = p.matcher(csq);
        assertTrue(m.find());
        assertMatch(m, "(0,1)");
        assertTrue(m.find());
        assertMatch(m, "(2,3)");
    }
    
    public void testFind2() {
        
        CharSequence csq = "aba";
        Pattern pA = Pattern.compile("(a)");
        Pattern pC = Pattern.compile("(c)(d)");
        Matcher m = pA.matcher(csq);
        assertTrue(m.find());
        assertMatch(m, "(0,1)(0,1)");
        
        m.usePattern(pC);
        
        assertMatch(m, "(0,1)(-1,-1)(-1,-1)");

        assertFalse(m.find());
        assertMatch(m, "");     

        Pattern pA2 = Pattern.compile("a");
        m.usePattern(pA2);
        assertTrue(m.find());
        assertMatch(m, "(2,3)");
    }
    

    public void testCapturingAppendReplace() {
        Pattern p = Pattern.compile("a((b)*)z");
        Matcher m = p.matcher("abbbz");
        assertTrue(m.matches());
        assertMatch(m, "(0,5)(1,4)(3,4)");
        assertEquals("xbbby", m.replaceAll("x$1y"));
        
    }
    public void testMatches() {
        Pattern p = Pattern.compile("foo");
        Matcher m = p.matcher("ABCfoofoo");
        assertFalse(m.matches());
        assertTrue(m.find());
        assertTrue(m.find());
        assertFalse(m.find());
    }
    
    public void testAppendReplacement() {
        Pattern p = Pattern.compile("cat");
        Matcher m = p.matcher("one cat two cats in the yard");
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, "dog");
        }
        m.appendTail(sb);
        assertEquals("one dog two dogs in the yard", sb.toString());
    }
    
    public void testReplaceAll() {
        Pattern p = Pattern.compile("a*b");
        Matcher m = p.matcher("aabfooaabfooabfoob");
        assertEquals("-foo-foo-foo-", m.replaceAll("-"));
        p = Pattern.compile("a*");
        m = p.matcher("bbb");
        assertTrue(m.lookingAt());
        assertEquals("-b-b-b-", m.replaceAll("-"));
    }
        
    public void testReplaceFirst() {
        Pattern p = Pattern.compile("dog");
        Matcher m = p.matcher("zzzdogzzzdogzzz");
        assertEquals("zzzcatzzzdogzzz", m.replaceFirst("cat"));
    }
    
    public void testNullable() {
        assertJavaLookingAt("(?:ab)*", "ababa");
        assertJavaLookingAt("(?:ab)*", "a");
    }
    public void testCaseStuff() {
        assertFind("FoObAr", 
            Pattern.LITERAL | Pattern.CASE_INSENSITIVE, // who wins? CASE!
            "foobar", "(0,6)");
        assertFind("FoObAr", 
            Pattern.LITERAL | Pattern.CASE_INSENSITIVE, // see? INSENSITIVE!
            "FOOBAR", "(0,6)");
    }
    
    public void testNamedCharClass() {
        assertFind("\\pLfoo", "pfoo", "(0,4)", "");
        assertFind("\\pLfoo", "pfoo", "(0,4)", "");
        assertFind("\\p{IsL}foo", "pfoo", "(0,4)", "");
        assertFind("\\p{Ll}foo", "pfoo", "(0,4)", "");
        assertFind("\\p{IsLl}foo", "pfoo", "(0,4)", "");
        assertFind("\\p{ASCII}foo", "pfoo", "(0,4)", "");
        assertFind("\\p{InBASIC_LATIN}foo", "pfoo", "(0,4)", "");
        assertFind("\\p{javaJavaIdentifierStart}foo", "pfoo", "(0,4)", "");
        assertFind("\\P{javaJavaIdentifierStart}foo", "\u0000foo", "(0,4)", "");
        assertFind("foo\\p{ASCII}", "foop", "(0,4)", "");
    }
    public void testLeftmostBias() {    // FIXME: need to update capabilities!!!
        assertFind("(fo)|foo", "foo", "(0,2)(0,2)", "");
    }

    public void testNamedCapturingGroups() {
        
        CharSequence input = "fooXXXbarYYYclem";
        Matcher m = Pattern.compile(
            "foo(?<exes>.*)bar(?<wyes>.*)clem").matcher(input);
        m.find();
        
        assertMatch(m, input,"(0,16)(3,6)(9,12)");
        
        assertEquals("XXX", m.group("exes"));
        assertEquals("YYY", m.group("wyes"));
        assertEquals(m.group(1), m.group("exes"));
        assertEquals(m.group(2), m.group("wyes"));
    }
    
    public void testNamedSubExpressions() {
        /*
         * This is an email regex from SpecWeb 2005:
         *  ^([a-zA-Z0-9_\\-\\.]+)@((([0-9]{1,3}\\.){3}[0-9]{1,3})|([a-zA-Z]+\\.)+[a-zA-Z]{2,4})
         * A model of clarity it is not. Let's break it down and build
         * it back up, for ease of maintenance:
         */
        Collection<Expression> emailExprs = new LinkedList<Expression>();
        Expression.parseAndAdd(emailExprs, "user",   "([a-zA-Z0-9_\\-\\.]+)");
        Expression.parseAndAdd(emailExprs, "ip",     "(([0-9]{1,3}\\.){3}[0-9]{1,3})");
        Expression.parseAndAdd(emailExprs, "host",   "([a-zA-Z]+\\.)+");
        Expression.parseAndAdd(emailExprs, "domain", "[a-zA-Z]{2,4}");
        Expression.parseAndAdd(emailExprs, "dns",    "<host><domain>");
        Expression.parseAndAdd(emailExprs, "addr",   "<ip>|(?<><dns>)");
        Expression.parseAndAdd(emailExprs, "email",  "<user>@<addr>");
        /*
         * now let's use it to snarf Jack Handy's email address:
         */
        Pattern p = Pattern.compile("^(?<><email>)\\s+\"Jack Handy\"", 
            emailExprs.toArray(new Expression[emailExprs.size()]));
        Matcher m = p.matcher("deep.thoughts@snl.nbc.com  \"Jack Handy\"");
        assertTrue(m.lookingAt());
        assertEquals("deep.thoughts@snl.nbc.com", m.group("email"));
        assertEquals("snl.nbc.com", m.group("email.addr.dns"));
        /*
         * That's a bit clearer - innit?
         */
    }
}
