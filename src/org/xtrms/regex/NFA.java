/*
 * @LICENSE@
 */

/**
 * NFA: Nondeterministic Finite Automata - plus some extras.
 */
package org.xtrms.regex;

import static org.xtrms.regex.AST.captureGroup;
import static org.xtrms.regex.AST.cat;
import static org.xtrms.regex.AST.terminal;
import static org.xtrms.regex.ArcContainer.disjointNS;
import static org.xtrms.regex.Misc.LS;
import static org.xtrms.regex.Misc.tagsStringFrom;
import static org.xtrms.regex.Misc.topologicalSort;
import static org.xtrms.regex.Misc.isSet;
import static org.xtrms.regex.RegexParser.CC_WORD; 
import static org.xtrms.regex.RegexParser.CC_NWORD; 
import static org.xtrms.regex.CharClass.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xtrms.regex.AST.Alt;
import org.xtrms.regex.AST.CG;
import org.xtrms.regex.AST.Cat;
import org.xtrms.regex.AST.Node;
import org.xtrms.regex.AST.Plus;
import org.xtrms.regex.AST.Quantifier;
import org.xtrms.regex.AST.Question;
import org.xtrms.regex.AST.Star;
import org.xtrms.regex.AST.Terminal;
import org.xtrms.regex.AST.Quantifier.Mood;
import org.xtrms.regex.AST.Visitor.TraversalOrder;
import org.xtrms.regex.AbstractMatcher.DBC;
import org.xtrms.regex.ArcContainer.FWP.Mode;
import org.xtrms.regex.Misc.BreadthFirstVisitor;
import org.xtrms.regex.Misc.DepthFirstVisitor;
import org.xtrms.regex.Misc.Edge;
import org.xtrms.regex.Misc.Vertex;
import org.xtrms.regex.Pattern.Feature;


final class NFA {

    private static final Logger logger = Logger.getLogger("org.xtrms.regex");
    private static final Level level = Level.FINER;

    /**
     * Implementation notes:
     * <p>
     * hitEnd() should be meaningful regardless of match result. <br>
     * requireEnd() only has meaning when positive match result.
     * <p>
     * question: can you ever requireEnd if you don't hitEnd? I don't think so!
     * If (c == EOF) then a match is (possibly) loseable, because EOF maybe be
     * non-EOF with more input, and POUND won't be reached.<br>
     * The EOF test is crucial because the EOF -> non EOF change is the _only
     * change possible_ on replay with more input! All else is the same!
     * <p>
     * It's not essential to be _exact_ with hitEnd and requireEnd(). It is OK
     * to be pessimistic with regard to hitEnd() and requireEnd() - it's not OK
     * to be optimistic!
     * <p>
     * The initState an int which represents the init conditions: one bit set
     * for each condition, also bit31 set (negative). This is passed into the
     * Engine.eval() method (via the Matcher) as the first "character" to run
     * through the engine. Set up the special CharClasses to correctly contain
     * the inital state. Each possible init state in the posAttr map is a
     * negative position. That way, the engine just activates all the intial
     * states and feeds the (int) init parameter into the machine. CharClass
     * special instances constructed so as to contain the equivalent flag:
     * CharClass.BOL := all intervals for which bit 1 is set, etc. <tbody>
     * <code>
     *      case    \A  \G  ^   \b  \B  $   \Z  \z
     *      init    X   X   X   X   X
     *      omega               X   X   X       X
     * </code>
     */

