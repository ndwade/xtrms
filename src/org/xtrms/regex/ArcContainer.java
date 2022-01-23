/*@LICENSE@
 */
package org.xtrms.regex;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import org.xtrms.regex.AST.Quantifier.Mood;
import org.xtrms.regex.NFA.Arc;
import org.xtrms.regex.NFA.State;


/**
 * Specialized containers for constructing "important-state-only" {@link NFA}
 * directly from an {@link AST}. The containers maintain the correct sequencing
 * (priority) and inclusion properties for the {@link org.xtrms.regex.NFA.Arc}s in a
 * {@link State} while the NFA is being constructed. Containers for
 * {@linkplain FP first pos}, {@linkplain LP last pos} and
 * {@linkplain FWP follow pos} are provided.
 * <p>
 * The API design is "YAGNI" with a {@link Collection}s flavor.
 */
final class ArcContainer {
    
//    private static final class Arc {    // mock test object
//        final Integer ns;
//        Arc(int ns) {this.ns=ns;}
//        @Override
//        public String toString() {
//            return "arc:" + ns;
//        }
//    }
//    static Arc arc(int ns) {return new Arc(ns);}

    private interface Hashed{};   // Marker, for assertions only
    
    private static final class Node {
        final Arc arc;
        Node hashNext = null;
        Node prev = null;
        Node next = null;
        public Node(Arc arc) {
            this.arc = arc;
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("arc:").append(arc).append(',')
              .append("p->").append(label(prev)).append(',')
              .append("n->").append(label(next)).append(',')
              .append("hn->").append(label(hashNext));              
            return sb.toString();
        }
        private static String label(Node node) {
            return (node == null) ? "null" 
                    : node.arc == null ? "nil" : node.arc.toString(); 
        }
    }
    
    private static abstract class Abstract {

        enum Direction {NONE, PREV, NEXT;}

        class ListIter implements ListIterator<Arc> {
            
            Direction dir = Direction.NONE;
            
            protected Node prev;
            protected Node next;
        
            private ListIter() {this(nil);}
            
            private ListIter(ListIter li) {
                assert nil == li.nil();
                prev = li.prev;
                next = li.next;
            }
            
            private ListIter(Node n) {  // after node
                prev = n;
                next = n.next;
            }
            
            /*
             * double secret methods
             */
            protected ListIter start() {
                prev = nil;
                next = nil.next;
                return this;
            }
        
            protected ListIter end() {
                prev = nil.prev;
                next = nil;
                return this;
            }
        
            protected Node nil() {
                return nil;
            }
        
            protected void importFrom(ListIter li) {
                assert nil != li.nil();
                assert !(li.prev == li.next);
                if (li.prev == li.nil()) {
                    assert li.next != li.nil();
                    prev = li.next.prev;
                    next = li.next;
                } else if (li.next == li.nil()) {
                    assert li.prev != li.nil();
                    prev = li.prev;
                    next = li.prev.next;
                } else {
                    prev = li.prev;
                    next = li.next;
                }
            }
            
            /**
             * Splices in a single Node.
             * 
             * @param li
             *            The insertion point for the node
             * @param node
             *            the Node to be added
             *            
             * @return An valid iterator representing the original position of the
             *         <code>li</code> parameter
             */
            protected ListIter splice(ListIter li, Node node) {
                
                ListIter oldli = new ListIter(li);
                
                li.prev.next = node;
                node.prev = li.prev;
                
                li.next.prev = node;
                node.next = li.next;
                
                oldli.next = li.prev = node;
            
                return oldli;
            
            }
            
            public void add(Arc arc) {
                assert nullCursor();
                Node node = new Node(arc);  // only part of API where Node created!
                splice(this, node);
                ++size;
                dir = Direction.NONE;
            }
            
            private ListIter hashAdd(Node node) {
                assert !nullCursor();
                if (!hash(node)) return this;
                ListIter ret = splice(this, node);
                ++size;
                maybeResize();
                dir = Direction.NONE;
                return ret;
            }

            public boolean hasNext() {
                return next != nil;
            }
        
            public boolean hasPrevious() {
                return prev != nil;
            }
        
            public Arc next() {
                if (next == nil) throw new NoSuchElementException();
                prev = next;
                next = next.next;
                dir = Direction.NEXT;
                return prev.arc;
            }
        
            public int nextIndex() {
                throw new UnsupportedOperationException();
            }
        
            public Arc previous() {
                if (prev == nil) throw new NoSuchElementException();
                next = prev;
                prev = prev.prev;
                dir = Direction.PREV;
                return next.arc;
            }
        
