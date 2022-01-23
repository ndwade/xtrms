/* @LICENSE@  
 */

package org.xtrms.regex;

import junit.framework.TestCase;

public class CharClassTestCase extends TestCase {
    
    private CharClass cc0, cc1, ccx, cc;
    
    private RegexParser rxParser = new RegexParser();
    
    private CharClass newPositive(String s) {
        return rxParser.parseCharClass("[" + s + ']');
    }

    private CharClass newComplement(String s) {
        return rxParser.parseCharClass("[^" + s + ']');
    }


    public static void main(String[] args) {
        junit.textui.TestRunner.run(CharClassTestCase.class);
    }

    public CharClassTestCase(String arg0) {
        super(arg0);
    }

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }
    
    public void testCtor() {
        cc0 = newPositive("abc");
        cc1 = newPositive("abc");
        assertEquals(cc0, cc1);
    }
    public void testUnion() {
        cc0 = newPositive("abc");
        cc1 = newPositive("def");
        ccx = newPositive("abcdef");
        cc = cc0.union(cc1);
        assertEquals(ccx, cc);
        
        cc0 = newPositive("abc");
        cc1 = newComplement("abc");
//        ccx = newComplement("");
        ccx = CharClass.EMPTY.complement();
        cc = cc0.union(cc1);
        assertEquals(ccx, cc);
    }
    public void testDifference() {
        cc0 = newPositive("abcdef");
        cc1 = newPositive("acef");
        ccx = newPositive("bd");
        cc = cc0.difference(cc1);
        assertEquals(ccx, cc);

        cc0 = newPositive("abcdef");
        cc1 = newPositive("abc");
        ccx = newPositive("def");
        cc = cc0.difference(cc1);
        assertEquals(ccx, cc);

        cc0 = newPositive("acegik");
        cc1 = newPositive("abcdefghjik");
//        ccx = newPositive("");
        ccx = CharClass.EMPTY;
        cc = cc0.difference(cc1);
        
        assertEquals(ccx, cc);
        cc0 = newPositive("acegik");
        cc1 = newPositive("abcdefhjik");
        ccx = newPositive("g");
        cc = cc0.difference(cc1);
        assertEquals(ccx, cc);

        cc0 = newPositive("abcegik");
        cc1 = newPositive("bcdefghjik");
        ccx = newPositive("a");
        cc = cc0.difference(cc1);

        cc0 = newPositive("abcdefg123");
        cc1 = newComplement("abc");
        ccx = newPositive("abc");
        cc = cc0.difference(cc1);
        
    }
    
    public void testIntersection() {
        
        cc0 = newComplement("a");
        cc1 = newComplement("ei");
        ccx = newComplement("aei");
        cc = cc0.intersection(cc1);
        assertEquals(ccx, cc);
        
        cc0 = newComplement("a");
        cc1 = newPositive("b");
        ccx = newPositive("b");
        cc = cc0.intersection(cc1);
        assertEquals(ccx, cc);

        cc0 = newPositive("abcdef");
        cc1 = newPositive("acefxyz");
        ccx = newPositive("acef");
        cc = cc0.intersection(cc1);
        assertEquals(ccx, cc);
        
        cc0 = newPositive("abcegik");
        cc1 = newPositive("bcdefghjik");
        ccx = newPositive("bcegik");
        cc = cc0.intersection(cc1);
        assertEquals(ccx, cc);
    }
    
    public void testContains() {
        
        cc0 = newComplement("abcdef");
        cc1 = newPositive("axyz");
        assertFalse(cc0.contains(cc1));
        
        cc0 = newPositive("abcdef");
        cc1 = newPositive("abc");
        assertTrue(cc0.contains(cc1));
        
        cc0 = newPositive("abcdef");
        cc1 = newPositive("abcdef");
        assertTrue(cc0.contains(cc1));
        
        cc0 = newComplement("abcdef");
        cc1 = newPositive("xyz");
        assertTrue(cc0.contains(cc1));
        
        cc0 = newComplement("abc");
        cc1 = newComplement("abcdef");
        assertTrue(cc0.contains(cc1));
        
        cc0 = newComplement("ac");
        cc1 = newComplement("acdf");
        assertTrue(cc0.contains(cc1));
    }
    
//    public void testInitAnchors() {
//        for (int flags=0; flags < RXE.MAX_INIT_COMBOS; ++flags) {
//            if (flags > 0) {
//                assertFalse(
//                    CharClass.initAnchors[flags].equals(
//                        CharClass.initAnchors[flags-1]));
//            }
//            System.out.println("" + flags + ": " + CharClass.initAnchors[flags]);
//        }
//        for (int f : CharClass.initAnchorMap.keySet()) {
//            System.out.println("" + f + ": " + 
//                new CharClass.Builder(CharClass.initAnchorMap.get(f)).build());
//        }
//        System.out.println("all: " + CharClass.ALL_INIT);
//    }
    
//    public void testMergedSpecial() {
//        cc = CharClass.EOF.union(CharClass.EMPTY);
//        System.out.println(cc);
//        cc0 = CharClass.OMEGA;
//        cc1 = CharClass.newSingleChar('a');
//        SortedSet<CharClass> ccs = new TreeSet<CharClass>();
//        ccs.add(cc0);
//        ccs.add(cc1);
//        ccs = CharClass.disjointPartition(ccs);
//        for (CharClass cc : ccs) {
//            System.out.println(cc);
//        }
//    }
}
