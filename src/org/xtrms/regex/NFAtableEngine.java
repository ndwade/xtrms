/*@LICENSE@
 */
package org.xtrms.regex;

import static org.xtrms.regex.Misc.EOF;
import static org.xtrms.regex.Misc.LS;
import static org.xtrms.regex.Misc.isSet;
import static org.xtrms.regex.Misc.tagsStringFrom;
import static org.xtrms.regex.Pattern.Feature.*;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.xtrms.regex.AbstractMatcher.CGA;
import org.xtrms.regex.AbstractMatcher.DBC;


final class NFAtableEngine extends Engine {

    public static final EnumSet<Pattern.Feature> CAPABILITIES = EnumSet.of(
        CAPTURING_GROUPS, DYNAMIC_BOUNDARIES, RELUCTANT_QUANTIFIERS,
        FIND_LOOP, LOOP_DBC, LEFTMOST_FIRST);
    
    private final class Arc {
        
        final boolean[] tags;
        final DBC[] dbcs;
        final int ns;

        public Arc(boolean[] tags, DBC[] dbcs, int ns) {
            this.tags = tags;
            this.dbcs = dbcs;
            this.ns = ns;
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('{')
              .append(tagsStringFrom(tags)).append(',')
              .append(Arrays.toString(dbcs)).append(',')
              .append("ns=").append(ns)
              .append('}');
            return sb.toString();
        }
    }
    
    private final class State {
        
        final CharClass cc;
        final Arc[] arcs;
        final int position; // for ease of debug only
        final int i;        // ditto

        public State(CharClass cc, Arc[] arcs, int position, int i) {
            this.cc = cc;
            this.arcs = arcs;
            this.position = position;
            this.i = i;
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("i=").append(i).append(',')
              .append("pos:").append(position).append(',')
              .append("cc:").append(cc).append(',')
              .append("arcs:").append(Arrays.toString(arcs));
            return sb.toString();
        }
    }

    /*
     * why "Strand"? Because it's not a Thread, dammit. Just not. m'k? ;)
     */
    private final class Strand {
        
        int s = -1;
        final CGA cga;
        DBC[] dbcs = null;
        
        Strand() {
            cga = new CGA(tncg);   // 1 is for cg[0] - total match
        }
        private void init(int a) {
            s = a;
            cga.clear();
            dbcs = nildbcs;
        }
        
        @Override
        public String toString() {
            return   "{s=" + s  + ','
                    + "p=" + states[s].position + ','
                    + "dbc=" + Arrays.toString(dbcs) + ',' 
                    + "cg=" + cga + '}';
        }
    }
    
    /*
     * StrandList 
     *  operations
     *  - add a Strand from the pool to the end
     *      - keep track of how many have been added: size() in constant time
     *  - remove all strands (returning all at once to the pool)
     *  - iterate through the Strands
     */
    
