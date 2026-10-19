package Model;

import Controller.HttpError;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import org.json.*;

public final class Sandboxes {
  public final JSONObject baseline;
  public final Classifier base;
  private final Map<String, Session> sessions = new LinkedHashMap<>();
  private final int capacity;
  private final Set<String> reserved = new HashSet<>();
  private static final SecureRandom RANDOM = new SecureRandom();

  private static final class Session {
    Classifier model;
    List<Classifier> versions = new ArrayList<>();
    JSONArray history = new JSONArray();
    long expires;

    Session(Classifier base) {
      model = base.copy();
      versions.add(model.copy());
      touch();
    }

    void touch() {
      expires = System.nanoTime() + 1800_000_000_000L;
    }
  }

  public Sandboxes() {
    this(Integer.parseInt(System.getenv().getOrDefault("MAX_SESSIONS", "128")));
  }

  public Sandboxes(int capacity) {
    this.capacity = Math.max(1, Math.min(128, capacity));
    try (var in = getClass().getResourceAsStream("/baseline.json")) {
      baseline = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
    base = new Classifier(baseline);
    for (String split : List.of("validation", "test"))
      for (Object text : baseline.getJSONObject(split).getJSONArray("texts"))
        reserved.add(Text.fold(text.toString()));
    for (Object text : baseline.getJSONArray("shifted")) reserved.add(Text.fold(text.toString()));
  }

  private Session get(String id) {
    Session s = sessions.get(id);
    if (s == null || s.expires < System.nanoTime()) {
      sessions.remove(id);
      throw new HttpError(404, "Session expired. Start a new sandbox.");
    }
    s.touch();
    return s;
  }

  public synchronized JSONObject create() {
    sessions.values().removeIf(s -> s.expires < System.nanoTime());
    if (sessions.size() >= capacity)
      throw new HttpError(503, "Sandbox capacity reached. Try later.");
    byte[] b = new byte[32];
    RANDOM.nextBytes(b);
    String id = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    sessions.put(id, new Session(base));
    return new JSONObject().put("session", id).put("seed", 41).put("labels", Classifier.LABELS);
  }

  public synchronized JSONObject predict(String id, String text) {
    Session s = get(id);
    double[] p = s.model.predict(text);
    JSONObject probabilities = new JSONObject();
    for (int c = 0; c < 3; c++) probabilities.put(Classifier.LABELS[c], p[c]);
    return new JSONObject()
        .put("label", Classifier.LABELS[Classifier.argmax(p)])
        .put("probabilities", probabilities)
        .put("version", s.history.length());
  }

  public synchronized JSONObject feedback(String id, String text, String label) {
    Session s = get(id);
    if (s.history.length() >= 20)
      throw new HttpError(409, "20 corrections reached. Reset to continue.");
    if (!Arrays.asList(Classifier.LABELS).contains(label))
      throw new HttpError(422, "Invalid label.");
    if (reserved.contains(
        Text.fold(text.replaceAll("(?U)^[\\s\\u001c-\\u001f]+|[\\s\\u001c-\\u001f]+$", ""))))
      throw new HttpError(422, "Reserved evaluation example. Write your own example.");
    s.model.update(text, label);
    s.history.put(new JSONObject().put("text", text).put("label", label));
    s.versions.add(s.model.copy());
    return new JSONObject().put("version", s.history.length());
  }

  public synchronized JSONObject evaluate(String id, boolean shift) {
    Session s = get(id);
    return new JSONObject()
        .put("baseline", base.metrics(baseline, shift))
        .put("current", s.model.metrics(baseline, shift))
        .put("shift", shift)
        .put("version", s.history.length());
  }

  public synchronized JSONObject rollback(String id) {
    Session s = get(id);
    if (!s.history.isEmpty()) {
      s.history.remove(s.history.length() - 1);
      s.versions.remove(s.versions.size() - 1);
      s.model = s.versions.get(s.versions.size() - 1).copy();
    }
    return new JSONObject().put("version", s.history.length());
  }

  public synchronized JSONObject delete(String id) {
    sessions.remove(id);
    return new JSONObject().put("deleted", true);
  }

  public synchronized JSONObject export(String id) {
    Session s = get(id);
    return new JSONObject()
        .put("seed", 41)
        .put(
            "model",
            "SGDClassifier log_loss / HashingVectorizer 4096 (Java portable implementation)")
        .put("version", s.history.length())
        .put("corrections", new JSONArray(s.history.toString()))
        .put("clean", s.model.metrics(baseline, false))
        .put("shifted", s.model.metrics(baseline, true));
  }

  public synchronized String csv(String id) {
    StringBuilder out = new StringBuilder("text,label\r\n");
    for (Object o : get(id).history) {
      JSONObject row = (JSONObject) o;
      String text = row.getString("text");
      if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) text = "'" + text;
      out.append('"')
          .append(text.replace("\"", "\"\""))
          .append("\",")
          .append(row.getString("label"))
          .append("\r\n");
    }
    return out.toString();
  }
}
