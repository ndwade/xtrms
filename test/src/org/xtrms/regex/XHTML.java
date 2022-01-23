package org.xtrms.regex;

import static org.xtrms.regex.Misc.LS;

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xtrms.regex.Misc.ImmutableIterator;

public final class XHTML {
    
    public static final String PROLOG = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + LS + 
        "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" " + LS + 
        "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">" + LS + 
        "<html xmlns=\"http://www.w3.org/1999/xhtml\">" + LS;
    public static final String BODY = "<body>" + LS;
    public static final String EPILOG = "</body>" + LS + "</html>";
    
    private enum Tag {
        A, P, SPAN, BR, HR, 
        OBJECT, IMG, 
        TABLE, CAPTION, TR, TH, TD, FONT, 
        HEAD, TITLE, LINK;
    }
    
    public enum TagCase {
        LOWER() {
             @Override
            String stringFrom(Tag tag) {
                 return tag.toString().toLowerCase();
             }
        },
        UPPER() {
            @Override
            String stringFrom(Tag tag) {
                 return tag.toString();
             }
            
        };
        abstract String stringFrom(Tag tag);
    }
    
    private static final class Pair {
        final String key;
        final Object value;
        Pair(String key, Object value) {
            this.key = key;
            this.value = value;
        }
    }
    
    private static final class CollectionRecord {
        final String keylabel;
        final String valclass;
        final Iterable<Pair> pairs;
        final Class<?> valType;
        public CollectionRecord(String keylabel, String valclass, 
                Iterable<Pair> pairs, Class<?> valType) {
            this.keylabel = keylabel;
            this.valclass = valclass;
            this.pairs = pairs;
            this.valType = valType;
        }
    }
    
    public interface DecompositionFilter {
        boolean decompose(Class<?> clazz);
    }
    
    public static final DecompositionFilter defaultDecompositionFilter = 
            new DecompositionFilter() {
        public boolean decompose(java.lang.Class<?> clazz) {
            return clazz.getPackage() == XHTML.class.getPackage()
                && !clazz.isEnum();
        }
    };

    private static boolean isComposite(Class<?> clazz, DecompositionFilter df) {
        return clazz == null 
            ? false 
            : !df.decompose(clazz) 
                ? false
                : clazz.getDeclaredFields().length > 0 
                    ? true
                    : isComposite(clazz.getSuperclass(), df);
    }

    private static boolean isOK(Field f) {
        int mod;
        return !f.isSynthetic() 
                && !Modifier.isStatic(mod = f.getModifiers()) 
                && !Modifier.isTransient(mod);
    }
    
