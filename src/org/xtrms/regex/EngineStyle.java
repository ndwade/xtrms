/* @LICENSE@  
 */
package org.xtrms.regex;

import static org.xtrms.regex.Pattern.Feature;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents each implemented matching algorithm. Internally, this enum
 * class is used as a factory to create matcher "Engine"s.
 */
public enum EngineStyle {

    /**
     * The default style, which selects the appropriate <code>Engine</code>
     * implementation based on the requirements of the regular expression
     * and the capabilities of the various available engines. Uses the sequence
     * of available EngineStyles as a search path, and selects the first
     * that meets the requirements and constructs successfully.
     */
    DYNAMIC {
        @Override
        Engine newEngine(NFA nfa) {
            Engine engine = null;
            for (EngineStyle style : EngineStyle.values()) {
                if (style == DYNAMIC) continue;
                if (style.capabilities().containsAll(nfa.requirements)) {
                    try {
                        logger.log(level, "EngineStyle selected: " + style);
                        engine = style.newEngine(nfa);
                        break;
                    } catch (ConstructionException e) {
                        logger.log(level, e.toString(), e);
                    }
                }
            }
            assert engine != null : nfa.requirements;
            return engine;
        }
    },

    /**
     * DFA implementation using tables (arrays).
     */
    DFA_TABLE("DFAtableEngine"),

    /**
     * Full featured table driven NFA implementation.
     */
//    NFA_TABLE("NFApikeEngine"); 
    NFA_TABLE("NFAtableEngine"); 


    private static final Logger logger = Logger.getLogger("org.xtrms.regex");
    private static final Level level = Level.FINEST;

    /**
     * A runtime exception thrown when a specified engine style cannot
     * be constructed with the specified pattern.
     */
    public static final class ConstructionException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ConstructionException(String msg) {
            super(msg);
        }
    }
    
    final String className;

    private final Set<Feature> capabilities;
    /**
     * @return the capabilities
     */
    public Set<Feature> capabilities() {
        return capabilities;
    }

    EngineStyle() {
        assert this.name().equals("DYNAMIC");
        this.className = null;
        this.capabilities = EnumSet.allOf(Pattern.Feature.class);
    }

    @SuppressWarnings("unchecked")  // TODO: how to do reflection type safe?!
    EngineStyle(String className) {
        this.className = className;
        try {
            Class<?> clazz = Class.forName("org.xtrms.regex." + className);
            Field f = clazz.getField("CAPABILITIES");
            capabilities = (Set<Pattern.Feature>) f.get(null);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    Engine newEngine(NFA nfa) {
        assert className != null;
        if (!capabilities.containsAll(nfa.requirements)) {
            Set<Feature> shortcomings = EnumSet.noneOf(Feature.class);
            shortcomings.addAll(nfa.requirements);
            shortcomings.removeAll(capabilities);
            assert !shortcomings.isEmpty();
            throw new ConstructionException(
                "EngineStyle: " + this + " is missing required features: "
                + shortcomings);
        }
        try {
            Class<?> clazz = Class.forName("org.xtrms.regex." + className);
            Constructor<?> ctor = clazz.getDeclaredConstructor(
                    EngineStyle.class, NFA.class);
            return (Engine) ctor.newInstance(this, nfa);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}