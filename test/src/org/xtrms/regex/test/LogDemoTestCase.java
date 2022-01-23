/*@LICENSE@
 */
package org.xtrms.regex.test;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.xtrms.regex.AbstractRxTestCase;
import org.xtrms.regex.Pattern;

public class LogDemoTestCase extends AbstractRxTestCase {

    Logger logger = Logger.getLogger("org.xtrms.regex");
    Level level = Level.FINEST;

    public LogDemoTestCase(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
        logRxXHTML();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }
    
    public void testReluctantQuant() {
        Pattern.compile("(a+?b*)((?:a|b)+)");
    }

    public void testFunkyAlt() {
        Pattern.compile("(ab|a)(bc|c)");
    }
    
    public void testDFA() {
        Pattern.compile("ab*|a*b", Pattern.X_LEFTMOST_LONGEST);
    }
    
    public void testDBC() {
        Pattern.compile("(?:(\\A)|(\\G))foo*(\\b)?.bar(\\z)?", Pattern.DOTALL);
    }
    
    public void testDBC2() {
        Pattern.compile("\\G(?:\\A|\\b).*", Pattern.DOTALL);
    }

    public void testDBC3() {
        Pattern.compile("\\G(?:\\A|(?<wordbreak>\\b)).*", Pattern.DOTALL);
    }
    
    public void testDBC4() {
        Pattern.compile("foo(?:(\\B)|(\\b$)|($\\z))|()", Pattern.MULTILINE | Pattern.UNIX_LINES);
    }
    
    public void testDBC5() {
        Pattern.compile("foo(?:(\\b)|())bar");
    }
    
    public void testComplexEmail() {
        Pattern.compile("(?<user>(?:(?:[^ \\t\\(\\)\\<\\>@,;\\:\\\\\\\"\\.\\[\\]\\r\\n]+)|(?:\\\"(?:(?:[^\\\"\\\\\\r\\n])|(?:\\\\.))*\\\"))(?:\\.(?:(?:[^ \\t\\(\\)\\<\\>@,;\\:\\\\\\\"\\.\\[\\]\\r\\n]+)|(?:\\\"(?:(?:[^\\\"\\\\\\r\\n])|(?:\\\\.))*\\\")))*)@(?<domain>(?:(?:[^ \\t\\(\\)\\<\\>@,;\\:\\\\\\\"\\.\\[\\]\\r\\n]+)|(?:\\[(?:(?:[^\\[\\]\\\\\\r\\n])|(?:\\\\.))*\\]))(?:\\.(?:(?:[^ \\t\\(\\)\\<\\>@,;\\:\\\\\\\"\\.\\[\\]\\r\\n]+)|(?:\\[(?:(?:[^\\[\\]\\\\\\r\\n])|(?:\\\\.))*\\])))*)", Pattern.DOTALL); 
    }
    
    public void testKadafi() {
        Pattern.compile("M[ou]\'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", Pattern.DOTALL);
    }
    
    public void testBoundedQuant() {
        Pattern.compile("a{3,5}");
        Pattern.compile("a{3,5}?");
    }

    public void testTusker() {
        int flags = Pattern.DOTALL | Pattern.X_LEFTMOST_LONGEST;
        Pattern.compile("^(([^:]+)://)?([^:/]+)(:([0-9]+))?(/.*)", flags);
        Pattern.compile("usd [+-]?[0-9]+.[0-9][0-9]", flags);
//        Pattern.compile("\\{(\\d+):(([^}](?!-} ))*)", flags); // problem: neg lookahead ?!
//        Pattern.compile("\\b(\\w+)(\\s+\\1)+\\b", flags);     // problem: backref...
    }
    
    public void testTusker2() {
        int flags = Pattern.DOTALL | Pattern.X_LEFTMOST_LONGEST | Pattern.X_STRIP_CG;
        Pattern.compile("^(([^:]+)://)?([^:/]+)(:([0-9]+))?(/.*)", flags);
        Pattern.compile("usd [+-]?[0-9]+.[0-9][0-9]", flags);
    }

}
