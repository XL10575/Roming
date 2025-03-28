import org.junit.*;
import static org.junit.Assert.*;
import java.lang.reflect.Field;
import java.util.*;

public class MatrixMapTest {

    @Test
    public void testInvalidLengthExceptionGetters() {
        // This covers getTheCause() and getTheLength():
        var ex = new MatrixMap.InvalidLengthException(
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
        // triggers InvalidLengthException logic internally
        MatrixMap.identity(0, 0, 1);
    }

    @Test
    public void testIdentityBranchCoverage() {
        // For size=2, we cover diagonal vs. non-diagonal calls
        MatrixMap<Integer> mm = MatrixMap.identity(2, 0, 9);
        // (0,0) => 9, (0,1) => 0, (1,1) => 9, etc.
        assertEquals((Integer)9, mm.value(0,0));
        assertEquals((Integer)0, mm.value(0,1));
        assertEquals((Integer)9, mm.value(1,1));
    }

    @Test
    public void testSize_EmptyMapCase() {
        // If the matrix has no keys at all, size() returns ORIGIN.
        // We can create an empty matrix by skipping build. 
        // Easiest hack: create 1x1 but remove the keys by reflection. 
        MatrixMap<Integer> mm = MatrixMap.constant(1, 99);

        try {
            Field f = mm.getClass().getDeclaredField("matrix");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Indexes, Integer> actualMap = (Map<Indexes, Integer>) f.get(mm);
            actualMap.clear();  // now truly empty

            Indexes s = mm.size(); // hits the "if (!iterator.hasNext())" path
            assertEquals(0, s.row());
            assertEquals(0, s.column());
        } catch (Exception e) {
            fail("Reflection error: " + e.getMessage());
        }
    }

    @Test
    public void testSizeBranchCoverage() {
        // Normal matrix with multiple keys
        MatrixMap<String> mm = MatrixMap.constant(2, "X"); 
        // The final row & column is (1,1), so size is (2,2)
        Indexes s = mm.size();
        assertEquals(2, s.row());
        assertEquals(2, s.column());
    }

    @Test
    public void testConstructor() {
        // Just some coverage for the MatrixMap constructor path
        // (Though typically it's private, we use from(...) or instance(...) to build.)
        MatrixMap<String> mm = MatrixMap.constant(1, "Test");
        assertEquals("Test", mm.value(0,0));
    }

    @Test
public void testSize_EmptyMatrix() throws Exception {
    // Build a 1x1 matrix, then remove the key by reflection => truly empty
    MatrixMap<String> mm = MatrixMap.constant(1, "X");

    Field f = MatrixMap.class.getDeclaredField("matrix");
    f.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<Indexes, String> underlying = (Map<Indexes, String>) f.get(mm);
    underlying.clear();  // now there are 0 keys

    // Now size() should see that there's no next() in the iterator
    Indexes s = mm.size();
    // Typically that means row=0, col=0
    assertEquals(0, s.row());
    assertEquals(0, s.column());
}

@Test
public void testSize_NormalMatrix() {
    MatrixMap<Integer> mm = MatrixMap.constant(2, 99);
    Indexes s = mm.size();
    // 2x2 => row=2, col=2
    assertEquals(2, s.row());
    assertEquals(2, s.column());
}

@Test(expected = NullPointerException.class)
public void testFrom_Null() {
    MatrixMap.from(null); // triggers Objects.requireNonNull(matrix)
}

@Test(expected = IllegalArgumentException.class)
public void testFrom_ZeroRow() {
    MatrixMap.from(new Integer[0][0]); // triggers requireNonEmpty for row
}

@Test(expected = IllegalArgumentException.class)
public void testFrom_ZeroColumn() {
    MatrixMap.from(new Integer[][] { {} }); // triggers requireNonEmpty for column
}

@Test
public void testFrom_Valid() {
    // Non-empty array
    Integer[][] arr = {
        {10, 20},
        {30, 40}
    };
    MatrixMap<Integer> mm = MatrixMap.from(arr);

    // Actually read back some values to ensure we fully execute "indexes -> indexes.value(matrix)"
    assertEquals((Integer)10, mm.value(0,0));
    assertEquals((Integer)20, mm.value(0,1));
    assertEquals((Integer)30, mm.value(1,0));
    assertEquals((Integer)40, mm.value(1,1));
}


    // ... (and you keep the original tests from before, such as testFromValid, testConstantValid, etc.)

}