            public int previousIndex() {
                throw new UnsupportedOperationException();
            }
        
            public void remove() {
                assert nullCursor();
                Node node = null;
                switch(dir) {
                case NONE:
                    throw new IllegalStateException("no call to previous or next");
                case NEXT:
                    node = prev;
                    prev = node.prev;
                    break;
                case PREV:
                    node = next;
                    next = node.next;
                    break;
                default: assert false;
                }
                assert node != nil;
                node.prev.next = node.next;
                node.next.prev = node.prev;
                --size;
                dir = Direction.NONE;
            }
            
            public void set(Arc o) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int hashCode() {
                final int prime = 31;
                int result = 1;
//                result = prime * result + ((dir == null) ? 0 : dir.hashCode());
                result =
                        prime * result + ((next == null) ? 0 : next.hashCode());
                result =
                        prime * result + ((prev == null) ? 0 : prev.hashCode());
                return result;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj)
                    return true;
                if (obj == null)
                    return false;
                if (!(obj instanceof ListIter))
                    return false;
                final ListIter other = (ListIter) obj;
//                if (dir == null) {
//                    if (other.dir != null)
//                        return false;
//                } else if (!dir.equals(other.dir))
//                    return false;
                if (next == null) {
                    if (other.next != null)
                        return false;
                } else if (!next.equals(other.next))
                    return false;
                if (prev == null) {
                    if (other.prev != null)
                        return false;
                } else if (!prev.equals(other.prev))
                    return false;
                return true;
            }
        }

        protected final Node nil;
        protected int size = 0;

        private Abstract() {
            nil = new Node(null);
            nil.prev = nil.next = nil;
        }

        protected boolean nullCursor() {return true;}
        
        protected void maybeResize() {}
        protected boolean hash(Node node) {return true;}
        protected void emptyBuckets() {}

        /**
         * Adds all elements of a container as if added by listIterator, but
         * constant time. The ListIter is left at the end of the added elements.
         * 
         * @param li
         *            The insertion point for the elements of ac
         * @param ac
         *            the container whose elements are to be added, and which is
         *            left empty.
         * @return An valid iterator representing the original position of the
         *         <code>li</code> parameter
         *         
         */
        protected ListIter splice(ListIter li, Abstract ac) {
            
            assert !(this instanceof Hashed);   // splice doesn't handle hashing
            
            if (ac.isEmpty()) return li;
            
            Node first = ac.nil.next;
            Node last = ac.nil.prev;
            ListIter oldli = new ListIter(li);
            
            li.prev.next = first;
            first.prev = li.prev;
            
            li.next.prev = last;
            last.next = li.next;
            
            oldli.next = oldli.prev.next;
            li.prev = li.next.prev;
            
            size += ac.size;
            ac.clear();
            
            return oldli;
        
        }

        ListIterator<Arc> listIterator() {
            return new ListIter(nil); 
        }

        void add(Arc arc) {
            new ListIter().end().add(arc);
        }
        
        abstract Abstract copy() ;
        
        void initFrom(Abstract ac) {
            assert isEmpty();
            for (Arc arc : ac.arcs()) add(arc.copy());
        }
        int size() {
            assert new Object() {
                boolean test() {
                    int n = 0;
                    for (ListIter li = new ListIter(); li.hasNext(); li.next()) ++n;
                    return n == size;
                }
            }.test();
            return size;
        }

        boolean isEmpty() {
            assert !(size == 0  && !(nil.next == nil && nil.prev == nil));
            return size == 0;
        }

        void clear() {
            nil.prev = nil.next = nil;
            size = 0;
            emptyBuckets();
        }

