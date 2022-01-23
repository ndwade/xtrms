/* @LICENSE@  
 */
package org.xtrms.regex;

import static org.xtrms.regex.Misc.LS;
import static org.xtrms.regex.Misc.clear;

import java.io.Flushable;
import java.io.IOException;
import java.util.Stack;

import org.xtrms.regex.AST.Visitor.TraversalOrder;

/**
 * 
 * Uninstantiable class which serves as a source container for the static
 * classes and static methods used the construction of Abstract Syntax Trees.
 * 
 * @author ndw
 *
 */
final class AST {

    static abstract class Node {
        
        final Node copy() {
            return new CopyVisitor().copy(this);
        }
        
        final String toTreeString() {
            final StringBuilder sb = new StringBuilder();
            new AbstractTreePrinter(sb) {
                @Override
                protected Formatter newFormatter() {
                    return new Formatter() {
                        private int nspace = 0;
                        private void indent() {
                            try {
                                for (int i=0; i<nspace; ++i) {
                                    a.append(' ');
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        @Override
                        void push() {
                            nspace += 4;
                        }
                        @Override
                        void pop() {
                            nspace -= 4;
                        }                        
                        @Override
                        void appendNonTerminal(String label) {
                            try {
                                indent();
                                a.append(label).append(LS);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        };
                        @Override
                        void appendTerminal(
                                int position, 
                                String ccName) {
                            try {
                                indent();
                                a.append(ccName).append(' ')
                                    .append('{')
                                        .append(Integer.toString(position))
                                    .append('}')
                                    .append(LS);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    };
                }
            }.print(Node.this);
            return sb.toString();
        }
    
        /**
         * The equals relation is always the identity relation for all Node
         * subclasses. 
         */
        @Override
        public final boolean equals(Object o) {
            return super.equals(o);
        }
        @Override
        public final int hashCode() {
            return super.hashCode();
        }
        @Override
        public final String toString() {
            
            return new Visitor(TraversalOrder.SUBCLASS_DEFINED) {
                
                private final StringBuilder sb = new StringBuilder();
                
                int level = -1; 
                int cgLevel = Integer.MIN_VALUE;
                
                @Override 
                public String toString() {
                    clear(sb);
                    visit(Node.this);
                    return sb.toString();
                }
                
                @Override 
                protected void visit(Node node) {
                    ++level;
                    super.visit(node);
                    --level;
                }
                
                @Override
                protected void visit(Cat node) {
                    visit(node.first);
                    visit(node.second);
                }
                
                @Override
                protected void visit(Alt node) {
                    boolean capturing = isCapturing();
                    sb.append(capturing ? "" : "(?:");
                    visit(node.first);
                    sb.append('|');
                    visit(node.second);
                    sb.append(capturing ? "" : ")");
                }
                
                @Override
                protected void visit(Plus node) {
                    visitUnaryChild(node.child);
                    sb.append('+').append(node.mood.glyph);
                }
                @Override
                protected void visit(Question node) {
                    visitUnaryChild(node.child);
                    sb.append('?').append(node.mood.glyph);
                }

                @Override
                protected void visit(Star node) {
                    visitUnaryChild(node.child);
                    sb.append('*').append(node.mood.glyph);
                }

                @Override
                protected void visit(CG node) {
                    cgLevel = level;
                    sb.append('(');
                    visit(node.child);
                    sb.append(')');
                }

                @Override
                protected void visit(Terminal node) {
                    sb.append(node.cc);
                }
                
                private boolean isCapturing() {
                    return cgLevel == level - 1;
                }

                private void visitUnaryChild(Node child) {
                    boolean paren = !(                  // everything _except_ ...
                            child instanceof Terminal   // unary operator binds OK
                            || child instanceof Alt      // 'Or' already has parens
                            || child instanceof CG);    // CG will get parens
                    if (paren) {
                        sb.append("(?:");
                    }
                    visit(child);
                    if (paren) {
                        sb.append(')');
                    }
                }
            }.toString();
        }
    }
    
    static final class Terminal extends Node {
        
        final CharClass cc;

        private Terminal(CharClass cc) {
            assert cc != null;
            this.cc = cc;
        }
    }
    
    static abstract class NonTerminal extends Node {

        abstract Node[] children();
    }
    
    static abstract class Unary extends NonTerminal {

        final Node child;
        private Unary(Node child) {
            this.child = child;
        }

        @Override
        final Node[] children() {
            return new Node[] {child};
        }
    }
    
    static final class CG extends Unary {
        
        final int cgi;
        final String name;
        
        private CG(int cgi, Node child) {
            this(cgi, null, child);
        }
        private CG(int cgi, String name, Node child) {
            super(child);
            this.cgi = cgi;
            this.name = name;
        }
    }
    
    static abstract class Quantifier extends Unary {
        
        enum Mood {
            
            GREEDY(""), RELUCTANT("?"), POSSESSIVE("+")
            /* SNARKY("~"), OBSESSIVE("@"), COMPULSIVE("!") */ ;

            final String glyph;
            Mood(String glyph) {
                this.glyph = glyph;
            }
        }
        
        final Mood mood;
        private Quantifier(Node child, Mood mood) {
            super(child);
            this.mood = mood;
        }
        private Quantifier(Node child) {
            this(child, Mood.GREEDY);
        }
    }
    
    static final class Star extends Quantifier {

        private Star(Node child, Mood mood) {
            super(child, mood);
        }
    }
    
    static final class Plus extends Quantifier {

        private Plus(Node child, Mood mood) {
            super(child, mood);
        }
    }
    
    static final class Question extends Quantifier {

        private Question(Node child, Mood mood) {
            super(child, mood);
        }
    }

    static abstract class Binary extends NonTerminal {
        
        final Node first, second;
        
        private Binary(Node first, Node second) {
            this.first = first;
            this.second = second;
        }
        
        @Override
        final Node[] children() {
            return new Node[] {first, second};
        }
    }

    static final class Cat extends Binary {
        
        private Cat(Node first, Node second) {
            super(first, second);
        }
    }
    
    static final class Alt extends Binary {
        
        private Alt(Node first, Node second) {
            super(first, second);
        }
    }
    
    static abstract class Visitor {
        
        enum TraversalOrder {
            TOP_DOWN,
            BOTTOM_UP,
            SUBCLASS_DEFINED;
        }

        private final TraversalOrder order;
        
        protected Visitor(TraversalOrder order) {
            this.order = order;
        }
        
        
        /*
         * multi-dispatch: 
         * - allows Visitor subclasses to deal with the exact granularity they want. 
         * - "instanceof" dispatch is ugly but it's only in one place - here.
         */
        
        protected void visit(Node node) {
            if (node instanceof NonTerminal) {
                visit((NonTerminal) node);
            } else if (node instanceof Terminal) {
                visit((Terminal) node);
            } else {
                error(node);
            }
        }
        
        protected void visit(NonTerminal node) {
            if (order == TraversalOrder.BOTTOM_UP) {
                for (Node n : node.children()) {
                    visit(n);
                }
            }
            if (node instanceof Binary) {
                visit((Binary) node);
            } else if (node instanceof Unary){
                visit((Unary) node);
            } else {
                error(node);
            }
            if (order == TraversalOrder.TOP_DOWN) {
                for (Node n : node.children()) {
                    visit(n);
                }
            }
        }
        
        protected void visit(Binary node) {
            if (node instanceof Cat) {
                visit((Cat) node);
            } else if (node instanceof Alt) {
                visit((Alt) node);
            } else {
                error(node);
            }
        }
        
        protected void visit(Unary node) {

            if (node instanceof CG) {
                visit((CG) node);
            } else if (node instanceof Quantifier) {
                visit((Quantifier) node);
            } else error(node);
        }
        
        protected void visit(Quantifier node) {
            if (node instanceof Star) {
                visit((Star) node);
            } else if (node instanceof Plus) {
                visit((Plus) node);
            } else if (node instanceof Question) {
                visit((Question) node);
            } else error(node);        
        }
        
        protected void visit(Cat node) {}
        protected void visit(Alt node) {}
        
        protected void visit(Star node) {}
        protected void visit(Plus node) {}
        protected void visit(Question node) {}
        protected void visit(CG node) {}

        protected void visit(Terminal node) {}
        
        private static void error(Node node) {
            assert false : "unknown node type " + node;
        }
    }
        
    static abstract class AbstractTreePrinter extends Visitor {

        protected abstract class Formatter {
            
            abstract void appendNonTerminal(String label);
            abstract void appendTerminal(int position, String ccName);
            abstract void push();
            abstract void pop();
            
            protected String prolog() {return "";}
            protected String epilog() {return "";}
        }
        
        protected final Appendable a;

        protected AbstractTreePrinter(Appendable a) {
            super(TraversalOrder.TOP_DOWN);
            this.a = a;
            formatter = newFormatter();
        }
        
        final void print(Node root) {
            try {
                a.append(formatter.prolog());
                position = 0;
                visit(root);
                a.append(formatter.epilog());
            } catch (IOException e1) {
                throw new RuntimeException(e1);
            }
            if (a instanceof Flushable) {
                try {
                    ((Flushable) a).flush();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }            
        }
        
        private final Formatter formatter;
        protected abstract Formatter newFormatter();
        
        protected int position = 0;
        
        @Override
        protected final void visit(NonTerminal node) {
            super.visit(node);
            formatter.pop();
        }

        @Override
        protected final void visit(Cat node) {
            formatter.appendNonTerminal("&");
            formatter.push();
        }

        @Override
        protected final void visit(Alt node) {
            formatter.appendNonTerminal("|");
            formatter.push();
        }

        @Override
        protected final void visit(Plus node) {
            formatter.appendNonTerminal("+" + node.mood.glyph);
            formatter.push();
        }

        @Override
        protected final void visit(Question node) {
            formatter.appendNonTerminal("?" + node.mood.glyph);
            formatter.push();
        }

        @Override
        protected final void visit(Star node) {
            formatter.appendNonTerminal("*" + node.mood.glyph);
            formatter.push();
        }
        @Override
        protected final void visit(CG node) {
            formatter.appendNonTerminal("cg[" + node.cgi + ']');
            formatter.push();
        }

        @Override
        protected final void visit(Terminal node) {
            formatter.appendTerminal(position++,node.toString());
        }
        /*
         * prevent subclasses from overriding
         */
        @Override
        protected final void visit(Node node) {
            super.visit(node);
        }
        @Override
        protected final void visit(Binary node) {
            super.visit(node);
        }
        @Override
        protected final void visit(Unary node) {
            super.visit(node);
        }
        @Override
        protected final void visit(Quantifier node) {
            super.visit(node);
        }
    }
    
    
    /*
     * static factories of convenience for parser and testing
     */

    static Terminal literal(char c) {
        return literal(c, false, false);
    }
    static Terminal literal(char c, boolean foldcase, boolean unicode) {
        return new Terminal(CharClass.newSingleChar(c, foldcase, unicode));
    }

    static Node literal(String s) {
        return literal(s, false, false);
    }

    static Node literal(String s, boolean foldcase, boolean unicode) {
        char[] chars = s.toCharArray();
        Node root = null;
        
        for (char c : chars) {
            Node lc = new Terminal(
                CharClass.newSingleChar(c, foldcase, unicode));
            if (root == null) {
                root = lc;
            } else {
                root = new Cat(root, lc);
            }
        }
        return root;
    }

    static Terminal terminal(CharClass cc) {
        return new Terminal(cc);
    }

    static Terminal terminal(String cc) {
        return terminal(new RegexParser().parseCharClass(cc));
    }

    static Node cat(Node... nodes) {
        Node root = null;
        for (Node node : nodes) {
            if (root == null) {
                root = node;
            } else {
                root = new Cat(root, node);
            }
        }
        return root;
    }

    static Node alt(Node... nodes) {
        Node root = null;
        for (Node node : nodes) {
            if (root == null) {
                root = node;
            } else {
                root = new Alt(root, node);
            }
        }
        return root;
    }

    static Star star(Node child) {
        return new Star(child, Quantifier.Mood.GREEDY);
    }

    static Plus plus(Node child) {
        return new Plus(child, Quantifier.Mood.GREEDY);
    }

    static Question question(Node child) {
        return new Question(child, Quantifier.Mood.GREEDY);
    }

    static Star star(Node child, Quantifier.Mood mood) {
        return new Star(child, mood);
    }

    static Plus plus(Node child, Quantifier.Mood mood) {
        return new Plus(child, mood);
    }

    static Question question(Node child, Quantifier.Mood mood) {
        return new Question(child, mood);
    }

    static CG captureGroup(int cgi, Node child) {
        return new CG(cgi, child);
    }
    
    static CG captureGroup(int cgi, String name, Node child) {
        return new CG(cgi, name, child);
    }
    
    static class CopyVisitor extends Visitor {

        protected final Stack<Node> kids = new Stack<Node>();

        CopyVisitor() {
            super(TraversalOrder.BOTTOM_UP);
        }

        protected final void push(Node node) {
            kids.push(node);
        }
        
        Node copy(Node node) {
            assert node != null;
            visit(node);
            assert kids.size() == 1;
            return kids.pop();
        }
        
        @Override
        protected void visit(Terminal node) {
            push(new Terminal(node.cc));
        }
        @Override
        protected void visit(Cat node) {
            Node second = kids.pop();
            Node first = kids.pop();
            push(new Cat(first, second));
        }
        @Override
        protected void visit(Alt node) {
            Node second = kids.pop();
            Node first = kids.pop();
            push(new Alt(first, second));
        }
        @Override
        protected void visit(Star node) {
            push(new Star(kids.pop(), node.mood));
        }
        @Override
        protected void visit(Plus node) {
            push(new Plus(kids.pop(), node.mood));
        }
        @Override
        protected void visit(Question node) {
            push(new Question(kids.pop(), node.mood));
        }
        @Override
        protected void visit(CG node) {
            push(new CG(node.cgi, kids.pop()));
        }
    }
    
    private AST() {}    // uninstantiable
}
