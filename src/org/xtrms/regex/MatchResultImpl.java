/* @LICENSE@  
 */
/*
 * TODO:
 * + deal with offset problem on groups: start[1 thru n] += start[0]
 */

package org.xtrms.regex;

import java.util.regex.MatchResult;

import org.xtrms.regex.AbstractMatcher.CGA;


class MatchResultImpl implements MatchResult {

    static final class Stream extends MatchResultImpl {
        private Stream(AbstractMatcher m) {
            super(m);
        }
        @Override
        public int end() {
            uox();
            return -1;
        }
        @Override 
        public int end(int group) {
            uox();
            return -1;
        }
        @Override
        public int start() {
            uox();
            return -1;
        }
        @Override
        public int start(int group) {
            uox();
            return -1;
        }
    }
    
    private static final MatchResult bogusMR = new MatchResult() {

        public int end() {
            isx();
            return 0;
        }
        public int end(int group) {
            isx();
            return 0;
        }
        public int start() {
            isx();
            return 0;
        }
        public int start(int group) {
            isx();
            return 0;
        }
        public String group() {
            isx();
            return null;
        }
        public String group(int group) {
            isx();
            return null;
        }
        public int groupCount() {
            isx();
            return 0;
        }
    };
    
    static void uox() {
        throw new UnsupportedOperationException("unsupported operation");
    }
    
    static void isx() {
        throw new IllegalStateException("no previous match");
    }
    
    static MatchResult newMatchResult(AbstractMatcher m) {
        if (m.match) {
            if (m instanceof StreamMatcher) {   // forgive me.
                return new MatchResultImpl.Stream(m);
            } else {
                return new MatchResultImpl(m); 
            }
        } else {
            return bogusMR;
        }
    }

    final CGA cga;
    final String[] groups;
    /**
     * copy ctor
     * @param impl the Impl to copy
     */
    private MatchResultImpl(AbstractMatcher m) {
        assert m.match && m.cga.ngroups == m.pattern.ncg + 1;
        cga = new CGA(m.start, m.cga);
        groups = new String[m.cga.ngroups];
        groups[0] = m.csq.subSequence(cga.start(0), cga.end(0)).toString();
    }
    
    public int end() {
        return cga.end(0);
    }
    public int end(int group) {
        return cga.end(group);
    }
    public int start() {
        return cga.start(0);
    }
    public int start(int group) {
        return cga.start(group);
    }

    public String group() {
        return groups[0];
    }

    public String group(int group) {
        if (groups[group] == null) {
            if (start(group) != -1) {
                assert end(group) >= 0;
                groups[group] = groups[0].substring(
                    start(group) - start(), end(group) - end());
            }
        }
        return groups[group];
    }
    
    public int groupCount() {
        return groups.length - 1;
    }
    
}    