        Iterable<Arc> arcs() {
            return new Iterable<Arc>() {
                public Iterator<Arc> iterator() {
                    return listIterator();            
                }  
            };
        }
        

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            ListIter li = new ListIter();
            for (boolean first = true; li.hasNext(); first = false) {
                Arc arc = li.next();
                if (!first) {
                    sb.append(", ");
                }
                sb.append(arc);
            }
            sb.append(']');
            return sb.toString();
        }
 
    }
    
    static final class LP extends Abstract {
        
        @Override
        LP copy() {
            LP ret = new LP();
            ret.initFrom(this);
            return ret;
        }
        void addAll(LP ac) {
            splice(new ListIter(), ac);
        }
    }
    
    private static abstract class Cursored extends Abstract {

        protected ListIter cursor = null;

        @Override
        protected boolean nullCursor() {return cursor == null;}
        
        
        void initFrom(Cursored ac) {    // surgery will be required
            ListIter li = new ListIter();
            ListIter cursor = null;
            Node node;
            for (node = ac.nil.next; node != ac.nil; node = node.next) {
                li.add(node.arc.copy());
                if (ac.cursor != null && node == ac.cursor.next) {
                    assert cursor == null;
                    cursor = new ListIter(li);
                    assert cursor.hasPrevious();
                    cursor.previous();          // leaves dir==PREV... so what?
                    assert cursor.next.arc == li.prev.arc;
                }
            }
            // since there are N+1 possible cursor locations for size == N,
            // always need one more check...
            if (ac.cursor != null && ac.cursor.next == ac.nil) {
                assert cursor == null;  // shouldn't have hit
                cursor = new ListIter().end();
            }
            this.cursor = cursor;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            ListIter li = new ListIter();
            for (boolean first = true; li.hasNext(); first = false) {
                if (cursor != null && cursor.equals(li)) {
                    sb.append(" ^ ");
                }
                Arc arc = li.next();
                if (!first) {
                    sb.append(", ");
                }
                sb.append(arc);
            }
            if (cursor != null && cursor.equals(li)) {
                sb.append(" ^ ");
            }
            sb.append(']');
            return sb.toString();
        }
        
    }
    static final class FP extends Cursored {
        
        @Override
        FP copy() {
            FP ret = new FP();
            ret.initFrom(this);
            return ret;
        }

        @Override
        void clear() {
            super.clear();
            cursor = null;
        }

        void addAllAtCursor(final FP ac) {
            assert !ac.isEmpty();
            assert disjointNS(arcs(), ac.arcs());
            assert cursor != null;
            ListIter acCursor = ac.cursor;
            splice(cursor, ac);
            if (acCursor != null) cursor.importFrom(acCursor); else cursor = null;
        }

        void appendAndAdjustCursor(FP ac) {
            assert !ac.isEmpty();
            assert disjointNS(arcs(), ac.arcs());
            ListIter acCursor = ac.cursor;
            splice(new ListIter().end(), ac);
            if (cursor == null && acCursor != null) {
                cursor = new ListIter();
                cursor.importFrom(acCursor);
            }
        }
        void setCursorStart() {
            if (cursor == null) cursor = new ListIter();
            cursor.start();
        }
        void setCursorEnd() {
            if (cursor == null) cursor = new ListIter();
            cursor.end();
        }
        void maybeSetCursorEnd() {
            if (cursor == null) {
                cursor = new ListIter(nil.prev);
            }
        }
    }

    static final class FWP extends Cursored implements Hashed {
        
        enum Mode {SET, LIST;}

        // TODO: tune hashing parameters
        
        protected Node[] buckets;
        private int bucketOf(Arc arc) {
            assert (buckets.length & (buckets.length - 1)) == 0;    // is power of 2
            return arc.ns.hashCode() & (buckets.length - 1);
        }
        @Override
        protected void maybeResize() {
            assert cursor != null;
            if (size() > buckets.length * 2) {
                buckets = new Node[buckets.length << 1];
                for (Node node = nil.next; node != nil; node = node.next) {
                    hash(node);
                }
            }
        }
        protected boolean hash(Node node) {
            if (cursor == null) return true;    // no longer need to hash
            int i = bucketOf(node.arc);
            node.hashNext = null; 
            if (buckets[i] == null) {
                buckets[i] = node;
            } else {
                Node last = buckets[i];
                if (last.arc.ns == node.arc.ns) return false;
                while (last.hashNext != null) {
                    if (last.arc.ns == node.arc.ns) return false;
                    last = last.hashNext;
                }
                last.hashNext = node;
            }
            return true;
        }
        @Override
        protected void emptyBuckets() {
            // otaku, but why not be thorough about leaks
            for (Node node : buckets) { 
                while (node != null) {
                    Node prev = node;
                    node = node.hashNext;
                    prev.hashNext = null;
                }
            }
            Arrays.fill(buckets, null);
        }
        
        private ListIter hashAddAll(ListIter li, FP ac) {
            ListIter temp, oldli = null;
            Node node = ac.nil.next;
            while (node != ac.nil) {
                Node next = node.next;
                temp = li.hashAdd(node);           
                if (oldli == null && temp != li) oldli = temp;
                node = next;
            }
            return oldli != null ? oldli : li;
        }

        FWP() {this(Mode.SET, 1<<4);}

        FWP(Mode mode) {this(mode, 1<<4);}
        
        FWP(Mode mode, int nbuckets) {
            buckets = new Node[nbuckets];
            if (mode == Mode.SET) cursor = new ListIter(); else cursor = null;
        }

        @Override
        FWP copy() {
            assert cursor == null;
            FWP ret = new FWP(Mode.LIST, this.buckets.length);
            ret.initFrom(this);
            return ret;
        }
        @Override
        void clear() {
            super.clear();
            cursor = new ListIter();
        }

        /**
         * @param ac
         */
        void addAllAtCursor(FP ac) {
            assert !ac.isEmpty();
            assert disjointNS(arcs(), ac.arcs());
            assert cursor != null;
            hashAddAll(cursor, ac);
            if (ac.cursor != null) {
                cursor.importFrom(ac.cursor); 
            } else {
                clearCursor();
            }
            ac.clear();
        }

        /**
         * Merge follow positions from "outer orbit" of a repeating quantifier.
         * <p>
         * Two rules: 1) Never prefer an outer orbit if there is an inner orbit. 2)
         * For Mood.GREEDY, insert remaining positions before the cursor. For
         * Mood.RELUCTANT, insert after.
         * <p>
         * 
         * @param ac
         * @param mood
         */
        void mergeAndAdjustCursor(FP ac, Mood mood) {
            assert !ac.isEmpty();
            assert cursor != null;
            ListIter oldCursor = hashAddAll(cursor, ac);
            if (mood != Mood.GREEDY) cursor = oldCursor;
            ac.clear();
            assert cursor != null;
        }
        
        /**
         * Clears the cursor explicitly. Needed for direct creation of
         * {@link State}s; the <code>fwp</code> member must have cursor
         * cleared explicitly before Arcs are
         * {@link #add(org.xtrms.regex.ArcContainer.Arc)}ed.
         */
        /* private */ void clearCursor() {
            cursor = null;  // turns off hashing
            emptyBuckets(); // not essential, but for mem leaks etc            
        }
    }

    static boolean disjointNS(Iterable<Arc> lhs, Iterable<Arc> rhs) {
        for (Arc l : lhs) for (Arc r : rhs) if (l.ns == r.ns) return false;
        return true;
    }
    
