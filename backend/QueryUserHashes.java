import java.sql.*;
public class QueryUserHashes {
  public static void main(String[] args) throws Exception {
    Class.forName("org.opengauss.Driver");
    try (Connection conn = DriverManager.getConnection("jdbc:opengauss://111.229.72.224:15432/chest_xray_db", "gaussdb", "Gauss@1234");
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("select username, password_hash from public.sys_user order by user_id")) {
      while (rs.next()) {
        System.out.println(rs.getString(1)+"\t"+rs.getString(2));
      }
    }
  }
}
