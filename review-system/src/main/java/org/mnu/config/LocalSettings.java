package org.mnu.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/**
 * @brief 선택한 UTF-8 설정 파일을 읽고 환경변수 우선으로 필수 DB 설정을 제공합니다.
 */
public final class LocalSettings {
    /**
     * @brief 외부 파일에서 읽은 설정값을 보관합니다.
     */
    private final Properties values = new Properties();
    /**
     * @brief review.config, REVIEW_CONFIG, 사용자 기본 경로 순으로 설정 파일 위치를 선택하여 존재하는 파일을 읽습니다.
     * @throws IllegalStateException 존재하는 설정 파일을 읽는 중 I/O 오류가 발생한 경우
     */
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
    /**
     * @brief 환경변수 우선으로 필수 설정을 찾고 비어 있는 값을 거부합니다.
     * @param property 외부 Properties 파일의 설정 키
     * @param environment 우선 조회할 환경변수 이름
     * @return 공백 여부를 확인한 원래 설정 문자열
     * @throws IllegalStateException 환경변수와 설정 파일 모두에 유효한 값이 없는 경우
     */
    public String required(String property, String environment) {
        String value = System.getenv(environment);
        if (value == null || value.trim().isEmpty()) value = values.getProperty(property);
        if (value == null || value.trim().isEmpty()) throw new IllegalStateException("DB 연결 설정이 필요합니다. REVIEW_CONFIG 또는 DB 환경변수를 확인하세요.");
        return value;
    }
}
