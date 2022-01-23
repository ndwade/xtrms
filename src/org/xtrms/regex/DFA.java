/* @LICENSE@  
 */


package org.xtrms.regex;


import static org.xtrms.regex.Misc.LS;
import static org.xtrms.regex.Misc.clear;
import static org.xtrms.regex.Misc.topologicalSort;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xtrms.regex.CharClass.Interval;
import org.xtrms.regex.Misc.BreadthFirstVisitor;
import org.xtrms.regex.Misc.Edge;
import org.xtrms.regex.Misc.Vertex;



final class DFA {

    private static final Logger logger = Logger.getLogger("org.xtrms.regex");
    // private static final Level level = Level.INFO; 
    private static final Level level = Level.FINEST;      
    
    /**
     * An entry in the transition table: a {@linkplain Interval symbol} mapped
     * to a next state.
     */
    static final class Arc implements Edge<State> {
        
        final Interval iv;
        final State ns;

        private Arc(Interval iv, State ns) {
            this.iv = iv;
            this.ns = ns;
        }
        
        public State vertex() {
            return ns;
        }        
        /*
         * (non-Javadoc)
         * @see java.lang.Object#toString()
         * 
         * State is "immutable enough" for this to work... ;)
         */
        @Override
        public String toString() {
            if (s != null) return s;
            StringBuilder sb = new StringBuilder();
            sb.append("{iv:").append(iv).append(',');
            sb.append(" ns:");
            ns.toLabel(sb);            
            sb.append('}');
            return s = sb.toString();
        }        
        private String s;
    }
    
    
    static final class State implements Vertex<Arc> {
                
        private final Set<NFA.State> nfaStates;
        /* private */ Arc[] arcs;   // quick and dirty protection scope easement for speed
        
        final boolean init;
        final boolean containsOmega;
        final boolean stranded;
        final boolean accept;
        
        public State(NFA nfa, Set<NFA.State> nfaStates) {
            
            this.nfaStates = Collections.unmodifiableSet(
                new LinkedHashSet<NFA.State>(nfaStates));

            this.init = nfaStates.containsAll(nfa.alpha);
            this.containsOmega = nfaStates.contains(nfa.omega);
            this.accept = nfaStates.contains(nfa.accept);
            this.stranded = nfaStates.size() > (containsOmega ? accept ? 2 : 1 : 0);
        }
        private void arcs(Arc[] arcs) {
            this.arcs = arcs;
        }
        public Iterable<Arc> edges() {
            return Collections.unmodifiableList(Arrays.asList(arcs));
        }
        
        Arc[] arcs() {
            return arcs.clone();
        }
        
        boolean contains(Set<NFA.State> nfaStates) {
            return this.nfaStates.containsAll(nfaStates);
        }
        boolean intersects(final Set<NFA.State> nfaStates) {
            Set<NFA.State> temp = new HashSet<NFA.State>(nfaStates);
            temp.removeAll(this.nfaStates);
            return !temp.isEmpty();
        }
        boolean pureAccept() {
            return accept && nfaStates.size() == 1;
        }
        
        boolean dead() {
            if (accept) return false;
            for (Arc arc : arcs()) {
                if (arc.ns != this) return false;
            }
            return true;
        }
    
        private String toLabel(StringBuilder sb) {
            final int mark = sb.length();
            for (NFA.State state : nfaStates) {
                sb.append(sb.length() == mark ? '{' : ',');
                sb.append(state.position);
            }
            sb.append('}');
            return sb.toString();
        }
        
        String toLabel() {
            return toLabel(new StringBuilder());
        }
        
        private static final String INDENT = "    ";
        private transient StringBuilder sb = new StringBuilder();
        
        @Override
        public String toString() {
    
            clear(sb);
            
            sb.append("state: ");
            toLabel(sb);        sb.append(' ');
            if (init)           sb.append("(init) ");
            if (containsOmega)  sb.append("(containsOmega) ");
            if (stranded)       sb.append("(stranded) ");
            if (accept)         sb.append("(accept) ");
            sb.append(LS);
            
            for (Arc arc : arcs) {
                sb.append(INDENT).append(arc).append(LS);
            }
            sb.append(LS);
            
            return sb.toString();
        }
    }


