/* @LICENSE@  
 */
package org.xtrms.regex;

import java.util.Collection;
import java.util.Map;

import org.xtrms.regex.AST;
import org.xtrms.regex.Pattern;
import org.xtrms.regex.RegexParser;

/**
 * Encapsulates a named regular expression, to be used as a sub-expression
 * within subsequent Expressions and {@link Pattern}s. Note: the API for this
 * class is subject to change. Instances of this class are immutable and type
 * safe.
 * 
 * In lieu of massed verbiage, I'll offer the following examples from the 
 * test suite, which should provide a good illustration of how
 * to use this class:
 * <blockquote><pre>
   public void testNamedCapturingGroups() {
        
        CharSequence input = "fooXXXbarYYYclem";
        Matcher m = Pattern.compile("foo(?&lt;exes>.*)bar(?&lt;wyes>.*)clem").matcher(input);
        
        m.find();
        
        assertEquals("XXX", m.group("exes"));
        assertEquals("YYY", m.group("wyes"));
        assertEquals(m.group(1), m.group("exes"));
        assertEquals(m.group(2), m.group("wyes"));
    }
    
    public void testNamedSubExpressions() {
        //
        // This is an email regex from SpecWeb 2005:
        //  ^([a-zA-Z0-9_\\-\\.]+)@((([0-9]{1,3}\\.){3}[0-9]{1,3})|([a-zA-Z]+\\.)+[a-zA-Z]{2,4})
        // A model of clarity it is not. Let's break it down and build
        // it back up, for ease of maintenance:
        //
        Collection&lt;Expression> emailExprs = new LinkedList&lt;Expression>();
        Expression.parseAndAdd(emailExprs, "user",   "([a-zA-Z0-9_\\-\\.]+)");
        Expression.parseAndAdd(emailExprs, "ip",     "(([0-9]{1,3}\\.){3}[0-9]{1,3})");
        Expression.parseAndAdd(emailExprs, "host",   "([a-zA-Z]+\\.)+");
        Expression.parseAndAdd(emailExprs, "domain", "[a-zA-Z]{2,4}");
        Expression.parseAndAdd(emailExprs, "dns",    "&lt;host>&lt;domain>");
        Expression.parseAndAdd(emailExprs, "addr",   "&lt;ip>|(?&lt;>&lt;dns>)");
        Expression.parseAndAdd(emailExprs, "email",  "&lt;user>@&lt;addr>");
        //
        // now let's use it to snarf Jack Handy's email address:
        //
        Pattern p = Pattern.compile("^(?&lt;>&lt;email>)\\s+\"Jack Handy\"", 
            emailExprs.toArray(new Expression[emailExprs.size()]));
        Matcher m = p.matcher("deep.thoughts@snl.nbc.com  \"Jack Handy\"");
        assertTrue(m.lookingAt());
        assertEquals("deep.thoughts@snl.nbc.com", m.group("email"));
        assertEquals("snl.nbc.com", m.group("email.addr.dns"));
    }</pre></blockquote>
     
 */
public final class Expression {

    final String name;
    final AST.Node root;
    final Map<String, Integer> cgNames;
    
    @Override
    public String toString() {
        return "{" + name + '=' + root + '}';
    }

    private Expression(String name, String regex, int flags, Expression... exps) {
        this.name = name;
        RegexParser.Result r = new RegexParser().parse(regex, flags, exps);
        root = r.root;
        cgNames = r.cgNames;
    }

    public static Expression parse(String name, String regex,
            Expression... exps) {
        return new Expression(name, regex, 0, exps);
    }

    public static Expression parse(String name, String regex, int flags,
            Expression... exps) {
        return new Expression(name, regex, flags, exps);
    }

    public static Expression parse(String name, String regex,
            Collection<Expression> exps) {
        return new Expression(name, regex, 0, exps.toArray(new Expression[exps
            .size()]));
    }

    public static Expression parse(String name, String regex, int flags,
            Collection<Expression> exps) {
        return new Expression(name, regex, flags, exps
            .toArray(new Expression[exps.size()]));
    }

    public static boolean parseAndAdd(Collection<Expression> exps, String name,
            String regex) {
        return exps.add(new Expression(name, regex, 0, exps
            .toArray(new Expression[exps.size()])));
    }

    public static boolean parseAndAdd(Collection<Expression> exps, String name,
            String regex, int flags) {
        return exps.add(new Expression(name, regex, flags, exps
            .toArray(new Expression[exps.size()])));
    }

}
