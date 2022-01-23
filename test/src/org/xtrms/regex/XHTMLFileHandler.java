/* @LICENSE@  
 */

/**
 * 
 */
package org.xtrms.regex;

import static org.xtrms.regex.Misc.FS;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;

/**
 * 
 * This class exists so that the Formatter can snarf the directory;
 * also so that logger.properties can set a default on this
 * Handler class.
 * 
 * @author nick
 *
 */
public final class XHTMLFileHandler extends FileHandler {
    
    final String testClass;
    final String testName;
    final String dir;

    /**
     * make separate fields so that Formatter can snarf the directory
     */
    public XHTMLFileHandler(String testClass, String testName) throws IOException {
        super("log" + FS + testClass + FS + testName + ".xhtml");
        this.testClass = testClass;
        this.testName = testName;
        this.dir = "log" + FS + testClass + FS + testName + FS;

    }
    @Override
    public void setFormatter(Formatter newFormatter) throws SecurityException {
        super.setFormatter(newFormatter);
        if (newFormatter instanceof XHTMLFormatter) {
            XHTMLFormatter formatter = (XHTMLFormatter) newFormatter;
            formatter.m_dir = dir;
        }
    }
}
