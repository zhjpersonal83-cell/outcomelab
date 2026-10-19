import static org.junit.Assert.*;

import Model.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
import org.junit.*;

public class ClassifierTest {
  static JSONArray fixtures;
  static Sandboxes s;

  @BeforeClass
  public static void load() throws Exception {
    try (var in = ClassifierTest.class.getResourceAsStream("/parity.json")) {
      fixtures = new JSONArray(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
    s = new Sandboxes();
  }

  static void compare(Object expected, Object actual, String path, double tolerance) {
    if (expected instanceof Number a && actual instanceof Number b) {
      assertEquals(path, a.doubleValue(), b.doubleValue(), tolerance);
    } else if (expected instanceof JSONObject a) {
      assertTrue(path, actual instanceof JSONObject);
      JSONObject b = (JSONObject) actual;
      for (String k : a.keySet()) compare(a.get(k), b.get(k), path + "." + k, tolerance);
    } else if (expected instanceof JSONArray a) {
      assertTrue(path, actual instanceof JSONArray);
      JSONArray b = (JSONArray) actual;
      assertEquals(path, a.length(), b.length());
      for (int i = 0; i < a.length(); i++)
        compare(a.get(i), b.get(i), path + "[" + i + "]", tolerance);
    } else assertEquals(path, expected, actual);
  }

  @Test
  public void pythonParityAcrossUpdatesAndUnicode() {
    Classifier m = s.base.copy();
    for (Object o : fixtures) {
      JSONObject f = (JSONObject) o;
      for (Object x : f.getJSONArray("predictions")) {
        JSONObject p = (JSONObject) x;
        double[] actual = m.predict(p.getString("text"));
        compare(
            p.getJSONArray("probabilities"),
            new JSONArray(actual),
            "predict v" + f.getInt("version"),
            1e-12);
        var feats = Classifier.features(p.getString("text"));
        assertEquals(p.getJSONObject("features").length(), feats.size());
        for (String key : p.getJSONObject("features").keySet())
          assertEquals(
              p.getJSONObject("features").getDouble(key), feats.get(Integer.parseInt(key)), 1e-15);
      }
      compare(f.getJSONObject("clean"), m.metrics(s.baseline, false), "clean", 1e-12);
      compare(f.getJSONObject("shifted"), m.metrics(s.baseline, true), "shifted", 1e-12);
      m.update("The box vanished again", "delivery");
    }
  }

  @Test
  public void isolationRollbackAndReservedSamples() {
    Sandboxes store = new Sandboxes(2);
    String a = store.create().getString("session"), b = store.create().getString("session");
    JSONObject baseline = store.evaluate(a, false);
    store.feedback(a, "The box vanished again", "delivery");
    compare(baseline, store.evaluate(b, false), "isolated", 0);
    assertFalse(store.evaluate(a, false).similar(baseline));
    store.rollback(a);
    compare(baseline, store.evaluate(a, false), "rollback", 0);
    try {
      store.feedback(
          a, s.baseline.getJSONObject("test").getJSONArray("texts").getString(0), "billing");
      fail();
    } catch (Controller.HttpError ex) {
      assertEquals(422, ex.status);
    }
    try {
      store.create();
      fail();
    } catch (Controller.HttpError ex) {
      assertEquals(503, ex.status);
    }
  }

  @Test
  public void concurrencyCapAndCsv() throws Exception {
    Sandboxes store = new Sandboxes();
    String a = store.create().getString("session");
    var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
    try {
      var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Integer>>();
      for (int i = 0; i < 24; i++)
        tasks.add(
            () -> {
              try {
                return store.feedback(a, "=SUM(1,2)", "delivery").getInt("version") > 0 ? 200 : 0;
              } catch (Controller.HttpError ex) {
                return ex.status;
              }
            });
      int ok = 0, cap = 0;
      for (var result : pool.invokeAll(tasks)) {
        if (result.get() == 200) ok++;
        if (result.get() == 409) cap++;
      }
      assertEquals(20, ok);
      assertEquals(4, cap);
      assertTrue(store.csv(a).contains("'=SUM"));
    } finally {
      pool.shutdownNow();
    }
  }
}
