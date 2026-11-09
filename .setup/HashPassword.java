import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class HashPassword {
    public static void main(String[] args) throws Exception {
        String value=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8)).readLine();
        if(value==null || value.isEmpty())throw new IllegalArgumentException("Password input required");
        System.out.print(new BCryptPasswordEncoder(12).encode(value));
    }
}