    /**
     * EdgeAttributes is an immutable class representing the path betwen, tags,
     * and Dynamic Boundaries
     */
    private static class EdgeAttributes 
            /* implements Comparable<EdgeAttributes> */ {
        
        protected final boolean[] tags;
        protected final EnumSet<DBC> dbcs;
        
        EdgeAttributes(int tncg) {
            this.tags = new boolean[2 * tncg];      // 2 tags per capture group
            this.dbcs = EnumSet.noneOf(DBC.class);
        }
        boolean[] tags() {
            return tags.clone();
        }
        EnumSet<DBC> dbcs() {
            return EnumSet.copyOf(dbcs);
        }
        int tncg() {
            return tags.length >> 1;
        }
        EdgeAttributes copy() {
            EdgeAttributes ea = new EdgeAttributes(tags.length);
            ea.initFrom(this);
            return ea;
        }
        private EdgeAttributes initFrom(final EdgeAttributes  ea) {
            assert isEmpty();
            merge(ea);
            return this;
        }
        private EdgeAttributes merge(final EdgeAttributes ea) {
            boolean[] src = ea.tags;
            assert src.length == tags.length;
            for (int i = 0; i < src.length; ++i) {
                tags[i] |= src[i];
            }
            dbcs.addAll(ea.dbcs);
            return this;
        }
        boolean isTagFree() {
            return noTagsSet(tags);
        }
        protected boolean isEmpty() {
            return isTagFree() && dbcs.isEmpty();
        }
//        private static int lt(boolean lhs, boolean rhs) {
//            if (!lhs && rhs) {
//                return -1;
//            } else if (lhs && !rhs) {
//                return 1;
//            } else return 0;
//
//        }
//        public int compareTo(EdgeAttributes ea) {
//            int ret;
//            for (int i = 0; i < tags.length; i+=2) {
//                if ((ret = lt(   tags[i],  ea.tags[i]))   != 0) return ret;
//                if ((ret = lt(ea.tags[i+1],   tags[i+1])) != 0) return ret;
//            }
//            assert dbcs.equals(ea.dbcs);
//            return 0;
//        }
        
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((dbcs == null) ? 0 : dbcs.hashCode());
            result = prime * result + Arrays.hashCode(tags);
            return result;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null)
                return false;
            if (!(o instanceof EdgeAttributes))
                return false;
            final EdgeAttributes ea = (EdgeAttributes) o;
            if (dbcs == null) {
                if (ea.dbcs != null)
                    return false;
            } else if (!dbcs.equals(ea.dbcs))
                return false;
            if (!Arrays.equals(tags, ea.tags))
                return false;
//            assert compareTo(ea) == 0;
            return true;
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            sb.append(tagsStringFrom(tags));
            final int mark = sb.length();
            for (DBC dbc : dbcs) {
                sb.append(isTagFree() && sb.length() == mark ? "" : ",").append(dbc.cc);
            }
            sb.append('}');
            return sb.toString();
        }
    }
    
    static final class Arc extends EdgeAttributes implements Edge<State> {
        
        final State ns;     // next state

        private Arc(State ns, int tncg) {
            super(tncg);
            this.ns = ns;
        }
        static Arc emptyArc(State ns) {
            return new Arc(ns, 0);
        }
        Arc copy() {
            Arc arc = new Arc(ns, tncg());
            ((EdgeAttributes) arc).initFrom(this);
            return arc;
        }
        private Arc mergeEA(EdgeAttributes ea) {
            super.merge(ea);
            return this;
        }
        public State vertex() {
            return ns;
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((ns == null) ? 0 : ns.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!super.equals(obj))
                return false;
            if (!(obj instanceof Arc))
                return false;
            final Arc other = (Arc) obj;
            if (ns == null) {
                if (other.ns != null)
                    return false;
            } else if (!ns.equals(other.ns))
                return false;
            return true;
        }
        @Override
        public String toString() {
            return Integer.toString(ns.position) + ':' + super.toString();
        }
    }

    static final class State implements Vertex<Arc> {

        final int position;
        final CharClass cc;
        private final ArcContainer.FWP fwp;

        private State(int position, CharClass cc) {
            this(position, cc, new ArcContainer.FWP());
        }
        private State(int position, CharClass cc, ArcContainer.FWP fwp) {
            this.position = position;
            this.cc = cc;
            this.fwp = fwp;
        }
        
        Iterable<Arc> arcs() {
            return fwp.arcs();
        }
        
        int size() {return fwp.size();}
        
        public Iterable<Arc> edges() {
            return fwp.arcs();
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("pos:").append(position).append(',');
            sb.append("cc:").append(cc).append(',');
            sb.append("fwp:").append(fwp);
            return sb.toString();
        }
    }


    private static final Node OMEGA_POUND = 
        cat(terminal(CharClass.OMEGA),      // nPos - 2
            terminal(CharClass.ACCEPT));     // nPos - 1

