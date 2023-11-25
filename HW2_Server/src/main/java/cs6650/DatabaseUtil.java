package cs6650;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseUtil {
    private static HikariConfig config = new HikariConfig();
    private static HikariDataSource ds;

    static {
        try {
            // Explicitly loading the MySQL JDBC driver class
            Class.forName("com.mysql.cj.jdbc.Driver");

            config.setJdbcUrl("jdbc:mysql://database-3.cdydja2nksqm.us-west-2.rds.amazonaws.com:3306/albumstore?useSSL=false");
            config.setUsername("admin");
            config.setPassword("goodluck123!");
            config.setMaximumPoolSize(29); // Set your desired pool size
            ds = new HikariDataSource(config);

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load MySQL JDBC driver", e);
        }
    }

    private  DatabaseUtil() {}
    public static Connection connectionToDb() throws SQLException, ClassNotFoundException{
        return ds.getConnection();
    }
}
