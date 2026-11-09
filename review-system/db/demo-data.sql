-- Explicit demo import only. Create demo_viewer with the application beforehand.
-- This script does not reset or delete existing data.
SET NAMES utf8mb4;
SET time_zone='+09:00';
SET @owner=(SELECT id FROM app_user WHERE username='demo_viewer');
INSERT INTO work(title,media_type,release_year,synopsis,created_by)
SELECT '작품 A · 시연','TV',2026,'9월 첫 기록, 10월 재감상과 최신 미평점 사례',@owner FROM DUAL
WHERE NOT EXISTS(SELECT 1 FROM work WHERE title='작품 A · 시연' AND created_by=@owner);
INSERT INTO work(title,media_type,release_year,synopsis,created_by)
SELECT '작품 B · 시연','MOVIE',2026,'같은 기간의 두 번 감상과 평점 변화 사례',@owner FROM DUAL
WHERE NOT EXISTS(SELECT 1 FROM work WHERE title='작품 B · 시연' AND created_by=@owner);
INSERT INTO work(title,media_type,synopsis,created_by)
SELECT '작품 C · 시연','MOVIE','9월에만 유효 평점이 있는 작품',@owner FROM DUAL
WHERE NOT EXISTS(SELECT 1 FROM work WHERE title='작품 C · 시연' AND created_by=@owner);
INSERT INTO work(title,media_type,synopsis,created_by)
SELECT '상태만 있는 작품 · 시연','TV','상태만 완료이며 감상 기록 수에는 포함되지 않음',@owner FROM DUAL
WHERE NOT EXISTS(SELECT 1 FROM work WHERE title='상태만 있는 작품 · 시연' AND created_by=@owner);
SET @a=(SELECT MIN(id) FROM work WHERE title='작품 A · 시연' AND created_by=@owner);
SET @b=(SELECT MIN(id) FROM work WHERE title='작품 B · 시연' AND created_by=@owner);
SET @c=(SELECT MIN(id) FROM work WHERE title='작품 C · 시연' AND created_by=@owner);
SET @d=(SELECT MIN(id) FROM work WHERE title='상태만 있는 작품 · 시연' AND created_by=@owner);
SET @drama=(SELECT id FROM genre WHERE name='드라마');
SET @sf=(SELECT id FROM genre WHERE name='SF');
INSERT IGNORE INTO work_genre(work_id,genre_id) VALUES(@a,@drama),(@a,@sf),(@b,@drama),(@c,@sf),(@d,@drama);
INSERT INTO viewing_record(user_id,work_id,viewed_on,rating,review)
SELECT @owner,@a,'2026-09-20',4.0,'시연 데이터: 9월 첫 감상' FROM DUAL WHERE NOT EXISTS(SELECT 1 FROM viewing_record WHERE user_id=@owner AND work_id=@a AND viewed_on='2026-09-20' AND review='시연 데이터: 9월 첫 감상');
INSERT INTO viewing_record(user_id,work_id,viewed_on,rating,review)
SELECT @owner,@a,'2026-10-10',4.5,'시연 데이터: 최신 유효 평점' FROM DUAL WHERE NOT EXISTS(SELECT 1 FROM viewing_record WHERE user_id=@owner AND work_id=@a AND viewed_on='2026-10-10' AND review='시연 데이터: 최신 유효 평점');
INSERT INTO viewing_record(user_id,work_id,viewed_on,rating,review)
SELECT @owner,@a,'2026-10-12',NULL,'시연 데이터: 최신 기록은 미평점' FROM DUAL WHERE NOT EXISTS(SELECT 1 FROM viewing_record WHERE user_id=@owner AND work_id=@a AND viewed_on='2026-10-12' AND review='시연 데이터: 최신 기록은 미평점');
INSERT INTO viewing_record(user_id,work_id,viewed_on,rating,review)
SELECT @owner,@b,'2026-10-11',3.0,'시연 데이터: 작품 B 첫 감상' FROM DUAL WHERE NOT EXISTS(SELECT 1 FROM viewing_record WHERE user_id=@owner AND work_id=@b AND viewed_on='2026-10-11' AND review='시연 데이터: 작품 B 첫 감상');
INSERT INTO viewing_record(user_id,work_id,viewed_on,rating,review)
SELECT @owner,@b,'2026-10-13',4.0,'시연 데이터: 작품 B 재감상' FROM DUAL WHERE NOT EXISTS(SELECT 1 FROM viewing_record WHERE user_id=@owner AND work_id=@b AND viewed_on='2026-10-13' AND review='시연 데이터: 작품 B 재감상');
INSERT INTO viewing_record(user_id,work_id,viewed_on,rating,review)
SELECT @owner,@c,'2026-09-21',5.0,'시연 데이터: 기간 밖 평점' FROM DUAL WHERE NOT EXISTS(SELECT 1 FROM viewing_record WHERE user_id=@owner AND work_id=@c AND viewed_on='2026-09-21' AND review='시연 데이터: 기간 밖 평점');
INSERT IGNORE INTO user_work_state(user_id,work_id,status) VALUES(@owner,@a,'WATCHING'),(@owner,@b,'COMPLETED'),(@owner,@d,'COMPLETED');
