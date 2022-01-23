/*
 * @LICENSE@
 */

/**
 * 
 */
package org.xtrms.regex;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Iterator;
import java.util.regex.MatchResult;

import static org.xtrms.regex.MatchResultImpl.uox;

/**
 * A {@link Matcher}-like class which operates on streaming input. Note: the
 * API for this class is new and subject to change. 
 *
 */
public final class StreamMatcher extends AbstractMatcher implements
        MatchResult, Closeable {

    /**
     * The initial input char buffer capacity.
     * <p>
     * {@link #CHAR_BUFFER_CAPACITY_INIT} is not <code>private</code> or
     * <code>final</code> in order to facilitate testing.
     */
    static int CHAR_BUFFER_CAPACITY_INIT = 4096;

    /**
     * The maximum input buffer size.
     * <p>
     * This represents an absolute limit on the size of lexemes matched. This
     * limit is set to one megabyte. It has default accessability (package
     * protected) for the reasons specified for
     * {@link #CHAR_BUFFER_CAPACITY_INIT}.
     */
    static int CHAR_BUFFER_CAPACITY_LIMIT = CHAR_BUFFER_CAPACITY_INIT * 256;

    private final Readable r;
    
    /**
     * Constructs a new <code>StreamMatcher</code> which reads input from the
     * specified file and uses the specified <code>Pattern</code>. Characters
     * are decoded from the input using {@link Charset#defaultCharset()} (the
     * platform default).
     * 
     * @param file
     *            The input file
     * @param p
     *            the {@link Pattern} used to match against the input.
     * @throws FileNotFoundException
     *             if <code>file</code> is not found.
     */
    public StreamMatcher(File file, Pattern p) throws FileNotFoundException {
        this(new FileInputStream(file).getChannel(), Charset.defaultCharset()
            .newDecoder(), p);
    }

    /**
     * Constructs a new <code>StreamMatcher</code> which reads input from the
     * specified file, and using the specfied charset name, and specified
     * <code>Pattern</code>.
     * 
     * @param file
     *            The input file
     * @param charsetName
     *            The name of the charset used to decode characters.
     * @param p
     *            the {@link Pattern} used to match against the input.
     * @throws FileNotFoundException
     *             if <code>file</code> is not found.
     */
    public StreamMatcher(File file, String charsetName, Pattern p)
            throws FileNotFoundException {
        this(new FileInputStream(file).getChannel(), Charset.forName(
            charsetName).newDecoder(), p);
    }

    public StreamMatcher(InputStream in, Pattern p) {
        this(new InputStreamReader(in, Charset.defaultCharset()), p);
    }

    public StreamMatcher(InputStream in, String charsetName, Pattern p) {
        this(new InputStreamReader(in, Charset.forName(charsetName)
            .newDecoder()), p);
    }

    public StreamMatcher(ReadableByteChannel rbc, Pattern p) {
        this(rbc, Charset.defaultCharset().newDecoder(), p);
    }

    public StreamMatcher(ReadableByteChannel rbc, String charsetName, Pattern p) {
        this(rbc, Charset.forName(charsetName).newDecoder(), p);
    }

    private StreamMatcher(ReadableByteChannel rbc, CharsetDecoder dec, Pattern p) {
        this(Channels.newReader(rbc, dec, -1), p);
    }

    public StreamMatcher(Readable r, Pattern p) {
        super(p);
        this.r = r;
        CharBuffer cb = CharBuffer.allocate(CHAR_BUFFER_CAPACITY_INIT);
        cb.flip();
        csq = cb;
    }

    private boolean charBufferInvariants() {
        CharBuffer cb = (CharBuffer) csq;
        return 0 <= appendPosition && appendPosition <= start 
                && start <= i && i <= regionEnd 
                && cb.length() == regionEnd
                && (
                        (cb.position() == 0 && cb.limit() == cb.length())
                    ||
                        (cb.position() == 1 && cb.limit() == cb.length() - 1));
    }

    /**
     * Get more input and grow the buffer if necessary. Note, when compacting
     * the buffer, if at least one character has been consumed, then one
     * character previously sent into the engine is saved in the buffer, so that
     * 1) the lookbehind semantics of the anchors and boundaries are preserved,
     * and 2) so the start == regionStart condition is only true for the _true_
     * beginning of input. TODO: need a test case where first match consumes 1
     * char and next match exhausts buffer, forcing read. TODO: test that the \A
     * anchor truly only works at the begining of input.
     * 
     * @return true if at least one new character was read.
     */
    @Override
    protected boolean moreInput() {
        assert charBufferInvariants();
        int nchars = 0;
        if (result != null) {
            try {
                result.append(csq, appendPosition, start);
            } catch (IOException e) {
                iox = e;
            }
        }
        CharBuffer cb = (CharBuffer) csq;
        int newCbPosition, newStart;
        if (start == 0) {
            newCbPosition = newStart = 0;
        } else {
            newCbPosition = start - 1;
            newStart = 1;
        }
        cb.position(newCbPosition);
        if (cb.remaining() < cb.capacity()) {
            cb.compact();
        } else {
            if (cb.capacity() >= CHAR_BUFFER_CAPACITY_LIMIT) {
                throw new IllegalStateException("max char buffer size exceeded");
            }
            CharBuffer newCb = CharBuffer.allocate(cb.capacity() * 2);
            newCb.put(cb);
            cb = newCb;
        }
        try {
            while ((nchars = r.read(cb)) == 0);     // got something, or EOF
        } catch (IOException e) {
            iox = e;
        }
        cb.flip();
        csq = cb;
        i -= newCbPosition;
        matchEnd -= newCbPosition;
        start = end = appendPosition = newStart;
        regionEnd = csq.length();
        assert charBufferInvariants();
        return nchars != -1;
    }

    public IOException ioException() {
        return iox;
    }

    /**
     * Scans input for a match of the current pattern which begins at the
     * current location.
     * @return true if there is a match of the current pattern at the current
     * location.
     */
    public boolean scanNext() {
        end = matchEnd;             
        end += zedBump;
        start = end;
        
        if (end > regionEnd) {
            assert zedBump == 1 && end == regionEnd + 1; // only way to get here
            if (!moreInput()) {
                cga.clear(0);
                match = false;
                return found = false;
            } else {
                assert end <= regionEnd;    // must be true if >0 chars returned
            }
        }

        evalProlog(false);
        
        assert start == end;
        engine.eval(this);
        
        evalEpilog();
        if (match) {
            zedBump = (end == start) ? 1 : 0;
        } else {
            ++start;
            end = start;
        }
        return match;
    }

    private final Iterable<String> scanner = new Iterable<String>() {
        public Iterator<String> iterator() {
            return new Iterator<String>() {
                public boolean hasNext() {
                    return scanNext();
                }

                public String next() {
                    return group();
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    };

    public Iterable<String> scaner() {
        return scanner;
    }
    public boolean findNext() {
        /*
         * Note: always need to guarantee that moreInput() gets a shot at
         * providing more input, even in cases where zedBump pushes start past
         * regionEnd().
         * Also, the fix that worked for Matcher here doesn't work because 
         * CharBuff invariants are violated.
         * This needs to be fixed before find2() is implemented, because
         * bump-along loop needs to be working and completely generic 
         * for vanilla DFA to incorporate.
         */
        end = matchEnd;
        end += zedBump;
        start = end;
        found = false;
        if (end > regionEnd) {
            assert zedBump == 1 && end == regionEnd + 1; // only way to get here
            if (!moreInput()) {
                cga.clear(0);
                match = false;
                return found = false;
            } else {
                assert end <= regionEnd;    // must be true if >0 chars returned
            }
        }
        if (engineHasFindLoop) {
            evalProlog(true);
            engine.eval(this);
            if (found = match = cga.match(0)) {
                end = matchEnd = start + cga.end(0);
                zedBump = (cga.end(0) - cga.start(0)) == 0 ? 1 : 0;
            } else {
                end = regionEnd;
                zedBump = 1;
            }
        } else {
            while (end <= regionEnd) {
                evalProlog(false);
                engine.eval(this);
                if (match = cga.match(0)) {
                    end = matchEnd = start + cga.end(0);
                    zedBump = (end == start) ? 1 : 0;
                    return found = true;
                } else {
                    ++start;
                    end = start;
                }
            }
            return found = false;
        }
        return found;
    }

    private final Iterable<String> finder = new Iterable<String>() {
        public Iterator<String> iterator() {
            return new Iterator<String>() {
                public boolean hasNext() {
                    return findNext();
                }

                public String next() {
                    return group();
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    };

    public Iterable<String> finder() {
        return finder;
    }

    public Appendable setResult(Appendable result) {
        Appendable ret = this.result;
        this.result = result;
        return ret;
    }

    public Appendable getResult() {
        return result;
    }

    public StreamMatcher appendReplacement(String replacement) {
        installReplacement(replacement);
        return (StreamMatcher) doAppendReplacement();

    }

    /**
     * Implements a terminal step in an append-and-replace loop.
     * <p>
     * In contrast to the {@link Matcher#appendTail(StringBuilder)} method,
     * {@link #appendTail()} is not necessary when the final call to
     * {@link #findNext()} returns <code>false</code>. This is because all
     * input which is not matched is automatically appended to the
     * <code>result</code> object (passed as a parameter in
     * {@link #setResult(Appendable)}); when the end of the stream is reached,
     * all unmatched input has been appended to the <code>result</code>.
     * <p>
     * However, if replacements are done programmatically, and it is then
     * desired to pass through the rest of the input stream to the
     * <code>result</code> object, the {@link #appendTail()} method should be
     * used.
     * <p>
     * Example: replace the first three "foo"s with "bar"s. 
     * <blockquote><pre>
     *      for (int i=0; i<3; ++i) {
     *          findNext("foo"))
     *          appendReplacement("bar");
     *      }
     *      appendTail();
     * </pre></blockquote>
     * 
     * @return the {@link Appendable} object passed in
     *         {@link #setResult(Appendable)}.
     */
    public Appendable appendTail() {
        do {
            doAppendTail();
            start = end = appendPosition = i = csq.length();
        } while (!moreInput());
        return result;
    }

    public Appendable replaceAll(String replacement) {
        installReplacement(replacement);
        while (findNext()) {
            doAppendReplacement();
        }
        doAppendTail();
        return result;
    }
    /**
     * Sets a new Pattern for the StreamMatcher to use. 
     * @param newPattern
     *            the pattern to attach this StreamMatcher to.
     * @return this StreamMatcher (useful for invocation chaining)
     */

    @Override
    public StreamMatcher usePattern(Pattern newPattern) {
        return (StreamMatcher) super.usePattern(newPattern);
    }

    @Override
    public String toString() { // TODO: report buffer state?
        return super.toString();
    }

    public void close() {
        if (this.result != null) {
            try {
                if (result instanceof Flushable) {
                    ((Flushable) this.result).flush();
                }
                if (result instanceof Closeable) {
                    ((Closeable) this.result).close();
                }
            } catch (IOException e) {
                iox = e;
            }
        }
    }

    /*
     * TODO: consider having these return the offset from the beginning
     * of the stream - could be useful to clients. How much does this alter
     * the contract? What does it even mean in the context of not having
     * a csq? Reporting the offset could be viewed as more true to the intent.
     * 
     * As an alternative, consider a new interface which returns longs.
     */
    
    /**
     * <i>Not supported</i>.
     * @throws UnsupportedOperationException
     */
    @Override
    public int start() {
        uox();
        return -1;
    }

    /**
     * <i>Not supported</i>.
     * @throws UnsupportedOperationException
     */
    @Override
    public int start(int group) {
        uox();
        return -1;
    }

    /**
     * <i>Not supported</i>.
     * @throws UnsupportedOperationException
     */
    @Override
    public int start(String name) {
        uox();
        return -1;
    }

    /**
     * <i>Not supported</i>.
     * @throws UnsupportedOperationException
     */
    @Override
    public int end() {
        uox();
        return -1;
    }
    
    /**
     * <i>Not supported</i>.
     * @throws UnsupportedOperationException
     */
    @Override
    public int end(int group) {
        uox();
        return -1;
    }
    
    /**
     * Not implemented.
     * @throws UnsupportedOperationException
     */
    @Override
    public int end(String name) {
        uox();
        return -1;
    }
}
