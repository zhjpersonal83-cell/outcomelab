import static org.junit.Assert.*;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import org.apache.catalina.startup.Tomcat;
import org.json.*;
import org.junit.*;

public class ServletIntegrationTest {
  static Tomcat tomcat;
  static String base;
  static HttpClient client;

  @BeforeClass
  public static void start() throws Exception {
    System.setProperty("marginloom.db.driver", "org.h2.Driver");
    System.setProperty(
        "marginloom.db.url", "jdbc:h2:mem:api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    System.setProperty("marginloom.db.user", "sa");
    System.setProperty("marginloom.schema.auto.init", "true");
    tomcat = new Tomcat();
    tomcat.setBaseDir(Files.createTempDirectory("test-tomcat").toString());
    tomcat.setPort(0);
    tomcat.getConnector().setProperty("address", "127.0.0.1");
    var context =
        tomcat.addWebapp("/test", Path.of("src/main/JavaWeb/WebAPP").toAbsolutePath().toString());
    context.setParentClassLoader(ServletIntegrationTest.class.getClassLoader());
    tomcat.start();
    base = "http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/test/";
    client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
  }

  @AfterClass
  public static void stop() throws Exception {
    tomcat.stop();
    tomcat.destroy();
  }

  static HttpResponse<String> call(HttpClient c, String path, String body, boolean csrf)
      throws Exception {
    var b = HttpRequest.newBuilder(URI.create(base + path));
    if (body != null) {
      b.POST(HttpRequest.BodyPublishers.ofString(body)).header("Content-Type", "application/json");
      if (csrf) b.header("X-Marginloom-Client", "1");
    }
    return c.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  public void staticAndHttpContract() throws Exception {
    assertEquals(200, call(client, "index.html", null, false).statusCode());
    assertEquals(200, call(client, "api/health", null, false).statusCode());
    assertEquals(200, call(client, "assets/js/app.js", null, false).statusCode());
    String sid =
        new JSONObject(call(client, "api/sessions", "", false).body()).getString("session");
    String root = "api/sessions/" + sid;
    var prediction =
        call(client, root + "/predict", "{\"text\":\"The box vanished again\"}", false);
    assertEquals(200, prediction.statusCode());
    JSONObject before = new JSONObject(prediction.body());
    assertEquals(
        200,
        call(
                client,
                root + "/feedback",
                "{\"text\":\"The box vanished again\",\"label\":\"delivery\"}",
                false)
            .statusCode());
    assertEquals(
        1, new JSONObject(call(client, root + "/evaluate", "{}", false).body()).getInt("version"));
    assertEquals(200, call(client, root + "/rollback", "", false).statusCode());
    assertTrue(
        before.similar(
            new JSONObject(
                call(client, root + "/predict", "{\"text\":\"The box vanished again\"}", false)
                    .body())));
    assertEquals(413, call(client, "api/sessions", "x".repeat(8193), false).statusCode());
    assertEquals(422, call(client, root + "/predict", "{\"text\":123}", false).statusCode());
  }
}
