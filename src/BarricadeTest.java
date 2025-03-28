import org.junit.*;
import static org.junit.Assert.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;

public class BarricadeTest {

    private static class TestHandler extends Handler {
        public List<LogRecord> records = new ArrayList<>();

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

    @Before
    public void setupLogger() {
        logger = Logger.getLogger(Barricade.class.getName());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        for (Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
        }
        handler = new TestHandler();
        logger.addHandler(handler);
    }

    @After
    public void teardownLogger() {
        logger.removeHandler(handler);
    }

    // Utility: inject custom Map into final RoamingMap using reflection
    private <K extends Comparable<K>, V> RoamingMap<K, V> makeCustomRoamingMap(Map<K, V> mapImpl) {
        RoamingMap<K, V> rm = new RoamingMap<>();
        try {
            Field f = RoamingMap.class.getDeclaredField("map");
            f.setAccessible(true);
            f.set(rm, mapImpl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject map", e);
        }
        return rm;
    }

    @Test
    public void testGetWithStateVar_IncorrectValueLogged() {
        Map<Integer, String> badMap = new TreeMap<>() {
            @Override
            public String get(Object key) {
                return "WRONG";
            }
        };
        badMap.put(1, "RIGHT");
        RoamingMap<Integer, String> map = makeCustomRoamingMap(badMap);

        var result = Barricade.getWithStateVar(map, 1);
        assertEquals("RIGHT", result.value());
        assertFalse(handler.records.isEmpty());
        assertTrue(handler.records.get(0).getMessage().contains("returned incorrect value"));
    }

    @Test
    public void testCorrectSize_WrongSizeLogged() {
        Map<String, String> badMap = new TreeMap<>() {
            @Override
            public int size() {
                return 999;
            }
        };
        badMap.put("a", "1");
        badMap.put("b", "2");
        RoamingMap<String, String> map = makeCustomRoamingMap(badMap);

        int size = Barricade.correctSize(map);
        assertEquals(2, size);
        assertFalse(handler.records.isEmpty());
        assertTrue(handler.records.get(0).getMessage().contains("size method of RoamingMap returned incorrect value"));
    }

    @Test
    public void testPutWithStateVar_MisbehavingPutThrows() {
        Map<Integer, String> badMap = new TreeMap<>() {
            @Override
            public String put(Integer key, String value) {
                return null; // doesn't actually put anything
            }
        };
        RoamingMap<Integer, String> map = makeCustomRoamingMap(badMap);
        try {
            Barricade.putWithStateVar(map, 5, "five");
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("put method"));
        }
    }

    @Test
    public void testCorrectStringRepresentation_WrongStringLogged() {
        Map<String, String> badMap = new TreeMap<>() {
            @Override
            public String toString() {
                return "BROKEN";
            }
        };
        badMap.put("x", "y");
        RoamingMap<String, String> map = makeCustomRoamingMap(badMap);

        String s = Barricade.correctStringRepresentation(map);
        assertTrue(s.contains("x"));
        assertFalse(s.contains("BROKEN"));
        assertFalse(handler.records.isEmpty());
        assertTrue(handler.records.get(0).getMessage().contains("toString method"));
    }

    // Basic sanity check (correct use)
    @Test
    public void testGetPutSizeCorrect() {
        RoamingMap<String, Integer> map = new RoamingMap<>();
        Barricade.putWithStateVar(map, "A", 100);
        var result = Barricade.getWithStateVar(map, "A");
        assertEquals(Integer.valueOf(100), result.value());
        assertEquals(1, Barricade.correctSize(map));
    }

    @Test
    public void testKeySetAndEntrySet() {
        RoamingMap<String, String> map = new RoamingMap<>();
        map.put("A", "1");
        map.put("B", "2");
        Set<String> keys = Barricade.correctKeySet(map);
        Set<Map.Entry<String, String>> entries = Barricade.correctEntrySet(map);
        assertEquals(Set.of("A", "B"), keys);
        assertEquals(2, entries.size());
    }

    @Test(expected = RuntimeException.class)
    public void testGetWithStateVar_StateChangingGetThrows() {
        Map<String, String> badMap = new TreeMap<>() {
            @Override
            public String get(Object key) {
                this.clear(); // mutates map
                return "oops";
            }
        };
        badMap.put("k", "v");
        RoamingMap<String, String> map = makeCustomRoamingMap(badMap);
        Barricade.getWithStateVar(map, "k"); // should throw
    }

    @Test(expected = RuntimeException.class)
    public void testCorrectSize_StateChangingSizeThrows() {
        Map<String, String> badMap = new TreeMap<>() {
            @Override
            public int size() {
                this.remove("x");
                return super.size();
            }
        };
        badMap.put("x", "1");
        RoamingMap<String, String> map = makeCustomRoamingMap(badMap);
        Barricade.correctSize(map); // should throw
    }

    @Test(expected = RuntimeException.class)
    public void testCorrectStringRepresentation_StateChange() {
        Map<String, String> badMap = new TreeMap<>() {
            @Override
            public String toString() {
                this.clear(); // mutates
                return "oops";
            }
        };
        badMap.put("x", "y");
        RoamingMap<String, String> map = makeCustomRoamingMap(badMap);
        Barricade.correctStringRepresentation(map); // should throw
    }

    @Test
    public void testConstructor() {
        new Barricade(); // to cover default constructor
    }
}
