/*
 * @LICENSE@
 */

package org.xtrms.regex;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.zip.ZipFile;


/**
 * This class implements a bunch of possibly reusable, miscelaneous static
 * objects, interfaces, classes, and methods.
 */
final class Misc {

    private Misc() {
    } // never instantiated

    public static final String LS = System.getProperty("line.separator");
    public static final String FS = System.getProperty("file.separator");
    public static final int EOF = -1; // end of char sequence marker
    
    /*
     * idiom suppression for clearing StringBuilders
     */
    public static void clear(StringBuilder sb) {
        sb.delete(0, sb.length());
    }
    
    /*
     * So many iterators don't suport remove()
     */
    public static abstract class ImmutableIterator<E> implements Iterator<E> {
        public final void remove() {
            throw new UnsupportedOperationException("sorry!");
        }
    }

    /*
     * idiom suppression for Strings
     */
    static Iterable<Character> iterize(final CharSequence cs) {
        return new Iterable<Character>() {
            public Iterator<Character> iterator() {
                return new Iterator<Character>() {
                    private int i = 0;

                    public boolean hasNext() {
                        return i < cs.length();
                    }

                    public Character next() {
                        return cs.charAt(i++);
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    static String tagsStringFrom(boolean[] tags) {
        StringBuilder sb = new StringBuilder();

        for (int t = 0; t < tags.length; ++t) {
            if (tags[t]) {
                sb.append(t).append(',');
            }
        }
        if (sb.length() == 0)
            return "";
        sb.insert(0, "t<");
        sb.setCharAt(sb.length() - 1, '>');
        return sb.toString();
    }

    @SuppressWarnings("unused")
    static <K, V> String stringFrom(String title, Map<K, V> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("map: ").append(title).append(LS);
        Set<Map.Entry<K, V>> entries = map.entrySet();
        for (Map.Entry<K, V> e : entries) {
            sb.append("    ").append(e.getKey()).append(" --> ").append(LS)
                .append('\t').append(e.getValue()).append(LS);
        }
        sb.append(LS);
        return sb.toString();
    }



    static <T> HashSet<T> intersect(Collection<T> lhs, Collection<T> rhs) {
        HashSet<T> ret = new HashSet<T>(lhs);
        for (T t : lhs) if (!rhs.contains(t)) ret.remove(t);
        return ret;
    }
    
    static <T> boolean disjoint(Collection<T> lhs, Collection<T> rhs) {
        return intersect(lhs, rhs).isEmpty();
    }

    static <T> boolean disjoint(Iterable<T> lhs, Iterable<T> rhs) {
        for (T t : lhs) for (T u : rhs) if (t.equals(u)) return false;
        return true;
    }
    static <T> HashSet<T> difference(Set<T> lhs, Set<T> rhs) {
        HashSet<T> ret = new HashSet<T>(lhs);
        ret.removeAll(rhs);
        return ret;
    }

    static final class FlagMgr {
        
        private List<String> labels = new ArrayList<String>(4);
        private int defined = 0;
        private Integer implemented = null;
        boolean frozen = false;
        
        private boolean contains(int f, int g) {
            return (g | f) == f;
        }

        int next(String label) {
            if (frozen)
                throw new IllegalStateException("frozen FlagGen");
            labels.add(label);
            int flag = 1 << (labels.size() - 1);
            defined |= flag;
            return flag;
        };

        int freezeAndCount() {
            frozen = true;
            return labels.size();
        }
        
        FlagMgr setImplemented(int implemented) {
            if (!contains(defined, implemented)) {
                throw new IllegalArgumentException(
                    "unknown flags: " + (implemented & ~defined));
            }
            this.implemented = implemented;
            return this;
        }
        
        void check(int flags) {
            if (!contains(defined, flags)) {
                throw new IllegalArgumentException(
                    "unknown flags: " + (flags & ~defined));
            } else if (!contains(implemented, flags)) {
                throw new IllegalArgumentException(
                    "unimplemented flags: " + stringFrom(flags & ~implemented));
            }
        }

        String stringFrom(int flags) {
            assert (((1 << freezeAndCount()) - 1) | flags) == ((1 << freezeAndCount()) - 1);
            StringBuilder sb = new StringBuilder();
            int n = 0;
            while (flags != 0) {
                for (; (flags & 1) == 0; flags >>= 1, ++n)
                    ;
                sb.append(sb.length() == 0 ? "" : ", ").append(labels.get(n));
                flags &= ~1;
            }
            return sb.toString();
        }
    };
    
    static boolean isSet(int flags, int FLAG) {
        return (flags & FLAG) != 0;
    }

    private static abstract class Escaper {
        abstract boolean esc(StringBuilder sb, int c);
    }

    private static final class MapEscaper extends Escaper {
        private final Map<Character, String> map =
                new HashMap<Character, String>();

        public boolean esc(StringBuilder sb, int c) {
            boolean ret = (c == (char) c) ? map.containsKey((char) c) : false;
            if (ret)
                sb.append(map.get((char) c));
            return ret;
        }

        MapEscaper map(Character c, String s) {
            map.put(c, s);
            return this;
        }
    }

    private static final MapEscaper jsEscaper =
            new MapEscaper().map('\\', "\\\\").map('"', "\\\"");
    private static final MapEscaper rxEscaper =
            new MapEscaper().map('\r', "\\r").map('\n', "\\n").map('\t', "\\t")
                .map('\b', "\\b").map('\f', "\\f").map('\013', "\\v").map(
                    '\007', "\\a");
    private static final MapEscaper rxpEscaper =
            new MapEscaper().map('.', "\\.").map('%', "\\%").map('^', "\\^")
                .map('$', "\\$").map('[', "\\[").map('|', "\\|")
                .map('(', "\\(").map(')', "\\)");
    private static final MapEscaper rxccEscaper =
            new MapEscaper().map('^', "\\^").map('-', "\\-").map('[', "\\[")
                .map(']', "\\]");
    private static final MapEscaper xmlEscaper =
            new MapEscaper().map('<', "&lt;").map('&', "&amp;").map('>', "&gt;");

    private static final Escaper unicodeEscaper = new Escaper() {
        public boolean esc(StringBuilder sb, int c) {
            boolean ret = false;
            if (c < 0) {
                sb.append("0x" + Integer.toHexString(c));
                ret = true;
            } else if (c < 32 || 126 < c) {
                int mark = sb.length();
                sb.append(Integer.toHexString(c));
                while (sb.length() - mark < 4) {
                    sb.insert(mark, "0");
                }
                sb.insert(mark, "\\u");
                ret = true;
            }
            return ret;
        }
    };

    /**
     * A collection of singleton objects which implement methods used to create
     * Strings where certain characters are replaced by escape sequences.
     */
    enum Esc {

        /**
         * Java lang escaper - escapes " and /, non-printable-ASCI and beyond ->
         * \\u codes
         */
        JAVA(jsEscaper, unicodeEscaper),
        /**
         * Regex escaper - escapes Java and some ASCI ctl, non-printable-ASCI
         * and beyond -> \\u codes
         */
        RX(jsEscaper, rxEscaper, unicodeEscaper),
        /**
         * Regex Pattern escaper - escapes as RX plus metachars (like '('),
         * non-printable-ASCI and beyond -> \\u codes
         */
        RXP(jsEscaper, rxEscaper, rxpEscaper, unicodeEscaper),
        /**
         * Regex Pattern escaper - escapes as RX plus char class metachars (like
         * '-'), non-printable-ASCI and beyond -> \\u codes
         */
        RXCC(jsEscaper, rxEscaper, rxccEscaper, unicodeEscaper), 
        /**
         * Barebones XML escaper only escapes < and & 
         */
        XML(xmlEscaper);

        private final Escaper[] path;

        Esc(final Escaper... path) {
            this.path = path;
        }

        void esc(StringBuilder sb, int c) {
            for (Escaper e : path) {
                if (e.esc(sb, c))
                    return;
            }
            sb.append((char) c);
        }

        String esc(int c) {
            StringBuilder sb = new StringBuilder();
            esc(sb, c);
            return sb.toString();
        }

        void esc(StringBuilder sb, CharSequence cs) {
            for (char c : iterize(cs)) {
                esc(sb, c);
            }
        }

        String esc(CharSequence cs) {
            StringBuilder sb = new StringBuilder();
            esc(sb, cs);
            return sb.toString();
        }
    }

    
    static final class IdentitySetQueue<E> extends AbstractQueue<E> {
        
        final Map<E, Void> map = new IdentityHashMap<E, Void>();
        final LinkedList<E> list = new LinkedList<E>();

        public IdentitySetQueue() {
            super();
        }
        public IdentitySetQueue(Collection<? extends E> c) {
            this();
            addAll(c);
        }
        @Override
        public Iterator<E> iterator() {
            return list.iterator();
        }

        @Override
        public int size() {
            assert list.size() == map.size();
            return map.size();
        }

        public boolean offer(E o) {
            if (o == null || map.containsKey(o)) return false;
            map.put(o, null);
            return list.offer(o);
        }

        public E peek() {
            return list.peek();
        }

        public E poll() {
            if (isEmpty()) return null;
            E ret = list.poll();
            assert map.containsKey(ret);
            map.remove(ret);
            return ret;
        }        
    }

    static final class TSidentitySetDeque<E> /* implements Deque<E> */ {
        
        int ts = 0;
        final Map<E, Integer> map = new IdentityHashMap<E, Integer>();
        final LinkedList<E> list = new LinkedList<E>();
        
        public boolean offerFirst(E e) {
            if (!map.containsKey(e)) {
                map.put(e, ts++);
                list.addFirst(e);
                return true;
            }
            return false;
        }
        public E removeFirst() {
            if (isEmpty()) throw new NoSuchElementException();
            E e = list.removeFirst();
            assert map.containsKey(e);
            map.remove(e);
            return e;
        }
        public E peekFirst() {
            assert map.containsKey(list.getFirst());
            return list.getFirst();
        }
        
        public int tsFirst() {
            E e = list.getFirst();
            return map.get(e);
        }
        
        public boolean contains(Object e) {
            assert map.containsKey(e) == list.contains(e);
            return map.containsKey(e);
        }
        public void clear() {
            map.clear(); list.clear(); ts = 0;
        }
        public boolean isEmpty() {
            assert map.isEmpty() == list.isEmpty();
            return map.isEmpty();
        }
        @Override
        public String toString() {
            return list.toString();
        }
    }
    
    /*
     * Generic digraph visitors
     */
    private interface SimpleVertex {
        Iterable<? extends SimpleEdge> edges();
    }
    private interface SimpleEdge {
        SimpleVertex vertex();
    }
    interface Vertex<E extends SimpleEdge> extends SimpleVertex {
        Iterable<E> edges();
    }
    interface Edge<V extends SimpleVertex> extends SimpleEdge {
         V vertex();
    }

    static abstract class BreadthFirstVisitor<V extends Vertex<E>, E extends Edge<V>> {
        
        final Map<V, Void> black = new IdentityHashMap<V, Void>(); 
        final Queue<V> gray = new IdentitySetQueue<V>();
        V vertex;
        
        final BreadthFirstVisitor<V, E> start(Iterable<V> inits) {
            black.clear(); gray.clear();
            for (V init : inits) if (!black.containsKey(init)) visitFrom(init);
            return this;
        }
        
        final BreadthFirstVisitor<V, E> start(V init) {
            black.clear(); gray.clear();
            visitFrom(init);
            return this;
        }
        
        protected V current() {
            return vertex;
        }
        
        private void visitFrom(V init) {
            gray.offer(init);
            while (!gray.isEmpty()) {
                black.put(vertex = gray.remove(), null);
                visit(vertex);
                for (E edge : vertex.edges()) {
                    if (black.containsKey(edge.vertex())) {
                        visit(edge, false);
                    } else {
                        visit(edge, gray.offer(edge.vertex()));
                    }
                }
            }
        }
        /*
         * tree edges can be identified, but no more without augmenting the data structures.
         */
        protected void visit(E edge, boolean tree) {}
        protected void visit(V vertex) {}
    }

    static abstract class DepthFirstVisitor<V extends Vertex<E>, E extends Edge<V>> {
        
        private Map<V, Integer> black = new IdentityHashMap<V, Integer>(); 
        private TSidentitySetDeque<V> gray = new TSidentitySetDeque<V>();
        private LinkedList<Iterator<E>> eiDeq = new LinkedList<Iterator<E>>();
        
        public final DepthFirstVisitor<V, E> start(Iterable<V> inits) {
            black.clear(); gray.clear(); eiDeq.clear();
            for (V init : inits) if (!black.containsKey(init)) visitFrom(init);
            return this;
        }
        
        public final DepthFirstVisitor<V, E> start(V init) {
            black.clear(); gray.clear();
            visitFrom(init);
            return this;
        }

        private void visitFrom(V init) {
            
            V vertex;
            E edge;
            Iterator<E> ei;
            boolean tree, back, a;
            int ts;
            
            gray.offerFirst(init);
            eiDeq.addFirst(init.edges().iterator());
            while (!eiDeq.isEmpty()) {
                if ((ei = eiDeq.getFirst()).hasNext()) {
                    edge = ei.next();
                    tree = !black.containsKey(edge.vertex()) 
                         & !(back = gray.contains(edge.vertex()));
                    if (visit(edge, tree, back) && tree) {
                        gray.offerFirst(edge.vertex());
                        eiDeq.addFirst(edge.vertex().edges().iterator());
                    }
                } else {
                    eiDeq.removeFirst();
                    ts = gray.tsFirst();
                    a = black.put(vertex = gray.removeFirst(), ts) == null;  
                    assert a;
                    visit(vertex);
                }
            }
            assert  gray.isEmpty() : gray;
        }
        
        /*
         * tree, back, and forward-or-cross ((!tree && !back) is "free"...
         * ... distinguishing forward from cross costs another method invocation
         */
        protected boolean visit(E edge, boolean tree, boolean back) {
            return true;
        }
        protected void visit(V vertex) {}
        
        protected final V current() {
            return gray.peekFirst();
        }
        
        protected final boolean cross(E edge) {
            return black.get(edge.vertex()) < gray.tsFirst();
        }
        protected Set<V> gray() {return Collections.unmodifiableSet(gray.map.keySet());}
        protected Set<V> black() {return Collections.unmodifiableSet(black.keySet());}
    }
    
    static <V extends Vertex<E>, E extends Edge<V>> 
    List<V> topologicalSort(Iterable<V> init) {
        final LinkedList<V> ret = new LinkedList<V>();
        new DepthFirstVisitor<V, E>() {
            @Override
            protected void visit(V s) {
                ret.addFirst(s);
            }
        }.start(init);
        return ret;
    }
    
    static <V extends Vertex<E>, E extends Edge<V>> 
    List<V> topologicalSort(V init) {
        final LinkedList<V> ret = new LinkedList<V>();
        new DepthFirstVisitor<V, E>() {
            @Override
            protected void visit(V s) {
                ret.addFirst(s);
            }
        }.start(init);
        return ret;
    }
    
    interface PushbackIterator<E> extends Iterator<E> {
        void pushback();
    }
    static final class CharScanner implements Iterable<Integer> {
        static final int BACKSLASH = 0x80000000;
        private final CharSequence cs;
        CharScanner(CharSequence cs) {this.cs = cs;}
        public PushbackIterator<Integer> iterator() {
            return new PushbackIterator<Integer>() {
                int i = 0;
                int c;
                public boolean hasNext() {
                    return i < cs.length();
                }
                public Integer next() {
                    c = cs.charAt(i++);
                    if (c == '\\' && i < cs.length()) {
                        c = cs.charAt(i++);
                        c |= BACKSLASH;
                    }
                    return c;
                }
                public void remove() {
                    throw new UnsupportedOperationException();
                }
                public void pushback() {
                    assert 0 <= i && i <= cs.length();
                    if (i == 0) {
                        throw new IndexOutOfBoundsException();
                    }
                    i = c < 0 ? i - 2 : i - 1;
                    assert 0 <= i && i < cs.length();
                }
            };
        }
    }
    
    /*
     * Filter iterator stuff
     */
    interface Filter<T> {
        boolean filter(T t);
    }

    private static final Filter<Object> passAll = new Filter<Object>() {
        public boolean filter(Object o) {
            return true;
        }
    };

    static final class FilterIterable<T> implements Iterable<T> {
        private final Collection<T> collection;
        private Filter<? super T> filter = null;

        public FilterIterable<T> filter(Filter<? super T> filter) {
            this.filter = filter;
            return this;
        }

        public FilterIterable<T> filterPassAll() {
            this.filter = passAll;
            return this;
        }

        public FilterIterable(Collection<T> collection) {
            this.collection = collection;
        }

        public FilterIterable(T[] array) {
            this.collection = Arrays.asList(array);
        }

        public Iterator<T> iterator() {
            return new FilterIterator<T>(collection.iterator(), filter);
        }
    }

    /*
     * N.B. "fast fail" is one element slower...
     */
    private static final class FilterIterator<T> implements Iterator<T> {
        private final Iterator<T> iter;
        private final Filter<? super T> filter;
        private T t;

        FilterIterator(Iterator<T> iter, Filter<? super T> filter) {
            this.iter = iter;
            this.filter = filter;
            this.t = nextT();
        }

        public boolean hasNext() {
            return t != null;
        }

        public T next() {
            T ret = t;
            t = nextT();
            return ret;
        }

        public void remove() {
            throw new UnsupportedOperationException("cannot remove()");
        }

        private T nextT() {
            while (iter.hasNext()) {
                T t = iter.next();
                if (filter.filter(t))
                    return t;
            }
            return null;
        }
    }
    
    static final class MaybeZipped {
        
        File f;
        ZipFile zf;
        MaybeZipped(String pathname) {
            f = new File(pathname);
            if (!f.exists()) {
                try {
                    zf = new ZipFile(pathname + ".zip");
                    if (zf.size() != 1) throw new IllegalStateException(
                        "must be exactly one entry: " + zf.size());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        
        InputStream getInputStream() {
            try {
                return zf == null 
                        ? new FileInputStream(f) 
                        : zf.getInputStream(zf.getEntry(f.getName()));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);    // shouldn't happen, we checked
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        void close() {
            if (zf != null) {
                try {
                    zf.close();
                } catch (IOException e) { /* stfu */}
            }
        }
    }
}