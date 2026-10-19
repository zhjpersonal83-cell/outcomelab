package Model;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Portable scikit-learn hashing + one-vs-rest logistic SGD; no Python runtime. */
public final class Classifier {
  public static final String[] LABELS = {"account", "billing", "delivery"};
  private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}_]{2,}");
  private final double[][] weights;
  private final double[] intercept;
  private double t;

  public Classifier(JSONObject baseline) {
    weights = new double[3][4096];
    intercept = new double[3];
    for (int c = 0; c < 3; c++) {
      intercept[c] = baseline.getJSONArray("intercepts").getDouble(c);
      for (int j = 0; j < 4096; j++)
        weights[c][j] = baseline.getJSONArray("coefficients").getJSONArray(c).getDouble(j);
    }
    t = baseline.getDouble("t");
  }

  private Classifier(Classifier other) {
    weights = Arrays.stream(other.weights).map(double[]::clone).toArray(double[][]::new);
    intercept = other.intercept.clone();
    t = other.t;
  }

  public Classifier copy() {
    return new Classifier(this);
  }

  public static int hash(String text) {
    byte[] b = text.getBytes(StandardCharsets.UTF_8);
    int h = 0, i = 0;
    for (; i + 4 <= b.length; i += 4) {
      int k = (b[i] & 255) | ((b[i + 1] & 255) << 8) | ((b[i + 2] & 255) << 16) | (b[i + 3] << 24);
      k *= 0xcc9e2d51;
      k = Integer.rotateLeft(k, 15);
      k *= 0x1b873593;
      h ^= k;
      h = Integer.rotateLeft(h, 13);
      h = h * 5 + 0xe6546b64;
    }
    int k = 0;
    for (int j = i; j < b.length; j++) k |= (b[j] & 255) << (8 * (j - i));
    if (i < b.length) {
      k *= 0xcc9e2d51;
      k = Integer.rotateLeft(k, 15);
      k *= 0x1b873593;
      h ^= k;
    }
    h ^= b.length;
    h ^= h >>> 16;
    h *= 0x85ebca6b;
    h ^= h >>> 13;
    h *= 0xc2b2ae35;
    h ^= h >>> 16;
    return h;
  }

  public static Map<Integer, Double> features(String text) {
    Map<Integer, Double> x = new TreeMap<>();
    Matcher m = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
    while (m.find()) {
      int h = hash(m.group());
      int index = (h == Integer.MIN_VALUE ? 0 : Math.abs(h)) % 4096;
      x.merge(index, 1.0, Double::sum);
    }
    double norm = Math.sqrt(x.values().stream().mapToDouble(v -> v * v).sum());
    if (norm > 0) x.replaceAll((k, v) -> v / norm);
    return x;
  }

  private static double sigmoid(double z) {
    return z >= 0 ? 1 / (1 + Math.exp(-z)) : Math.exp(z) / (1 + Math.exp(z));
  }

  private double score(int c, Map<Integer, Double> x) {
    double z = intercept[c];
    for (var e : x.entrySet()) z += weights[c][e.getKey()] * e.getValue();
    return z;
  }

  public double[] predict(String text) {
    var x = features(text);
    double[] p = new double[3];
    double sum = 0;
    for (int c = 0; c < 3; c++) {
      p[c] = sigmoid(score(c, x));
      sum += p[c];
    }
    for (int c = 0; c < 3; c++) p[c] /= sum;
    return p;
  }

  public void update(String text, String label) {
    int target = Arrays.asList(LABELS).indexOf(label);
    if (target < 0) throw new IllegalArgumentException("label");
    var x = features(text);
    double eta = 1 / (.001 * (Math.pow(.001, -.75) + t - 1));
    for (int c = 0; c < 3; c++) {
      double change = eta * ((c == target ? 1 : 0) - sigmoid(score(c, x)));
      for (int j = 0; j < 4096; j++) weights[c][j] *= Math.max(0, 1 - eta * .001);
      for (var e : x.entrySet()) weights[c][e.getKey()] += change * e.getValue();
      intercept[c] += change * .01;
    }
    t++;
  }

  public static int argmax(double[] p) {
    int i = 0;
    for (int j = 1; j < p.length; j++) if (p[j] > p[i]) i = j;
    return i;
  }

  public JSONObject metrics(JSONObject baseline, boolean shift) {
    JSONArray
        texts =
            shift
                ? baseline.getJSONArray("shifted")
                : baseline.getJSONObject("test").getJSONArray("texts"),
        labels = baseline.getJSONObject("test").getJSONArray("labels");
    int n = texts.length(), correct = 0;
    double loss = 0, brier = 0, ece = 0;
    int[][] matrix = new int[3][3];
    int[] count = new int[5];
    double[] conf = new double[5], acc = new double[5];
    for (int i = 0; i < n; i++) {
      double[] p = predict(texts.getString(i));
      int y = Arrays.asList(LABELS).indexOf(labels.getString(i)), pred = argmax(p);
      matrix[y][pred]++;
      boolean ok = y == pred;
      if (ok) correct++;
      loss -= Math.log(Math.max(Math.ulp(1.0), Math.min(1 - Math.ulp(1.0), p[y])));
      for (int c = 0; c < 3; c++) brier += Math.pow(p[c] - (c == y ? 1 : 0), 2);
      for (int j = 0; j < 5; j++)
        if (p[pred] >= j * .2 && p[pred] < j * .2 + .2 + 1e-12) {
          count[j]++;
          conf[j] += p[pred];
          if (ok) acc[j]++;
        }
    }
    JSONArray bins = new JSONArray();
    for (int j = 0; j < 5; j++) {
      if (count[j] > 0) ece += Math.abs(acc[j] - conf[j]) / n;
      bins.put(
          new JSONObject()
              .put("lower", j / 5.0)
              .put("count", count[j])
              .put("accuracy", count[j] > 0 ? acc[j] / count[j] : JSONObject.NULL)
              .put("confidence", count[j] > 0 ? conf[j] / count[j] : JSONObject.NULL));
    }
    return new JSONObject()
        .put("n", n)
        .put("accuracy", (double) correct / n)
        .put("log_loss", loss / n)
        .put("brier", brier / n)
        .put("ece", ece)
        .put("matrix", new JSONArray(matrix))
        .put("calibration", bins)
        .put("labels", new JSONArray(LABELS));
  }
}
