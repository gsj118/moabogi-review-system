-- MySQL 8.4. Run against the selected existing database after schema.sql.
-- Check each column separately so an interrupted upgrade can be retried.
SET NAMES utf8mb4;
SET @add_poster = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'work' AND column_name = 'poster_path'),
    'DO 0', 'ALTER TABLE work ADD COLUMN poster_path VARCHAR(255) NULL');
PREPARE upgrade_step FROM @add_poster;
EXECUTE upgrade_step;
DEALLOCATE PREPARE upgrade_step;

SET @add_fetched = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'work' AND column_name = 'external_fetched_at'),
    'DO 0', 'ALTER TABLE work ADD COLUMN external_fetched_at DATETIME(6) NULL');
PREPARE upgrade_step FROM @add_fetched;
EXECUTE upgrade_step;
DEALLOCATE PREPARE upgrade_step;

CREATE TABLE IF NOT EXISTS user_hidden_genre (
    user_id BIGINT NOT NULL,
    genre_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, genre_id),
    CONSTRAINT fk_hidden_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_hidden_genre FOREIGN KEY (genre_id) REFERENCES genre(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS external_genre_map (
    source VARCHAR(20) NOT NULL,
    media_type VARCHAR(10) NOT NULL,
    external_genre_id BIGINT NOT NULL,
    genre_id BIGINT NOT NULL,
    PRIMARY KEY (source, media_type, external_genre_id),
    CONSTRAINT ck_external_map_type CHECK (media_type IN ('MOVIE', 'TV')),
    CONSTRAINT fk_external_map_genre FOREIGN KEY (genre_id) REFERENCES genre(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

START TRANSACTION;
INSERT INTO genre (name)
SELECT '미분류' WHERE NOT EXISTS (SELECT 1 FROM genre WHERE name = '미분류');

-- Map only explicit equivalents in the local catalogue. Preserve customized mappings.
INSERT INTO external_genre_map (source, media_type, external_genre_id, genre_id)
SELECT 'TMDB', m.media_type, m.external_id, g.id
FROM (
    SELECT 'MOVIE' AS media_type, 18 AS external_id, '드라마' AS local_name
    UNION ALL SELECT 'MOVIE', 878, 'SF'
    UNION ALL SELECT 'MOVIE', 35, '코미디'
    UNION ALL SELECT 'MOVIE', 28, '액션'
    UNION ALL SELECT 'MOVIE', 53, '스릴러'
    UNION ALL SELECT 'MOVIE', 10749, '로맨스'
    UNION ALL SELECT 'MOVIE', 99, '다큐멘터리'
    UNION ALL SELECT 'MOVIE', 16, '애니메이션'
    UNION ALL SELECT 'TV', 18, '드라마'
    UNION ALL SELECT 'TV', 35, '코미디'
    UNION ALL SELECT 'TV', 99, '다큐멘터리'
    UNION ALL SELECT 'TV', 16, '애니메이션'
) AS m
JOIN genre g ON g.name = m.local_name
LEFT JOIN external_genre_map current_map
    ON current_map.source = 'TMDB' AND current_map.media_type = m.media_type
    AND current_map.external_genre_id = m.external_id
WHERE current_map.genre_id IS NULL;
COMMIT;
