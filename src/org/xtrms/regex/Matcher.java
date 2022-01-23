/*
 * @LICENSE@
 */
package org.xtrms.regex;

import static org.xtrms.regex.Misc.LS;
import static org.xtrms.regex.Misc.clear;

import java.util.regex.MatchResult;

/**
 * Analog to the {@link java.util.regex.Matcher} class. Note that like the
 * analagous {@link java.util.regex.Matcher} class of the standard regex
 * package, instances of this class are <em>not</em> thread safe - it is the
 * responsibility of the client to ensure that the methods of an instance are
 * not re-entered.
 */
public final class Matcher extends AbstractMatcher implements MatchResult {

    Matcher(Pattern pattern, CharSequence csq) {
        super(pattern);
        reset(csq);
    }

    @Override
    protected boolean moreInput() {
        return false;
    }

    public String replaceAll(String replacement) {
        reset();
        StringBuilder sb = new StringBuilder();
        while (find()) {
            appendReplacement(sb, replacement);
        }
        appendTail(sb);
        return sb.toString();
    }

    public String replaceFirst(String replacement) {
        reset();
        StringBuilder sb = new StringBuilder();
        if (find()) {
            appendReplacement(sb, replacement);
        }
        appendTail(sb);
        return sb.toString();
    }

    public Matcher appendReplacement(StringBuilder sb, String replacement) {
        installReplacement(replacement);
        result = sb;
        return (Matcher) doAppendReplacement();
    }

    public StringBuilder appendTail(StringBuilder sb) {
        result = sb;
        return (StringBuilder) doAppendTail();
    }

    public Matcher reset() {
        return reset(csq);
    }

    public Matcher reset(CharSequence csq) {
        start = 0;
        end = 0;
        regionStart = 0;
        regionEnd = csq.length();
        hitEnd = false;
        found = false;
        appendPosition = 0;
        matchEnd = 0; // used to evaluate \G anchor condition
        zedBump = 0; // used to advance find() on zero length match
        this.csq = csq;
        return this;
    }

    public boolean lookingAt() {
        end = start = regionStart;
        evalProlog(false);
        assert start == end;
        engine.eval(this);
        evalEpilog();
        return match;
    }

    public boolean find() {
        end = matchEnd;
        end += zedBump;
        start = end;
        if (end > regionEnd) {
            assert zedBump == 1 && end == regionEnd + 1; // only way to get
            // here
            cga.clear(0);
            match = false;
            return found = false;
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
            return found;
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
    }

    public boolean find(int start) {
        reset();
        matchEnd = start;
        return find();
    }

    public boolean matches() {
        return lookingAt() && end == regionEnd;
    }

    public Matcher region(int start, int end) {
        if (start < 0 || start > end || end > csq.length()) {
            throw new IndexOutOfBoundsException();
        }
        reset();
        regionStart = matchEnd = start;
        regionEnd = end;
        return this;
    }

    public int regionStart() {
        return regionStart;
    }

    public int regionEnd() {
        return regionEnd;
    }

    public boolean hasTransparentBounds() {
        return false;
    }

    /**
     * <i>Not currently implemented.</i> Bounds are "stuck at" anchoring.
     * Attempts to set transparent bounds will result in an
     * {@link UnsupportedOperationException}.
     * 
     * @param b
     * @return this Matcher
     */
    public Matcher useTransparentBounds(boolean b) {
        if (b) {
            throw new UnsupportedOperationException();
        }
        return this;
    }

    public boolean hasAnchoringBounds() {
        return true;
    }

    /**
     * <i>Not currently implemented.</i> Bounds are "stuck at" anchoring.
     * Attempts to set transparent bounds will result in an
     * {@link UnsupportedOperationException}.
     * 
     * @param b
     * @return this Matcher
     */
    public Matcher useAnchoringBounds(boolean b) {
        if (!b) {
            throw new UnsupportedOperationException();
        }
        return this;
    }

    public boolean hitEnd() {
        return hitEnd;
    }

    public boolean requireEnd() {
        return requireEnd;
    }

    /**
     * Sets a new Pattern for the Matcher to use. 
     * @param newPattern
     *            the pattern to attach this Matcher to.
     * @return this Matcher (useful for invocation chaining)
     */
    @Override
    public Matcher usePattern(Pattern newPattern) {
        return (Matcher) super.usePattern(newPattern);
    }

    @Override
    public String toString() {
        String superString = super.toString();
        clear(sb);
        sb.append(superString).append(LS);
        sb.append("appendPosition=").append(appendPosition).append(LS);
        return sb.toString();
    }
}
