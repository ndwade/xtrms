/* @LICENSE@  
 */

package org.xtrms.regex;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.xtrms.regex.AST.CG;
import org.xtrms.regex.AST.CopyVisitor;
import org.xtrms.regex.AST.Node;
import org.xtrms.regex.AST.Visitor;
import org.xtrms.regex.AST.Visitor.TraversalOrder;
import org.xtrms.regex.Misc.MaybeZipped;

import static org.xtrms.regex.AST.*;
import static org.xtrms.regex.Misc.isSet;

final class RegexParser {
    
    static final class Result {
        Result(Node root, int ncg, Map<String, Integer> cgNames) {
            this.root = root;
            this.ncg = ncg;
            this.cgNames = Collections.unmodifiableMap(cgNames);
        }
        final Node root;
        final int ncg;
        final Map<String, Integer> cgNames;
    }
    
    private static final int EOX = -1;  // end of expression
    private static final int BACKSLASH = 0x80000000;
    
    private static final CharClass DOT_UNICODE = 
        new CharClass.Builder(CharClass.LS_UNICODE).complement().build();
    private static final CharClass DOT_UNIX = 
        new CharClass.Builder(CharClass.LS_UNIX).complement().build();
    
    private static final CharClass CC_DIGIT = new CharClass.Builder()
            .add('0', '9')
            .build();
    
    static final CharClass CC_WORD = new CharClass.Builder()
            .add('a', 'z').add('A', 'Z').add('_').add(CC_DIGIT)
            .build();
    
    static final CharClass CC_NAME = 
            new CharClass.Builder(CC_WORD).add('.').build();
    
    static final CharClass CC_NWORD = (CC_WORD.complement()).intersection(CharClass.OMEGA);
    
    private static final CharClass CC_SPACE = new CharClass.Builder()
            .add(' ').add('\t').add('\n').add('\u000B').add('\f').add('\r')
            .build();
    
    private static final Node NIL = question(terminal(CharClass.EPSILON));
    
    private static final Map<String, CharClass> posixCcMap = new HashMap<String, CharClass>();
    
    static {
        CharClass.Builder ccb = new CharClass.Builder();
        RegexParser rp = new RegexParser(); // yeah, I know...
        posixCcMap.put("Lower", rp.parseCharClass("[a-z]"));
        posixCcMap.put("Upper", rp.parseCharClass("[A-Z]"));
        posixCcMap.put("ASCII", rp.parseCharClass("[\\x00-\\x7f]"));
        posixCcMap.put("Alpha", ccb.init(posixCcMap.get("Lower")).add(posixCcMap.get("Upper")).build());
        posixCcMap.put("Digit", rp.parseCharClass("[0-9]"));
        posixCcMap.put("Alnum", ccb.init(posixCcMap.get("Alpha")).add(posixCcMap.get("Digit")).build());
        posixCcMap.put("Punct", rp.parseCharClass("[!\"#$%&\'()*+,-./:;<=>?@\\[\\\\\\]\\^_`{|}~]"));
        posixCcMap.put("Graph", ccb.init(posixCcMap.get("Alnum")).add(posixCcMap.get("Punct")).build());
        posixCcMap.put("Print", ccb.init(posixCcMap.get("Graph")).add('\u0020').build());
        posixCcMap.put("Blank", rp.parseCharClass("[ \t]"));
        posixCcMap.put("Cntrl", rp.parseCharClass("[\\x00-\\x1F\\x7F]"));
        posixCcMap.put("XDigit", rp.parseCharClass("[0-9a-fA-F]"));
        posixCcMap.put("Space", rp.parseCharClass("[ \t\n\\x0B\f\r]"));
    }
    
    /*
     *  Lazy initialization holder class idiom for static fields
     */
    private static final class CategoryCc {
        static final Map<String, CharClass> map = initCcMap("categoryCcMap");
    }
    private static Map<String, CharClass> categoryCcMap() {
        return CategoryCc.map;
    }

    private static final class BlockCc {
        static final Map<String, CharClass> map = initCcMap("blockCcMap");
    }
    private static Map<String, CharClass> blockCcMap() {
        return BlockCc.map;
    }

    private static final class JavaCc {
        static final Map<String, CharClass> map = initCcMap("javaCcMap");
    }
    private static Map<String, CharClass> javaCcMap() {
        return JavaCc.map;
    }

