/*@LICENSE@
 */

package org.xtrms.regex;

import static junit.framework.Assert.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import java.util.regex.MatchResult;

import org.xtrms.regex.EngineStyle;
import org.xtrms.regex.Matcher;
import org.xtrms.regex.Pattern;
import org.xtrms.regex.Pattern.Feature;


/**
 * @author ndw
 *
 */
public final class RegexAssert {
    
    private RegexAssert() {}   // not instantiable.

    /*
     * Engine enumeration
     */
    public static EnumSet<EngineStyle> engineStylesFor(Pattern p) {
        EnumSet<EngineStyle> ret = EnumSet.noneOf(EngineStyle.class);
        Set<Feature> requirements = p.requirements();
        for (EngineStyle style : EngineStyle.values()) {
            if (style.ordinal() == 0  // EngineStyle.values()[0] always DYNAMIC.
                    || !style.capabilities().containsAll(requirements)) {
                continue;
            }
            ret.add(style);
        }
        assert !ret.isEmpty();
        return ret;
    }
    public static List<Pattern> allEngineStylesPatternList(Pattern p) {
        List<Pattern> ret = new LinkedList<Pattern>();
        for (EngineStyle style : engineStylesFor(p)) {
            ret.add(Pattern.compile(p.toString(), style));
        }
        return ret;
    }
    /*
     * capturing group spec based stuff
     */
    public static void assertFind(
            String regex, CharSequence input, String... specs) {
        
        assertFind(regex, 0, input, specs);
    }
    
    public static void assertFind(
            String regex, int flags, CharSequence input, String... specs) {
        
        for (EngineStyle style 
                : engineStylesFor(Pattern.compile(regex, flags))) {
            
            Matcher m = Pattern.compile(regex, flags, style).matcher(input);
            for (String spec : specs) {
                m.find();
                assertMatch(m, input, spec);
            }
        }
    }
    
    public static void assertNamedGroup(Matcher m, String name, int index) {
        assertEquals(m.group(index), m.group(name));
        assertEquals(m.start(index), m.start(name));
        assertEquals(m.end(index), m.end(name));
    }
    
    /*
     * empty spec is legit; means no match
     */
    private static final java.util.regex.Pattern specPattern = 
        java.util.regex.Pattern.compile(
            "\\A" +
                "((?:\\s*\\((?:\\?|-?\\d+),\\s*(?:\\?|-?\\d+)\\))*)" + // g[1]
                "(?:\\s*\\{([TtFf]),?\\s*([TtFf])})?" +          // g[2], g[3]
                "\\s*"
        );
 
    private static final java.util.regex.Pattern groupPattern = 
        java.util.regex.Pattern.compile(
            "(?:\\A|\\G)" +
                "(?:\\s*\\((\\?|-?\\d+),\\s*(\\?|-?\\d+)\\))"   // g[1], g[2]
        );

    private static final java.util.regex.Matcher sm = specPattern.matcher("");
    
    private static int decodeSpec(String s) {
        return s.equals("?") ? -1 : Integer.parseInt(s);
    }
    
    public static void assertMatch(Matcher m, String spec) {
        assertMatch(m, null, spec);
    }
    
    public static void assertMatch(Matcher m, CharSequence input, String spec) {

        sm.usePattern(specPattern).reset(spec);
        assertTrue("bad spec: " + spec, sm.matches());

        Boolean he = null;
        Boolean re = null;
        if (sm.group(2) != null) {
            he = sm.group(2).equalsIgnoreCase("T") ? 
                    Boolean.TRUE : Boolean.FALSE;
            re = sm.group(3).equalsIgnoreCase("T") ? 
                    Boolean.TRUE : Boolean.FALSE;
        }
        
        String groupSpecs = sm.group(1);
        sm.usePattern(groupPattern).reset(groupSpecs);
        int i;
        for (i=0; sm.find(); ++i) {
            if (i == 0) {
                assertEquals("mismatch: start: ",
                    decodeSpec(sm.group(1)), m.start());
                assertEquals("mismatch: end: ",
                    decodeSpec(sm.group(2)), m.end());
            }
            try {
                assertEquals("group[" + i + "] mismatch: start: ",
                    decodeSpec(sm.group(1)), m.start(i));
                assertEquals("group[" + i + "] mismatch: end: ",
                    decodeSpec(sm.group(2)), m.end(i));
            } catch (IllegalStateException e) {
                fail("no match found");
            }
        }
        if (i == 0) {
            try {
                m.start();
                fail("failed match: should have thrown exception");
            }
            catch (IllegalStateException e) {/* correct, expected behavior */}
        }
        
        if (input != null) {
            CharSequence[] groups = new CharSequence[i];
            for (int j=0; j<i; ++j) {
                groups[j] = m.start(j) < 0 ? 
                        null : input.subSequence(m.start(j), m.end(j));
            }
            assertGroups(m, groups);
        } else {
            checkGroupCount(i, m);
        }
        
        // hitEnd / requireEnd stuff
        if (he != null) {
            assert re != null;
            assertEquals("mismatch: hitEnd", 
                he.booleanValue(), m.hitEnd());
            assertEquals("mismatch: requireEnd", 
                re.booleanValue() , m.requireEnd());
        }
    }
    
