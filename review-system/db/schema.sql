-- Apply explicitly to a selected, dedicated database. Never executed on app startup.
SET NAMES utf8mb4;
SET time_zone = '+09:00';
CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(40) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  name VARCHAR(60) NOT NULL,
  email VARCHAR(254) NOT NULL,
  role VARCHAR(10) NOT NULL DEFAULT 'USER',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_user_username (username),
  CONSTRAINT ck_user_role CHECK (role IN ('USER','ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS work (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  media_type VARCHAR(10) NOT NULL,
  release_year SMALLINT NULL,
  synopsis VARCHAR(1000) NULL,
  source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
  external_id BIGINT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_external_work (source, media_type, external_id),
  KEY ix_work_title (title,media_type),
  CONSTRAINT fk_work_creator FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT ck_work_type CHECK (media_type IN ('MOVIE','TV')),
  CONSTRAINT ck_work_year CHECK (release_year IS NULL OR release_year BETWEEN 1888 AND 2100),
  CONSTRAINT ck_work_source CHECK ((source='MANUAL' AND external_id IS NULL) OR (source='TMDB' AND external_id > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS genre (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(40) NOT NULL,
  UNIQUE KEY uq_genre_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS work_genre (
  work_id BIGINT NOT NULL,
  genre_id BIGINT NOT NULL,
  PRIMARY KEY (work_id,genre_id),
  KEY ix_genre_work (genre_id,work_id),
  CONSTRAINT fk_wg_work FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE CASCADE,
  CONSTRAINT fk_wg_genre FOREIGN KEY (genre_id) REFERENCES genre(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS viewing_record (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  work_id BIGINT NOT NULL,
  viewed_on DATE NOT NULL,
  rating DECIMAL(3,1) NULL,
  review VARCHAR(4000) NULL,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  KEY ix_record_user_date (user_id,viewed_on,created_at,id),
  KEY ix_record_user_work (user_id,work_id,viewed_on,created_at,id),
  CONSTRAINT fk_record_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_record_work FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE RESTRICT,
  CONSTRAINT ck_record_rating CHECK (rating IS NULL OR (rating BETWEEN 0.5 AND 5.0 AND MOD(rating,0.5)=0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS user_work_state (
  user_id BIGINT NOT NULL,
  work_id BIGINT NOT NULL,
  status VARCHAR(12) NOT NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id,work_id),
  KEY ix_state_user_status (user_id,status),
  CONSTRAINT fk_state_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_state_work FOREIGN KEY (work_id) REFERENCES work(id) ON DELETE RESTRICT,
  CONSTRAINT ck_state_status CHECK (status IN ('WANT','WATCHING','COMPLETED','DROPPED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- A durable idempotency ledger also prevents replay after a record has been deleted.
CREATE TABLE IF NOT EXISTS record_submission (
  user_id BIGINT NOT NULL,
  request_token CHAR(36) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  record_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id,request_token),
  CONSTRAINT fk_submission_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_submission_record FOREIGN KEY (record_id) REFERENCES viewing_record(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
