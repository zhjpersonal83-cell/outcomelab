package Model;

import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Python-compatible Unicode case folding for reserved evaluation-example checks. */
public final class Text {
  private static final JSONObject FOLD;

  static {
    try (var in = Text.class.getResourceAsStream("/casefold.json")) {
      FOLD = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  private Text() {}

  public static String fold(String value) {
    StringBuilder out = new StringBuilder();
    value
        .codePoints()
        .forEach(
            cp -> {
              String c = new String(Character.toChars(cp));
              out.append(FOLD.optString(c, c));
            });
    return out.toString();
  }
}
