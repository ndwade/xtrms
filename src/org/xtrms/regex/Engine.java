/* @LICENSE@  
 */
package org.xtrms.regex;

abstract class Engine {
    
    final EngineStyle style;
    protected Engine(EngineStyle style) {
        this.style = style;
    }
    
    abstract protected void eval(AbstractMatcher m);
    
    @Override
    public final String toString() {
        return style + ": " + doToString();
    }
    
    protected String doToString() {
        return getClass().getName() + "@" + Integer.toHexString(hashCode());
    }    
}
