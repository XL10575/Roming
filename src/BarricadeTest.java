import org.junit.*;
import static org.junit.Assert.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.*;

public class BarricadeTest {
    /**
     * A Handler that stores LogRecords in a list (so we can test logging).
     */
    private static class TestHandler extends Handler {
        public final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record != null) {
                records.add(record);
            }
        }

        @Override public void flush() {}
        @Override public void close() throws SecurityException {}
    }

    private Logger logger;
    private TestHandler handler;

    /**
     * Set up the logger before each test.
     */
    @Before
    public void setUp() {
        logger = Logger.getLogger(Barricade.class.getName());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        for (Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
        }
        handler = new TestHandler();
        logger.addHandler(handler);
    }

    /**
     * Remove the logger handler after each test.
     */
    @After
    public void tearDown() {
        logger.removeHandler(handler);
    }

    /**
     * Inject a fake underlying map implementation into a RoamingMap, so we can control behavior.
     */
    private <K extends Comparable<K>, V> RoamingMap<K, V> inject(Map<K, V> fake) {
        RoamingMap<K, V> rm = new RoamingMap<>();
        try {
            Field f = RoamingMap.class.getDeclaredField("map");
            f.setAccessible(true);
            f.set(rm, fake);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return rm;
    }

    // -----------------------------------------------------------
    // TESTS for correctSize(...) coverage
    // -----------------------------------------------------------

    /**
     * Mismatch in entry set => "size method of RoamingMap operated incorrectly"
     */
    @Test
    public void testCorrectSize_OperatedIncorrectly() {
        Map<String, String> fake = new TreeMap<>() {
            boolean firstCall = true;
            @Override
            public Set<Map.Entry<String, String>> entrySet() {
                // On second call, let's add something, so the sets differ
                if (!firstCall) {
                    put("NEW", "val");
                }
                firstCall = false;
                return super.entrySet();
            }
        };
        fake.put("A", "1");
        RoamingMap<String, String> map = inject(fake);

        try {
            Barricade.correctSize(map);
            fail("Expected runtime exception for changed entrySet");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("size method of RoamingMap operated incorrectly"));
        }
    }

    /**
     * Wrong numeric size => logs a warning, but doesn't throw
     */
    @Test
    public void testCorrectSize_WrongValue() {
        Map<String, String> fake = new TreeMap<>() {
            @Override
            public int size() {
                // The actual set has 1 entry, but let's say 999
                return 999;
            }
        };
        fake.put("X", "abc");
        RoamingMap<String, String> map = inject(fake);

        int actual = Barricade.correctSize(map);
        assertEquals(1, actual);
        assertFalse("Should have logged a warning", handler.records.isEmpty());
        assertTrue(
            handler.records.stream().anyMatch(
                r -> r.getMessage().contains("size method of RoamingMap returned incorrect value")
            )
        );
    }

    /**
     * Normal/happy path => correct result, no logging
     */
    @Test
    public void testCorrectSize_HappyPath() {
        RoamingMap<String, String> map = new RoamingMap<>();
        map.put("A", "one");
        map.put("B", "two");

        int size = Barricade.correctSize(map);
        assertEquals(2, size);
        assertTrue("No warnings expected", handler.records.isEmpty());
    }

    // -----------------------------------------------------------
    // TESTS for correctStringRepresentation(...) coverage
    // -----------------------------------------------------------

    /**
     * Mismatch in entry set => "toString method of RoamingMap operated incorrectly"
     */
    @Test
    public void testCorrectStringRepresentation_OperatedIncorrectly() {
        Map<String, String> fake = new TreeMap<>() {
            boolean firstCall = true;
            @Override
            public String toString() {
                if (!firstCall) {
                    // On the second call, let's remove something => mismatch
                    remove("B");
                }
                firstCall = false;
                return super.toString();
            }
        };
        fake.put("A","X");
        fake.put("B","Y");
        RoamingMap<String, String> map = inject(fake);

        try {
            Barricade.correctStringRepresentation(map);
            fail("Expected runtime exception for changed entry set in toString");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("toString method of RoamingMap operated incorrectly"));
        }
    }

    /**
     * Wrong string => logs a warning, but doesn't throw
     */
    @Test
    public void testCorrectStringRepresentation_WrongString() {
        Map<String, String> fake = new TreeMap<>() {
            @Override
            public String toString() {
                return "WRONG";
            }
        };
        fake.put("K","V");
        RoamingMap<String, String> map = inject(fake);

        String s = Barricade.correctStringRepresentation(map);
        // The correct string is something like "{K=V}"
        assertTrue(s.contains("K=V"));
        assertFalse(handler.records.isEmpty());
        assertTrue(
            handler.records.stream().anyMatch(
                r -> r.getMessage().contains("toString method of RoamingMap returned incorrect value")
            )
        );
    }

    /**
     * Normal/happy path => no warning, no throw
     */
    @Test
    public void testCorrectStringRepresentation_HappyPath() {
        RoamingMap<Integer, String> map = new RoamingMap<>();
        map.put(10, "ten");
        String s = Barricade.correctStringRepresentation(map);
        assertTrue(s.contains("10=ten"));
        assertTrue("No logs expected", handler.records.isEmpty());
    }

    // -----------------------------------------------------------
    // TESTS for getWithStateVar(...) coverage
    // -----------------------------------------------------------

    /**
     * Changing the entry set mid-get => "get method of RoamingMap operated incorrectly"
     */

    /**
     * get(...) returns wrong value => logs a warning, but doesn't throw
     */
    @Test
    public void testGetWithStateVar_WrongValue() {
        Map<String, String> fake = new TreeMap<>() {
            @Override
            public String get(Object key) {
                return "WRONGVAL";
            }
        };
        fake.put("K","REALVAL");
        RoamingMap<String, String> map = inject(fake);

        var result = Barricade.getWithStateVar(map, "K");
        assertEquals("REALVAL", result.value()); // used the old "correct" value
        assertFalse(handler.records.isEmpty());
        assertTrue(
            handler.records.stream().anyMatch(
                r -> r.getMessage().contains("get method of RoamingMap returned incorrect value")
            )
        );
    }

    /**
     * Normal/happy path => no logs, no throw
     */
    @Test
    public void testGetWithStateVar_HappyPath() {
        RoamingMap<String, String> map = new RoamingMap<>();
        map.put("A","valA");

        var result = Barricade.getWithStateVar(map, "A");
        assertEquals("valA", result.value());
        assertTrue("No warnings expected", handler.records.isEmpty());
    }

    // -----------------------------------------------------------
    // TESTS for putWithStateVar(...) coverage
    // -----------------------------------------------------------

    /**
     * Changing entry set after put => "put method of RoamingMap operated incorrectly"
     */
    @Test
    public void testPutWithStateVar_StateChange() {
        Map<String, String> fake = new TreeMap<>() {
            boolean secondCall = false;
            @Override
            public Set<Map.Entry<String, String>> entrySet() {
                if (secondCall) {
                    // remove the newly inserted entry
                    remove("NEW");
                }
                secondCall = true;
                return super.entrySet();
            }
        };
        RoamingMap<String, String> map = inject(fake);

        try {
            Barricade.putWithStateVar(map, "NEW", "VALUE");
            fail("Expected runtime exception for changed entry set in put");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("put method of RoamingMap operated incorrectly"));
        }
    }

    /**
     * put(...) fails to actually insert => "put method of RoamingMap operated incorrectly"
     */
    @Test
    public void testPutWithStateVar_FailsToInsert() {
        Map<String, String> fake = new TreeMap<>() {
            @Override
            public String put(String k, String v) {
                // do nothing
                return null;
            }
        };
        RoamingMap<String, String> map = inject(fake);

        try {
            Barricade.putWithStateVar(map, "A", "B");
            fail("Expected runtime exception due to missing insertion");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("put method of RoamingMap operated incorrectly"));
        }
    }

    /**
     * Normal/happy path => no logs, no throw
     */
    @Test
    public void testPutWithStateVar_HappyPath() {
        RoamingMap<String, String> map = new RoamingMap<>();
        var result = Barricade.putWithStateVar(map, "X", "Y");
        assertNull("No previous value => returns null", result.value());
        assertTrue("No warnings expected", handler.records.isEmpty());
    }

    // -----------------------------------------------------------
    // NULL-ARGUMENT TESTS
    // -----------------------------------------------------------

    @Test(expected = NullPointerException.class)
    public void testGetWithStateVar_NullMap() {
        Barricade.getWithStateVar(null, "KEY");
    }

    @Test(expected = NullPointerException.class)
    public void testGetWithStateVar_NullKey() {
        RoamingMap<String, String> map = new RoamingMap<>();
        Barricade.getWithStateVar(map, (String) null);
    }

    @Test(expected = NullPointerException.class)
    public void testPutWithStateVar_NullMap() {
        Barricade.putWithStateVar(null, "K", "V");
    }

    @Test(expected = NullPointerException.class)
    public void testPutWithStateVar_NullKey() {
        RoamingMap<String, String> map = new RoamingMap<>();
        Barricade.putWithStateVar(map, (String) null, "val");
    }

    @Test(expected = NullPointerException.class)
    public void testPutWithStateVar_NullValue() {
        RoamingMap<String, String> map = new RoamingMap<>();
        Barricade.putWithStateVar(map, "K", null);
    }

    @Test(expected = NullPointerException.class)
    public void testCorrectSize_NullMap() {
        Barricade.correctSize(null);
    }

    @Test(expected = NullPointerException.class)
    public void testCorrectKeySet_NullMap() {
        Barricade.correctKeySet(null);
    }

    @Test(expected = NullPointerException.class)
    public void testCorrectEntrySet_NullMap() {
        Barricade.correctEntrySet(null);
    }

    @Test(expected = NullPointerException.class)
    public void testCorrectStringRepresentation_NullMap() {
        Barricade.correctStringRepresentation(null);
    }

    // -----------------------------------------------------------
    // TRIVIAL CONSTRUCTOR COVERAGE
    // -----------------------------------------------------------
    @Test
    public void testBarricadeConstructor() {
        new Barricade();
    }
}
