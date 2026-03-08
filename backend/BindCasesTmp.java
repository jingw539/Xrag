import java.sql.*;
public class BindCasesTmp {
  public static void main(String[] args) throws Exception {
    Class.forName("org.postgresql.Driver");
    try (Connection conn = DriverManager.getConnection("jdbc:postgresql://111.229.72.224:15432/chest_xray_db", "gaussdb", "Gauss@1234")) {
      conn.setAutoCommit(true);
      try (PreparedStatement ps = conn.prepareStatement("UPDATE case_info SET responsible_doctor_id = ? WHERE case_id IN (?, ?) AND responsible_doctor_id IS NULL")) {
        ps.setLong(1, 2030115542780289025L);
        ps.setLong(2, 2001L);
        ps.setLong(3, 2002L);
        System.out.println("updated=" + ps.executeUpdate());
      }
      try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT case_id, exam_no, responsible_doctor_id FROM case_info WHERE case_id IN (2001,2002,2003) ORDER BY case_id")) {
        while (rs.next()) {
          System.out.println(rs.getLong(1) + "," + rs.getString(2) + "," + rs.getString(3));
        }
      }
    }
  }
}
