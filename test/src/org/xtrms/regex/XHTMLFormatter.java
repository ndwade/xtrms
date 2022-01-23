/*@LICENSE@
 */
package org.xtrms.regex;

import static org.xtrms.regex.Misc.FS;
import static org.xtrms.regex.Misc.LS;
import static org.xtrms.regex.Misc.clear;
import static org.xtrms.regex.Misc.tagsStringFrom;
import static org.xtrms.regex.Misc.Esc.XML;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.xtrms.regex.AST.AbstractTreePrinter;
import org.xtrms.regex.AST.Node;
import org.xtrms.regex.DFA.Arc;
import org.xtrms.regex.DFA.State;
import org.xtrms.regex.Misc.DepthFirstVisitor;
import org.xtrms.regex.NFA.TempContainer;


/**
 * Emits an XHTML table for each object passed. Expects exactly one object parameter. This
 * class doesn't follow the formatting conventions; it expects a string which acts as a kind of
 * label for the object being emitted. Reflection is used to inspect the object and fill out the
 * fields of the table. Tree and graph objects are rendered using GraphViz.
 * 
 */
final class XHTMLFormatter extends Formatter {
    
    public static String GV_DOT = "/usr/local/bin/dot";
    
    private static final java.util.regex.Pattern msgPrefixPattern = 
        java.util.regex.Pattern.compile(
            "^(\\p{javaJavaIdentifierStart}[\\p{javaJavaIdentifierPart}\\s]*): (.*)$",
            java.util.regex.Pattern.DOTALL);
    
    private final java.util.regex.Matcher msgPrefixMatcher =
        msgPrefixPattern.matcher("");

    String m_dir = null;
    
    private StringBuilder sb = new StringBuilder();
    
    private Map<String, Integer> imgMap = new HashMap<String, Integer>();
    
    @Override
    public String getHead(Handler handler) {

        XHTMLFileHandler xh = (XHTMLFileHandler) handler;
        
        clear(sb);        
        sb.append(XHTML.PROLOG);
        sb.append(XHTML.head(xh.testClass + '.' + xh.testName, 
            getClass().getResource("/resources/stylesheets/log.css")));
        sb.append(XHTML.BODY);
        sb.append(new XHTML.Para(xh.testClass + '.' + xh.testName + "()")
                .setAttribute("class", "title"));
        sb.append(XHTML.br()).append(XHTML.br());
        return sb.toString();
    }
    
    private static String idFrom(String s) {
        return s.replace(' ', '-');
    }
 
    @Override
    public String format(LogRecord record) {
        
        assert m_dir != null;
        
        String msg = record.getMessage();
        msgPrefixMatcher.reset(msg);
        if (!msgPrefixMatcher.matches()) {
            throw new AssertionError(msg);
        }
        final String varname = msgPrefixMatcher.group(1);

        Object[] params = record.getParameters();
        assert params == null || params.length == 1;
        
        clear(sb);
        
        XHTML.Hr hr = new XHTML.Hr();
        hr.setAttribute("id", idFrom(varname));
        sb.append(hr);
        
        sb.append(XHTML.span("caption",
            record.getSourceClassName() + 
                '.' + record.getSourceMethodName() + "() --> "));
        sb.append(XHTML.span("field", varname));

        final Object o;
        if (params == null) {
            o = msgPrefixMatcher.group(2);
        } else {
            o = params[0];
        }
        sb.append(XHTML.br()).append(XHTML.br());
        
        Class<?> clazz = o.getClass();
        assert clazz != NFA.class;
        sb.append(XHTML.table(varname, maybeXform(o), new XHTML.DecompositionFilter() {
            public boolean decompose(Class<?> clazz) {
                return XHTML.defaultDecompositionFilter.decompose(clazz)
                    && !clazz.getSimpleName().startsWith("EdgeAttributes")
                    && clazz.getEnclosingClass() != ArcContainer.class
                    && clazz != NFA.Arc.class
                    && clazz != DFA.Arc.class
                    && clazz != CharClass.class
                    && clazz != CharClass.Interval.class
                    && clazz.getEnclosingClass() != AST.class
                    && !(o instanceof DFA && clazz == NFA.State.class);
            }
        }));
        sb.append(XHTML.br()).append(XHTML.br());
        if (grapherMap.containsKey(o.getClass())) {
            sb.append(XHTML.para(varname));
            writeImage(idFrom("img-" + varname), o, grapherMap.get(o.getClass()));
        }
        return sb.toString();
    }
        
    @Override
    public String getTail(Handler h) {
        return XHTML.EPILOG;
    }
    
