import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class ApplyCaseDoctorMigration {
  public static void main(String[] args) throws Exception {
    Class.forName("org.postgresql.Driver");
    try (Connection conn = DriverManager.getConnection("jdbc:postgresql://111.229.72.224:15432/chest_xray_db", "gaussdb", "Gauss@1234");
         Statement stmt = conn.createStatement()) {
      stmt.executeUpdate("ALTER TABLE case_info ADD COLUMN responsible_doctor_id BIGINT");
      stmt.executeUpdate("CREATE INDEX idx_case_info_responsible_doctor ON case_info(responsible_doctor_id)");
      System.out.println("ok");
    }
  }
}