    private static Field[] getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<Field>();
        getAllFields(clazz, fields);
        return fields.toArray(new Field[fields.size()]);
        
    }
    private static void getAllFields(Class<?> clazz, List<Field> fields) {
        if (clazz == null) {
            return;
        }
        for (Field f : clazz.getDeclaredFields()) {
            if (isOK(f)) {
                f.setAccessible(true);
                fields.add(f);
            }
        }
        getAllFields(clazz.getSuperclass(), fields);
    }

    private static CollectionRecord crFrom(final Object o) {
        if (o.getClass().isArray()) {
            Class<?> valType = o.getClass().getComponentType();
            Object[] a = (Object[]) o;
            if (a.length > 0) valType = a[0].getClass();    // veddy dodgy stuff
            return new CollectionRecord(
                "Index",
                o.getClass().getComponentType().getSimpleName(),
                new Iterable<Pair>() {
                    public Iterator<Pair> iterator() {
                        return new ImmutableIterator<Pair>() {
                            int i = 0;
                            public boolean hasNext() {
                                return i < Array.getLength(o);
                            }
                            public Pair next() {
                                return new Pair("[" + i + ']', Array.get(o, i++));
                            }
                        };
                    }
                }, valType);
        } else if (o instanceof Map) {
            final Map<?, ?> m = (Map<?, ?>) o;
            Class<?> valType = Object.class;
            if (m.size() > 0) valType = m.values().iterator().next().getClass();
            return new CollectionRecord(
                "Key",
                "Value",
                new Iterable<Pair>() {
                    public Iterator<Pair> iterator() {
                        return new ImmutableIterator<Pair>() {
                            final Iterator<?> iter = m.keySet().iterator();
                            public boolean hasNext() {
                                return iter.hasNext();
                            }
                            public Pair next() {
                                Object key = iter.next();
                                return new Pair(key.toString(), m.get(key));
                            }
                        };
                    }
                }, valType);
        } else if (o instanceof Iterable) {
            final Collection<?> c = (Collection<?>) o;
            Class<?> valType = Object.class;
            if (c.size() > 0) valType = c.iterator().next().getClass();
            return new CollectionRecord(
                "Ordinal",
                "Value",
                new Iterable<Pair>() {
                    public Iterator<Pair> iterator() {
                        return new ImmutableIterator<Pair>() {
                            int i = 0;
                            final Iterator<?> iter = c.iterator();
                            public boolean hasNext() {
                                return iter.hasNext();
                            }
                            public Pair next() {
                                return new Pair("(" + i++ + ')', iter.next());
                            }
                        };
                    }
                }, valType);
        } else {
            return null;
        }
    }
    
    private static final Document newDoc() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        return builder.newDocument();
    }
    
    private static class RawElement {

        protected final Document doc;
        protected final Element root;
        protected final TagCase tc;
        
        protected RawElement(Tag tag) {
            this(tag, TagCase.LOWER);
        }
        protected RawElement(Tag tag, TagCase tc) {
            doc = newDoc();
            doc.appendChild(root = doc.createElement(tc.stringFrom(tag)));
            this.tc = tc;
        }
        protected RawElement(Document doc, Element parent, Tag tag) {
            this(doc, parent, tag, TagCase.LOWER);
        }
        protected RawElement(Document doc, Element parent, Tag tag, TagCase tc) {
            this.doc = doc;
            this.root = (Element) parent.appendChild(
                doc.createElement(tc.stringFrom(tag)));
            this.tc = tc;
        }
        public final RawElement setAttribute(String name, String value) {
            root.setAttribute(name, value);
            return this;
        }
        public final RawElement appendChild(RawElement child) {
            root.appendChild(doc.importNode(child.root, true));
            return this;
        }
        public final RawElement appendTextChild(String data) {
            root.appendChild(doc.createTextNode(data));
            return this;
        }
        @Override
        public String toString() {
            StringWriter sw;
            try {
                Transformer t = TransformerFactory.newInstance().newTransformer();
                if (tc == TagCase.LOWER) {
                    t.setOutputProperty(OutputKeys.INDENT, "yes");
                }
                t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                t.transform(new DOMSource(doc), new StreamResult(sw = new StringWriter()));
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            return sw.toString();
        }
    }
    
    public static final class Br extends RawElement {
        private Br(Document doc, Element parent, TagCase tc) {
            super(doc, parent, Tag.BR, tc);
        }
        public Br() {super(Tag.BR);}
    }
    
    public static final class Hr extends RawElement {
        public Hr() {
            super(Tag.HR);
        }
    }
    public static final class Para extends RawElement {
        private Para(Document doc, Element parent, TagCase tc, String data) {
            super(doc, parent, Tag.P, tc);
            appendTextChild(data);
        }
        public Para(String data) {
            super(Tag.P); 
            appendTextChild(data);
        }
    }
    public static final class Span extends RawElement {
        private Span(Document doc, Element parent, TagCase tc, 
                String classval, String data) {
            super(doc, parent, Tag.SPAN, tc);
            if (classval != null) setAttribute("class", classval);
            appendTextChild(data);
        }
        public Span(String classval, String data) {
            super(Tag.SPAN, TagCase.LOWER);
            if (classval != null) setAttribute("class", classval);
            appendTextChild(data);
        }
    }
    public static final class Anchor extends RawElement {
        Anchor(String url) {
            super(Tag.A);
            setAttribute("href", url);
        }
    }
    public static final class ObjectElement extends RawElement {
        public ObjectElement(String url, String alt) {
            super(Tag.OBJECT);
            setAttribute("data", url);
            appendTextChild(alt);
        }
    }
    
    public static final class Img extends RawElement {
        public Img(String src, String alt) {
            super(Tag.IMG);
            setAttribute("src", src);
            setAttribute("alt", alt);
        }
        public Img(String src, String alt, int width) {
            this(src, alt);
            setAttribute("width", Integer.toString(width));
        }
    }
    
    public static final class Font extends RawElement {
        private Font(Document doc, Element parent, TagCase tc,
                String face, String color, int points) {
            super(doc, parent, Tag.FONT, tc);
            if (face != null) setAttribute("FACE", face);
            if (color != null) setAttribute("COLOR", color);
            if (points > 0) setAttribute("POINT-SIZE", Integer.toString(points));
        }
        public Font(String face, String color, int points) {
            super(Tag.FONT, TagCase.UPPER);
            if (face != null) setAttribute("FACE", face);
            if (color != null) setAttribute("COLOR", color);
            if (points > 0) setAttribute("POINT-SIZE", Integer.toString(points));
        }
        
        public Table table(int border, int cellpadding, int cellspacing) {
            Table table = new Table(doc, root, TagCase.UPPER, null);
            table.setAttribute("BORDER", Integer.toString(border));
            table.setAttribute("CELLPADDING", Integer.toString(cellpadding));
            table.setAttribute("CELLSPACING", Integer.toString(cellspacing));
            return table;
        }
    }
    
    public static final class Table extends RawElement {

        final DecompositionFilter df;

        private Table(Document doc, Element parent, TagCase tc, String caption) {
            this(doc, parent, tc, defaultDecompositionFilter, caption);
        }

        private Table(Document doc, Element parent, TagCase tc, 
                DecompositionFilter df, String caption) {
            super(doc, parent, Tag.TABLE, tc);
            this.df = df;
            if (caption != null) {
                appendChild(new RawElement(Tag.CAPTION, tc)
                .appendTextChild(caption));
            }
        }

        public Table(String caption, String...ths) {
            this(TagCase.LOWER, caption, ths);
        }
        public Table(TagCase tc, String caption, String...ths) {
            this(tc, defaultDecompositionFilter, caption, ths);
        }
        public Table(TagCase tc, DecompositionFilter df, String caption, String...ths) {
            super(Tag.TABLE, tc);
            this.df = df;
            if (caption != null) {
                appendChild(new RawElement(Tag.CAPTION, tc)
                .appendTextChild(caption));
            }
            if (ths.length > 0) row(Tag.TH, ths);            
        }
        
        public final class Row extends RawElement {
            
            Cell cell;
            private Row() {
                super(Table.this.doc, Table.this.root, Tag.TR, Table.this.tc);
            }
            
            public final class Cell extends RawElement {
                private Cell(Tag tag) {
                    super(Row.this.doc, Row.this.root, tag, Row.this.tc);
                }
                public Br br() {
                    return new Br(doc, root, tc);
                }
                public Para para(String data) {
                    return new Para(doc, root, tc, data);
                }
                public Span span(String data) {
                    return new Span(doc, root, tc, null, data);
                }
                public Font font(String face, String color, int points) {
                    return new Font(doc, root, tc, face, color, points);
                }
                public Table table() {
                    return table(null);
                }
                public Table table(String caption) {;
                    Table table = new Table(doc, root, tc, df, caption);
                    return table;
                }
            }
            public Cell cell() {
                return new Cell(Tag.TD);
            }
            public Cell cell(Object o) {
                return cell("", o);
            }
            private Cell cell(String classval, Object o) {
                cell = new Cell(Tag.TD);
                CollectionRecord cr;
                if (o == null) {
                    cell.setAttribute("class", classval);
                    cell.appendTextChild("null");
                } else if ((cr = crFrom(o)) != null) {
                    Table subtable = cell.table();
                    if (isComposite(cr.valType, df)) {
                        Field[] fields = getAllFields(cr.valType);
                        Row subrow = subtable.row();
                        subrow.headercell("", cr.keylabel);
                        for (Field field : fields) {
                            subrow.headercell("", field.getName());
                        }
                        for (Pair p : cr.pairs) {
                            subrow = subtable.row();
                            subrow.cell("key", p.key);
                            for (Field field : fields) {
                                try {
                                    subrow.cell("val", field.get(p.value));
                                } catch (Exception ex) {
                                    throw new RuntimeException(ex);
                                }
                            }
                        }
                    } else {
                        subtable.header(cr.keylabel, cr.valclass);
                        for (Pair p : cr.pairs) {
                            Row subrow = subtable.row();
                            subrow.cell("key", p.key);
                            subrow.cell("val", p.value);
                        }
                    }
                } else if (isComposite(o.getClass(), df)) {
                    cell.setAttribute("class", "composite");
                    Field[] fields = getAllFields(o.getClass());
                    Table subtable = cell.table(o.getClass().getSimpleName());
                    Row subrow = subtable.row();
                    for (Field field : fields) {
                        subrow.headercell("", field.getName());
                    }
                    subrow = subtable.row();
                    for (Field field : fields) {
                        try {
                            subrow.cell("", field.get(o));
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                } else {
                    cell.setAttribute("class", classval);
                    cell.appendTextChild(o.toString());
                }
                return cell;
            }
            public Row headercell(String classval, String data) {
                return cell(Tag.TH, classval, data);
            }
            private Row cell(Tag tag, String classval, String data) {
                cell = new Cell(tag);
                cell.setAttribute("class", classval != null ? classval : "");
                cell.appendTextChild(data);
                return this;
            }
        }
        Row row() {
            return new Row();
        }
        Row row(String...datas) {
            return row(Tag.TD, datas);
        }
        Row header(String...datas) {
            return row(Tag.TH, datas);
        }
        private Row row(Tag tag, String...datas) {
            Row row = row();
            for (String data : datas) {
                row.cell(tag, "", data);
            }
            return row;
        }
    }
    
    public static String head(String title, URL stylesheet) {
        
        RawElement head = new RawElement(Tag.HEAD);
        
        RawElement re = new RawElement(Tag.TITLE);
        re.appendTextChild(title);
        head.appendChild(re);
        
        re = new RawElement(Tag.LINK);
        re.setAttribute("rel", "stylesheet");
        re.setAttribute("type", "text/css");
        re.setAttribute("href", stylesheet.toString());
        
        head.appendChild(re);
        
        return head.toString();
    }
    public static String br() {
        return new Br().toString();
    }
    public static String hr() {
        return new Hr().toString();
    }
    public static String para(String data) {
        return new Para(data).toString();
    }
    public static String span(String classval, String data) {
        return new Span(classval, data).toString();
    }
    public static String objectElement(String url, String alt) {
        return new ObjectElement(url, alt).toString();
    }
    public static String img(String src, String alt, int width) {
        return new Img(src, alt, width).toString();
    }
    public static Table table(String caption, String...ths) {
        return new Table(TagCase.LOWER, caption, ths);
    }
    public static Table table(String varname, Object o, DecompositionFilter df) {
        Table table = new Table(
            TagCase.LOWER, df, /* o.getClass().getSimpleName() */ null, varname);
        table.row().cell(o);
        return table;
    }
    public static Table table(int border, int cellpadding, int cellspacing) {
        Table table = new Table(TagCase.UPPER, null);
        table.setAttribute("BORDER", Integer.toString(border));
        table.setAttribute("CELLPADDING", Integer.toString(cellpadding));
        table.setAttribute("CELLSPACING", Integer.toString(cellspacing));
        return table;
    }
    public static Font font(String face, String color, int points) {
        return new Font(face, color, points);
    }

    
    private XHTML() {}

    static class Baz {
        String bleg = "oh hai";
        Integer n = 42;
    }
    static class Zorp {
        Baz baz = new Baz();
        Double f = 3.14;
    }
    enum Nonce {NONCE;}

    /**
     * @param args
     */
    public static void main(String[] args) throws IOException {
        
        FileWriter fw = new FileWriter("test.xhtml");
        
        fw.write(PROLOG);
        fw.write(head("Mah Nu Bitz", 
            XHTML.class.getResource("/resources/stylesheets/log.css")));
        fw.write(BODY);
        
        RawElement re = new Para("title paragraph test data lah dee dah");
        re.setAttribute("class", "title");
        fw.write(re.toString());
        
        fw.write(span("caption", "caption span-tastic"));
        fw.write(br() + br());
        
        Table table = new Table("mah table", "column A", "column B");
        table.setAttribute("FOO", "bar");
        table.row("foo yung", "bar &roll");
        table.row("weak tea", "strong <booze>");
        Table.Row row = table.row();
        row.cell("will this work?");
        row.cell(new Zorp());
        row = table.row();
        List<Integer> list = new ArrayList<Integer>();
        list.add(1); list.add(1); list.add(2); list.add(3); list.add(5);
        row.cell(list);
        row.cell(new String[] {"Hughey", "Lewy", "Screwy"});
        row = table.row();
        row.cell("some map");
        row.cell(new Object() {
            @SuppressWarnings("unused")
            double d = 6.18;
            @SuppressWarnings({
                    "serial", "unused"
            })
            Object map = new HashMap<Integer, String>() {
                Map<Integer, String> init(Integer i, String s) {
                    put(i, s);
                    return this;
                }
            }.init(42, "answer"); 
        });
        row = table.row();
        row.cell(Nonce.NONCE);
        row.cell(Nonce.NONCE);
        fw.write(table.toString());
        fw.write(EPILOG);
        fw.flush();
        fw.close();
    }

}
