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

/**
 * @brief 서비스·Mapper 탐색과 DB·트랜잭션·시간 의존성을 구성하는 루트 Spring 설정입니다.
 */
@Configuration
@ComponentScan(basePackages = {"org.mnu.service"})
@MapperScan("org.mnu.mapper")
@EnableTransactionManagement(proxyTargetClass = true)
@Import(SecurityConfig.class)
public class RootConfig {
    /**
     * @brief 감상일 계산에 사용하는 Asia/Seoul 시스템 시계를 제공합니다.
     * @return 서울 시간대를 사용하는 Clock
     */
    @Bean public Clock clock() { return Clock.system(ZoneId.of("Asia/Seoul")); }
    /**
     * @brief 외부 DB 설정으로 최대 10개 연결과 2초 연결 대기를 사용하는 Hikari 풀을 생성합니다.
     * @return 세션 시간대를 +09:00으로 설정하는 DataSource
     * @throws IllegalStateException 외부 DB 필수 설정을 읽거나 확보할 수 없는 경우
     */
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
    /**
     * @brief Mapper XML·밑줄 이름 변환·10초 SQL 제한을 적용한 MyBatis 팩토리를 구성합니다.
     * @param source 애플리케이션 DB 연결 풀
     * @return Mapper 실행에 사용하는 SqlSessionFactory
     * @throws Exception Mapper 리소스 로딩 또는 팩토리 초기화에 실패한 경우
     */
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
    /**
     * @brief 동일 DataSource의 서비스 트랜잭션을 관리합니다.
     * @param source 트랜잭션을 적용할 DB 연결 풀
     * @return JDBC 기반 DataSourceTransactionManager
     */
    @Bean public PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
}
