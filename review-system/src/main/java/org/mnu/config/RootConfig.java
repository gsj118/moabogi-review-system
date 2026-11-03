package org.mnu.config;

import java.time.Clock;
import java.time.ZoneId;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@ComponentScan(basePackages = {"org.mnu.service"})
@MapperScan("org.mnu.mapper")
@EnableTransactionManagement(proxyTargetClass = true)
@Import(SecurityConfig.class)
public class RootConfig {
    @Bean public Clock clock() { return Clock.system(ZoneId.of("Asia/Seoul")); }
    @Bean(destroyMethod = "close") public HikariDataSource dataSource() {
        LocalSettings settings = new LocalSettings();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(settings.required("db.url", "DB_URL"));
        config.setUsername(settings.required("db.user", "DB_USER"));
        config.setPassword(settings.required("db.password", "DB_PASSWORD"));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(2000);
        config.setConnectionInitSql("SET time_zone = '+09:00'");
        config.setPoolName("ReviewPool");
        return new HikariDataSource(config);
    }
    @Bean public SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        config.setMapUnderscoreToCamelCase(true);
        config.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        config.setDefaultStatementTimeout(10);
        factory.setConfiguration(config);
        return factory.getObject();
    }
    @Bean public PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
}