    private final class StrandList {
        int size = 0;
        int initialized = 0;
        Strand[] strands = new Strand[1];   // DEBUG ONLY
        Strand next() {
            if (size == initialized) {
                if (size == strands.length) {
                    Strand[] newstrands = new Strand[strands.length * 2];
                    System.arraycopy(strands, 0, newstrands, 0, strands.length);
                    this.strands = newstrands;
                }
                strands[initialized++] = new Strand();
            }
            return strands[size];
        }
        void expand() {
            Strand[] newstrands = new Strand[strands.length * 2];
            System.arraycopy(strands, 0, newstrands, 0, strands.length);
            this.strands = newstrands;
        }
        void commit(Strand s) {
            assert strands[size] == s;
            ++size;
        }
        void clear() {size = 0;}
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<size; ++i) 
                sb.append(i == 0 ? "[" : ", ").append(strands[i]);
            sb.append(']');
            return sb.toString();
        }
    }

    final int tncg;
    final State[] states;
    final int[] alpha;
    int omega = -1;
    int accept = -1;
    final DBC[] nildbcs = new DBC[0];
    final boolean leftmostBiased;


    public NFAtableEngine(EngineStyle style, final NFA nfa) {

        super(style);
        tncg = nfa.tncg;
        Map<NFA.State, Integer> s2i = new HashMap<NFA.State, Integer>();
        int i = 0;
        for (NFA.State s : nfa.states()) s2i.put(s, i++);
        states = new State[i];
        for (NFA.State s : s2i.keySet()) {
            Arc[] arcs = new Arc[s.size()];
            states[i = s2i.get(s)] = new State(s.cc, arcs, s.position, i);
            if (s == nfa.omega) omega = i;
            if (s == nfa.accept) accept = i;
            int j = 0;
            for (NFA.Arc a : s.arcs()) {
                Arc arc = arcs[j++] = new Arc(
                    a.tags, new DBC[a.dbcs.size()], s2i.get(a.ns));
                int k = 0;
                for (DBC dbc : a.dbcs) arc.dbcs[k++] = dbc; 
            }
        }
        alpha = new int[nfa.alpha.size()];
        i = 0;
        for (NFA.State s : nfa.alpha) {
            alpha[i++] = s2i.get(s);
        }
        leftmostBiased = !isSet(nfa.pattern.flags, Pattern.X_LEFTMOST_LONGEST);
        // N.B. : no reference to NFA is kept around.
    }

    private final class MLS {
        StrandList curr = new StrandList(); 
        StrandList next = new StrandList();
        int[] lenOfState = new int[states.length];
        void swap() {
            StrandList temp = curr;
            curr = next;
            next = temp;
            next.clear();
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{curr=").append(curr).append(LS)
              .append(" next=").append(next).append(LS)
              .append(" lens=").append(Arrays.toString(lenOfState)).append('}');
            return sb.toString();
        }
    }
    
    @SuppressWarnings("unused")
    private void propagate(int len, Strand src, Arc arc, Strand dst) {
        dst.s = arc.ns;
        dst.dbcs = arc.dbcs;
        CGA.propagate(len, src.cga, arc.tags, dst.cga);
    }

    @Override
    protected void eval(AbstractMatcher m) {
        
        int len = 0;
        int c = m.initStatus;
        MLS mls = m.mls != null ? (MLS)  m.mls : (MLS) (m.mls = new MLS());
        
        int ls, rs;
        StrandList temp;
        boolean[] tags;
        CGA src, dst;
        Strand sq;
        
        for (int a : alpha) {
            Arrays.fill(mls.lenOfState, -1);
            Strand s = mls.next.next();
            s.init(a);
            mls.next.commit(s);
        }
        
        while (true) {
            
//            mls.swap();
            temp = mls.curr;
            mls.curr = mls.next;
            mls.next = temp;
            mls.next.clear();
            
            next_state:
            for (int i=0; i<mls.curr.size; ++i) {
                
                Strand sp = mls.curr.strands[i];
                /*
                 * if we have a match, then all other potitial matches must be
                 * _as_ leftmost.
                 */
//                if (m.cga.match(0) && m.cga.start(0) < sp.cga.start(0)) break;
                if (m.cga.a[0] != -1 && m.cga.a[0] < sp.cga.a[0]) break;
                
                /*
                 * if the Dynamic Boundary Check fails, it's as if the state
                 * had never been put on the list.
                 */
                for (DBC dbc : sp.dbcs) if (!dbc.check(m)) continue next_state;
                
                State state = states[sp.s];
                if (state.cc.contains(c)) {
                    for (Arc arc : state.arcs) {
                        assert len >= mls.lenOfState[arc.ns];
                        if (len > mls.lenOfState[arc.ns]) {
                            
//                            Strand sq = mls.next.next();
                            if (mls.next.size == mls.next.initialized) {
                                if (mls.next.size == mls.next.strands.length) {
                                    mls.next.expand();
                                }
                                mls.next.strands[mls.next.initialized++] = new Strand();
                            }
                            sq = mls.next.strands[mls.next.size];
                            
//                            propagate(len, sp, arc, sq);
                            sq.s = arc.ns;
                            sq.dbcs = arc.dbcs;
                            tags = arc.tags;
                            src = sp.cga;
                            dst = sq.cga;
                            assert src.length == dst.length && src.length == tags.length;
                            for (int i1=0; i1 < src.length; ++i1) {
                                dst.a[i1] = tags[i1] ? len : src.a[i1];
                            }
                            
                            if (arc.ns == accept) {
                                
                                if (leftmostBiased) {
//                                    m.cga.copyFrom(sq.cga);
                                    assert sq.cga.a.length == m.cga.a.length;
                                    System.arraycopy(
                                        sq.cga.a, 0, m.cga.a, 0, m.cga.a.length);
                                    break next_state;
                                } else {
//                                    if (CGA.lefterLonger(sq.cga, m.cga)) {
//                                        m.cga.copyFrom(sq.cga);
//                                  }
                                    if (((ls = sq.cga.a[0]) != -1) 
                                            && ((  rs =  m.cga.a[0]) == -1
                                                || ls < rs
                                                || (   ls == rs 
                                                    && sq.cga.a[1] > m.cga.a[1]))
                                                    ) {
                                        assert sq.cga.a.length == m.cga.a.length;
                                        System.arraycopy(
                                            sq.cga.a, 0, m.cga.a, 0, m.cga.a.length);
                                    }
                                }
                                
                            } else {
//                                mls.next.commit(sq);
                                assert mls.next.strands[mls.next.size] == sq;
                                ++mls.next.size;
                            }
                            /*
                             * cut off further propagations to position q
                             * ONLY if the path there is unconditional.
                             */
                            if (sq.dbcs.length == 0) {
                                mls.lenOfState[arc.ns] = len;
                            }
                        } 
                    }
                }
            }
            
            if (mls.next.size == 0) break;
            
            c = m.nextChar();
            ++len;
        }
        /*
         * this is awkward, but the start(0) test is necessary:
         * consider "foo\Z|\z" matching "foo{EOF}", and then
         * getting a subsequent "x" - the match would be
         * lost, but without a start(0) test here the 
         * omega from the last iteration of the find()
         * would be active (with a start(0) == 3) and 
         * traversedOmega would be true, killing the
         * requireEnd, which would be wrong. 
         * 
         * This can be made more efficient, at the cost
         * of making it more pessimistic, which is 
         * probably the right tradeoff.
         */
        boolean stranded = false;
        boolean containsOmega = false; // cannot lose match if true
        int start = mls.curr.strands[0].cga.start(0);
        for (int i=0; i<mls.curr.size; ++i) {
            Strand s = mls.curr.strands[i];
            if (s.cga.start(0) == start) {
                if (s.s != omega) stranded = true;
                else containsOmega = true;
            } else break;
        }
        m.hitEnd = c == EOF 
                && stranded;
        m.requireEnd = c == EOF
                && m.cga.match(0)
                && m.cga.end(0) == len - 1
                && !containsOmega;
        
    }
}
