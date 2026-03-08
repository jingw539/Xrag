import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
public class GenerateJwt {
  public static void main(String[] args) {
    String username = args[0];
    long userId = Long.parseLong(args[1]);
    String roleCode = args[2];
    String secret = "chest-xray-jwt-secret-key-production-2024-change-this";
    long expiration = 7200L;
    SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    Map<String, Object> claims = new HashMap<>();
    claims.put("userId", userId);
    claims.put("roleCode", roleCode);
    String token = Jwts.builder()
      .claims(claims)
      .subject(username)
      .issuedAt(new Date())
      .expiration(new Date(System.currentTimeMillis() + expiration * 1000))
      .signWith(key)
      .compact();
    System.out.println(token);
  }
}