    private static String SERIALIZED_DATA_PATH = "/resources/serialized/";
    
    @SuppressWarnings("unchecked")
    private static Map<String, CharClass> initCcMap(String filename) {
        filename = SERIALIZED_DATA_PATH + filename + ".ser";
        Map<String, CharClass> ret;
        try {
            InputStream is = RegexParser.class.getResourceAsStream(filename);
            ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(is));
             ret = (Map) ois.readObject();
        } catch(Throwable t) {
            throw new IllegalStateException(
                "error reading serialized named character classes", t);
        }
        return ret;
    }
    /*
     *  normalization method is a little too permissive but wtf.  
     */
    private static String normalizedBlockName(String name) {
        StringBuilder sb = new StringBuilder();
        assert name.startsWith("In");
        for (int i=2; i<name.length(); ++i) {
            char c = name.charAt(i);
            if (c != ' ' && c != '_') sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /*
     * fields to hold parameters and derived parameters
     */
    private String regex;
    private Expression[] exps;
    private boolean hasExps;
    private CharClass ccDot;
    private CharClass ccCaret;
    private CharClass ccDollar;
    private boolean foldcase;
    private boolean unicode;
    private boolean flatCGnames;
    
    /*
     * aux fields for parsing
     */
    private int ncg;        // capturing group index
    private int lowerQuant; // lower bound for quantification
    private int upperQuant; // etc.
    private final Map<String, Integer> cgNames = new HashMap<String, Integer>();
       
    /*
     * state for nextChar() and pushback()
     */
    private int token;      // bit 31 is set if escaped
    private int scannedInt;  // for hex code parsing etc
    
    private void init() {
        ncg = 0;
        iNext = 0;
        iCurrent = -1;
        token = EOX;
    }

    Result parse(String regex, int flags, Expression...exps) {
        
        this.regex = isSet(flags, Pattern.LITERAL) ? 
                Pattern.quote(regex) : regex;
        
        this.exps = exps;
        hasExps = exps.length > 0;
        
        if (isSet(flags, Pattern.DOTALL)) {
            ccDot = CharClass.DOT_ALL;
        } else if (isSet(flags, Pattern.UNIX_LINES)) {
            ccDot = RegexParser.DOT_UNIX;
        } else {
            ccDot = RegexParser.DOT_UNICODE;
        }
        
        /*
         * when not in MULTILINE mode, ^ behaves like \A and $ like \z
         * ...so convert them in the parser. 
         */
        if (isSet(flags, Pattern.MULTILINE)) {
            ccCaret = CharClass.CARET;
            if (isSet(flags, Pattern.UNIX_LINES)) {
                ccDollar = CharClass.DOLLAR_UNIX;
            } else {
                ccDollar = CharClass.DOLLAR_UNICODE;
            }
        } else {
            ccCaret = CharClass.BOF;
            ccDollar = CharClass.EOF;
        }
        
        foldcase = isSet(flags, Pattern.CASE_INSENSITIVE);
        unicode = isSet(flags, Pattern.UNICODE_CASE);
        flatCGnames = isSet(flags, Pattern.X_FLAT_CG_NAMES);
        
       
        init();
                
        final boolean stripCg = isSet(flags, Pattern.X_STRIP_CG);

        final Node rootTemp = regex();
        
        assert new Visitor(TraversalOrder.TOP_DOWN) {
            
            boolean noReconvergence() {
                visit(rootTemp);
                return !reconvergence;
            }
            
            boolean reconvergence = false;
            private final Map<Node, Object> id =     // in case Node.equals()
                new IdentityHashMap<Node, Object>(); // is ever defined
            
            @Override
            protected void visit(Node node) {
                if (id.put(node, new Object()) != null) reconvergence = true;
                super.visit(node);
            }  
            
        }.noReconvergence();

        final Node root = stripCg ? new AST.CopyVisitor() {
            @Override
            protected void visit(CG node) { /* intentionally empty */ }
        }.copy(rootTemp) : rootTemp;
        
        if (stripCg) {
            ncg = 0;
            cgNames.clear();
        }

        return new Result(root, ncg, cgNames);
    }
    
    CharClass parseCharClass(String cc) {
        regex = cc;
        init();
        return parseCharClass();
    }

    private Node regex() {
        Node ret = exp();
        switch(token) {
        case EOX:
            break;
        case ')':
            syntaxError("unbalanced parenthesis");
            break;
        default:
            assert false : "unexpected char at end of term: " + (char) token;
        }
        return ret;
    }

    private Node exp() {
        Node ret = term();
        switch(token) {
        case EOX:
            break;
        case ')':
            break;
        case '|':
            ret = alt(ret, exp());
            break;
        default:
            assert false : "unexpected char at end of term: " + (char) token;
        }
        return ret;
    }

    private Node term() {
        Node ret = factor();
        switch(token) {
        case EOX:
            break;
        case ')':
            break;
        case '|':
            break;
        default:
            ret = cat(ret, term());
        }
        return ret;
    }
    
    private Node factor() {
        Node ret = null;
        Node node;
        loop:
        while (true) {
            nextToken();
            switch(token) {
            case EOX:
                break loop;
            case ')':
                break loop;
            case '|':
                break loop;
            case '(':
                nextToken();
                if (token == '?') {
                    nextToken();
                    if (token == ':') {
                        node = exp();
                    } else if (token == '<') {
                        node = parseNamedCaptureGroup();
                    } else {
                        pushbackToken();
                        node = parseSpecialConstruct();
                        if (node == null) continue loop;
                    }
                } else {
                    pushbackToken();
                    node = captureGroup(++ncg, exp());
                }
                if (token != ')') {
                    syntaxError("unbalanced parenthesis");
                }
                break;
            case '.':
                node = terminal(ccDot);
                break;                
            case '[':
                pushbackToken();
                node = terminal(parseCharClass());
                break;
            case BACKSLASH | 'd': 
                node = terminal(CC_DIGIT);
                break;
            case BACKSLASH | 'D':
                node = terminal(CC_DIGIT.complement());
                break;
            case BACKSLASH | 's': 
                node = terminal(CC_SPACE);
                break;
            case BACKSLASH | 'S':
                node = terminal(CC_SPACE.complement());
                break;
            case BACKSLASH | 'w': 
                node = terminal(CC_WORD);
                break;
            case BACKSLASH | 'W':
                node = terminal(CC_NWORD);
                break;
            case BACKSLASH | 'p':
                pushbackToken();
                node = terminal(parseNamedCharClass());
                break;
            case BACKSLASH | 'P':
                pushbackToken();
                node = terminal(parseNamedCharClass().complement());
                break;
            case BACKSLASH | 'A':
                node = terminal(CharClass.BOF);
                break;
            case BACKSLASH | 'z':
                node = terminal(CharClass.EOF);
                break;
            case BACKSLASH | 'G':
                node = terminal(CharClass.MATCH);
                break;
            case BACKSLASH | 'b':
                node = terminal(CharClass.WORD_B); 
                break;
            case BACKSLASH | 'B':
                node = terminal(CharClass.WORD_NB); 
                break;
            case BACKSLASH | 'Z':
                node = terminal(CharClass.BIGZED);
                break;
            case '^':
                node = terminal(ccCaret);
                break;
            case '$':
                node = terminal(ccDollar);
                break;
            case BACKSLASH | 'Q':
                // n.b. no quant
                ret = maybeCat(ret, literal(scanLiteral(), foldcase, unicode));
                continue loop;
            case BACKSLASH | 'E':
                node = null;
                syntaxError("bad quoting syntax: didn't see \\Q");
                break;
            case BACKSLASH | '1': case BACKSLASH | '2': case BACKSLASH | '3': case BACKSLASH | '4':
            case BACKSLASH | '5': case BACKSLASH | '6': case BACKSLASH | '7': case BACKSLASH | '8':
            case BACKSLASH | '9':
                node = null;
                syntaxError("back references are not (yet) supported by this package");
                break;
            default:
                if (hasExps && token == '<') {
                    final String name = scanName();
                    node = parseNamedSubExpression(name);
                } else {
                    maybeScanEscCode();
                    assert ((char) token) == token;
                    node = literal((char) token, foldcase, unicode);
                }
            }
            ret = maybeCat(ret, maybeQuantify(node));
        }
        return (ret != null) ?  ret : RegexParser.NIL;
    }
    
    private Node parseNamedSubExpression(final String name) {
        
        final Node namedNode = nodeOf(name);
        final int[] xncg = new int[] {ncg};

        Node node = new CopyVisitor() {

            final boolean flat = flatCGnames;

            Node instantiate() {
                visit(namedNode);
                return kids.pop();
            }
            
            @Override
            protected void visit(CG node) {
                String fullName = !flat && node.name != null 
                                ? name + '.' + node.name
                                : node.name;
                push(captureGroup(++xncg[0], fullName, kids.pop()));
                if (fullName != null) putCgName(fullName, xncg[0]);
            }
        }.instantiate();
        ncg = xncg[0];
        return node;
    }
    
    private Node parseNamedCaptureGroup() {
        String name = scanName();
        Node node;
        if (name.equals("")) { // eponymous
            if (!hasExps) {
                syntaxError(
                    "eponymous subexpression has no Expressions to reference");
            }
            nextToken();
            if (token != '<') {
                syntaxError("malformed eponymous subexpression capture");
            }
            int cgi = ++ncg;
            name = scanName();
            node = parseNamedSubExpression(name);
            nextToken();
            if (token != ')') {
                syntaxError("malformed eponymous subexpression capture");
            }
            putCgName(name, cgi);
            return captureGroup(cgi, name, node);
        } else {
            putCgName(name, ++ncg);
            return captureGroup(ncg, name, exp());
        }
    }
    private void putCgName(String name, int cgi) {
        assert name != null;
        assert !name.equals("");
        if (cgNames.put(name, cgi) != null) {
            syntaxError("duplicate capture group name: " + name);
        }
    }
    
    private Node nodeOf(String name) {
        for (Expression x : exps) {
            if (x.name.equals(name)) return x.root;
        }
        syntaxError("unknown expression name: " + name);
        return null;
    }

    private String scanName() {
        assert token == '<';
        StringBuilder sb = new StringBuilder();
        for (nextRawChar(); token != '>'; nextRawChar()) {
            if (CC_NAME.contains(token)) {
                sb.append((char) token);
            } else syntaxError("disallowed character in name");
        }
        return sb.toString();
    }
    private Node parseSpecialConstruct() {
        /*
         * n.b.: whem implementing, consume the trailing ')' iff returning null
         */
        syntaxError("special constructs not yet implemented");
        return null;
    }
    
    private static abstract class NCC {             // nested char class
        final CharClass ncc;
        NCC(CharClass ncc) {
            this.ncc = ncc;
        }
        abstract CharClass compose(CharClass cc);       // intersection or union
    }
    
    private CharClass parseCharClass() {
        List<NCC> nccList = new ArrayList<NCC>(4);      // plenty room
        CharClass.Builder ccb = new CharClass.Builder();
        nextToken();
        int beginCc = iCurrent;
        if (token != '[') {
            syntaxError("expected '['");
        }
        boolean comp = false;
        nextToken();
        if (token == '^') {
            comp = true;
            nextToken();
        }
        loop:
        while (true) {
            switch(token) {
            case EOX:
                syntaxError("char class missing end bracket");
                break loop;
            case '[':
                pushbackToken();
                nccList.add(new NCC(parseCharClass()) {
                    @Override CharClass compose(CharClass cc) {
                        return cc.union(ncc);
                    }
                });
                break;
            case '&':
                nextToken();
                if (token == '&') {
                    nccList.add(new NCC(parseCharClass()) {
                        @Override CharClass compose(CharClass cc) {
                            return cc.intersection(ncc);
                        }
                    });
                } else {
                    ccb.add('&');
                    pushbackToken();
                    break;
                }
                break;
            case ']':
                if (ccb.isEmpty() && nccList.isEmpty()) {
                    ccb.add((char) token);         // end bracket not meta if cc is empty
                } else {
                    break loop;
                }
            case '-':   
                if (!ccb.isEmpty() && hasNextChar()) {
                    char first = ccb.previousChar();   // last char
                    nextToken();
                    maybeScanEscCode();
                    if (token == ']') {         // dangling dash - non-meta
                        pushbackToken();
                        ccb.add('-');
                        break;
                    } else if (((char) token) <= first) {
                        syntaxError("non-ascending range in char class");
                    }
                    ccb.add(first, (char) token);  // automagically overlapps prev char
                } else {
                    ccb.add((char) token);         // leading '-' is just another char
                }
                break;
            case BACKSLASH | 'Q':
                while (nextLiteralChar()) {
                    ccb.add((char) token);
                }
                break;
            case BACKSLASH | 'E':
                syntaxError("bad quoting syntax: didn't see \\Q");
                break;
            case BACKSLASH | 'd': 
                ccb.add(CC_DIGIT);
                break;
            case BACKSLASH | 'D':
                ccb.add(CC_DIGIT.complement());
                break;
            case BACKSLASH | 's': 
                ccb.add(CC_SPACE);
                break;
            case BACKSLASH | 'S':
                ccb.add(CC_SPACE.complement());
                break;
            case BACKSLASH | 'w': 
                ccb.add(CC_WORD);
                break;
            case BACKSLASH | 'W':
                ccb.add(CC_WORD.complement());
                break;
            case BACKSLASH | 'p':
                pushbackToken();
                ccb.add(parseNamedCharClass());
                break;
            case BACKSLASH | 'P':
                pushbackToken();
                ccb.add(parseNamedCharClass().complement());
                break;
            default:
                maybeScanEscCode();
                assert ((char) token) == token;
                ccb.add((char) token);
                break;
            }
            nextToken();
        }
        /*
         * if compatibility with java bug 6609854 is desired, uncomment the 
         * following and then eliminate the trailing if (comp) statement
         */
//        if (comp) {
//            ccb.complement();
//        }
        CharClass ret = ccb.build();
        for (NCC ncc : nccList) {
            ret = ncc.compose(ret);
        }
        if (comp) {
            ret = ret.complement();
        }
        /*
         * we rebuild to get the correct string after composition...
         * ...init() is a cheap operation.
         */
        return ccb.init(ret).build(regex.substring(beginCc, iNext));
    }
    
    private CharClass parseNamedCharClass() {
        nextToken();
        assert token == (BACKSLASH | 'p') || token == (BACKSLASH | 'P');
        nextRawChar();
        String name;
        if (token == '{') {
            StringBuilder sb = new StringBuilder();
            while (nextRawChar() && token != '}') sb.append((char) token);
            if (token != '}') syntaxError("unclosed char class");
            name = sb.toString();
        } else {
            name = "" + (char) token;
        }
        CharClass ret = null;
        if (name.startsWith("Is")) name = name.substring("Is".length());
        if (name.startsWith("In")) {
            ret = blockCcMap().get(normalizedBlockName(name));
        } else if (name.startsWith("java")) {
            ret = javaCcMap().get(name.substring("java".length()));
        } else {
            ret = posixCcMap.get(name);
            if (ret == null) {
                ret = categoryCcMap().get(name);
            }
        }
        if (ret == null) syntaxError("unknown char class " + name);
        return ret;
    }

    private Node maybeCat(Node n1, Node n2) {
        assert n2 != null;
        return (n1 != null) ? cat(n1, n2) : n2; 
    }
    
    private Node maybeQuantify(Node node) {
        nextToken();
        switch(token) {
        case '*':
            node = star(node, maybeMoodify());
            break;
        case '+':
            node = plus(node, maybeMoodify());
            break;
        case '?':
            node = question(node, maybeMoodify());
            break;
        case '{':
            parseQuant();
            Quantifier.Mood mood = maybeMoodify();
            if (upperQuant == -1) {             // {lower,}
                if (lowerQuant > 0) {
                    node = cat(repeat(node, lowerQuant), star(node, mood));
                } else {
                    node = star(node, mood);          // {0,}
                }
            } else {                            //  {lower} or {lower,upper}
                if (upperQuant < lowerQuant) syntaxError(
                    "upper quantification bound less than lower bound");
                int range = upperQuant - lowerQuant;
                if (range > 0) {            // upper > lower
                    if (lowerQuant > 0) {       // {lower > 0, upper > lower} 
                        node = cat(repeat(node, lowerQuant), questionRange(node, range, mood));
                    } else {                    // {lower == 0, upper > lower}
                        node = question(questionRange(node, range, mood));
                    }
                } else {                        // upper == lower
                    assert range == 0;
                    if (lowerQuant > 0) {       // {lower > 0, upper == lower} 
                        node = repeat(node, lowerQuant);
                    } else {
                        return NIL;             // {0} is legal. Who knew?
                    }
                }
            }
            break;
        default:
            pushbackToken();
        }
        return node;
    }
    
    Node questionRange(Node node, int range, Quantifier.Mood mood) {
        Node ret = question(node.copy(), mood);
        while (--range > 0) {
            ret = question(cat(node.copy(), ret), mood);
        }
        return ret;
    }
    
    private Quantifier.Mood maybeMoodify() {
        Quantifier.Mood ret = Quantifier.Mood.GREEDY;
        nextToken();
        if (token == '?') {
            ret = Quantifier.Mood.RELUCTANT;
        } else if (token == '+') {
            ret = Quantifier.Mood.POSSESSIVE;
        } else {
            pushbackToken();
        }
        return ret;
    }
    
    private void parseQuant() {
        scanDecimal();
        lowerQuant = upperQuant = scannedInt;
        if (lowerQuant == -1) {
            syntaxError("malformed exact quantification");
        }
        nextToken();
        switch (token) {
        case ',':
            break;
        case '}':
            return;
        default:
            pushbackToken();
            break;
        }
        scanDecimal();
        upperQuant = scannedInt;
        nextToken();
        if (token != '}') {
            syntaxError("bad exact quantifier");
        }
    }
    
    private Node repeat(Node node, int n) {
        assert node != null;
        assert n > 0 : n;
        if (n > 255) {
            syntaxError("repeated quantifier too large: " + n);
        }
        Node[] na = new Node[n];
        for (int i=0; i<na.length; ++i) {
            na[i] = node.copy();
        }
        return cat(na);
    }
    
    /*
     * char scanner stuff
     */
    
    private int iNext;

    private boolean hasNextChar() {
        return iNext < regex.length();
    }

    private boolean nextRawChar() {
        if (hasNextChar()) {
            token = regex.charAt(iNext++);
            return true;
        } else {
            return false;
        }
    }

    private void pushbackRaw() {
        --iNext;
    }

    private int iCurrent;

    private void nextToken() {
        iCurrent = iNext;
        if (nextRawChar()) {
            if (token == '\\' && nextRawChar()) {
                token |= BACKSLASH;     
            }
        } else {
            token = EOX;
        }
    }

    private void pushbackToken() {
        iNext = iCurrent;
    }

    private boolean nextLiteralChar() {
        if (nextRawChar()) {
            if (token == '\\') {
                if (nextRawChar() && token == 'E') {
                    return false;   // no more literals!
                } else {
                    pushbackRaw();
                    token = '\\';
                    return true;
                }
            } else {
                return true;
            }
        } else {
            return false;
        }
    }
    
    private String scanLiteral() {
        StringBuilder sb = new StringBuilder();
        while (nextLiteralChar()) {
            sb.append((char) token);
        }
        return sb.toString();
    }

    private boolean scanDigit(int radix) {
        if (!nextRawChar()) {
            return false;
        }
        int d = Character.digit(token, radix);
        if (d != -1) {
            token = d;
            scannedInt *= radix;
            scannedInt += token;
            return true;
        } else {
            pushbackRaw();
            return false;
        }
    }

    private void scanDecimal() {
        scannedInt = 0;
        int digits = 0;
        while(scanDigit(10)) ++digits;
        if (digits == 0) {
            scannedInt = -1;
        }
    }
    private static final String ILLEGAL_ESC_CHARS = 
        "ghijklmoqyCEFHIJKLMNORTUVXY";

    private static final String LEGAL_CTL_CHARS = 
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
            "abcdefghijklmnopqrstuvwxyz" +
            "@[\\]^_";
    
    private void maybeScanEscCode() {
        if ((BACKSLASH & token) != 0) {
            switch(token) {
            
            case BACKSLASH | 't':
                token = '\t';
                break;
            case BACKSLASH | 'n':
                token = '\n';
                break;
            case BACKSLASH | 'r':
                token = '\r';
                break;
            case BACKSLASH | 'f':
                token = '\f';
                break;
            case BACKSLASH | 'a':
                token = '\u0007';
                break;
            case BACKSLASH | 'e':
                token = '\u001B';
                break;
           
                // octal
            case BACKSLASH | '0':
                scannedInt = 0;
                if (!scanDigit(8)) {
                    syntaxError("not enough octal digits");
                }
                boolean maybe3 = scannedInt < 4;
                if (!scanDigit(8) || (maybe3 && !scanDigit(8)));
                token = scannedInt;
                break;
            
            // hex
            case BACKSLASH | 'x':
                    scannedInt = 0;
                    if (!scanDigit(16) || !scanDigit(16)) {
                        syntaxError("not enough hex digits!");
                    }
                token = scannedInt;
                break;
                
           // unicode
            case BACKSLASH | 'u':
                    scannedInt = 0;
                    if (!scanDigit(16) || !scanDigit(16)
                            || !scanDigit(16) || !scanDigit(16)) {
                        syntaxError("not enough unicode digits!");
                    }
                    int ret = (char) scannedInt;
                    if ((ret) != scannedInt) {
                        throw new UnsupportedOperationException(
                            "code point:" + scannedInt + 
                            " is outside BMP and is unsupported at this time");
                    }
                token = ret;
                break;
                
            // ctl
            case BACKSLASH | 'c':
                assert token == (BACKSLASH | 'c');
                if (nextRawChar()) {
                    if (LEGAL_CTL_CHARS.indexOf(token) != -1) {
                        token = Character.toUpperCase(token) - 64;
                    } else {
                        pushbackRaw();
                        token = 'c';
                    }
                } else {
                    token = 'c';
                }
                break;
            
            default:
                char c = (char) token;
                if (ILLEGAL_ESC_CHARS.indexOf(c) != -1) {
                    syntaxError("Illegal or unsupported escape sequence");
                }
                token = c;
                break;
            }
        }
    }

    private void syntaxError(String msg) {
        throw new java.util.regex.PatternSyntaxException(msg, regex, iCurrent);
    }    
    
    

    /**
     * Main method exists to create serialzied maps of named char classes
     * directly from unicode.txt file. This should be done if it is found to be
     * necessary to edit the unicode.txt file to fix a bug. The method is
     * <strong>extremely</strong> inefficient! It didn't hang; give it a
     * second... l)
     */
    public static void main(String[] args) {
        
        // Fragile. This is intentional. Everything must be just so...
        
        Map<String, CharClass> categoryCcMap = new HashMap<String, CharClass>();
        Map<String, CharClass> blockCcMap = new HashMap<String, CharClass>();
        Map<String, CharClass> javaCcMap = new HashMap<String, CharClass>();

        BufferedReader br = null;
        MaybeZipped mz = new MaybeZipped("unicode/unicode.txt");
        @SuppressWarnings("unused") // for stepping through debugger only
        Reader r;           
        @SuppressWarnings("unused")
        InputStream is;
        try {            
            br = new BufferedReader(
                r = new InputStreamReader(
                    is = mz.getInputStream()));
            
            for (String line; (line = br.readLine()) != null;) {
                
                String[] fields = line.split(",");
                
                int c = Integer.valueOf(fields[0], 16);
                
                String majCat = fields[1].intern();
                check(majCat.matches("[A-Z]"));
                union(categoryCcMap(), majCat, c);

                String minCat = fields[2].intern();
                check(minCat.matches("[A-Z][a-z]"));
                union(categoryCcMap(), minCat, c);
                
                if (fields.length == 3) continue;
                
                check(fields[3].matches("[A-Z][A-Z0-9_]*[A-Z]"));
                String block = normalizedBlockName("In" + fields[3]).intern();
                union(blockCcMap, block, c);
                
                for (int i=4; i<fields.length; ++i) {
                    String java = fields[i].intern();
                    check(java.matches("[A-Z][A-Za-z]+"));
                    union(javaCcMap, java, c);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        } finally {
            try {
                if (br != null) br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (!(      serialize(categoryCcMap, "categoryCcMap")
                &&  serialize(blockCcMap,    "blockCcMap")
                &&  serialize(javaCcMap,     "javaCcMap"))) {
            System.exit(-1);
        }
    }
    
    private static void check(boolean b) {if (!b) throw new AssertionError();}
    
    private static void union(Map<String, CharClass> map, String name, int c) {
        CharClass cc = map.get(name);
        if (cc == null) cc = CharClass.EMPTY;
        cc = cc.union(CharClass.newSingleChar((char) c));
        map.put(name, cc);
    }
    
    private static boolean serialize(Map<String, CharClass> map, String filename) {
        
        filename = "src" + SERIALIZED_DATA_PATH + filename + ".ser";
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(new FileOutputStream(filename));
            oos.writeObject(map);
            oos.flush();
            oos.close();
        } catch(Throwable t) {
            t.printStackTrace();
            return false;
        }
        return true;
    }
}
