package com.vn.jmixcamel.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Đăng ký DataSource phụ cho database "Test" (chạy cùng PG server localhost:5432, schema khác).
 * DynamicDbQueryProcessor sẽ chọn JdbcTemplate theo tên `dbQuery.datasource` (mặc định = "main").
 */
@Configuration
public class ExternalDataSourceConfig {

    @Bean(name = "testDataSource")
    public DataSource testDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:postgresql://localhost:5432/Test");
        ds.setUsername("postgres");
        ds.setPassword("123");
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(3);
        ds.setPoolName("test-pool");
        ds.setReadOnly(true);
        return ds;
    }

    @Bean(name = "testJdbcTemplate")
    public JdbcTemplate testJdbcTemplate(@Qualifier("testDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
