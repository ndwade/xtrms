/* @LICENSE@  
 */

package org.xtrms.regex;

import static org.xtrms.regex.Misc.FS;

import java.io.File;
import java.util.BitSet;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.TestCase;

import org.xtrms.regex.AST.Node;

public abstract class AbstractRxTestCase extends TestCase {

    protected static final Logger logger = Logger.getLogger("org.xtrms.regex.test");
    protected static final Level level = Level.FINEST; 
    
    static {
        boolean assertsEnabled = false;
        assert assertsEnabled = true; // Intentional side effect!!!
        if (!assertsEnabled){
            throw new RuntimeException("Asserts must be enabled!!!");
        }
    } 
    public static void main(String[] args) {
        junit.swingui.TestRunner.run(AbstractRxTestCase.class);
    }

    public AbstractRxTestCase(String name) {
        super(name);
    }

    private Logger rxLogger = Logger.getLogger("org.xtrms.regex");
    private Handler rxHandler = null;
    
    private void mkdirs() {
        File logDir = new File(
                "log" + FS + 
                getClass().getSimpleName() + FS + 
                getName());
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
    }
    
    protected void logRxXHTML(Level level) {
        if (rxHandler != null || !rxLogger.isLoggable(level)) return;
        mkdirs();
        try {
            rxHandler = new XHTMLFileHandler(
                    this.getClass().getSimpleName(),
                    getName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (rxHandler.getLevel().intValue() <= level.intValue()) {
            rxHandler.setLevel(level);
            rxHandler.setFormatter(new XHTMLFormatter());
            rxLogger.addHandler(rxHandler);
        }
    }
    protected void logRxXHTML() {
        logRxXHTML(level);
    }

    String loggingPropertiesFile;
    protected void setUp() throws Exception {
        super.setUp();
        loggingPropertiesFile = System.getProperty(
            "java.util.logging.config.file");
//        logger.entering(this.getClass().getSimpleName(), this.getName());
//        logRxXHTML();
    }

    protected void tearDown() throws Exception {
        if (rxHandler != null) {
            rxHandler.flush();
            rxHandler.close();
            rxLogger.removeHandler(rxHandler);
            rxHandler = null;
        }
        logger.exiting(this.getClass().getSimpleName(), this.getName());
        super.tearDown();
        result.delete(0, result.length());
    }
    
    protected final StringBuilder result = new StringBuilder();
    
    protected int count = 0;

    protected static BitSet pos(Integer... integers) {
        BitSet ret = new BitSet();
        for (int i : integers) {
            ret.set(i);
        }
        return ret;
    }
    
    /*
     * wrapper for StaticSupport functions needed by subclasses outside package
     */
    protected static String esc(String s) {
        return Misc.Esc.JAVA.esc(s);
    }
    
    /**
     * Re-creates AST for a given {@link Pattern} - useful for logging and
     * testing.
     * @param p The pattern to re-create root {@link Node} for
     * @return the root <code>Node</code>.
     */
    protected static AST.Node rootOf(Pattern p) {
        return (new RegexParser().parse(p.toString(), p.flags)).root;
    }
}
