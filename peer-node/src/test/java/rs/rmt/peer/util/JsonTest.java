package rs.rmt.peer.util;

import rs.rmt.peer.testutil.Assert;

import java.util.List;
import java.util.Map;

public class JsonTest {

    public void testRoundTripPrimitives() {
        Map<String, Object> obj = Json.obj("name", "test", "count", 42, "ok", true, "missing", null);
        String json = Json.stringify(obj);
        Map<String, Object> parsed = Json.parseObject(json);
        Assert.assertEquals("test", parsed.get("name"), "string round trip");
        Assert.assertEquals(42L, parsed.get("count"), "int round trip (parsed as Long)");
        Assert.assertEquals(true, parsed.get("ok"), "boolean round trip");
        Assert.assertTrue(parsed.containsKey("missing"), "null key preserved");
        Assert.assertNull(parsed.get("missing"), "null value round trip");
    }

    public void testEscaping() {
        String tricky = "line1\nline2\t\"quoted\"\\backslash";
        String json = Json.stringify(Json.obj("text", tricky));
        Map<String, Object> parsed = Json.parseObject(json);
        Assert.assertEquals(tricky, parsed.get("text"), "special characters must round-trip exactly");
    }

    @SuppressWarnings("unchecked")
    public void testNestedArraysAndObjects() {
        String json = "{\"files\":[{\"fileHash\":\"abc\",\"size\":10},{\"fileHash\":\"def\",\"size\":20}]}";
        Map<String, Object> parsed = Json.parseObject(json);
        List<Object> files = (List<Object>) parsed.get("files");
        Assert.assertEquals(2, files.size(), "array length");
        Map<String, Object> first = (Map<String, Object>) files.get(0);
        Assert.assertEquals("abc", first.get("fileHash"), "nested object field");
        Assert.assertEquals(10L, first.get("size"), "nested numeric field");
    }

    public void testEmptyArrayAndObject() {
        Assert.assertTrue(Json.parseArray("").isEmpty(), "blank string parses to empty array");
        Assert.assertTrue(Json.parseObject("").isEmpty(), "blank string parses to empty object");
        Assert.assertTrue(Json.parseArray("[]").isEmpty(), "empty array literal");
        Assert.assertTrue(Json.parseObject("{}").isEmpty(), "empty object literal");
    }

    public void testGetLongWithDefault() {
        Map<String, Object> obj = Json.parseObject("{\"a\":5}");
        Assert.assertEquals(5L, Json.getLong(obj, "a", 99), "present key uses actual value");
        Assert.assertEquals(99L, Json.getLong(obj, "b", 99), "missing key uses default");
    }

    public void testGetBooleanWithDefault() {
        Map<String, Object> obj = Json.parseObject("{\"flag\":true}");
        Assert.assertTrue(Json.getBoolean(obj, "flag", false), "present boolean used");
        Assert.assertFalse(Json.getBoolean(obj, "missing", false), "missing key uses default");
    }
}
