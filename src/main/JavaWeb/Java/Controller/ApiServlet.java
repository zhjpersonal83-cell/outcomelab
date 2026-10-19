package Controller;

import Model.Sandboxes;
import java.util.*;
import javax.servlet.http.*;
import org.json.*;

public final class ApiServlet extends JsonServlet {
  private final Sandboxes sandboxes = new Sandboxes();
  private final Map<String, ArrayDeque<Long>> rates = new HashMap<>();

  private synchronized void rate(String peer) {
    long now = System.currentTimeMillis();
    rates.values().removeIf(q -> q.isEmpty() || q.peekLast() < now - 60000);
    if (!rates.containsKey(peer) && rates.size() >= 4096)
      throw new HttpError(429, "Capacity reached");
    var q = rates.computeIfAbsent(peer, k -> new ArrayDeque<>());
    while (!q.isEmpty() && q.peekFirst() < now - 60000) q.removeFirst();
    if (q.size() >= 180) throw new HttpError(429, "Rate limit reached");
    q.addLast(now);
  }

  @Override
  protected void handle(HttpServletRequest req, HttpServletResponse res) throws Exception {
    rate(req.getRemoteAddr());
    String path = req.getPathInfo() == null ? "" : req.getPathInfo(), method = req.getMethod();
    if (req.getContentLengthLong() > 8192) throw new HttpError(413, "Request too large");
    if (method.equals("GET") && path.equals("/health")) {
      json(
          res,
          new JSONObject()
              .put("status", "ok")
              .put("runtime", "Java 21 / Servlet 4")
              .put(
                  "database",
                  DatabaseService.BaselineRepository.configured()
                      ? "ready"
                      : "packaged-test-baseline"));
      return;
    }
    if (method.equals("GET") && path.equals("/ready")) {
      json(res, new JSONObject().put("status", "ready").put("features", 4096));
      return;
    }
    if (method.equals("POST") && path.equals("/sessions")) {
      if (req.getInputStream().readNBytes(8193).length > 8192)
        throw new HttpError(413, "Request too large");
      json(res, sandboxes.create());
      return;
    }
    String[] p = path.split("/");
    if (p.length < 3 || !p[1].equals("sessions")) throw new HttpError(404, "Endpoint not found.");
    String sid = p[2].equals("current") ? req.getHeader("X-OutcomeLab-Session") : p[2];
    if (sid == null) throw new HttpError(404, "Session expired. Start a new sandbox.");
    if (p.length == 3 && method.equals("DELETE")) {
      json(res, sandboxes.delete(sid));
      return;
    }
    if (p.length != 4) throw new HttpError(404, "Endpoint not found.");
    String action = p[3];
    if (method.equals("GET") && action.equals("export")) {
      String format = Optional.ofNullable(req.getParameter("format")).orElse("json");
      res.setHeader(
          "Content-Disposition",
          "attachment; filename=outcomelab-experiment." + (format.equals("csv") ? "csv" : "json"));
      if (format.equals("csv")) {
        res.setContentType("text/csv;charset=UTF-8");
        res.getWriter().write(sandboxes.csv(sid));
      } else if (format.equals("json")) json(res, sandboxes.export(sid));
      else throw new HttpError(422, "Use json or csv");
      return;
    }
    if (!method.equals("POST")) throw new HttpError(405, "Method not allowed.");
    JSONObject b = (action.equals("rollback")) ? new JSONObject() : body(req, 8192);
    Object result =
        switch (action) {
          case "predict" -> sandboxes.predict(sid, string(b, "text", 1, 1000));
          case "feedback" ->
              sandboxes.feedback(sid, string(b, "text", 1, 1000), string(b, "label", 1, 20));
          case "evaluate" -> {
            if (b.has("shift") && !(b.get("shift") instanceof Boolean))
              throw new HttpError(422, "Invalid shift.");
            yield sandboxes.evaluate(sid, b.optBoolean("shift", false));
          }
          case "rollback" -> sandboxes.rollback(sid);
          default -> throw new HttpError(404, "Endpoint not found.");
        };
    json(res, result);
  }
}
