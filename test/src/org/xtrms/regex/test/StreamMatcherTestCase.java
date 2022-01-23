/*@LICENSE@
 */

package org.xtrms.regex.test;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import org.xtrms.regex.AbstractRxTestCase;
import org.xtrms.regex.Pattern;
import org.xtrms.regex.StreamMatcher;


/**
 * @author ndw
 *
 */
public class StreamMatcherTestCase extends AbstractRxTestCase {

    /**
     * @param name
     */
    public StreamMatcherTestCase(String name) {
        super(name);
    }

    /* (non-Javadoc)
     * @see org.xtrms.regex.AbstractRxTestCase#setUp()
     */
    protected void setUp() throws Exception {
        super.setUp();
    }

    /* (non-Javadoc)
     * @see org.xtrms.regex.AbstractRxTestCase#tearDown()
     */
    protected void tearDown() throws Exception {
        super.tearDown();
    }
    
    public void testReplaceAll() {
        Reader r = new StringReader("one fish two fysh red fish blue fische");
        Pattern p = Pattern.compile("f([a-z]+)");
        StreamMatcher sm = new StreamMatcher(r, p);
        Writer w = new StringWriter();
        sm.setResult(w);
        sm.replaceAll("ph$1");
        assertEquals("one phish two physh red phish blue phische", w.toString());
    }
    public void testReplaceAll2() {
        Reader r = new StringReader("one fish two fysh baby!");
        Pattern p = Pattern.compile("f([a-z]+)");
        StreamMatcher sm = new StreamMatcher(r, p);
        Writer w = new StringWriter();
        sm.setResult(w);
        sm.replaceAll("ph$1");
        assertEquals("one phish two physh baby!", w.toString());
    }
}
