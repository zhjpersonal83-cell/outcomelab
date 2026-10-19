import static org.junit.Assert.*;

import DatabaseService.BaselineRepository;
import java.sql.*;
import org.junit.Test;

public class BaselineRepositoryTest {
  @Test
  public void immutableRegistryChecksBothStoredAndReleaseDigests() throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:h2:mem:registry;MODE=MySQL", "sa", "")) {
      String payload = BaselineRepository.packaged();
      BaselineRepository.initialize(c, payload);
      assertEquals(4096, BaselineRepository.load(c, payload).getInt("features"));
      BaselineRepository.initialize(c, payload);
      try (Statement s = c.createStatement()) {
        s.executeUpdate("UPDATE model_artifacts SET payload='{}'");
      }
      try {
        BaselineRepository.load(c, payload);
        fail();
      } catch (IllegalStateException expected) {
        assertTrue(expected.getMessage().contains("baseline"));
      }
    }
  }
}
