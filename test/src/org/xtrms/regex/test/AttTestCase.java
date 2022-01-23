/*@LICENSE@
 */
package org.xtrms.regex.test;

import static org.xtrms.regex.RegexAssert.assertFind;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.xtrms.regex.AbstractRxTestCase;
import org.xtrms.regex.Pattern;

/**
 * Implements tests adapted from the <a
 * href="http://www.research.att.com/~gsf/testregex/testregex.html">testregex</a>
 * program from Greg Fowler of ATT Research. These tests have in some instances
 * been altered to accommodate the slightly different syntax of the java
 * package. See each individual test file for details.
 */
public class AttTestCase extends AbstractRxTestCase {

    private static final java.util.regex.Pattern hexPattern =
            java.util.regex.Pattern.compile("\\\\x([0-9a-fA-F][0-9a-fA-F])");

    private final java.util.regex.Matcher hm = hexPattern.matcher("");

    private static final java.util.regex.Pattern testFilePattern =
            java.util.regex.Pattern.compile(
                "^(?:#.*|([^\t]*)\t+([^\t]*)\t+([^\t]*)\t+([^\t]*).*)$",
                java.util.regex.Pattern.MULTILINE);

    // test file matcher
    private final java.util.regex.Matcher tfm = testFilePattern.matcher("");

    /**
     * @param name
     */
    public AttTestCase(String name) {
        super(name);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.xtrms.regex.AbstractRxTestCase#setUp()
     */
    protected void setUp() throws Exception {
        super.setUp();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.xtrms.regex.AbstractRxTestCase#tearDown()
     */
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private String getDataName(String testDir) {
        return getClass().getResource("/resources/input/").getPath() 
                + testDir + '/' + getName().substring(4).toLowerCase() + ".txt";
    }

    // all att tests assume posix match semantics (leftmost longest)
    private static int flagsFromString(String s) {
        return Pattern.X_LEFTMOST_LONGEST; // TODO: flesh out
    }

    private String format(String input) {
        input = input.replaceAll("\\\\n", "\n");
        StringBuffer sb = new StringBuffer();
        for (hm.reset(input); hm.find(); hm.appendReplacement(sb, ""
                + (char) Integer.valueOf(hm.group(1), 16).intValue()))
            ;
        hm.appendTail(sb);
        input = sb.toString();
        return input;
    }

    private void doTest() {

        BufferedReader r = null;

        try {
            r = new BufferedReader(new FileReader(getDataName("att")));
            for (String line; (line = r.readLine()) != null;) {
                logger.fine(line);
                tfm.reset(line);
                assertTrue("malformed test line" + line, tfm.matches());
                if (tfm.start(1) == -1)
                    continue; // comment
                String flags = tfm.group(1);
                String regex = tfm.group(2);
                String input = tfm.group(3);
                String spec = tfm.group(4);
                input = input.equalsIgnoreCase("null") ? "" : input;
                spec = spec.equalsIgnoreCase("nomatch") ? "" : spec;
                assertFind(regex, flagsFromString(flags), format(input), spec);
            }
        } catch (IOException e) {
            e.printStackTrace();
            fail("IOException reading data file!!");
        } finally {
            try {
                if (r != null)
                    r.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void testTemp() {
        Pattern.compile("\\n");
        assertFind("\\n", "\n", "(0,1)");
        assertFind("abracadabra$", "abracadabracadabra", "(7,18)");
        assertFind("(Ab|cD)*", Pattern.CASE_INSENSITIVE, "aBcD", "(0,4)(2,4)");
    }

    public void testBasic() {
        doTest();
    }

    // public void testRepetition() {
    //        
    // }
}