    private static final Object maybeXform(Object o) {
        if (o instanceof DFA) {
            DFA dfa = ((DFA) o);
            List<Object> list = new LinkedList<Object>();
            for (final DFA.State state : dfa.states()) {
                list.add(new Object() {
                    final String label = state.toLabel();
                    final boolean init = state.init;
                    final boolean accept = state.accept;
                    final boolean omega = state.containsOmega;
                    final boolean stranded = state.stranded;
                    final Object[] arcs = new Object[state.arcs.length];
                    {
                        int i = 0;
                        for (final DFA.Arc arc : state.arcs) {
                            arcs[i++] = new Object() {
                                CharClass.Interval iv = arc.iv;
                                String ns = arc.ns.toLabel();
                            };
                        }
                    }
                });
            }
            return list;
        } else if (o instanceof NFA.TempContainer) {
            final LinkedList<NFA.State> ret = new LinkedList<NFA.State>();
            new DepthFirstVisitor<NFA.State, NFA.Arc>() {
                @Override
                protected void visit(NFA.State s) {
                    ret.addFirst(s);
                }
            }.start(((NFA.TempContainer) o).alpha);
            return ret;
        } else return o;
    }
    
    @SuppressWarnings("serial")
    private static final class FormatterMap<T> extends HashMap<Class<?>, T> {
        @Override
        public boolean containsKey(Object key) {
            Class<?> clazz = (Class<?>) key;
            return super.containsKey(key) ? true 
                    : (clazz = clazz.getSuperclass()) == null  ? false 
                            : containsKey(clazz);
        }
        @Override
        public T get(Object key) {
            Class<?> clazz = (Class<?>) key;
            T ret;
            return (ret = super.get(key)) != null ? ret 
                    : (clazz = clazz.getSuperclass()) == null  ? null 
                            : get(clazz);
        }
        @Override
        public T put(Class<?> key, T value) {
            assert value != null;
            return super.put(key, value);
        }
    }

    interface GVPrinter<T> {
        void printGV(T t, Appendable a) throws IOException;
    }

    private static final Map<Class<?>, GVPrinter<?>> grapherMap =
        new FormatterMap<GVPrinter<?>>();
    
    /*
     * GraphViz image stuff
     */
    
    private static final String GV_FONT = "Verdana";
    
    private static String gvStringFrom(Object o) {
        String s = o.toString();
        int brackets = 0;
        for (int i=0; i<s.length(); ++i) {
            char c = s.charAt(i);
            brackets += c == '[' ? 1 : c == ']' ? -1 : 0;
        }
        if (s.length() > 0 && brackets < 0) {
            s = s.replace("]", "&#93;");
        }
        s = s.replace("\\G", "\\\\G");
        return s;
    }
    
    private static String astLabel(String s) {
        XHTML.Table table = XHTML.table(0, 3, 0);
        table.row().cell().font(GV_FONT, "black", 10)
            .appendTextChild(gvStringFrom(s));
        return "label=<" + table.toString() + '>';
    }
    private static String tailLabel(int n) {
        XHTML.Font font = XHTML.font(GV_FONT, "brown", 8);
        font.appendTextChild(Integer.toString(n));
        return "taillabel=<" + font.toString() + '>';
    }
    private static String nfaStateLabel(String s) {
        XHTML.Table table = XHTML.table(0, 3, 0);
        table.row().cell().font(GV_FONT, "black", 8)
            .appendTextChild(s);
        return "label=<" + table.toString() + '>';
    }
    private static String nfaStateLabel(int pos, CharClass cc) {
        XHTML.Table table = XHTML.table(0, 3, 0);
        table.row().cell().font(GV_FONT, "black", 8)
            .appendTextChild(Integer.toString(pos));
        table.row().cell().font(GV_FONT, "indigo", 10)
            .appendTextChild(gvStringFrom(cc));
        return "label=<" + table.toString() + '>';
    }
    private static String nfaArcLabel(NFA.Arc arc) {
        if (arc.isEmpty()) {
            return "label=\"\"";
        }
        XHTML.Table table = XHTML.font(GV_FONT, null, -1).table(0, 3, 0);
        XHTML.Table.Row.Cell cell = table.row().cell();
        if (!arc.isTagFree()) {
            cell.font(null, "blue", 10).appendTextChild(
                tagsStringFrom(arc.tags()));
        }
        if (!arc.dbcs().isEmpty()) {
            cell.font(null, "indianred", 8).appendTextChild(
                gvStringFrom(arc.dbcs()));
        }
        return "label=<" + table.toString() + '>';
    }
    private static String dfaStateLabel(String s) {
        XHTML.Table table = XHTML.table(0, 3, 0);
        table.row().cell().font(GV_FONT, "black", 10)
            .appendTextChild(gvStringFrom(s));
        return "label=<" + table.toString() + '>';
    }
    private static String dfaArcLabel(String s) {
        XHTML.Table table = XHTML.table(0, 3, 0);
        table.row().cell().font(GV_FONT, "black", 10)
            .appendTextChild(gvStringFrom(s));
        return "label=<" + table.toString() + '>';
    }

