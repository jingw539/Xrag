import java.sql.*;
public class QueryTables {
  public static void main(String[] args) throws Exception {
    Class.forName("org.opengauss.Driver");
    try (Connection conn = DriverManager.getConnection("jdbc:opengauss://111.229.72.224:15432/chest_xray_db", "gaussdb", "Gauss@1234");
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("select table_schema, table_name from information_schema.tables where table_schema not in ('pg_catalog','information_schema') order by table_schema, table_name")) {
      while (rs.next()) {
        System.out.println(rs.getString(1)+"\t"+rs.getString(2));
      }
    }
  }
}
