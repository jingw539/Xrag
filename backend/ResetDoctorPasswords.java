import java.sql.*;
public class ResetDoctorPasswords {
  public static void main(String[] args) throws Exception {
    Class.forName("org.opengauss.Driver");
    try (Connection conn = DriverManager.getConnection("jdbc:opengauss://111.229.72.224:15432/chest_xray_db", "gaussdb", "Gauss@1234");
         Statement stmt = conn.createStatement()) {
      String hash = "$2b$10$8AKFiVrkmrm4unuktdTPJ.zJ9T4zNJKidmi9tErPqe3dpqAK3BElq";
      stmt.executeUpdate("update public.sys_user set password_hash='" + hash + "' where username in ('Dr_zhang','Dr_zhao')");
      System.out.println("ok");
    }
  }
}