    public static void assertGroups(MatchResult m, CharSequence... groups) {
        int i;
        for (i=0; i<groups.length; ++i) {
            if (i == 0) {
                assertEquals("mismatch: group: ", groups[0], m.group());
            }
            assertEquals("mismatch: group[" + i + "]: ", groups[i], m.group(i));
        }
        checkGroupCount(i, m);
    }

    private static void checkGroupCount(int n, MatchResult m) {
        if (n == 0) {
            try {
                m.start();
                fail("expected no match but found " + specFrom(m));
            } catch(IllegalStateException e) {}
        }
        if (n > 0) {
            assertEquals("group count mismatch", n-1, m.groupCount());
        }

    }
    
    /*
     * create specs from Matchers - shortcut to writing tests
     */
    static String[] specsFrom(Matcher m) {
        List<String> specs = new ArrayList<String>();
        while (m.find()) specs.add(specFrom(m));
        specs.add(specFrom(m)); // last is always empty
        return specs.toArray(new String[specs.size()]);
    }
    
    static String[] specsFrom(java.util.regex.Matcher m) {
        List<String> specs = new ArrayList<String>();
        while (m.find()) specs.add(specFrom(m));
        specs.add(specFrom(m)); // last is always empty
        return specs.toArray(new String[specs.size()]);
    }

    static String specFrom(Matcher m) {
        return specFrom(m, m.hitEnd(), m.requireEnd());
    }
    
    static String specFrom(java.util.regex.Matcher m) {
        return specFrom(m, m.hitEnd(), m.requireEnd());
    }
    
    private static String specFrom(MatchResult mr, boolean he, boolean re) {
        StringBuilder sb = new StringBuilder();
        sb.append(specFrom(mr))
          .append("{")
          .append(he ? 't' : 'f')
          .append(re ? 't' : 'f')
          .append('}');
        return sb.toString();
    }

    private static String specFrom(final MatchResult mr) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append(specFrom(mr.start(0), mr.end(0)));
        } catch (IllegalStateException e) {
            return "";
        }
        for (int i=1; i<=mr.groupCount(); ++i) {
            sb.append(specFrom(mr.start(i), mr.end(i)));
        }
        return sb.toString();
    }    
    
    private static String specFrom(int s, int e) {
        return "(" + (s == -1 ? "?" : s) + ',' + (e == -1 ? "?" : e) + ')';
    }
    
    /*
     * stuff to compare result to standard java lib
     */
    public static void assertJavaLookingAt(String regex, CharSequence csq) {
        java.util.regex.Pattern jp = java.util.regex.Pattern.compile(
                regex);
        java.util.regex.Matcher jm = jp.matcher(csq);
        for (EngineStyle style : engineStylesFor(Pattern.compile(regex))) {
            jm.reset(csq);
            assertJavaLookingAt(regex, csq, style, jm);
        }        
    }
    private static void assertJavaLookingAt(
            String regex, 
            CharSequence csq, 
            EngineStyle style,
            java.util.regex.Matcher jm) {
        
        Pattern p = Pattern.compile(regex, style);
        Matcher m = p.matcher(csq);
        
        String msg = 
                "rx: \"" + regex + "\", " 
                + "csq: \"" + csq + "\", " 
                + "style: " + style;
        
        boolean xFound = jm.lookingAt();
        boolean found = m.lookingAt();
        assertEquals(msg, xFound, found);
        if (found) {
            assertEquals(msg + "::start: x=" + jm.start() + " y=" + m.start(), 
                jm.start(), m.start());
            assertEquals(msg + "::end: x=" + jm.end() + " y=" + m.end(), 
                jm.end(), m.end());
            assertEquals(msg + "::hitEnd", 
                jm.hitEnd(), m.hitEnd());
            /*
             * OK to be pessimistic - requireEnd == true when not strictly 
             * necessary.
             * Reverse is not OK - when library says rE == true, so do we.
             */
            if (jm.requireEnd()) {  
                assertTrue(msg + "::requireEnd", m.requireEnd());
            }
        }
    }
    
}