//    private static final CharClass WORD_ZED =
//            new CharClass.Builder(RegexParser.CC_WORD).add(CharClass.EOF).build("\\w|\\z");

    private static final CharClass NWORD_ZED =
            new CharClass.Builder(RegexParser.CC_NWORD).add(CharClass.EOF).build("\\W|\\z");

    private static boolean noTagsSet(boolean[] tags) {
        for (boolean t : tags) if (t) return false;
        return true;
    }
    
    private static final EnumSet<DBC> ALL_INIT_DBC = EnumSet.noneOf(DBC.class);
    static {
        for (DBC dbc : DBC.values()) {
            if (CharClass.ALL_INIT.contains(dbc.cc)) {
                ALL_INIT_DBC.add(dbc);
            }
        }
    }
    
    private static final EnumSet<DBC> ALL_LP_DBC = EnumSet.of(
        DBC.DOLLAR_UNICODE, DBC.DOLLAR_UNIX, DBC.EOF, DBC.WORD_B, DBC.WORD_NB);

    /*
     * static version of total ordering of tag values - we can do the tag
     * comparison staticall in this case because the source and dest states are
     * the same, just taking two possible paths: the ordering function will
     * always chose the same path. In other words, two rows in the transition
     * relation <sourceState, cs, tags, destState> _never_ differ in "tags"
     * only.
     */

    final Pattern pattern;
    final int tncg;
    final State loop;
    final List<State> alpha;
    final State omega;
    final State accept;
    final Set<Feature> requirements;
    
    /**
     * Construct the NFA from a parsed <code>Pattern</code>. The constructor
     * calculates attributes for nodes in the AST using recursive AST traversal
     * algorithms based on those found in Chapter 3 of the Dragon book and in
     * Ville Laurikari's thesis. These attributes are needed for the table
     * generation for the different engine styles.
     * 
     * @param pattern
     *            The {@link Pattern} instance used to create <code>root</code>.
     * @param root
     *            The {@linkplain AST abstract syntax tree} created from
     *            <code>pattern</code>.
     */
    @SuppressWarnings("serial")
    NFA(final Pattern pattern, final Node root) { 

        this.pattern = pattern;
        final Node augmentedRoot = cat(captureGroup(0, root), OMEGA_POUND);
        
        // workaround for formatter syntax issue - toTreeString output was
        // triggering parameter formatting for Terminal(0)
        logger.log(level, "augmentedRoot: ", augmentedRoot);
        logger.log(level, "augmentedRootTree: " + augmentedRoot.toTreeString());

        tncg = pattern.ncg + 1;     // includes cg[0] - the complete match

        final EnumSet<Feature> requirements = EnumSet.noneOf(Feature.class);

        final class NodeAttributes {
            
            private boolean nullable = false;
            final EdgeAttributes emptyMatch;
            final ArcContainer.FP fp;
            final ArcContainer.LP lp;
        
            private NodeAttributes(int tncg) {
                emptyMatch = new EdgeAttributes(tncg);
                fp = new ArcContainer.FP();
                lp = new ArcContainer.LP();
            }
        
            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append("nullable=" + nullable).append(LS);
                sb.append('\t').append("fp: ").append(fp).append(LS);
                sb.append('\t').append("lp: ").append(lp).append(LS);
                sb.append('\t').append("em: ").append(emptyMatch)
                    .append(LS).append(LS);
                return sb.toString();
            }
        
        }
        final Map<Node, NodeAttributes> nodeAttr =
                new LinkedHashMap<Node, NodeAttributes>();
        
        final State[] omegaRef = new State[1];
        final State[] acceptRef = new State[1];
        
        /*
         * compute the First and Last positions for node attributes; also the
         * nullable property for nodes
         */

        /**
         * cursor is an internal state of PositionSet. A null falue is possible
         * and means that there is no cursor. fp/lp Visitor: merges
         * PositionSets.
         * <p>
         * PositionSet disjoint merge ops incorporating cursor: (why disjoint?
         * non-reconvergence property of AST insures the same terminal node is
         * not reacheable in two different paths)
         * <p>
         * A PositionSet which is the fp or lp of a node will have a cursor iff
         * the node is nullable.
         * <p>
         * 1) merge disjoint at end regardless of cursor. retain upper cursor if
         * present, otherwise lower, otherwise null. (Alt)
         * <p>
         * 2) merge disjoint at current cursor, use new cursor (Cat, pulling
         * lastpost postitions into firstpos because firstpos is nullable and
         * therefore has a cursor). (vice versa for fp/lp). Iff the PositionSet
         * which is merged also has a cursor, this becomes the new cursor.
         * <p>
         * 3)
         * <p> * - introduce cursor at end.
         * <p> *? - introduce cusror at start
         * <p> ? - leave cursor if present, otherwise introduce cursor at end
         * <p> ?? - introduce cursor at start.
         * <p> + - nop.
         * <p> +? - nop.
         * <p>
         * fwp Visitor:
         * <p>
         * if you are adding positions to a non-empty fwp set, then it _must_ be
         * the case that there is a cursor for that set.
         * <p>
         * Cat node: adding a set of lp to the fwp - like merge for node
         * attribute visitor. Is always a disjoint merge
         * <p>
         * Star node: same as Cat, except the merge of new fwp sets is not
         * disjoint.
         * <p> - If a duplicate fwp state is found which is higher in prio in
         * the original list, then delete the member of the new fwp set before
         * merging.
         * <p> - If a duplicate fwp state is found which is lower in prio in the
         * original list, then delete that lower priority state in the original
         * set.
         * <p>
         * why this matters: tags!!!
         * 
         *<p>
         * Nota Bene: the merging of EmptyMatchTags has to be done for the 
         * final new set of fwp states. Need to be careful to do this correctly.
         * Plan: create a _copy_ of the proposed new fwp set, then do the 
         * deletions of the higher priority duplicates, then merge the EMT,
         * then add in at the cursor.
         * 
         * <p> Kill EmptyMatchTag propagation for reluctant nodes. Inspection
         * of Cox diagrams for the NFA graphs (with prioritized arcs) reveals
         * that the empty match tags are in fact bypassed for reluctant quants.
         */
        final int npos = new AST.Visitor(TraversalOrder.BOTTOM_UP) {
            
            private int position = 0;

            @Override
            protected void visit(Node node) {
                NodeAttributes na = nodeAttr.put(node, new NodeAttributes(tncg));
                if (na != null) {
                    throw new IllegalStateException("reconvergence: node "
                            + node);
                }
                na = nodeAttr.get(node);
                /*
                 * do type specific init
                 */
                super.visit(node);
            }

            @Override
            protected void visit(Terminal node) {
                
                State ns = new State(position++, node.cc);

                if (node.cc.isDynamicBoundary()) {
                    requirements.add(Feature.DYNAMIC_BOUNDARIES);
                }

                Arc fa = new Arc(ns, tncg);
                Arc la = new Arc(ns, tncg);

                NodeAttributes na = nodeAttr.get(node);
                na.nullable = false;
                na.fp.add(fa);
                na.lp.add(la);

                if (node.cc.isDynamicBoundary()) {
                    fa.dbcs.add(DBC.DBCofCC(node.cc));
                }
                
                if      (node.cc.equals(CharClass.OMEGA))  omegaRef[0]  = ns;
                else if (node.cc.equals(CharClass.ACCEPT)) acceptRef[0] = ns;
            }

            @Override
            protected void visit(Alt node) {

                NodeAttributes na = nodeAttr.get(node);
                NodeAttributes na1 = nodeAttr.get(node.first);
                NodeAttributes na2 = nodeAttr.get(node.second);

                na.nullable = na1.nullable || na2.nullable;

                assert na.emptyMatch.isEmpty() : na.emptyMatch;
                if (na1.nullable) {
                    na.emptyMatch.merge(na1.emptyMatch);
                } else {
                    na.emptyMatch.merge(na2.emptyMatch);
                }

                na.fp.initFrom(na1.fp);
                na.fp.appendAndAdjustCursor(na2.fp.copy());

                na.lp.initFrom(na1.lp);   
                na.lp.addAll(na2.lp.copy());   
            }

            @Override
            protected void visit(Cat node) {

                NodeAttributes na = nodeAttr.get(node);
                NodeAttributes na1 = nodeAttr.get(node.first);
                NodeAttributes na2 = nodeAttr.get(node.second);

                na.nullable = na1.nullable && na2.nullable;

                assert na.emptyMatch.isEmpty() : na.emptyMatch;
                na.emptyMatch.merge(na1.emptyMatch);
                na.emptyMatch.merge(na2.emptyMatch);

                na.fp.initFrom(na1.fp);
                if (na1.nullable) {
                    ArcContainer.FP ps = na2.fp.copy();
                    for (Arc arc : ps.arcs()) arc.mergeEA(na1.emptyMatch);
                    assert disjointNS(na.fp.arcs(), ps.arcs());
                    na.fp.addAllAtCursor(ps);
                }

                assert na.lp.isEmpty();
                na.lp.initFrom(na2.lp);
                if (na2.nullable) {
                    ArcContainer.LP ps = na1.lp.copy();
                    for (Arc arc : ps.arcs()) arc.mergeEA(na2.emptyMatch);
                    assert disjointNS(na.lp.arcs(), ps.arcs());
                    na.lp.addAll(ps);  // note: no cursors needed
                }
            }
            
            @Override
            protected void visit(Star node) {
                
                NodeAttributes na = nodeAttr.get(node);
                NodeAttributes naChild = nodeAttr.get(node.child);

                na.nullable = true;

                assert na.emptyMatch.isEmpty() : na.emptyMatch;
                if (naChild.nullable && node.mood != Mood.RELUCTANT) {
                    na.emptyMatch.merge(naChild.emptyMatch);
                }
                na.fp.initFrom(naChild.fp);
                if (node.mood == Mood.RELUCTANT) {
                    na.fp.setCursorStart();
                } else {
                    na.fp.setCursorEnd();
                }
                assert na.lp.isEmpty();
                na.lp.initFrom(naChild.lp);
            }

            @Override
            protected void visit(Question node) {
                /*
                 * derivation of capturing group tag calculations: fp(node?) :=
                 * alt(fp(epsilon), fp(node)) := fp(node)
                 */
                NodeAttributes na = nodeAttr.get(node);
                NodeAttributes naChild = nodeAttr.get(node.child);

                na.nullable = true;

                assert na.emptyMatch.isEmpty() : na.emptyMatch;
                if (naChild.nullable && node.mood != Mood.RELUCTANT) {
                    na.emptyMatch.merge(naChild.emptyMatch);
                }
                assert na.fp.isEmpty();
                na.fp.initFrom(naChild.fp);
                if (node.mood == Mood.RELUCTANT) {
                    na.fp.setCursorStart();
                } else {
                    na.fp.maybeSetCursorEnd(); // different from Star
                }
                assert na.lp.isEmpty();
                na.lp.initFrom(naChild.lp);   // note: no cursor
            }

            @Override
            protected void visit(Plus node) {
                /*
                 * invariant: all props calculated as if substituted by
                 * and(child, star(child))
                 *
                 * derivation of capturing group tag calculations: 
                 * fp(node+) := cat(fp(node), fp(node*))
                 */
                NodeAttributes na = nodeAttr.get(node);
                NodeAttributes naChild = nodeAttr.get(node.child);

                na.nullable = naChild.nullable;
                
                assert na.emptyMatch.isEmpty() : na.emptyMatch;
                na.emptyMatch.merge(naChild.emptyMatch);

                assert na.fp.isEmpty();
                na.fp.initFrom(naChild.fp);
                
                assert na.lp.isEmpty();
                na.lp.initFrom(naChild.lp);
                
                if (naChild.nullable) {
                    for (Arc arc : na.fp.arcs()) arc.mergeEA(naChild.emptyMatch);
                    for (Arc arc : na.lp.arcs()) arc.mergeEA(naChild.emptyMatch);
                }
            }

            @Override
            protected void visit(CG node) {
                
                NodeAttributes na = nodeAttr.get(node);
                NodeAttributes naChild = nodeAttr.get(node.child);
                
                na.nullable = naChild.nullable;
                
                assert na.fp.isEmpty();
                na.fp.initFrom(naChild.fp);
                
                assert na.lp.isEmpty();
                na.lp.initFrom(naChild.lp);
                
                na.emptyMatch.merge(naChild.emptyMatch);
                int ts = node.cgi * 2;
                na.emptyMatch.tags[ts] = na.emptyMatch.tags[ts + 1] = true;
                for (Arc arc : na.fp.arcs()) arc.tags[ts] = true;
                for (Arc arc : na.lp.arcs()) arc.tags[ts + 1] = true;
            }
            
            int npos() {
                visit(augmentedRoot);
                return position;
            }

        }.npos();

        logger.log(level, "nodeAttr: " + nodeAttr.toString(), nodeAttr);
        
        /*
         * based on convention that the augmentation is _omega_ _accept_.
         */
        accept = acceptRef[0];
        assert accept != null && accept.cc.equals(CharClass.ACCEPT);
        omega = omegaRef[0];
        assert omega != null && omega.cc.equals(CharClass.OMEGA);

        logger.log(level, "npos: " + npos, npos);

        /*
         * intial position for single pass eval
         */
        ArcContainer.FWP ifwp = new ArcContainer.FWP(Mode.LIST);
        ifwp.initFrom(nodeAttr.get(augmentedRoot).fp);
        ifwp.clearCursor(); // FIXME: ?
        State init = new State(-1, CharClass.ALL_INIT, ifwp);
        /*
         * initial position for self loop (find)
         */
        ArcContainer.FWP lfwp = ifwp.copy();
        loop = new State(npos, CharClass.DOT_ALL, lfwp);
        lfwp.add(new Arc(loop, tncg));
        
        Arc loopArc = new Arc(loop, tncg);
        loopArc.dbcs.add(DBC.LOOP);
        ifwp.add(loopArc);

        /*
         * compute the positions following each position, including tag sets.
         */
        new AST.Visitor(TraversalOrder.BOTTOM_UP) {

            @Override
            protected void visit(Cat node) {
                
                ArcContainer.FP fp = nodeAttr.get(node.second).fp;
                
                for (Arc lpa : nodeAttr.get(node.first).lp.arcs()) {
                    ArcContainer.FP temp = fp.copy();
                    for (Arc arc : temp.arcs()) arc.mergeEA(lpa);
                    lpa.ns.fwp.addAllAtCursor(temp);
                }
            }

            @Override
            protected void visit(Star node) {
                repeatingQuantifierFwp(node);
            }

            @Override
            protected void visit(Plus node) {
                repeatingQuantifierFwp(node);
            }
            private void repeatingQuantifierFwp(Quantifier node) {
                
                ArcContainer.FP fp = nodeAttr.get(node).fp;

                for (Arc lpa : nodeAttr.get(node).lp.arcs()) {
                    ArcContainer.FP temp = fp.copy();
                    for (Arc arc : temp.arcs()) arc.mergeEA(lpa);
                    lpa.ns.fwp.mergeAndAdjustCursor(temp, node.mood);
                }
            }
            
            @Override
            protected void visit(Quantifier node) {
                super.visit(node);
                if (node.mood == Quantifier.Mood.RELUCTANT) {
                    requirements.add(Feature.RELUCTANT_QUANTIFIERS);
                } else if (node.mood == Quantifier.Mood.POSSESSIVE) {
                    requirements.add(Feature.POSSESSIVE_QUANTIFIERS);
                }
            };
            
        }.visit(augmentedRoot);

        final class DBarcComposer extends DepthFirstVisitor<State, Arc> {
            
            List<Arc> dbaList = new ArrayList<Arc>();
            /* Deque<Arc> */ LinkedList<Arc> dbaDeq = new LinkedList<Arc>();
            
            Arc[] pathsFrom(Arc arc) {
                assert arc.ns.cc.isDynamicBoundary();
                dbaList.clear(); dbaDeq.clear();
                dbaDeq.addFirst(arc);
                start(arc.ns);
                assert dbaDeq.isEmpty() : dbaDeq;
                return dbaList.toArray(new Arc[dbaList.size()]);
            }
            @Override
            protected boolean visit(Arc arc, boolean tree, boolean back) {
                if (!back) {
                    arc = arc.copy().mergeEA(dbaDeq.getFirst());
                    if (arc.ns.cc.isDynamicBoundary()) {
                        if (tree) dbaDeq.addFirst(arc);     // push()
                        return true;
                    } else {
                        dbaList.add(arc);
                        return false;
                    }
                } else return false;
            }
            @Override
            protected void visit(State s) {
                dbaDeq.removeFirst();                       // pop()
            }
        }
        
        final DBarcComposer dbArcComposer = new DBarcComposer();
        
        if (requirements.contains(Feature.DYNAMIC_BOUNDARIES)) {
            logger.log(level, "nfa pre dbc collapse: ",
                new TempContainer(
                    Collections.singletonList(init), omega, accept));
            
            new BreadthFirstVisitor<State, Arc>() {
                @Override
                protected void visit(State state) {
                    final boolean notAtDb = !state.cc.isDynamicBoundary();
                    ListIterator<Arc> qi = state.fwp.listIterator();
                    while(qi.hasNext()) {
                        Arc qArc = qi.next();
                        if (qArc.ns.cc == EPSILON) {
                            qi.remove();
                        } else if (notAtDb && qArc.ns.cc.isDynamicBoundary()) {
                            /*
                             * accumulate arc along a path out, 
                             * and hook up the new arcs
                             */
                            qi.remove();
                            for (Arc dbp : dbArcComposer.pathsFrom(qArc)) {
                                qi.add(dbp);
                            }
                        }
                    }
                }
            }.start(init);
        }
        
        if (requirements.contains(Feature.DYNAMIC_BOUNDARIES)) {
            logger.log(level, "nfa pre dbc conversion: ",
                new TempContainer(
                    Collections.singletonList(init), omega, accept));
        }

        /*
         * convert DBCs for firstpos states to static CharClass checks
         * need to keep each state in the init list separate to ensure
         * correct priority ordering - can result in multiple init states
         * with the same init CC.
         */
        final LinkedList<State> alpha = new LinkedList<State>();
        CharClass cc;
        State s;
        int position = -1;
        for (Iterator<Arc> ai = init.fwp.listIterator(); ai.hasNext();) {
            Arc arc = ai.next().copy();
            cc = CharClass.ALL_INIT;
            for (DBC dbc : arc.dbcs) {
                if (ALL_INIT_DBC.contains(dbc)) {
                    cc = cc.intersection(dbc.cc);
                    arc.dbcs.remove(dbc);
                }
            }
            if (alpha.isEmpty() || !(s = alpha.getLast()).cc.equals(cc)) {
                alpha.add(s = new State(
                    position--, cc, new ArcContainer.FWP(Mode.LIST)));
            }
            s.fwp.add(arc);
            ai.remove();  
        }
        this.alpha = Collections.unmodifiableList(alpha);

        final Map<CharClass, State> fpDbcMap = new HashMap<CharClass, State>();
        new BreadthFirstVisitor<State, Arc>() {
            int n = npos;
            @Override
            protected void visit(State state) {
                for (   ListIterator<Arc> ai = state.fwp.listIterator();
                        ai.hasNext();
                    ) {
                    Arc arc = ai.next();
                    if (arc.ns == omega) {
                        if (!arc.dbcs.isEmpty() && ALL_LP_DBC.containsAll(arc.dbcs)) {
                            CharClass cc = OMEGA;
                            for (DBC dbc : arc.dbcs) { 
                                /*
                                 *  an arc must be redirected to a _single_ next state...
                                 *  ...or not redirected at all (to do otherwise would
                                 *  be to create a false prioritization).
                                 */
                                if (dbc == DBC.WORD_B) {
                                    if (CC_WORD.contains(current().cc)) {
                                        cc = cc.intersection(NWORD_ZED);
                                    } else if (CC_NWORD.contains(current().cc)) {
                                        cc = cc.intersection(CC_WORD);
                                    } else cc = EMPTY;  // no static check possible
                                } else if (dbc == DBC.WORD_NB) {
                                    if (CC_WORD.contains(current().cc)) {
                                        cc = cc.intersection(CC_WORD);
                                    } else if (CC_NWORD.contains(current().cc)) {
                                        cc = cc.intersection(NWORD_ZED);
                                    } else cc = EMPTY;
                                } else cc = cc.intersection(dbc.cc);
                            }
                            if (!cc.equals(EMPTY)) {                                
                                State ns = fpDbcMap.get(cc);
                                if (ns == null) {
                                    fpDbcMap.put(cc, 
                                        ns = new State(++n, cc, new ArcContainer.FWP(Mode.LIST)));
                                }
                                ns.fwp.add(new Arc(accept, tncg));
                                ai.remove();
                                Arc arcPrime = new Arc(ns, tncg);
                                System.arraycopy(arc.tags, 0, arcPrime.tags, 0, arc.tags.length);
                                ai.add(arcPrime);
                            } else {
                                logger.finer(
                                    "regex: " + pattern + 
                                    " removed untraversable arc from " + 
                                    current().position + " to omega: " + arc.dbcs());
                            }
                        }
                    }   // else - all potential optimizations better done by correcting regex
                }
            }
        }.start(alpha);
        /*
         * compute the requirements set
         */
        if (requirements.contains(Feature.DYNAMIC_BOUNDARIES)) {
            /*
             * N.B.:
             *  
             * - the arc to the loop state doesn't require general
             * DYNAMIC_BOUNDARIES capability on the part of the Engine -
             * it's just a signal.
             * 
             * - none of the arcs from the loop state require this
             * capability either - the find() loop can be implemented
             * as an explicit looping.
             * 
             * potential problem: vanilla NFA Engine with 
             * reluctant capture groups but no DBC checks.
             * 
             * New Feature: LOOP_DBC, which is true if there
             * are DBC from the LOOP state... for the regular
             * DYNAMIC_BOUNDARY feature these will not count.
             * 
             * TODO: revisit and reconsider; seems messy.
             */
            requirements.remove(Feature.DYNAMIC_BOUNDARIES);
            new DepthFirstVisitor<State, Arc>() {
                @Override
                protected boolean visit(Arc arc, boolean tree, boolean back) {
                    if (!arc.dbcs.isEmpty() && arc.ns != loop) 
                        requirements.add(
                            current() != loop ? Feature.DYNAMIC_BOUNDARIES 
                                              : Feature.LOOP_DBC);
                    return super.visit(arc, tree, back);
                }
            }.start(alpha);
        }
        
        if (pattern.ncg > 0) {
            requirements.add(Feature.CAPTURING_GROUPS);
        }
        
        if (!isSet(pattern.flags, Pattern.X_LEFTMOST_LONGEST)) {
            requirements.add(Feature.LEFTMOST_FIRST);
        }

        this.requirements = Collections.unmodifiableSet(requirements);
        logger.log(level, "requirements: " + requirements.toString(),
            requirements);
        
        logger.log(level, "nfa final: ",
            new TempContainer(this.alpha, this.omega, this.accept));

    }
    

    /**
     * An accessor for iterating through the {@link State}s. The underlying
     * {@link Collection} represents a snapshot of the graph as it exists at the
     * time of the method invocation; subsequent changes in the graph are not
     * reflected in the iteration.
     * 
     * @return an Iterable<State> object which, when iterated, returns the
     *         States in topologically sorted order.
     */
    Iterable<State> states() {
        return statesFrom(alpha);
    }

    /**
     * @return the numner of states in this NFA. Formally, the {@link #states()}
     * method will return {@link #size()} {@link State}s.
     */
    int size() {
        return new BreadthFirstVisitor<State, Arc>(){}.start(alpha).black.size();
    }
    
    private static List<State> statesFrom(List<State> alpha) {
        return Collections.unmodifiableList(topologicalSort(alpha));
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Pattern=").append(pattern).append(LS)
          .append("req=").append(requirements).append(LS )
          .append(statesFrom(alpha).toString());
        return sb.toString();
    }
    
    /**
     * Used to encapsulate NFA graph for GraphViz logging from within NFA ctor.
     */
    static final class TempContainer {
        
        final List<State> alpha;
        final State omega;
        final State accept;
        public TempContainer(
                List<State> alpha, State omega, 
                State accept) {
            this.alpha = alpha;
            this.omega = omega;
            this.accept = accept;
        }
        
    }
}