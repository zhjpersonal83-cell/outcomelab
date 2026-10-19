package DatabaseService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.HexFormat;
import javax.naming.InitialContext;
import org.json.JSONObject;

/** Immutable model registry. Sandbox corrections and text are never written to MySQL. */
public final class BaselineRepository {
  private BaselineRepository() {}

  private static String setting(String key, String fallback) {
    try {
      return new InitialContext().lookup("java:comp/env/outcomelab/" + key).toString();
    } catch (javax.naming.NamingException ignored) {
    }
    return System.getProperty(
        "outcomelab." + key.toLowerCase().replace('_', '.'),
        System.getenv().getOrDefault("OUTCOMELAB_" + key, fallback));
  }

  public static boolean configured() {
    return setting("DB_URL", null) != null;
  }

  public static Connection open() throws Exception {
    Class.forName(setting("DB_DRIVER", "com.mysql.cj.jdbc.Driver"));
    return DriverManager.getConnection(
        setting("DB_URL", null), setting("DB_USER", null), setting("DB_PASSWORD", ""));
  }

  public static String packaged() throws Exception {
    try (var in = BaselineRepository.class.getResourceAsStream("/baseline.json")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  public static String digest(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  public static void initialize(Connection c, String payload) throws Exception {
    try (Statement s = c.createStatement()) {
      s.execute(
          "CREATE TABLE IF NOT EXISTS model_artifacts (model_id VARCHAR(80) PRIMARY KEY,payload MEDIUMTEXT NOT NULL,sha256 CHAR(64) NOT NULL)");
    }
    try (PreparedStatement s =
        c.prepareStatement(
            "INSERT INTO model_artifacts(model_id,payload,sha256) VALUES(?,?,?) ON DUPLICATE KEY UPDATE model_id=model_id")) {
      s.setString(1, "support-sgd-seed41-v1");
      s.setString(2, payload);
      s.setString(3, digest(payload));
      s.executeUpdate();
    }
    load(c, payload);
  }

  public static JSONObject load(Connection c, String expected) throws Exception {
    try (PreparedStatement s =
        c.prepareStatement("SELECT payload,sha256 FROM model_artifacts WHERE model_id=?")) {
      s.setString(1, "support-sgd-seed41-v1");
      try (ResultSet r = s.executeQuery()) {
        if (!r.next()) throw new IllegalStateException("Register the baseline before startup.");
        String payload = r.getString(1);
        if (!digest(payload).equals(r.getString(2)) || !digest(payload).equals(digest(expected)))
          throw new IllegalStateException(
              "Registered baseline does not match the verified release.");
        return new JSONObject(payload);
      }
    }
  }

  public static JSONObject load() {
    try {
      String expected = packaged();
      if (!configured()) return new JSONObject(expected);
      try (Connection c = open()) {
        return load(c, expected);
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Baseline model registry is unavailable.", ex);
    }
  }

  public static void main(String[] args) throws Exception {
    try (Connection c = open()) {
      initialize(c, packaged());
      System.out.println("Immutable baseline registered and checksum verified.");
    }
  }
}
