import org.junit.*;
import static org.junit.Assert.*;
import java.util.*;
import java.util.logging.*;
import java.lang.reflect.*;

/**
 * Tests for Barricade methods – forcing branch coverage for:
 *   - getWithStateVar (both correct and state-change/wrong-value cases)
 *   - correctSize (both normal, wrong size, and changed entrySet cases)
 *   - correctStringRepresentation (both normal and changed entrySet cases)
 */
public class BarricadeTest {

    private Logger logger;
    private LoggerTestingHandler logHandler;

    @Before
    public void setupLogger() {
        logger = Logger.getLogger(Barricade.class.getName());
        logger.setUseParentHandlers(false);
        // Remove any existing handlers
        for (Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
        }
        logHandler = new LoggerTestingHandler();
        logger.addHandler(logHandler);
        logger.setLevel(Level.ALL);
    }

    @After
    public void teardownLogger() {
        logger.removeHandler(logHandler);
    }

    /**
     * Helper: Inject a fake Map into a RoamingMap via reflection.
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

    // --- getWithStateVar tests ---

    @Test
    public void testGetWithStateVar_ChangesEntrySet() {
        Map<String, String> fake = new TreeMap<>() {
            boolean secondCall = false;
            @Override
            public Set<Map.Entry<String, String>> entrySet() {
                if (secondCall) {
                    // Change the map on the second call
                    put("NEW", "changed");
                }
                secondCall = true;
                return super.entrySet();
            }
        };
        fake.put("KEY", "value");
        RoamingMap<String, String> rm = inject(fake);

        try {
            Barricade.getWithStateVar(rm, "KEY");
            fail("Expected RuntimeException for changed entry set in get");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("get method of RoamingMap operated incorrectly"));
        }
    }

    @Test
    public void testGetWithStateVar_WrongValue() {
        Map<String, String> fake = new TreeMap<>() {
            @Override
            public String get(Object key) {
                return "WRONGVAL";
            }
        };
        fake.put("K", "REALVAL");
        RoamingMap<String, String> rm = inject(fake);
        var result = Barricade.getWithStateVar(rm, "K");
        assertEquals("REALVAL", result.value());
        assertTrue(logHandler.getLastLog().isPresent());
        assertTrue(logHandler.getLastLog().get().contains("get method of RoamingMap returned incorrect value"));
    }

    @Test
    public void testGetWithStateVar_HappyPath() {
        RoamingMap<String, String> rm = new RoamingMap<>();
        rm.put("K", "valK");
        var result = Barricade.getWithStateVar(rm, "K");
        assertEquals("valK", result.value());
        assertFalse(logHandler.getLastLog().isPresent());
    }

    // --- correctSize tests ---

    @Test
    public void testCorrectSize_ChangesEntrySet() {
        Map<String, String> fake = new TreeMap<>() {
            boolean firstCall = true;
            @Override
            public Set<Map.Entry<String, String>> entrySet() {
                if (!firstCall) {
                    put("NEW", "anyVal");
                }
                firstCall = false;
                return super.entrySet();
            }
        };
        fake.put("A", "1");
        fake.put("B", "2");
        RoamingMap<String, String> rm = inject(fake);

        try {
            Barricade.correctSize(rm);
            fail("Expected RuntimeException for changed entry set in size");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("size method of RoamingMap operated incorrectly"));
        }
    }

    @Test
    public void testCorrectSize_WrongNumericValue() {
        Map<String, String> fake = new TreeMap<>() {
            @Override
            public int size() {
                return 999; // bogus size
            }
        };
        fake.put("X", "val");
        RoamingMap<String, String> rm = inject(fake);
        int result = Barricade.correctSize(rm);
        assertEquals(1, result);
        assertTrue(logHandler.getLastLog().isPresent());
        assertTrue(logHandler.getLastLog().get().contains("size method of RoamingMap returned incorrect value"));
    }

    @Test
    public void testCorrectSize_HappyPath() {
        RoamingMap<String, String> rm = new RoamingMap<>();
        rm.put("A", "1");
        rm.put("B", "2");
        int result = Barricade.correctSize(rm);
        assertEquals(2, result);
        assertFalse(logHandler.getLastLog().isPresent());
    }

    // --- correctStringRepresentation tests ---

    @Test
    public void testCorrectStringRepresentation_ChangesEntrySet() {
        Map<String, String> fake = new TreeMap<>() {
            boolean firstCall = true;
            @Override
            public String toString() {
                if (!firstCall) {
                    remove("B");
                }
                firstCall = false;
                return super.toString();
            }
        };
        fake.put("A", "X");
        fake.put("B", "Y");
        RoamingMap<String, String> rm = inject(fake);
        try {
            Barricade.correctStringRepresentation(rm);
            fail("Expected RuntimeException for changed entry set in toString");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("toString method of RoamingMap operated incorrectly"));
        }
    }

    @Test
    public void testCorrectStringRepresentation_WrongString() {
        Map<String, String> fake = new TreeMap<>() {
            @Override
            public String toString() {
                return "WRONG";
            }
        };
        fake.put("K", "V");
        RoamingMap<String, String> rm = inject(fake);
        String result = Barricade.correctStringRepresentation(rm);
        assertTrue(result.contains("K=V"));
        assertTrue(logHandler.getLastLog().isPresent());
        assertTrue(logHandler.getLastLog().get().contains("toString method of RoamingMap returned incorrect value"));
    }

    @Test
    public void testCorrectStringRepresentation_HappyPath() {
        RoamingMap<Integer, String> rm = new RoamingMap<>();
        rm.put(10, "ten");
        String result = Barricade.correctStringRepresentation(rm);
        assertTrue(result.contains("10=ten"));
        assertFalse(logHandler.getLastLog().isPresent());
    }

    // --- putWithStateVar tests ---

    @Test
    public void testPutWithStateVar_StateChange() {
        Map<String, String> fake = new TreeMap<>() {
            boolean secondCall = false;
            @Override
            public Set<Map.Entry<String, String>> entrySet() {
                if (secondCall) {
                    remove("NEW");
                }
                secondCall = true;
                return super.entrySet();
            }
        };
        RoamingMap<String, String> rm = inject(fake);
        try {
            Barricade.putWithStateVar(rm, "NEW", "VALUE");
            fail("Expected RuntimeException for changed entry set in put");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("put method of RoamingMap operated incorrectly"));
        }
    }

    @Test
    public void testPutWithStateVar_FailsToInsert() {
        Map<String, String> fake = new TreeMap<>() {
            @Override
            public String put(String k, String v) {
                return null; // does not insert new key
            }
        };
        RoamingMap<String, String> rm = inject(fake);
        try {
            Barricade.putWithStateVar(rm, "A", "B");
            fail("Expected RuntimeException due to missing insertion");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("put method of RoamingMap operated incorrectly"));
        }
    }

    @Test
    public void testPutWithStateVar_HappyPath() {
        RoamingMap<String, String> rm = new RoamingMap<>();
        var result = Barricade.putWithStateVar(rm, "X", "Y");
        assertNull(result.value());  // no previous value
        assertFalse(logHandler.getLastLog().isPresent());
    }

    // --- Null argument tests ---

    @Test(expected = NullPointerException.class)
    public void testCorrectSize_NullMap() {
        Barricade.correctSize(null);
    }

    @Test(expected = NullPointerException.class)
    public void testCorrectStringRepresentation_NullMap() {
        Barricade.correctStringRepresentation(null);
    }

    @Test(expected = NullPointerException.class)
    public void testGetWithStateVar_NullMap() {
        Barricade.getWithStateVar(null, "X");
    }

    @Test(expected = NullPointerException.class)
    public void testGetWithStateVar_NullKey() {
        RoamingMap<String, String> rm = new RoamingMap<>();
        Barricade.getWithStateVar(rm, (String)null);
    }

    @Test(expected = NullPointerException.class)
    public void testPutWithStateVar_NullMap() {
        Barricade.putWithStateVar(null, "K", "V");
    }

    @Test(expected = NullPointerException.class)
    public void testPutWithStateVar_NullKey() {
        RoamingMap<String, String> rm = new RoamingMap<>();
        Barricade.putWithStateVar(rm, (String)null, "val");
    }

    @Test(expected = NullPointerException.class)
    public void testPutWithStateVar_NullValue() {
        RoamingMap<String, String> rm = new RoamingMap<>();
        Barricade.putWithStateVar(rm, "K", null);
    }

    @Test
    public void testBarricadeConstructor() {
        new Barricade();
    }

    // --- Test for "size method of RoamingMap operated incorrectly" ---
@Test
public void testCorrectSize_ChangedEntrySet() {
    Map<String, String> faultyMap = new TreeMap<>() {
        boolean firstCall = true;
        @Override
        public Set<Map.Entry<String, String>> entrySet() {
            if (!firstCall) {
                // Alter the map on the second call so that the two snapshots differ.
                put("FAULT", "trigger");
            }
            firstCall = false;
            return super.entrySet();
        }
    };
    faultyMap.put("A", "1");
    faultyMap.put("B", "2");
    RoamingMap<String, String> rm = inject(faultyMap);
    
    try {
        Barricade.correctSize(rm);
        fail("Expected RuntimeException for changed entry set in size()");
    } catch (RuntimeException e) {
        assertTrue(e.getMessage().contains("size method of RoamingMap operated incorrectly"));
    }
}

// --- Test for "size method of RoamingMap returned incorrect value; correct value was used instead" ---
@Test
public void testCorrectSize_WrongSizeValue() {
    Map<String, String> faultyMap = new TreeMap<>() {
        @Override
        public int size() {
            return 999; // Return an incorrect size
        }
    };
    faultyMap.put("A", "1");
    RoamingMap<String, String> rm = inject(faultyMap);
    
    int correctedSize = Barricade.correctSize(rm);
    // The correct snapshot indicates there is only 1 entry.
    assertEquals(1, correctedSize);
    // Use LoggerTestingHandler (or your own) to check that a warning was logged.
    assertTrue(logHandler.getLastLog().isPresent());
    assertTrue(logHandler.getLastLog().get().contains("size method of RoamingMap returned incorrect value"));
}

// --- Test for "toString method of RoamingMap operated incorrectly" ---
@Test
public void testCorrectStringRepresentation_ChangedEntrySet() {
    Map<String, String> faultyMap = new TreeMap<>() {
        boolean firstCall = true;
        @Override
        public String toString() {
            if (!firstCall) {
                // Change the map on the second call so that the entry set differs.
                remove("B");
            }
            firstCall = false;
            return super.toString();
        }
    };
    faultyMap.put("A", "X");
    faultyMap.put("B", "Y");
    RoamingMap<String, String> rm = inject(faultyMap);
    
    try {
        Barricade.correctStringRepresentation(rm);
        fail("Expected RuntimeException for changed entry set in toString()");
    } catch (RuntimeException e) {
        assertTrue(e.getMessage().contains("toString method of RoamingMap operated incorrectly"));
    }
}

}