//    public static void main(String[] args) {
//        
//        FP fp0 = new FP();
//        FP fp1 = new FP();
//        FP fp = new FP();
//        
//        fp0.add(arc(0));
//        fp0.add(arc(1));
//        
//        fp1.add(arc(2));
//        fp1.add(arc(3));
//        
//        fp.initFrom(fp0);
//        fp.setCursorStart();
//        fp.addAllAtCursor(fp1.copy());
//        System.out.println(fp);
//
//        fp.clear();
//        System.out.println(fp);
//        
//        fp.initFrom(fp0);
//        fp1.setCursorStart();
//        fp.appendAndAdjustCursor(fp1.copy());
//        System.out.println(fp);
//        
//        fp.clear();
//        FP fp2 = new FP();
//        fp.initFrom(fp0);
//        fp1.setCursorStart();
//        fp.appendAndAdjustCursor(fp1.copy());
//        fp2.add(arc(99));
//        fp.addAllAtCursor(fp2.copy());
//        System.out.println(fp);
//        
//        System.out.println();
//        
//        FWP fwp = new FWP();
//        fp0.setCursorEnd();
//        fwp.addAllAtCursor(fp0.copy());
//        System.out.println(fwp);
//        fp1.setCursorStart();
//        fwp.addAllAtCursor(fp1.copy());
//        System.out.println(fwp);
//        fwp.mergeAndAdjustCursor(fp.copy(), Mood.GREEDY);
//        System.out.println(fwp);
//
//        fwp.clear();
//        fp0.setCursorEnd();
//        fwp.addAllAtCursor(fp0.copy());
//        System.out.println(fwp);
//        fp1.setCursorStart();
//        fwp.addAllAtCursor(fp1.copy());
//        System.out.println(fwp);
//        fwp.mergeAndAdjustCursor(fp.copy(), Mood.RELUCTANT);
//        System.out.println(fwp);
//
//        FP fp88 = new FP();
//        fp88.add(arc(8800));
//        fwp.addAllAtCursor(fp88);
//        System.out.println(fwp);
//        
//        for (ListIterator<Arc> li = fwp.listIterator(); li.hasNext();) {
//            Arc arc = li.next();
//            if (arc.ns > 100) {
//                li.remove();
//                li.add(arc(780));
//                li.add(arc(8650));
//            }
//        }
//        fwp.add(arc(42));
//        System.out.println(fwp);
//    }
}