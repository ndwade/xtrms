/* @LICENSE@  
 */


package org.xtrms.regex.test;

import org.xtrms.regex.CharClassTestCase;
import org.xtrms.regex.RegexParserTestCase;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllShort {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(AllShort.suite());
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("Short test suite.");
        //$JUnit-BEGIN$
        suite.addTestSuite(CharClassTestCase.class);
        suite.addTestSuite(MatcherTestCase.class);
        suite.addTestSuite(StreamMatcherTestCase.class);
        suite.addTestSuite(RegexParserTestCase.class);
        suite.addTestSuite(AnchorsTestCase.class);
        suite.addTestSuite(CaptureGroupTestCase.class);
        suite.addTestSuite(AttTestCase.class);
        //$JUnit-END$
        return suite;
    }

}
