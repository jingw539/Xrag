import java.sql.*;
public class QueryDoctorRole {
  public static void main(String[] args) throws Exception {
    Class.forName("org.opengauss.Driver");
    try (Connection conn = DriverManager.getConnection("jdbc:opengauss://111.229.72.224:15432/chest_xray_db", "gaussdb", "Gauss@1234");
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("select u.user_id, u.username, u.real_name, r.role_code from public.sys_user u left join public.sys_role r on u.role_id=r.role_id order by u.user_id")) {
      while (rs.next()) {
        System.out.println(rs.getString(1)+"\t"+rs.getString(2)+"\t"+rs.getString(3)+"\t"+rs.getString(4));
      }
    }
  }
}