    static {

        grapherMap.put(Node.class, new GVPrinter<Node>() {
            public void printGV(Node root, Appendable a) throws IOException {
                new AbstractTreePrinter(a) {
                    @Override
                    protected Formatter newFormatter() {
                        return new Formatter() {
                            private Stack<String> ntStack = new Stack<String>();
                            private int nt = 0;
                            private String currentNT;

                            @Override
                            void appendNonTerminal(String label) {
                                currentNT = "nt_" + nt++;
                                append(currentNT, "ellipse", XML.esc(label));
                            }

                            @Override
                            void appendTerminal(int position, String ccName) {
                                append("t_" + position, "box", "" 
                                    + position + ": "
                                        + XML.esc(ccName));
                            }

                            @Override
                            void push() {
                                ntStack.push(currentNT);
                            }

                            @Override
                            void pop() {
                                ntStack.pop();
                            }

                            @Override
                            protected String prolog() {
                                return "digraph AST {" + LS + 
                                "\tbgcolor=transparent;" + LS;
                           }

                            @Override
                            protected String epilog() {
                                return "}" + LS;
                            }

                            private void append(String name, String shape, 
                                    String label) {
                                try {
                                    a.append('\t').append("node [shape=")
                                        .append(shape)
                                        .append(", ")
                                        .append(astLabel(label))
                                        .append(" fillcolor=white, style=filled ")
                                        .append("]; ").append(name).append(';')
                                        .append(LS);
                                    if (!ntStack.isEmpty()) {
                                        a.append('\t').append(ntStack.peek())
                                        .append(" -> ").append(name).append(';')
                                        .append(LS);
                                    }
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        };
                    }                
                }.print(root);
            }
        });
        
        grapherMap.put(NFA.TempContainer.class, new GVPrinter<NFA.TempContainer>() {
            
            public void printGV(final TempContainer gvc, final Appendable a) 
                    throws IOException {

                a.append("digraph NFA {").append(LS);                
                a.append('\t').append("bgcolor=transparent;").append(LS);
                a.append('\t').append("rankdir=\"LR\";").append(LS);
//                a.append('\t').append("size=\"11,8\";").append(LS);

                /*
                 * start state
                 */
                a.append('\t').append("node [" +
                     "shape=box, fillcolor=green, style=filled];")
                 .append(" init [").append(nfaStateLabel("init")).append("];").append(LS);
                
                new Misc.BreadthFirstVisitor<NFA.State, NFA.Arc>() {
                    int n = 0;
                    @Override
                    protected void visit(NFA.State s) {
                        n = 0;
                        if (gvc.alpha.contains(s)) {
                            printNode(s, "palegreen", "circle");
                        }
                    }
                    @Override
                    protected void visit(org.xtrms.regex.NFA.Arc arc, boolean tree) {
                        
                        if (tree) {
                            NFA.State ns = arc.ns;                        
                            String color = 
                                gvc.omega == ns           ? "yellow" :
                                gvc.accept == ns          ? "red" :
                                ns.cc.isDynamicBoundary() ? "orange" : "white";
                            String shape = gvc.accept == ns ? "doublecircle" : "circle";
                            printNode(ns, color, shape);
                        }
                        try {
                            a.append('\t')
                             .append("" + current().position)
                             .append(" -> ")
                             .append("" + arc.ns.position)
                             .append(" [")
                             .append(tailLabel(n++)).append(' ')
                             .append(nfaArcLabel(arc))
                             .append("];").append(LS);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    private void printNode(NFA.State s, String color, String shape) {
                        try {
                            a.append('\t').append("node [shape=").append(shape) 
                                .append(", fillcolor=").append(color).append(", style=filled]; ");
                            a.append("" + s.position)
                            .append(" [")
                            .append(nfaStateLabel(s.position, s.cc))
                            .append("];").append(LS);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }.start(gvc.alpha);
                
                /*
                 * init pseudo arcs
                 */
                for (NFA.State state : gvc.alpha) {
                    if (state.position < 0) {
                        String ns = "\"" + state.position + "\"";
                        a.append('\t').append("init").append(" -> ").append(ns)
                         .append(';').append(LS);
                    }
                }

                a.append('}');
             } 
        });
        
//        grapherMap.put(NFA.class, new GVPrinter<NFA>() {
//            @SuppressWarnings("unchecked")
//            public void printGV(final NFA nfa, Appendable a) throws IOException {
//                TempContainer gvc = new TempContainer(
//                    nfa.alpha, nfa.omega, nfa.accept);
//                GVPrinter gvp = grapherMap.get(gvc.getClass());
//                gvp.printGV(gvc, a);
//            }
//        });
        
        grapherMap.put(DFA.class, new GVPrinter<DFA>() {
            
            public void printGV(final DFA dfa, final Appendable a) throws IOException {

                a.append("digraph DFA {").append(LS);
                
                a.append('\t').append("bgcolor=transparent;").append(LS);
                a.append('\t').append("rankdir=\"LR\";").append(LS);
//                a.append('\t').append("size=\"11,8\";").append(LS);
                
                a.append('\t')
                .append("node [shape=box, style=filled, fillcolor=green];" +
                        " start [" + dfaStateLabel("start") + "];")
                .append(LS);

                new Misc.BreadthFirstVisitor<DFA.State, DFA.Arc>() {
                    @Override
                    protected void visit(State state) {
                        if (state == dfa.init) printState(state);
                    }
                    @Override
                    protected void visit(Arc arc, boolean tree) {
                        if (tree) printState(arc.ns);
                        String ns;
                        ns = arc.ns.toLabel();
                        try {
                            a.append('\t')
                                .append('"')
                                .append(current().toLabel())
                                .append('"')
                                .append(" -> ")
                                .append('"')
                                .append(ns)
                                .append('"')
                                .append(" [" + dfaArcLabel(arc.iv.toString()))
                                .append("];").append(LS);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    
                    private void printState(DFA.State state) {
                        String label = state.toLabel();
                        String shape = state.accept ? "doublecircle" : "circle";
                        String color = 
                            state.init          ? "green"   :
                            state.pureAccept()  ? "red"     :
                            state.containsOmega ? "yellow"  : "white";
                        try {
                            a.append('\t').append("node [shape=").append(shape)
                             .append(", style=filled, fillcolor=").append(color).append("]; ");
                            a.append('"').append(label).append('"');
                            a.append(" [").append(dfaStateLabel(label)).append("];");
                            a.append(LS);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }.start(dfa.init);

                a.append('\t')
                .append("start")
                .append(" -> ")
                .append('"')
                .append(dfa.init.toLabel())
                .append('"')
                .append(";").append(LS);
                
                a.append('}');            
            }
        });
    }
    
    @SuppressWarnings("unchecked")
    private void writeImage(String varname, Object obj, GVPrinter gvp) {
        String fileName = obj.getClass().getSimpleName();
        String baseName = m_dir + fileName;
        int n = 0;
        if (imgMap.containsKey(baseName)) {
            n = imgMap.get(baseName);
            ++n;
        }
        imgMap.put(baseName, n);
        baseName = baseName + '_' + n;
        StringBuilder gvOut = new StringBuilder(LS + '\t');
        int gvReturn = -1;
        try {
            Writer gvw; 
            gvw = new FileWriter(baseName + ".gv");
            gvp.printGV(obj, gvw);    // code block
            gvw.flush();
            gvw.close();
            ProcessBuilder pb = new ProcessBuilder(
                    GV_DOT, "-Tgif", "-o",
                    baseName + ".gif",
                    baseName + ".gv");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            gvReturn = proc.waitFor();
            if (gvReturn != 0) {
                gvOut.append("graphviz returned " + gvReturn + LS);
            }
            InputStream is = proc.getInputStream();
            int c;
            while ((c = is.read()) != -1) {
                gvOut.append((char) c);
            }
        } catch (IOException e) {
            gvOut.append(e);
        } catch (InterruptedException e) {
            gvOut.append(e);
        }
        int i = baseName.indexOf(FS, baseName.indexOf(FS) + 1) + 1;
        String gifFile = baseName.substring(i) + ".gif";
        XHTML.Anchor a = new XHTML.Anchor(gifFile);
        a.setAttribute("id", varname);
        a.appendChild(new XHTML.Img(gifFile, "Browser can't display image(?!)", 
            Math.min(widthOf(baseName), MAXWIDTH)));
        sb.append(a.toString());
        sb.append(XHTML.para(gvOut.toString()));
        sb.append(XHTML.br()).append(XHTML.br());
    }
    
    private static final int MAXWIDTH = 800;
    
    private int widthOf(String baseName) {
        try {
            DataInputStream dis = new DataInputStream(new FileInputStream(baseName + ".gif"));
            byte[] buff = new byte[10];
            dis.readFully(buff);
            assert new String(buff, 0, 6, "US-ASCII").equals("GIF89a");
            return ((0xFF & buff[7]) << 8) | (0xFF & buff[6]);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}