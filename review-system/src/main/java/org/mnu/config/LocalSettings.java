package org.mnu.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

public final class LocalSettings {
    private final Properties values = new Properties();
    public LocalSettings() {
        String path = System.getProperty("review.config");
        if (path == null || path.trim().isEmpty()) path = System.getenv("REVIEW_CONFIG");
        if (path == null || path.trim().isEmpty()) path = System.getProperty("user.home") + "/dev/review-system/config/application.properties";
        Path file = Paths.get(path);
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
            catch (IOException e) { throw new IllegalStateException("외부 DB 설정 파일을 읽을 수 없습니다."); }
        }
    }
    public String required(String property, String environment) {
        String value = System.getenv(environment);
        if (value == null || value.trim().isEmpty()) value = values.getProperty(property);
        if (value == null || value.trim().isEmpty()) throw new IllegalStateException("DB 연결 설정이 필요합니다. REVIEW_CONFIG 또는 DB 환경변수를 확인하세요.");
        return value;
    }
}
