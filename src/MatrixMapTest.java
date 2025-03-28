import org.junit.*;
import static org.junit.Assert.*;
import java.lang.reflect.Field;
import java.util.*;

public class MatrixMapTest {

    @Test
    public void testInvalidLengthExceptionGetters() {
        MatrixMap.InvalidLengthException ex = new MatrixMap.InvalidLengthException(
            MatrixMap.InvalidLengthException.Cause.ROW, -99
        );
        assertEquals(MatrixMap.InvalidLengthException.Cause.ROW, ex.getTheCause());
        assertEquals(-99, ex.getTheLength());
    }

    @Test(expected = NullPointerException.class)
    public void testInstanceNullSize() {
        MatrixMap.instance(null, i -> i.toString());
    }

    @Test(expected = NullPointerException.class)
    public void testInstanceNullValueMapper() {
        MatrixMap.instance(new Indexes(2,2), null);
    }

    @Test
    public void testInstanceIndexesHappyPath() {
        MatrixMap<String> mm = MatrixMap.instance(new Indexes(2,2), i -> i.toString());
        // For a 2x2 matrix built with indices, (0,1) becomes "(0,1)"
        assertEquals("(0,1)", mm.value(0,1));
    }

    @Test(expected = NullPointerException.class)
    public void testValueNullIndexes() {
        MatrixMap<String> mm = MatrixMap.constant(2, "X");
        mm.value((Indexes)null);
    }

    @Test(expected = NullPointerException.class)
    public void testIdentityNullZero() {
        MatrixMap.identity(2, null, "I");
    }

    @Test(expected = NullPointerException.class)
    public void testIdentityNullIdentity() {
        MatrixMap.identity(2, "Z", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIdentityZeroSize() {
        MatrixMap.identity(0, 0, 1);
    }

    @Test
    public void testIdentityBranchCoverage() {
        MatrixMap<Integer> mm = MatrixMap.identity(2, 0, 9);
        assertEquals((Integer)9, mm.value(0,0));
        assertEquals((Integer)0, mm.value(0,1));
        assertEquals((Integer)9, mm.value(1,1));
    }

    @Test
    public void testSize_EmptyMapCase() {
        MatrixMap<Integer> mm = MatrixMap.constant(1, 99);
        try {
            Field f = mm.getClass().getDeclaredField("matrix");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Indexes, Integer> actualMap = (Map<Indexes, Integer>) f.get(mm);
            actualMap.clear();
            Indexes s = mm.size();
            assertEquals(0, s.row());
            assertEquals(0, s.column());
        } catch (Exception e) {
            fail("Reflection error: " + e.getMessage());
        }
    }

    @Test
    public void testSize_NormalMatrix() {
        MatrixMap<Integer> mm = MatrixMap.constant(2, 99);
        Indexes s = mm.size();
        assertEquals(2, s.row());
        assertEquals(2, s.column());
    }

    @Test(expected = NullPointerException.class)
    public void testFrom_Null() {
        MatrixMap.from(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFrom_ZeroRow() {
        MatrixMap.from(new Integer[0][0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFrom_ZeroColumn() {
        MatrixMap.from(new Integer[][] { {} });
    }

    @Test
    public void testFrom_Valid() {
        Integer[][] arr = { {10,20}, {30,40} };
        MatrixMap<Integer> mm = MatrixMap.from(arr);
        assertEquals((Integer)10, mm.value(0,0));
        assertEquals((Integer)20, mm.value(0,1));
        assertEquals((Integer)30, mm.value(1,0));
        assertEquals((Integer)40, mm.value(1,1));
    }

    @Test
    public void testMatrixMapToString() {
        MatrixMap<Integer> mm = MatrixMap.constant(2, 99);
        String s = mm.toString();
        assertNotNull(s);
        assertTrue(s.contains("99"));
    }

    @Test
    public void testSize_EmptyMatrix() throws Exception {
        MatrixMap<String> mm = MatrixMap.constant(1, "X");
        Field f = MatrixMap.class.getDeclaredField("matrix");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Indexes, String> underlying = (Map<Indexes, String>) f.get(mm);
        underlying.clear();
        Indexes s = mm.size();
        assertEquals(0, s.row());
        assertEquals(0, s.column());
    }

    @Test
    public void testSize_IfBranchFalse() throws Exception {
        Comparator<Indexes> descending = (a, b) -> b.compareTo(a);
        TreeMap<Indexes, String> descMap = new TreeMap<>(descending);
        descMap.put(new Indexes(3, 3), "A");
        descMap.put(new Indexes(1, 1), "B");
        descMap.put(new Indexes(0, 0), "C");
        MatrixMap<String> mm = MatrixMap.constant(1, "X");
        Field f = MatrixMap.class.getDeclaredField("matrix");
        f.setAccessible(true);
        f.set(mm, descMap);
        Indexes s = mm.size();
        assertEquals(4, s.row());
        assertEquals(4, s.column());
    }

    @Test
    public void testSize_IfBranchTrue() {
        MatrixMap<String> mm = MatrixMap.constant(2, "X");
        Indexes s = mm.size();
        assertEquals(2, s.row());
        assertEquals(2, s.column());
    }

    @Test
    public void testConstructor() {
        MatrixMap<String> mm = MatrixMap.constant(1, "Test");
        assertEquals("Test", mm.value(0,0));
    }
}
