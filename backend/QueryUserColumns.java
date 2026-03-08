import java.sql.*;
public class QueryUserColumns {
  public static void main(String[] args) throws Exception {
    Class.forName("org.opengauss.Driver");
    try (Connection conn = DriverManager.getConnection("jdbc:opengauss://111.229.72.224:15432/chest_xray_db", "gaussdb", "Gauss@1234");
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("select column_name from information_schema.columns where table_schema='public' and table_name='sys_user' order by ordinal_position")) {
      while (rs.next()) {
        System.out.println(rs.getString(1));
      }
    }
  }
}
