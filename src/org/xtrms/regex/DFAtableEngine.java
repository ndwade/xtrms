/* @LICENSE@  
 */
package org.xtrms.regex;

import static org.xtrms.regex.DFA.Arc;
import static org.xtrms.regex.DFA.State;
import static org.xtrms.regex.Misc.EOF;
import static org.xtrms.regex.Pattern.Feature.LOOP_DBC;

import java.util.EnumSet;

final class DFAtableEngine extends Engine {

    public static final EnumSet<Pattern.Feature> CAPABILITIES = EnumSet.of(
        LOOP_DBC);
    
    private final DFA dfa;
    DFA dfa() {
        return dfa;
    }
    
    DFAtableEngine(EngineStyle style, NFA nfa) {
        super(style);
        dfa = new DFA(nfa);
    }

    @Override
    protected void eval(AbstractMatcher m) {
        
        State state;
        State nextState = dfa.init; 
        int c = m.initStatus;
        int len = 0;
        
        while(true) {
            state = nextState;
            nextState = delta(c, state.arcs);
            if (nextState == null) break;
            if (nextState.accept) {
                m.cga.start(0, 0);
                m.cga.end(0, len-1);
            }
            if (nextState.pureAccept()) {
                break;
            }
            c = m.nextChar();
            ++len;
        }
        m.hitEnd = c == EOF 
                && state.stranded;        
        m.requireEnd = c == EOF
                && m.cga.match(0)
                && m.cga.end(0) == len - 1
                && !state.containsOmega;
    }
    
    private static State delta(int c, Arc[] arcs) {
        int hi = arcs.length;
        int lo = 0;
        int i = -1;
        while (lo < hi) {
            int m = (hi + lo) >> 1;
            if (arcs[m].iv.begin <= c) {
                if (m+1 == arcs.length || c < arcs[m+1].iv.begin) {
                    i = m;
                    break;
                } else {
                    lo = m+1;
                }
            } else {
                hi = m;
            }
        }
        return i != -1 ? c < arcs[i].iv.end ? arcs[i].ns : null : null;
    }
}