    private static final int MAX_STATE_COUNT = 10 * 1000;
    

    private Set<NFA.State> stateSetFrom(Iterable<NFA.Arc> arcs) {
        Set<NFA.State> ret = new LinkedHashSet<NFA.State>();
        for (NFA.Arc arc : arcs) {
            assert arc.dbcs().isEmpty();
            /*
             * Vanilla DFA can't do loop state, which requires reluctant 
             * quantifiers and true capture groups.
             */
            if (arc.ns != nfa.loop) ret.add(arc.ns);
        }
        return ret;
    }
    
    private static Arc[] arcsFrom(SortedMap<CharClass, State> ccNSmap) {
        Set<Map.Entry<Interval, State>> entrySet 
                = CharClass.intervalMapFrom(ccNSmap).entrySet();
        Arc[] ret = new Arc[entrySet.size()];
        int i=0;
        for (Map.Entry<Interval, State> e : entrySet) {
            ret[i++] = new Arc(e.getKey(), e.getValue());
        }
        return ret;
    }

    final NFA nfa;
    final State init;
    
    /**
     * Construct a complete DFA from an NFA.
     * 
     * @param nfa
     */
    @SuppressWarnings("serial")
    DFA(final NFA nfa) {
        
        this.nfa = nfa;
        
        @SuppressWarnings("serial")
        final class StateFactory {
            
            private Map<Set<NFA.State>, State> map = 
                new LinkedHashMap<Set<NFA.State>, State>();
            
            private State stateFrom(Set<NFA.State> nfaStates) {
                State state = map.get(nfaStates);
                if (state == null) {
                    state = new State(nfa, nfaStates);
                    map.put(state.nfaStates, state);
                }
                return state;
            }
        }
        final StateFactory factory = new StateFactory();
        
        /*
         * Subset construction as breadth first search
         */
        final Set<NFA.State> nextNFAstates = new LinkedHashSet<NFA.State>();
        nextNFAstates.addAll(nfa.alpha);
        
        init = factory.stateFrom(nextNFAstates);
        new BreadthFirstVisitor<State, Arc>() {
            
            SortedSet<CharClass> sigma = new TreeSet<CharClass>();
            SortedMap<CharClass, State> cc2ns = new TreeMap<CharClass, State>();
            int watchdog = 0;
            
            /*
             * Create all the arcs for the state already discovered.
             */
            @Override
            protected void visit(State state) {
                
                if (++watchdog >= MAX_STATE_COUNT) {
                    throw new EngineStyle.ConstructionException(
                        "DFA state count exceeded: " + MAX_STATE_COUNT);
                }
                
                sigma.clear();
                for(NFA.State nfaState : state.nfaStates) {
                     sigma.add(nfaState.cc);
                }
                sigma = CharClass.partition(sigma);
                
                cc2ns.clear();
                for (CharClass cc : sigma) {
                    nextNFAstates.clear();
                    for (NFA.State nfaState : state.nfaStates) {
                        if (nfaState.cc.contains(cc)) {
                            nextNFAstates.addAll(
                                stateSetFrom(nfaState.arcs()));
                        }
                    }
                    if (!nextNFAstates.isEmpty()) {
                        State nextState = factory.stateFrom(nextNFAstates);
                        cc2ns.put(cc, nextState);
                    }
                }
                state.arcs(arcsFrom(cc2ns));
            }
        }.start(init);
        
        assert new Object() {
            boolean test() {
                for (State state : states()) {
                    if (state.dead()) {
                        return false;
                    }
                }
                return true;
            }
        }.test();

        if (logger.isLoggable(level)) {
            logger.log(level, "dfa unminimized: " + toString(), this);
        }
    }
            
    Iterable<State> states() {
        return Collections.unmodifiableList(topologicalSort(init));
    }

    int size() {
        return new BreadthFirstVisitor<State, Arc>(){}.start(init).black.size();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int nArcs = 0;
        for (State state : states()) nArcs += state.arcs.length;
        sb
            .append("total states: ").append(size())
            .append(" total arcs ").append(nArcs)
            .append(LS);
        for (State state : states()) {
            sb.append(state);
        }        
        return sb.toString();
    }
}