-- =============================================================================
-- schema.sql
-- PostgreSQL schema cho ứng dụng nghe nhạc (java-tcp-music-finder)
-- Dựa trên thiết kế ERD trong database/README.md
-- =============================================================================



-- Thứ tự tạo bảng:
--   1. USERS, ARTISTS, SONGS          (không phụ thuộc FK nào)
--   2. SONG_ARTISTS                   (FK → SONGS, ARTISTS)
--   3. PLAYLISTS                      (FK → USERS)
--   4. PLAYLIST_SONGS                 (FK → PLAYLISTS, SONGS)
--   5. SEARCH_HISTORY                 (FK → USERS)
--   6. SEARCH_CACHE, (độc lập, không có FK)

-- =============================================================================
-- 1. USERS
-- Lưu thông tin tài khoản và thông tin cơ bản của người dùng.
-- =============================================================================
CREATE TABLE IF NOT EXISTS USERS (
    user_id       BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100),
    email         VARCHAR(150) UNIQUE,
    avatar_url    TEXT,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 2. ARTISTS
-- Lưu thông tin nghệ sĩ. external_id cho phép liên kết với nguồn API ngoài.
-- =============================================================================
CREATE TABLE IF NOT EXISTS ARTISTS (
    artist_id       BIGSERIAL    PRIMARY KEY,
    external_id     VARCHAR(100) UNIQUE,
    name            VARCHAR(150) NOT NULL,
    normalized_name VARCHAR(150),
    birth_date      DATE,
    hometown        VARCHAR(255),
    biography       TEXT,
    image_url       TEXT,
    search_count    BIGINT    DEFAULT 0 CHECK (search_count >= 0),
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 3. SONGS
-- Lưu thông tin các bài hát. external_id cho phép liên kết với nguồn API ngoài.
-- =============================================================================
CREATE TABLE IF NOT EXISTS SONGS (
    song_id          BIGSERIAL    PRIMARY KEY,
    external_id      VARCHAR(100) UNIQUE,
    title            VARCHAR(255) NOT NULL,
    normalized_title VARCHAR(255),
    composer         VARCHAR(255),
    duration_seconds INTEGER      CHECK (duration_seconds >= 0),
    audio_url        TEXT,
    video_url        TEXT,
    preview_url      TEXT,
    thumbnail_url    TEXT,
    lyric            TEXT,
    search_count     BIGINT     DEFAULT 0 CHECK (search_count >= 0),
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 4. SONG_ARTISTS
-- Bảng trung gian Many-to-Many giữa SONGS và ARTISTS.
-- Composite PK (song_id, artist_id) đảm bảo không trùng cặp.
-- =============================================================================
CREATE TABLE IF NOT EXISTS SONG_ARTISTS (
    song_id   BIGINT NOT NULL,
    artist_id BIGINT NOT NULL,
    PRIMARY KEY (song_id, artist_id),
    CONSTRAINT fk_song_artists_song   FOREIGN KEY (song_id)   REFERENCES SONGS(song_id) ON DELETE CASCADE,
    CONSTRAINT fk_song_artists_artist FOREIGN KEY (artist_id) REFERENCES ARTISTS(artist_id) ON DELETE CASCADE
);

-- =============================================================================
-- 5. PLAYLISTS
-- Lưu các playlist do người dùng tạo.
-- FK user_id → USERS.user_id (1 user : N playlists).
-- =============================================================================
CREATE TABLE IF NOT EXISTS PLAYLISTS (
    playlist_id BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    thumbnail_url VARCHAR(255),
    custom_thumbnail BOOLEAN NOT NULL DEFAULT FALSE,
    type VARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK (type IN ('NORMAL', 'FAVORITES')),
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_playlists_user FOREIGN KEY (user_id) REFERENCES USERS(user_id) ON DELETE CASCADE
);

-- =============================================================================
-- 6. PLAYLIST_SONGS
-- Bảng trung gian Many-to-Many giữa PLAYLISTS và SONGS.
-- Lưu thêm vị trí bài hát (position) và thời điểm thêm (added_at).
-- =============================================================================
CREATE TABLE IF NOT EXISTS PLAYLIST_SONGS (
    playlist_id BIGINT    NOT NULL,
    song_id     BIGINT    NOT NULL,
    position    INTEGER   CHECK (position >= 0),
    added_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (playlist_id, song_id),
    CONSTRAINT fk_playlist_songs_playlist FOREIGN KEY (playlist_id) REFERENCES PLAYLISTS(playlist_id) ON DELETE CASCADE,
    CONSTRAINT fk_playlist_songs_song     FOREIGN KEY (song_id)     REFERENCES SONGS(song_id) ON DELETE CASCADE
);

-- =============================================================================
-- 7. SEARCH_HISTORY
-- Lưu lịch sử tìm kiếm của người dùng.
-- FK user_id → USERS.user_id (1 user : N lịch sử).
-- =============================================================================
CREATE TABLE IF NOT EXISTS SEARCH_HISTORY (
    history_id         BIGSERIAL    PRIMARY KEY,
    user_id            BIGINT,
    keyword            VARCHAR(255) NOT NULL,
    normalized_keyword VARCHAR(255),
    search_type        VARCHAR(20),
    result_count       INTEGER      CHECK (result_count >= 0),
    searched_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_search_history_user FOREIGN KEY (user_id) REFERENCES USERS(user_id) ON DELETE CASCADE
);

-- =============================================================================
-- 8. SEARCH_CACHE
-- Cache kết quả tìm kiếm nhằm hạn chế truy vấn lặp lại.
-- response_json dùng JSONB để lưu trực tiếp kết quả tìm kiếm.
-- Không có FK tới bảng khác theo thiết kế.
-- =============================================================================
CREATE TABLE IF NOT EXISTS SEARCH_CACHE (
    cache_id           BIGSERIAL    PRIMARY KEY,
    cache_key          VARCHAR(300) NOT NULL UNIQUE,
    keyword            VARCHAR(255) NOT NULL,
    normalized_keyword VARCHAR(255) NOT NULL,
    search_type        VARCHAR(20)  NOT NULL,
    response_json      JSONB        NOT NULL,
    created_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    expires_at         TIMESTAMP    NOT NULL
);
-- =============================================================================
-- 8. SEARCH_STATISTICS
-- Lưu thống kê về các từ khóa tìm kiếm.
-- Không có FK tới bảng khác theo thiết kế.
-- =============================================================================
CREATE TABLE IF NOT EXISTS SEARCH_STATISTICS (
    statistic_id	    BIGSERIAL	PRIMARY KEY,
    keyword	            VARCHAR(255)	NOT NULL,	
    normalized_keyword	VARCHAR(255)	NOT NULL,	
    search_type	        VARCHAR(20)	    NOT NULL,	
    search_count	    BIGINT,
    last_searched_at	TIMESTAMP       
);

-- =============================================================================
-- INDEXES
-- Tạo index cho các FK và các trường thường xuyên tìm kiếm.
-- =============================================================================

-- USERS
CREATE INDEX IF NOT EXISTS idx_users_username ON USERS(username);
CREATE INDEX IF NOT EXISTS idx_users_email    ON USERS(email);

-- ARTISTS
CREATE INDEX IF NOT EXISTS idx_artists_normalized_name ON ARTISTS(normalized_name);

-- SONGS
CREATE INDEX IF NOT EXISTS idx_songs_normalized_title ON SONGS(normalized_title);
ALTER TABLE SONGS
ADD COLUMN IF NOT EXISTS lyric TEXT;

-- SONG_ARTISTS (PK đã cover song_id; thêm index riêng cho artist_id phục vụ lookup ngược)
CREATE INDEX IF NOT EXISTS idx_song_artists_artist_id ON SONG_ARTISTS(artist_id);

-- PLAYLISTS
CREATE INDEX IF NOT EXISTS idx_playlists_user_id ON PLAYLISTS(user_id);
ALTER TABLE PLAYLISTS
ADD COLUMN IF NOT EXISTS custom_thumbnail BOOLEAN NOT NULL DEFAULT FALSE;

-- Mỗi user chỉ có tối đa một playlist FAVORITES
CREATE UNIQUE INDEX IF NOT EXISTS ux_playlists_user_favorites
ON PLAYLISTS(user_id)
WHERE type = 'FAVORITES';

-- PLAYLIST_SONGS (PK đã cover playlist_id; thêm index riêng cho song_id phục vụ lookup ngược)
CREATE INDEX IF NOT EXISTS idx_playlist_songs_song_id ON PLAYLIST_SONGS(song_id);

-- SEARCH_HISTORY
CREATE INDEX IF NOT EXISTS idx_search_history_user_id            ON SEARCH_HISTORY(user_id);
CREATE INDEX IF NOT EXISTS idx_search_history_keyword            ON SEARCH_HISTORY(keyword);
CREATE INDEX IF NOT EXISTS idx_search_history_normalized_keyword ON SEARCH_HISTORY(normalized_keyword);

-- SEARCH_CACHE
CREATE INDEX IF NOT EXISTS idx_search_cache_cache_key          ON SEARCH_CACHE(cache_key);
CREATE INDEX IF NOT EXISTS idx_search_cache_expires_at         ON SEARCH_CACHE(expires_at);
CREATE INDEX IF NOT EXISTS idx_search_cache_normalized_keyword ON SEARCH_CACHE(normalized_keyword);


--SEARCH_STATISTICS
CREATE UNIQUE INDEX IF NOT EXISTS ux_search_stats_keyword_type 
ON SEARCH_STATISTICS(normalized_keyword, search_type);
CREATE INDEX IF NOT EXISTS idx_search_stats_trending 
ON SEARCH_STATISTICS(search_type, search_count DESC);


-- =============================================================================
-- SEED DATA TỰ ĐỘNG SINH BỞI DataSeederTool (Data thật từ Zing MP3)
-- Mật khẩu mặc định cho toàn bộ tài khoản demo là: abc123
-- =============================================================================

-- 1. USERS (7 tài khoản demo, password = 'abc123')
INSERT INTO USERS (username, password_hash, full_name, email, avatar_url) VALUES
    ('alice', '$2a$12$/vlsaZBqCSU4QtlaI/LqiO97.0afx0r3zr1hPWRpBb7bRsfDf3DKe', 'Alice Nguyễn', 'alice@musicfinder.vn', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150'),
    ('bob', '$2a$12$/vlsaZBqCSU4QtlaI/LqiO97.0afx0r3zr1hPWRpBb7bRsfDf3DKe', 'Bob Trần', 'bob@musicfinder.vn', 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150'),
    ('dong', '$2a$12$/vlsaZBqCSU4QtlaI/LqiO97.0afx0r3zr1hPWRpBb7bRsfDf3DKe', 'Đồng Nguyễn', 'dong@musicfinder.vn', 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150'),
    ('hiep', '$2a$12$/vlsaZBqCSU4QtlaI/LqiO97.0afx0r3zr1hPWRpBb7bRsfDf3DKe', 'Trương Hiệp', 'hiep@musicfinder.vn', 'https://images.unsplash.com/photo-1527980965255-d3b416303d12?w=150'),
    ('minh', '$2a$12$/vlsaZBqCSU4QtlaI/LqiO97.0afx0r3zr1hPWRpBb7bRsfDf3DKe', 'Minh Lê', 'minh@musicfinder.vn', 'https://images.unsplash.com/photo-1580489944761-15a19d654956?w=150'),
    ('phuong', '$2a$12$/vlsaZBqCSU4QtlaI/LqiO97.0afx0r3zr1hPWRpBb7bRsfDf3DKe', 'Phương Phạm', 'phuong@musicfinder.vn', 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150'),
    ('quang', '$2a$12$/vlsaZBqCSU4QtlaI/LqiO97.0afx0r3zr1hPWRpBb7bRsfDf3DKe', 'Quang Vũ', 'quang@musicfinder.vn', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150')
ON CONFLICT (username) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    email = EXCLUDED.email,
    avatar_url = EXCLUDED.avatar_url;

-- 2. ARTISTS (Thông tin nghệ sĩ thật thu thập từ Zing MP3)
INSERT INTO ARTISTS (external_id, name, normalized_name, birth_date, hometown, biography, image_url, search_count)
VALUES ('zing:artist:Son-Tung-M-TP', 'Sơn Tùng M-TP', 'son tung m tp', '1994-07-05', 'Việt Nam', 'Thanh Tùng bắt đầu chơi nhạc từ cấp ba với nghệ danh M-TP và được biết đến với "Cơn Mưa Ngang Qua".

Năm 2012, anh đậu thủ khoa Nhạc viện TPHCM và ký hợp đồng với Văn Production, đổi nghệ danh sang Sơn Tùng M-TP.

Từ 2013 đến 2015, anh có nhiều bản hit như "Em Của Ngày Hôm Qua", "Nắng Ấm Xa Dần"...

Năm 2015, anh rời khỏi công ty cũ và gia nhập WePro, tổ chức minishow đầu tiên "M-TP and Friends".

Năm 2017, anh rời khỏi WePro để thành lập M-TP Entertainment, ra mắt "Lạc Trôi" và "Nơi Này Có Anh". Anh ra mắt album đầu tay "m-tp M-TP".

Năm 2018 anh ra mắt "Chạy Ngay Đi" và "Hãy Trao Cho Anh" năm 2019. Cả hai bài hát đều trở thành hit. Đặc biệt "Hãy Trao Cho Anh" kết hợp với Snopp Dogg đã đưa tên tuổi anh ra thế giới.', 'https://photo-resize-zmp3.zmdcdn.me/w600_r1x1_jpeg/avatars/5/9/6/9/59696c9dba7a914d587d886049c10df6.jpg', 292)
ON CONFLICT (external_id) DO UPDATE SET
    name = EXCLUDED.name,
    normalized_name = EXCLUDED.normalized_name,
    biography = EXCLUDED.biography,
    image_url = EXCLUDED.image_url;

INSERT INTO ARTISTS (external_id, name, normalized_name, birth_date, hometown, biography, image_url, search_count)
VALUES ('zing:artist:Den', 'Đen', 'den', '1989-05-13', 'Việt Nam', 'Năm 2014, anh ra mắt "Đưa Nhau Đi Trốn" cùng Linh Cáo. Bài hát nhanh chóng trở thành hit. Tiếp nối thành công, cả hai tiếp tục kết hợp trong "Ta cứ Đi Cùng Nhau".

Năm 2018, Đen liên tiếp ra mắt các sản phẩm mới nhưng đến "Anh Đếch Cần Gì Nhiều Ngoài Em" thì Đen trở thành hiện tượng xã hội. 

Năm 2019 là một năm thành công cùa Đen với những bản hit nổi tiếng như "Mười Năm" (cùng Ngọc Linh) kỷ niệm hành trình 10 năm theo rap, "Bài Này Chill Phết" cùng Min, "Hai Triệu Năm" (cùng Biên), "Lối Nhỏ" (cùng Phương Anh Đào) và "Cảm Ơn" (cùng Biên). Liveshow đầu tiên của mang tên "Show của Đen" được tổ chức vào tháng 11.

Đầu năm 2020, Đen Vâu ra mắt "Một Triệu Like" kết hợp với Thành Đồng.', 'https://photo-resize-zmp3.zmdcdn.me/w600_r1x1_jpeg/avatars/0/9/e/c/09ec67133c4ff1e7c49e79aea4980ede.jpg', 280)
ON CONFLICT (external_id) DO UPDATE SET
    name = EXCLUDED.name,
    normalized_name = EXCLUDED.normalized_name,
    biography = EXCLUDED.biography,
    image_url = EXCLUDED.image_url;

INSERT INTO ARTISTS (external_id, name, normalized_name, birth_date, hometown, biography, image_url, search_count)
VALUES ('zing:artist:Vu', 'Vũ.', 'vu', NULL, 'Việt Nam', 'Trước khi nổi tiếng, Vũ. thường đăng tải các sáng tác của mình trên Soundcloud. Thể loại của anh theo đuổi là nhạc Indie Pop, Acoustic, Rock... .

Năm 2016, Vũ. phát hành các sáng tác của anh và gặt hái được thành công trong giới indie Việt. Các hit tiêu biểu của Vũ. với "Đông Kiếm Em", "Mùa Mưa Ngâu Nằm Cạnh", "Chuyện Những Người Yêu Xa", và album "Vũ Trụ Song Song" ra mắt năm 2019.

Năm 2020, Vũ. ký hợp đồng với Warner Music Group và trở thành nghệ sĩ trực thuộc công ty.', 'https://photo-resize-zmp3.zmdcdn.me/w600_r1x1_jpeg/avatars/d/1/7/1/d17181fe947a2c205119a5a774863889.jpg', 286)
ON CONFLICT (external_id) DO UPDATE SET
    name = EXCLUDED.name,
    normalized_name = EXCLUDED.normalized_name,
    biography = EXCLUDED.biography,
    image_url = EXCLUDED.image_url;

INSERT INTO ARTISTS (external_id, name, normalized_name, birth_date, hometown, biography, image_url, search_count)
VALUES ('zing:artist:SOOBIN', 'SOOBIN', 'soobin', '1992-09-10', 'Việt Nam', 'Năm 2014, anh đạt giải Á quân cuộc thi Ngôi Sao Việt và được đào tạo 6 tháng ở Hàn Quốc. Khi trở về, anh tham gia nhóm nhạc underground SpaceSpeakers do Hoàng Touliver thành lập.

Năm 2016, anh đạt giải Bạc cuộc thi Hòa Âm Ánh Sáng và ra mắt ca khúc tự sáng tác "La La La (Và Thế Là Hết)". Với thành công của "Phía Sau Một Cô Gái" (sáng tác Tiên Cookie) thì Soobin Hoàng Sơn được đông đảo công chúng đón nhận. Ca khúc cũng giúp anh nhận lấy danh hiệu "Hoàng Tử Ballad" từ người hâm mộ.

Từ 2017 đến nay anh liên tiếp ra mắt nhiều bản hit như "Ngày Mai Em Đi", "Đi Để Trở Về" năm 2017, "Đẹp Nhất Là Em" song ca cùng ca sĩ Hàn Quốc JiYeon năm 2018 và "Nếu Ngày Ấy", "Say Goodbye" trong năm 2019.', 'https://photo-resize-zmp3.zmdcdn.me/w600_r1x1_jpeg/avatars/a/3/a/b/a3ab763366da3d3df96b55a20177285c.jpg', 242)
ON CONFLICT (external_id) DO UPDATE SET
    name = EXCLUDED.name,
    normalized_name = EXCLUDED.normalized_name,
    biography = EXCLUDED.biography,
    image_url = EXCLUDED.image_url;

-- 3. SONGS (Bài hát thật thu thập từ Zing MP3)
INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:d7qtCrRV89zn', 'Nơi Này Có Anh', 'noi nay co anh', 'Sơn Tùng M-TP', 262, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/covers/3/a/3a9e48bc4df7bbde3acea30cc267f609_1487066528.jpg', '[ar: Sơn Tùng M-TP]
[ti: Nơi Này Có Anh]
[al: Nơi Này Có Anh (Single)]
[by: ZingMp3 Lyric]
[length: 04:21]
[id: nwwlozcu]
[00:00.00]Bài hát: Nơi Này Có Anh
[00:02.00]Ca sĩ: Sơn Tùng M-TP
[00:04.00]
[00:22.91]Em là ai từ đâu bước đến nơi đây 
[00:25.72]Dịu dàng chân phương 
[00:28.15]Em là ai tựa như ánh nắng ban mai 
[00:31.01]Ngọt ngào trong sương 
[00:33.20]Ngắm em thật lâu 
[00:36.17]Con tim anh yếu mềm 
[00:38.52]Đắm say từ phút đó 
[00:40.44]Từng giây trôi yêu thêm 
[00:43.72]Bao ngày qua bình minh đánh thức 
[00:45.98]Xua tan bộn bề nơi anh 
[00:48.56]
[00:48.95]Bao ngày qua niềm thương nỗi nhớ 
[00:51.21]Bay theo bầu trời trong xanh 

[00:54.05]Lướt đôi hàng mi 
[00:57.13]Mong manh anh thẫn thờ 
[00:59.40]Muốn hôn nhẹ mái tóc 
[01:01.34]Bờ môi em anh mơ

[01:04.53]Cầm tay anh dựa vai anh 
[01:07.21]Kề bên anh nơi này có anh 
[01:09.51]Gió mang câu tình ca 
[01:10.99]Ngàn ánh sao vụt qua 
[01:12.10]Nhẹ ôm lấy em 
[01:14.52]

[01:15.08]Cầm tay anh dựa vai anh 
[01:17.70]Kề bên anh nơi này có anh 
[01:19.92]Khép đôi mi thật lâu 
[01:21.37]Nguyện mãi bên cạnh nhau 
[01:22.51]Yêu say đắm như ngày đầu
[01:25.23]Mùa xuân đến bình yên 
[01:28.42]Cho anh những giấc mơ 
[01:30.43]Hạ lưu giữ ngày mưa 
[01:33.68]Ngọt ngào nên thơ 
[01:35.65]Mùa thu lá vàng rơi 
[01:38.77]Đông sang anh nhớ em 
[01:40.83]Tình yêu bé nhỏ xin 
[01:44.00]Dành tặng riêng em
[01:46.87]
[01:57.29]Còn đó tiếng nói ấy 
[01:58.36]Bên tai vấn vương bao ngày qua 
[02:00.10]Ánh mắt bối rối 
[02:00.91]Nhớ thương bao ngày qua 
[02:02.77]Yêu em anh thẫn thờ 
[02:03.81]Con tim bâng khuâng đâu có ngờ 
[02:05.06]Chẳng bao giờ phải mong chờ 
[02:06.48]Đợi ai trong chiều hoàng hôn mờ 
[02:07.84]Đắm chìm hoà vào vần thơ 
[02:09.15]Ngắm nhìn khờ dại mộng mơ 
[02:10.40]Đừng bước vội vàng rồi làm ngơ 
[02:11.66]Lạnh lùng đó làm bộ dạng thờ ơ 
[02:13.00]Nhìn anh đi em nha 
[02:13.90]Hướng nụ cười cho riêng anh nha 
[02:15.10]Đơn giản là yêu 
[02:15.82]Con tim anh lên tiếng thôi

[02:17.65]Cầm tay anh dựa vai anh 
[02:20.33]Kề bên anh nơi này có anh 
[02:22.57]Gió mang câu tình ca 
[02:24.01]Ngàn ánh sao vụt qua 
[02:25.09]Nhẹ ôm lấy em 
[02:27.46]

[02:28.04]Cầm tay anh dựa vai anh 
[02:30.66]Kề bên anh nơi này có anh 
[02:32.95]Khép đôi mi thật lâu 
[02:34.46]Nguyện mãi bên cạnh nhau 
[02:35.57]Yêu say đắm như ngày đầu

[02:38.27]Mùa xuân đến bình yên 
[02:41.42]Cho anh những giấc mơ 
[02:43.36]Hạ lưu giữ ngày mưa 
[02:46.66]Ngọt ngào nên thơ 
[02:48.79]Mùa thu lá vàng rơi 
[02:51.91]Đông sang anh nhớ em 
[02:53.84]Tình yêu bé nhỏ xin 
[02:57.02]Dành tặng riêng em
[02:59.64]

[03:02.59]Nhớ thương em 
[03:04.46]
[03:07.75]Nhớ thương em lắm 
[03:10.63]
[03:12.26]Phía sau chân trời 
[03:14.74]Có ai băng qua lối về 
[03:16.79]Cùng em đi trên đoạn đường dài

[03:20.19]Cầm tay anh dựa vai anh 
[03:22.87]Kề bên anh nơi này có anh 
[03:25.11]Gió mang câu tình ca 
[03:26.55]Ngàn ánh sao vụt qua 
[03:27.63]Nhẹ ôm lấy em 
[03:30.00]

[03:30.65]Cầm tay anh dựa vai anh 
[03:33.26]Kề bên anh nơi này có anh 
[03:35.56]Khép đôi mi thật lâu 
[03:37.00]Nguyện mãi bên cạnh nhau 
[03:38.10]Yêu say đắm như ngày đầu

[03:40.86]Mùa xuân đến bình yên 
[03:44.01]Cho anh những giấc mơ 
[03:45.96]Hạ lưu giữ ngày mưa 
[03:49.26]Ngọt ngào nên thơ 
[03:51.26]Mùa thu lá vàng rơi 
[03:54.32]Đông sang anh nhớ em 
[03:56.51]Tình yêu bé nhỏ xin 
[03:59.75]Dành tặng riêng em
[04:02.54]', 870)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:USJgi8Pq9Ouf', 'Âm Thầm Bên Em', 'am tham ben em', 'Sơn Tùng M-TP', 291, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/0/0/4/d/004dcaf097f0a0d6cff0654404d2479e.jpg', '[ar: Son Tung M-TP]
[ti: Am Tham Ben Em (Live)]
[al: mp3.zing.vn]
[by: ZingMp3 Lyric]
[length: 04:51]
[00:00.00]Bài Hát: Âm Thầm Bên Em
[00:02.00]Ca Sĩ: Sơn Tùng M-TP
[00:04.00]
[00:16.54]Khi bên anh em thấy điều chi 
[00:19.96]Khi bên anh em thấy điều gì
[00:22.54]Nước mắt rơi
[00:24.46]Gần kề làn mi 
[00:28.09]
[00:28.53]Chẳng còn những giây phút 
[00:31.96]Chẳng còn những ân tình 
[00:35.35]
[00:35.79]Gió mang em rời xa nơi đây 
[00:41.28]
[00:43.91]Khi xa anh em nhớ về ai 
[00:47.43]Khi xa anh em nhớ 
[00:49.15]Một người chắc không phải 
[00:52.09]Một người như anh 
[00:55.42]
[00:55.92]Người từng làm em khóc 
[00:59.42]Người từng khiến em buồn 
[01:02.69]
[01:03.28]Buông bàn tay
[01:04.76]Rời xa lặng thinh bước đi 
[01:08.71]
[01:09.20]Hạt mưa rơi bủa vây
[01:12.22]Trái tim hiu quạnh 
[01:15.44]
[01:16.16]Ngàn yêu thương 
[01:17.84]Vụt tan bỗng xa 
[01:21.05]
[01:22.93]Người từng nói ở bên 
[01:25.97]Cạnh anh mỗi khi anh buồn 
[01:30.33]
[01:30.66]Cớ sao giờ 
[01:32.15]Lời nói kia như gió bay 
[01:36.04]
[01:36.74]Đừng bỏ rơi 
[01:38.40]Bàn tay ấy bơ vơ mà
[01:42.35]
[01:43.51]Một mình anh lặng im chốn đây 
[01:49.32]Yêu em âm thầm bên em
[01:54.10]

[02:04.94]Yêu thương không còn nơi đây
[02:06.60]Em mang tình buồn theo mây
[02:08.22]Cơn mơ về mong manh câu thề
[02:09.91]Tan trôi qua mau
[02:10.48]Quên đi phút giây
[02:11.63]Mưa rơi trên đôi mi qua lối vắng
[02:13.42]Ánh sáng mờ
[02:13.82]Buông lơi làn khói trắng
[02:15.07]Bóng dáng em 
[02:15.52]Nụ cười ngày hôm qua 
[02:16.31]Ký ức có ngủ quên 
[02:17.11]Chìm trong màn sương đắng
[02:18.18]
[02:20.27]Anh nhớ giọt nước mắt sâu lắng
[02:21.86]
[02:23.28]Anh nhớ nỗi buồn 
[02:23.98]Của em ngày không nắng
[02:25.30]

[02:25.64]Buông bàn tay
[02:27.12]Rời xa lặng thinh bước đi 
[02:31.07]
[02:31.56]Hạt mưa rơi bủa vây
[02:34.58]Trái tim hiu quạnh 
[02:37.80]
[02:38.52]Ngàn yêu thương 
[02:40.20]Vụt tan bỗng xa 
[02:43.41]
[02:45.29]Người từng nói ở bên 
[02:48.33]Cạnh anh mỗi khi anh buồn 
[02:52.69]
[02:53.02]Cớ sao giờ 
[02:54.51]Lời nói kia như gió bay 
[02:58.40]
[03:01.36]Bàn tay bơ vơ mà
[03:05.05]
[03:06.21]Cầm bông hoa chờ mong nhớ thương
[03:11.23]
[03:12.95]Làm sao quên người ơi
[03:15.69]Tình anh mãi như hôm nào
[03:19.47]
[03:20.39]Vẫn yêu người
[03:21.87]Và vẫn mong em về đây
[03:25.69]
[03:26.42]Giọt nước mắt 
[03:28.08]Tại sao cứ lăn rơi hoài
[03:32.42]
[03:33.30]Ở bên anh chỉ có đớn đau
[03:39.41]
[03:40.04]Thì anh xin nhận hết 
[03:43.14]Ngàn đau đớn để thấy em cười
[03:47.40]
[03:47.80]Dẫu biết rằng  
[03:49.42]Người đến không như giấc mơ
[03:52.77]Yêu em âm thầm bên em
[03:57.08]
[03:59.76]Yêu em âm thầm bên em
[04:05.08]

[04:07.54]Thì anh xin nhận hết 
[04:10.57]Ngàn đau đớn để thấy em cười
[04:14.74]
[04:15.30]Dẫu biết rằng  
[04:16.92]Người đến không như giấc mơ
[04:20.27]Yêu em âm thầm bên em
[04:25.98]', 483)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:yTPspCIlrm0y', 'Em Của Ngày Hôm Qua', 'em cua ngay hom qua', 'Sơn Tùng M-TP', 232, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/covers/b/1/b1c839bd85e6081614cf770278b8f782_1387164001.jpg', '[ar: Son Tung M-TP]
[ti: Em Cua Ngay Hom Qua]
[al: mp3.zing.vn]
[by: ZingMp3 Lyric]
[length: 03:52]
[id: auhhpzcs]
[00:00.00]Bài Hát: Em Của Ngày Hôm Qua 
[00:02.00]Ca sĩ: Sơn Tùng M-TP
[00:04.00]
[00:06.83]Eh eh eh
[00:09.92]Em đang nơi nào
[00:10.83]Can you feel me
[00:12.21]
[00:13.14]Can you feel me
[00:14.56]
[00:15.94]Eh eh eh 
[00:19.09]M-tp 
[00:19.67]Liệu rằng chia tay trong em 
[00:21.37]Có quên được câu ca
[00:22.74]Tình yêu khi xưa em trao cho anh 
[00:23.74]đâu nào phôi pha
[00:24.83]Đừng lừa dối con tim anh
[00:25.87]Em sẽ không 
[00:26.54]Buông tay anh được đâu mà
[00:28.08]Em không thể bước đi
[00:28.96]
[00:29.39]Gạt nước mắt yếu đuối đó 
[00:30.38]Cứ quay lại nơi anh
[00:31.67]Em biết rằng cơn mưa qua 
[00:32.54]đâu có che lấp 
[00:33.33]được nụ cười đau thương kia
[00:34.80]Nước mắt đó vẫn rơi vì em
[00:36.13]Oh baby No baby
[00:38.00]Đừng nhìn anh nữa
[00:39.03]đôi mắt ngày xưa giờ ở đâu
[00:41.32]Em còn là em
[00:42.84]Em đã khác rồi
[00:43.94]Em muốn quay lưng quên hết đi
[00:46.06]
[00:47.00]Tình yêu trong em giờ toàn giả dối
[00:49.33]Anh không muốn vùi mình trong mơ
[00:51.64]Anh không muốn 
[00:52.73]đi tìm giấc mơ ngày hôm nao
[00:54.85]
[00:55.04]Đừng vội vàng em hãy là 
[00:55.90]Em của ngày hôm qua
[00:57.64]Uh uh uh uh 
[00:59.80]
[01:00.13]Xin hãy là em của ngày hôm qua
[01:02.32]Uh uh uh uh 
[01:04.45]
[01:04.64]Đừng bỏ mặc anh một mình nơi đây
[01:06.75]Uh uh uh uh 
[01:08.95]
[01:09.19]Dừng lại và xoá nhẹ đi kí ức
[01:11.40]Uh uh uh uh 
[01:13.37]Chìm đắm vào những lời ca dịu êm thuở nào
[01:15.04]
[01:15.40]Mưa đang rơi đôi tay buông lơi
[01:17.65]Mình anh ngồi đây tìm lại 
[01:18.33]Những khoảng không dường như chơi vơi
[01:19.74]Thật ngu ngốc vu vơ
[01:20.87]Lang thang trên con đường tìm lại giấc mơ
[01:21.98]Dường như đã quá xa
[01:22.94]Và em không còn thiết tha
[01:24.29]
[01:24.53]Nắm lấy đôi tay anh muốn giữ em ở lại
[01:26.78]Nếu cứ tiếp tục cả hai sẽ phải khổ đau
[01:28.73]Đừng cố tỏ ra mạnh mẽ
[01:30.23]Đó đâu phải là em vậy đi
[01:31.74]
[01:32.20]Tạm biệt em
[01:33.01]
[01:33.57]Nhắm mắt lại hãy nghĩ đi
[01:36.68]Lâu nay em sống cho mình em mà
[01:39.08]Phải không em hỡi người
[01:41.02]
[01:43.02]Tìm lại thời gian của riêng đôi ta
[01:45.23]Nhưng sao trong anh đã quá tuyệt vọng
[01:47.73]
[01:48.36]Sự thật đang bủa vây nơi anh
[01:50.79]Forget baby

[01:51.16]Đừng nhìn anh nữa
[01:52.19]đôi mắt ngày xưa giờ ở đâu
[01:54.48]Em còn là em, em đã khác rồi
[01:57.17]Em muốn quay lưng quên hết đi
[01:59.29]
[02:00.23]Tình yêu trong em giờ toàn giả dối
[02:02.56]Anh không muốn vùi mình trong mơ
[02:04.87]Anh không muốn 
[02:05.96]đi tìm giấc mơ ngày hôm nao
[02:08.08]
[02:08.27]Đừng vội vàng em hãy là 
[02:09.13]Em của ngày hôm qua
[02:10.87]Uh uh uh uh 
[02:13.03]
[02:13.28]Xin hãy là em của ngày hôm qua
[02:15.67]Uh uh uh uh 
[02:17.68]
[02:17.87]Đừng bỏ mặc anh một mình nơi đây
[02:19.98]Uh uh uh uh 
[02:22.18]
[02:22.52]Dừng lại và xoá nhẹ đi kí ức
[02:24.73]Uh uh uh uh 
[02:26.66]Em mang những cảm xúc theo người mất rồi
[02:28.61]
[02:29.42]Em mang tiếng cười
[02:31.74]
[02:33.54]Em mang hạnh phúc đi rời xa
[02:35.91]
[02:38.57]Xung quanh chỉ còn mỗi anh 
[02:40.28]Và những nỗi đau
[02:41.57]Hãy xoá sạch hết đi
[02:43.17]Đừng vấn vương
[02:44.30]Vì em, do em, chính em
[02:46.03]Hãy mang hết đi
[02:48.59]
[02:50.02]Người cứ vội vàng
[02:51.75]
[02:52.12]Người cứ vội vàng
[02:54.37]
[02:56.78]Xin hãy là em của ngày hôm qua
[02:58.91]
[02:59.10]Người bước xa rồi
[03:00.77]Và người đã bước xa rồi
[03:03.64]
[03:05.44]Đừng vội vàng em hãy là 
[03:06.31]Em của ngày hôm qua
[03:08.05]Uh uh uh uh 
[03:10.21]
[03:10.46]Xin hãy là em của ngày hôm qua
[03:12.60]Uh uh uh uh 
[03:14.79]
[03:15.04]Đừng bỏ mặc anh một mình nơi đây
[03:17.19]Uh uh uh uh 
[03:19.39]
[03:19.56]Dừng lại và xoá nhẹ đi kí ức
[03:21.77]Uh uh uh uh 
[03:23.65]Đừng vội vàng em hãy là 
[03:24.57]Em của ngày hôm qua
[03:26.07]
[03:26.51]Em đã quên chưa? 
[03:27.42]
[03:28.76]Xin hãy là em của ngày hôm qua
[03:30.91]Anh nghĩ những dòng cảm xúc 
[03:31.72]Trong anh giờ đã hết thật rồi đấy
[03:33.33]Đừng bỏ mặc anh một mình nơi đây
[03:35.63]Chẳng còn gì cả
[03:36.47]
[03:37.85]Dừng lại và xoá nhẹ đi kí ức
[03:39.97]', 845)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:wd5Yipfmfk8o', 'Chúng Ta Không Thuộc Về Nhau', 'chung ta khong thuoc ve nhau', 'Sơn Tùng M-TP', 233, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/9/0/2/3/9023a6daa36a34ff7abbf2698386187d.jpg', '[00:04.78] Về về về nơi này
[00:08.24] Chúng ta không thuộc về nhau
[00:12.98] Chúng ta không thuộc về
[00:14.56] Về về về nơi này
[00:17.76] Niềm tin đã mất,
[00:19.34] Giọt nước mắt cuốn ký ức anh chìm sâu
[00:22.27] Tình về nơi đâu,
[00:23.87] Cô đơn đôi chân
[00:24.67] Lạc trôi giữa bầu trời
[00:26.78] Màn đêm che giấu
[00:28.56] Từng góc tối khuất lấp
[00:29.63] Phía sau bờ môi
[00:31.75] Tại vì anh thôi
[00:33.35] Yêu say mê nên đôi khi quá dại khờ
[00:36.27] Nhắm mắt ơ thờ
[00:37.33] Anh không muốn lạc vào trong nỗi đau này
[00:40.79] Phía trước, bây giờ
[00:42.11] Ai đang nắm chặt bàn tay của em vậy
[00:45.04] Mông lung như một trò đùa
[00:47.43] Anh xin giơ tay rút lui thôi
[00:50.09] Do ai
[00:50.36] Trách ai bây giờ đây
[00:54.62] Chúng ta không thuộc về nhau
[00:57.00] Chúng ta không thuộc về nhau
[00:59.13] Chúng ta không thuộc về nhau
[01:00.73] Em hãy cứ đi bên người mà em cần
[01:03.91] Trái tim không thuộc về nhau
[01:06.04] Giấc mơ không là của nhau
[01:08.43] Xóa câu ca buồn chiều mưa
[01:10.02] Anh lỡ xóa luôn yêu thương ngày xưa rồi
[01:13.21] Chúng ta không thuộc về nhau
[01:18.27] Chúng ta không thuộc về
[01:20.40] Về về về nơi này
[01:22.53] Chúng ta không thuộc về nhau
[01:27.31] Chúng ta không thuộc về
[01:29.43] Về về về nơi này
[01:33.15] Hey, từng đêm qua
[01:35.01] Cơn mưa bao vây chia rời đôi ta
[01:37.88] Em, ngày hôm qua
[01:39.73] Cuốn gió theo mây đi về nơi xa
[01:42.12] Trời xanh rộng bao la
[01:43.46] Anh lê đôi chân mình bơ vơ, lang thang
[01:45.57] Có lẽ em đang yên vui bên nhân tình
[01:47.17] Quên đi để anh nhớ
[01:48.25] Hơi men để anh mơ
[01:49.31] Nơi đâu để anh giấu
[01:50.38] Một nỗi buồn vào trong thơ
[01:51.43] Nhắm mắt ơ thờ
[01:52.23] Anh không muốn lạc vào trong nỗi đau này
[01:55.42] Phía trước, bây giờ
[01:56.47] Ai đang nắm chặt bàn tay của em vậy
[01:59.93] Mông lung như một trò đùa
[02:02.06] Anh xin giơ tay rút lui thôi
[02:04.45] Do ai
[02:04.97] Trách ai bây giờ đây
[02:09.24] Chúng ta không thuộc về nhau
[02:11.35] Chúng ta không thuộc về nhau
[02:13.75] Chúng ta không thuộc về nhau
[02:15.35] Em hãy cứ đi bên người mà em cần
[02:18.26] Trái tim không thuộc về nhau
[02:20.65] Giấc mơ không là của nhau
[02:23.05] Xóa câu ca buồn chiều mưa
[02:24.64] Anh lỡ xóa luôn yêu thương ngày xưa rồi
[02:27.58] Chúng ta không thuộc về nhau
[02:32.63] Chúng ta không thuộc về
[02:34.48] Về về về nơi này
[02:37.14] Chúng ta không thuộc về nhau
[02:42.18] Chúng ta không thuộc về
[02:43.51] Về về về nơi này
[02:46.43] Chúng ta không thuộc về nhau
[02:49.56] Không thuộc về nhau
[02:51.89] Không thuộc về nhau
[02:55.87] Chúng ta không thuộc về nhau
[02:58.81] Không thuộc về nhau
[03:01.18] Không thuộc về nhau
[03:05.17] Chúng ta không thuộc về nhau
[03:07.30] Chúng ta không thuộc về nhau
[03:09.42] Chúng ta không thuộc về nhau
[03:11.29] Em hãy cứ đi bên người mà em cần
[03:14.21] Trái tim không thuộc về nhau
[03:16.60] Giấc mơ không là của nhau
[03:19.00] Xóa câu ca buồn chiều mưa
[03:20.58] Anh lỡ xóa luôn yêu thương ngày xưa rồi
[03:23.77] Chúng ta không thuộc về nhau
[03:26.17] Chúng ta không thuộc về nhau
[03:28.29] Chúng ta không thuộc về nhau
[03:33.07] Chúng ta không thuộc về nhau
[03:35.46] Chúng ta không thuộc về nhau
[03:37.59] Chúng ta không thuộc về nhau', 953)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:fITftN1bwVlE', 'Muộn Rồi Mà Sao Còn', 'muon roi ma sao con', 'Sơn Tùng M-TP', 276, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/e/f/6/c/ef6c9d4b97048c9911ea6069e968dadb.jpg', '[00:29.32] Muộn rồi mà sao còn
[00:31.38] Nhìn lên trần nhà
[00:32.72] Rồi quay ra lại quay vào
[00:35.64] Nằm trằn trọc vậy đến sáng mai
[00:37.22] Ôm tương tư, nụ cười của ai đó
[00:39.09] Làm con tim ngô nghê
[00:40.17] Như muốn khóc òa
[00:41.22] Vắt tay lên trên trán mơ mộng
[00:43.09] Được đứng bên em
[00:43.88] Trong nắng xuân hồng
[00:44.94] 1 giờ sáng trôi qua trôi nhanh
[00:47.86] Kéo theo ưu phiền miên man
[00:50.26] Âm thầm gieo tên em
[00:51.59] Vẽ lên hi vọng
[00:52.65] Đúng là yêu thật rồi
[00:54.24] Còn không thì hơi phí này
[00:56.29] Cứ thế loanh quanh loanh quanh
[00:57.64] Loanh quanh lật qua lật lại 2 giờ
[01:00.29] Những ngôi sao trên cao
[01:02.15] Là người bạn tâm giao
[01:04.00] Lắng nghe anh luyên thuyên
[01:05.07] Về một tình đầu đẹp tựa chiêm bao
[01:07.72] Có nghe thôi đã thấy ngọt ngào
[01:09.57] Đủ biết anh si mê em nhường nào
[01:11.71] Ít khi văn thơ anh dạt dào
[01:13.29] Bụng đói nhưng vui quên luôn cồn cào
[01:15.42] Nắm đôi tay kiêu sa
[01:17.29] Được một lần không ta
[01:19.03] Nghĩ qua thôi con tim trong anh
[01:20.89] Đập tung lên rung nóc rung nhà
[01:23.02] Hóa ra yêu đơn phương một người
[01:24.61] Hóa ra khi tơ vương một người
[01:26.75] 3 giờ đêm vẫn ngồi cười
[01:29.93] Cứ ôm anh đi ôm anh đi
[01:31.80] Ôm anh đi ôm anh đi
[01:33.65] Ôm trong cơn mơ trong cơn mơ
[01:35.78] Trong cơn mơ trong cơn mơ
[01:37.63] Có thế cũng khiến anh vui điên lên
[01:40.03] Ngỡ như em đang bên
[01:41.37] Chắp bút đôi ba câu thơ
[01:42.97] Ngọt ngào muốn em đặt tên
[01:45.10] Cứ ôm anh đi ôm anh đi
[01:46.95] Ôm anh đi ôm anh đi
[01:48.81] Ôm trong giấc mơ trong cơn mơ
[01:50.67] Trong cơn mơ trong cơn mơ
[01:53.22] Yêu đến vậy thôi
[01:54.82] Phát điên rồi làm sao giờ
[02:00.08] Chịu
[02:01.15] Đêm nay không ngủ
[02:03.01] Tay kê lên tủ
[02:05.14] Miên man anh tranh thủ
[02:06.20] Chơi với suy tư bao nhiêu cho đủ
[02:08.86] Yêu em ngu ngơ mình tôi
[02:10.72] Yêu không quan tâm ngày trôi
[02:12.84] Yêu ánh mắt bờ môi
[02:14.89] Yêu đơn phương vậy thôi
[02:16.49] Lại còn chối
[02:18.09] Con tim thẹn thùng đập lạc lối liên hồi
[02:20.21] Đừng chày cối
[02:21.53] Miệng cười cả ngày vậy là chết toi rồi
[02:23.92] Ngày càng nhiều thêm
[02:25.28] Tình yêu cho em ngày càng nhiều thêm
[02:27.13] Muốn nắm đôi bàn tay đó một lần
[02:28.72] Du dương chìm sâu
[02:29.54] Trong từng câu ca dịu êm
[02:31.13] Những ngôi sao trên cao
[02:32.72] Là người bạn tâm giao
[02:34.57] Lắng nghe anh luyên thuyên
[02:35.91] Về một tình đầu đẹp tựa chiêm bao
[02:38.57] Có nghe thôi đã thấy ngọt ngào
[02:40.42] Đủ biết anh si mê em nhường nào
[02:42.29] Ít khi văn thơ anh dạt dào
[02:44.15] Bụng đói nhưng vui
[02:44.95] Quên luôn cồn cào
[02:46.27] Nắm đôi tay kiêu sa
[02:47.86] Được một lần không ta
[02:49.73] Nghĩ qua thôi con tim trong anh
[02:51.32] Đập tung lên rung nóc rung nhà
[02:53.70] Hóa ra yêu đơn phương một người
[02:55.57] Hóa ra khi tơ vương một người
[02:57.43] 3 giờ đêm vẫn ngồi cười
[03:00.61] Cứ ôm anh đi ôm anh đi
[03:02.74] Ôm anh đi ôm anh đi
[03:04.33] Ôm trong cơn mơ trong cơn mơ
[03:06.45] Trong cơn mơ trong cơn mơ
[03:08.05] Có thế cũng khiến anh vui điên lên
[03:10.43] Ngỡ như em đang bên
[03:12.30] Chắp bút đôi ba câu thơ
[03:13.89] Ngọt ngào muốn em đặt tên
[03:15.76] Cứ ôm anh đi ôm anh đi
[03:17.62] Ôm anh đi ôm anh đi
[03:19.48] Ôm trong giấc mơ trong cơn mơ
[03:21.33] Trong cơn mơ trong cơn mơ
[03:23.46] Yêu đến vậy thôi
[03:25.31] Phát điên rồi làm sao giờ
[03:31.16] Em xinh như một thiên thần
[03:33.29] Như một thiên thần
[03:34.89] Như một thiên thần
[03:36.74] Ngỡ như em là thiên thần
[03:38.59] Em xinh như một thiên thần
[03:40.18] Như một thiên thần
[03:46.03] Em xinh như một thiên thần
[03:48.17] Như một thiên thần
[03:50.02] Như một thiên thần
[03:51.61] Ngỡ như em là thiên thần
[03:53.68] Ngỡ như ngỡ như
[03:55.54] Ngỡ như ngỡ như ngỡ như
[04:00.79] Cứ ôm anh đi ôm anh đi
[04:02.90] Ôm anh đi ôm anh đi
[04:04.78] Ôm trong cơn mơ trong cơn mơ
[04:06.88] Trong cơn mơ trong cơn mơ
[04:08.76] Có thế cũng khiến anh vui điên lên
[04:11.14] Ngỡ như em đang bên
[04:13.00] Chắp bút đôi ba câu thơ
[04:14.33] Ngọt ngào muốn em đặt tên
[04:16.18] Cứ ôm anh đi ôm anh đi
[04:18.05] Ôm anh đi ôm anh đi
[04:19.90] Ôm trong giấc mơ trong cơn mơ
[04:22.03] Trong cơn mơ trong cơn mơ
[04:23.89] Yêu đến vậy thôi
[04:25.75] Phát điên rồi làm sao giờ', 494)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:qYD31k5TVTCQ', 'Lạc Trôi', 'lac troi', 'Sơn Tùng M-TP', 233, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/covers/9/8/98e3677733fe52439823d1b1992d9ae0_1483242323.jpg', '[ar: Sơn Tùng M-TP]
[ti: Lạc Trôi]
[by: ZingMp3 Lyric]
[length: 03:53]
[00:00.00]Bài hát: Lạc Trôi
[00:02.00]Ca sĩ: Sơn Tùng M-TP
[00:04.00]
[00:27.80]Người theo hương hoa 
[00:28.93]Mây mù giăng lối 
[00:30.40]
[00:31.05]Làn sương khói phôi phai 
[00:32.43]Đưa bước ai xa rồi 
[00:34.82]Đơn côi mình ta vấn vương 
[00:37.34]Hồi ức trong men say 
[00:38.77]Chiều mưa buồn 
[00:40.97]Ngăn giọt lệ ngừng 
[00:41.72]Khiến khoé mi sầu bi
[00:43.33]
[00:45.41]Đường xưa nơi cố nhân 
[00:46.74]Từ giã biệt li 
[00:48.63]Cánh hoa rụng rời 
[00:51.40]Phận duyên mong manh 
[00:52.24]Rẽ lối trong mơ ngày tương phùng
[00:55.21]
[00:56.98]Tiếng khóc cuốn theo làn gió bay 
[00:58.54]
[00:59.66]Thuyền ai qua sông 
[01:00.31]Lỡ quên vớt ánh trăng tàn nơi này 
[01:02.21]
[01:04.11]Trống vắng bóng ai dần hao gầy
[01:05.80]
[01:10.30]Lòng ta xin nguyện khắc ghi 
[01:11.80]Trong tim tình nồng mê say 
[01:13.84]Mặc cho tóc mây 
[01:15.10]Vương lên đôi môi cay 
[01:17.12]Bâng khuâng mình ta lạc trôi giữa đời 
[01:20.03]
[01:20.86]Ta lạc trôi giữa trời
[01:23.69]
[01:25.22]Đôi chân lang thang về nơi đâu 
[01:27.17]Bao yêu thương giờ nơi đâu 
[01:28.84]Câu thơ tình xưa vội phai mờ 
[01:30.34]Theo làn sương tan biến trong cõi mơ 
[01:32.06]
[01:32.45]Mưa bụi vương trên làn mi mắt 
[01:34.19]Ngày chia lìa hoa rơi buồn hiu hắt 
[01:35.96]Tiếng đàn ai thêm sầu tương tư 
[01:36.92]Lặng mình trong chiều hoàng hôn 
[01:38.28]Tan vào lời ca 
[01:39.16]
[01:39.58]Lối mòn đường vắng một mình ta 
[01:41.40]Nắng chiều vàng úa nhuộm ngày qua 
[01:43.08]Xin đừng quay lưng xoá 
[01:44.84]Đừng mang câu hẹn ước kia rời xa 
[01:46.62]Yên bình nơi nào đây 
[01:48.54]Chôn vùi theo làn mây
[01:50.24]

[01:53.16]Người theo hương hoa 
[01:54.29]Mây mù giăng lối 
[01:55.76]
[01:56.44]Làn sương khói phôi phai 
[01:57.79]Đưa bước ai xa rồi 
[02:00.27]Đơn côi mình ta vấn vương 
[02:02.71]Hồi ức trong men say 
[02:04.25]Chiều mưa buồn 
[02:06.24]Ngăn giọt lệ ngừng 
[02:07.17]Khiến khoé mi sầu bi
[02:08.88]
[02:10.70]Đường xưa nơi cố nhân 
[02:12.07]Từ giã biệt li 
[02:14.03]Cánh hoa rụng rời 
[02:16.76]Phận duyên mong manh 
[02:17.60]Rẽ lối trong mơ ngày tương phùng
[02:20.57]
[02:22.31]Tiếng khóc cuốn theo làn gió bay 
[02:23.90]
[02:25.02]Thuyền ai qua sông 
[02:25.67]Lỡ quên vớt ánh trăng tàn nơi này 
[02:27.57]
[02:29.45]Trống vắng bóng ai dần hao gầy
[02:31.16]
[02:35.64]Lòng ta xin nguyện khắc ghi 
[02:37.16]Trong tim tình nồng mê say 
[02:39.20]Mặc cho tóc mây 
[02:40.46]Vương lên đôi môi cay 
[02:42.48]Bâng khuâng mình ta lạc trôi giữa đời 
[02:45.39]
[02:46.22]Ta lạc trôi giữa trời
[02:49.05]
[02:53.48]Ta lạc trôi
[02:55.13]
[02:56.02]Lạc trôi 
[02:57.00]Ta lạc trôi giữa đời
[02:59.56]
[03:01.13]Lạc trôi giữa trời
[03:03.64]
[03:21.81]Ta đang lạc nơi nào 
[03:23.74]
[03:29.02]Ta đang lạc nơi nào 
[03:30.96]
[03:34.30]Lối mòn đường vắng một mình ta 
[03:35.62]
[03:36.00]Ta đang lạc nơi nào 
[03:38.02]
[03:41.49]Nắng chiều vàng úa nhuộm ngày qua 
[03:42.82]
[03:43.17]Ta đang lạc nơi nào
[03:45.18]', 794)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:xt25l2WR1SyF', 'Không Phải Dạng Vừa Đâu', 'khong phai dang vua dau', 'Sơn Tùng M-TP', 246, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/covers/8/f/8ffcb87289215a21d169d7a8ec86162e_1414118901.jpg', '[ar: Son Tung M-TP]
[ti: Khong Phai Dang Vua Dau]
[al: mp3.zing.vn]
[by: ZingMp3 Lyric]
[length: 04:06]
[id: ihpuloca]
[00:00.00]Bài Hát: Không Phải Dạng Vừa Đâu
[00:02.00]Ca Sĩ: Sơn Tùng M-TP
[00:04.00]
[00:28.55]Ở sau bờ vai là những sầu lo
[00:31.05]Hình như đuổi theo là những nỗi lo
[00:32.68]Vô tư đi cứ bám vào anh này
[00:33.86]Suy tư anh u não cả tháng ngày
[00:35.11]Không may cho yêu tìm đến phải đúng 
[00:36.43]Thằng điên rồ trên khinh khí cầu 
[00:38.11]Tôi là ai mà ai là tôi
[00:39.17]Mặc kệ cứ bay nào
[00:40.61]Tôi vì ai và ai vì tôi
[00:41.74]Một cuộc sống dâng trào
[00:42.99]Đừng có ngồi đó nhìn ngó gạt đi 
[00:44.18]Sự e dè dang vòng tay 
[00:45.18]Cùng tôi và hát
[00:46.55]Ngân nga câu hát 
[00:47.74]Không phải dạng vừa đâu
[00:49.74]Vừa vừa vừa vừa vừa đâu
[00:52.53]Tôi không phải dạng vừa đâu
[00:54.85]Vừa vừa vừa vừa đâu
[00:57.86]Không phải dạng vừa đâu
[00:59.84]Vừa vừa vừa vừa vừa đâu
[01:02.66]Tôi không phải dạng vừa đâu
[01:04.91]Vừa vừa vừa vừa đâu
[01:08.34]One
[01:09.02]Bắt đầu sự hình thành của ladykillah 
[01:10.83]Two
[01:11.51]Bắt đầu sự hình thành của Youngpilots 
[01:13.27]Three
[01:14.01]Sự kết hợp độc đáo đã tạo nên cái tên 
[01:15.92]M M-TP 
[01:16.95]M M M-TP
[01:18.35]Người ta soi mói bạn đến 
[01:19.35]Từng phút từng giây
[01:20.60]Xung quanh bốn phương 
[01:21.22]ôi sao mỗi tôi khác biệt
[01:23.10]Ờ thì tôi sống 
[01:23.82]Cuộc sống của tôi mà thôi
[01:25.70]Chẳng làm khó ai 
[01:26.26]Hay làm mất thời gian ai
[01:28.20]Còn ngại chi nữa 
[01:28.82]Lại đây cùng tôi và bay
[01:30.70]Âm thanh chốn đây 
[01:31.26]Kéo tôi bước đi mỗi ngày
[01:33.26]Ngày trôi nhanh lắm 
[01:33.83]Chẳng mong chờ ai đợi ai
[01:35.76]Gửi vào gió mây kèm theo ước mơ 
[01:38.17]Cuối con đường kia 
[01:38.99]Thênh thang muôn lối
[01:40.61]Cuối chân trời kia không xa tôi tới
[01:43.11]Có cơn mưa 
[01:43.69]Hoà tan nỗi niềm thế gian
[01:45.76]Có ai đang cùng tôi 
[01:46.57]Sống trọn từng phút giây
[01:48.63]Cuối con đường kia 
[01:49.13]Thênh thang muôn lối
[01:50.63]Cuối chân trời kia không xa tôi tới
[01:53.19]Có cơn mưa hoà tan
[01:54.07]Nỗi niềm thế gian
[01:55.69]Có ai đang cùng tôi 
[01:56.56]Sống trọn từng phút giây
[01:58.86]Sinh ở trên đời
[02:00.05]Tôi đi ngàn phương trời
[02:00.99]Lang thang vi vu cùng mây cùng sao 
[02:02.24]Hoà theo nhiều câu ca tuyệt vời
[02:03.61]Rong chơi tôi quên tháng ngày
[02:04.68]Chuỗi ngày tôi tìm lấy bản thân này
[02:06.36]Cho tôi yên bình thân mình
[02:07.67]Không còn những khoảng cách lặng vô hình
[02:09.16]Đừng nhìn lại nhé
[02:09.79]Để lại muộn phiền ở lại nhé
[02:11.04]Tạm biệt chuyện đời ở lại nhé
[02:12.29]Chỉ còn lại một nụ cười nhé
[02:13.60]Tương lai ca vang mang đi ngày mai
[02:15.35]Ta mang đi ngày mai
[02:16.41]Đưa tay ôm không gian 
[02:17.09]Mênh mông bao la 
[02:17.65]Tương lai là ở ngày mai
[02:18.90]Nhìn cuộc đời nhẹ tênh
[02:20.35]ừ mình sống sao cho nụ cười còn mãi
[02:23.60]Vứt hết đi buồn bực ngoài kia
[02:25.35]Bay lên cùng những vần thơ
[02:27.35]Ta quên đi sự đời
[02:29.98]Cùng một khát khao
[02:32.42]Cùng một ước mơ
[02:34.79]Đằng sau bờ vai 
[02:35.79]Còn lại một lời nói
[02:37.69]Nào ta cùng bước!
[02:39.08]Lời ca cuốn âm thanh
[02:40.38]Từng cơn gió mong manh
[02:41.51]Đưa trái tim tôi hát ca 
[02:42.57]Vang từng ngày
[02:44.07]Bình minh đón tương lai
[02:45.32]Hoàng hôn khép chông gai
[02:46.51]Một nụ cười mới quanh giấc mơ
[02:49.20]Ở phía cuối đoạn đường
[02:50.38]Một ai đó đêm nay
[02:51.51]Dang cánh tay 
[02:52.13]ôm lấy thân tôi vào lòng
[02:54.14]Một điều tôi kiếm tìm
[02:55.32]Một loài hoa khác biệt
[02:56.58]Mùi hương chẳng giống theo một ai
[02:57.82]Tôi là tôi ai là ai tôi mặc kệ thôi
[02:58.82]Cuối con đường kia 
[02:59.76]Thênh thang muôn lối
[03:01.45]Cuối chân trời kia không xa tôi tới
[03:03.95]Có cơn mưa hoà tan 
[03:04.82]Nỗi niềm thế gian
[03:06.51]Có ai đang cùng tôi 
[03:07.32]Sống trọn từng phút giây
[03:09.20]Cuối con đường kia 
[03:09.82]Thênh thang muôn lối
[03:11.45]Cuối chân trời kia không xa tôi tới
[03:14.01]Có cơn mưa hoà tan 
[03:14.82]Nỗi niềm thế gian
[03:16.57]Có ai đang cùng tôi 
[03:17.38]Sống trọn từng phút giây
[03:19.26]Không phải dạng vừa đâu
[03:21.38]Vừa vừa vừa vừa vừa đâu
[03:24.13]Tôi không phải dạng vừa đâu
[03:26.45]Vừa vừa vừa vừa đâu
[03:29.39]Không phải dạng vừa đâu
[03:31.45]Vừa vừa vừa vừa vừa đâu
[03:34.26]Tôi không phải dạng vừa đâu
[03:36.51]Vừa vừa vừa vừa đâu
[03:38.76]', 365)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:um7LDE6F9vJl', 'Chắc Ai Đó Sẽ Về', 'chac ai do se ve', 'Sơn Tùng M-TP', 274, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/covers/8/f/8ffcb87289215a21d169d7a8ec86162e_1414118901.jpg', '[ar: Son Tung M-TP]
[ti: Chac Ai Do Se Ve]
[by: ngoduc99]
[length: 04:34]
[id: ciqnwicc]
[00:00.00]Bài Hát: Chắc ai đó sẽ về
[00:02.00]Ca Sĩ: Sơn Tùng M-TP
[00:04.00]
[00:11.67]Anh tìm nỗi nhớ
[00:14.53]Anh tìm quá khứ
[00:17.16]Nhớ lắm kí ức anh và em
[00:21.40]
[00:22.10]Trả lại anh yêu thương ấy
[00:24.76]xin người hãy về nơi đây
[00:27.34]Bàn tay yếu ớt cố níu em ở lại
[00:32.10]
[00:33.23]Những giọt nước mắt
[00:35.78]Lăn dài trên mi
[00:38.63]Cứ thế anh biết phải làm sao?
[00:42.66]
[00:43.42]Tình yêu trong em đã mất
[00:46.15]phai dần đi theo gió bay
[00:48.83]Còn lại chi nơi đây 
[00:50.63]cô đơn riêng anh
[00:54.38]
[00:54.89]Em đi xa quá
[00:57.02]Em đi xa anh quá
[00:59.64]Có biết không nơi đây 
[01:01.50]anh vẫn đứng đợi một giấc mơ
[01:05.11]Anh chờ đợi một cơn mưa
[01:07.74]sẽ xoá sạch giọt nước mắt
[01:10.41]Ngồi trong đêm bơ vơ 
[01:12.16]anh thấy đau em có biết không?
[01:15.82]
[01:16.24]Em ơi anh nhớ
[01:18.49]Em ơi anh rất nhớ
[01:21.16]Từng câu nói ánh mắt 
[01:22.83]của em giờ này ở nơi đâu?
[01:26.43]Chắc ai đó sẽ sớm quay lại thôi
[01:29.12]Chắc ai đó sẽ sớm quay về thôi
[01:31.80]Cầm bông hoa trên tay nước mắt rơi
[01:36.28]Anh nhớ em
[01:40.51]

[01:51.42]Những giọt nước mắt
[01:53.97]Lăn dài trên mi
[01:56.82]Cứ thế anh biết phải làm sao?
[02:00.85]
[02:01.61]Tình yêu trong em đã mất
[02:04.34]phai dần đi theo gió bay
[02:07.02]Còn lại chi nơi đây 
[02:08.82]cô đơn riêng anh
[02:12.57]
[02:12.88]Em đi xa quá
[02:15.01]Em đi xa anh quá
[02:17.63]Có biết không nơi đây 
[02:19.49]anh vẫn đứng đợi một giấc mơ
[02:23.10]Anh chờ đợi một cơn mưa
[02:25.73]sẽ xoá sạch giọt nước mắt
[02:28.40]Ngồi trong đêm bơ vơ 
[02:30.15]anh thấy đau em có biết không?
[02:33.81]
[02:34.23]Em ơi anh nhớ
[02:36.48]Em ơi anh rất nhớ
[02:39.15]Từng câu nói ánh mắt 
[02:40.82]của em giờ này ở nơi đâu?
[02:44.42]Chắc ai đó sẽ sớm quay lại thôi
[02:47.11]Chắc ai đó sẽ sớm quay về thôi
[02:49.79]Cầm bông hoa trên tay nước mắt rơi
[02:54.27]Anh nhớ em
[02:57.71]
[03:17.62]Anh sẽ mãi nhớ thật nhiều 
[03:20.43]những thứ thuộc về em
[03:22.54]Trong tim này 
[03:23.48]vẫn mãi yêu người 
[03:24.84]riêng em
[03:27.97]Uh oh

[03:30.72]Em đi xa quá
[03:32.92]Em đi xa anh quá
[03:35.53]Có biết không nơi đây 
[03:37.35]anh vẫn đứng đợi một giấc mơ
[03:40.84]Anh chờ đợi một cơn mưa
[03:43.51]sẽ xoá sạch giọt nước mắt
[03:46.19]Ngồi trong đêm bơ vơ 
[03:48.06]anh thấy đau em có biết không?
[03:51.62]
[03:51.98]Em ơi anh nhớ
[03:54.34]Em ơi anh rất nhớ
[03:57.03]Từng câu nói ánh mắt 
[03:58.73]của em giờ này ở nơi đâu?
[04:02.28]Chắc ai đó sẽ sớm quay lại thôi
[04:04.97]Chắc ai đó sẽ sớm quay về thôi
[04:07.65]Cầm bông hoa trên tay nước mắt rơi
[04:12.12]Anh nhớ em
[04:16.29]', 196)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:woP4j7NXbgG', 'Nắng Ấm Xa Dần', 'nang am xa dan', 'Sơn Tùng M-TP', 190, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/covers/b/1/b1c839bd85e6081614cf770278b8f782_1387164001.jpg', '[ar: Son Tung M-TP]
[ti: Nang Am Xa Dan]
[al: mp3.zing.vn]
[by: ZingMp3 Lyric]
[length: 03:10]
[id: ioiwbosw]
[00:00.06]Bài Hát: Nắng Ấm Xa Dần
[00:02.06]Ca Sĩ: Sơn Tùng M-TP
[00:04.06]
[00:08.06]Chẳng phải anh đâu
[00:09.45]
Chuuuu…
Chẳng phải anh đâu

[00:16.50]Nắng ấm xa dần rồi
[00:18.62]Nắng ấm xa dần rồi
[00:20.79]Nắng ấm xa dần bỏ rơi
[00:22.33]Để lại những giấc mơ
[00:23.71]Giữ lại đi, giữ giữ lại đi
[00:25.09]Nắng ấm xa dần rồi
[00:27.09]Nắng ấm xa dần rồi
[00:29.27]Nắng ấm xa dần, xa dần 
[00:31.21]Theo những tiếng cười
[00:32.30]Hãy mang đi 
[00:33.48]Giúp những nỗi buồn

[00:34.90]Theo thời gian 
[00:35.97]Những hạt mưa như nặng thêm
[00:37.81]Xóa hết thương yêu mặn nồng 
[00:39.28]Ngày nào giữa chúng ta

[00:40.79]Anh lục tìm 
[00:41.50]Vẫn cứ mãi lục tìm
[00:42.97]
[00:43.31]Giơ bàn tay cố kìm nén 
[00:45.21]Những cảm xúc
[00:46.29]Vùi mình vào đêm đen 
[00:47.66]Anh chẳng tìm thấy lối ra
[00:49.59]
[00:50.53]Sau lưng là tiếng nói 
[00:52.32]Yêu anh chẳng rời xa anh
[00:54.05]
[00:54.75]Trước mắt anh điều đấy
[00:55.79]Nó dối trá
[00:56.38]Tại sao người vội quên mau

[00:58.05]Là vì em
[00:59.05]Bài ca anh viết sẽ không 
[01:00.88]Được trọn vẹn đâu em

[01:02.32]Bước đi
[01:03.30]Em yêu một ai thật rồi 
[01:05.10]Mãi chẳng là anh đâu

[01:06.41]Vậy thì người cứ bước 
[01:07.50]Đi xa nơi này
[01:08.41]Ánh bình minh 
[01:09.37]Sẽ không còn nơi đây
[01:11.55]Bước đi xa nơi này
[01:12.61]Những lời yêu 
[01:13.55]Sẽ không còn nơi đây
[01:15.24]
[01:15.99]Phải tự đứng lên mà thôi
[01:17.88]Che nhẹ đi những 
[01:18.79]Niềm đau và nỗi buồn
[01:20.21]Xung quanh anh 
[01:20.73]Giờ đây cô đơn 
[01:21.84]Mình anh ôm giấc mơ
[01:23.74]
[01:24.08]Nhìn em bước ra đi xa dần
[01:25.89]
Ehhhhhh….
[01:28.21]Nhìn em bước ra đi xa dần
[01:30.24]
Ehhhhh…
[01:32.49]Nhìn em bước ra đi xa dần
[01:34.47]
Ehhhhh….
[01:36.69]Nhìn em bước ra đi xa dần
[01:38.70]
[01:42.38]Đến rồi lại đi
[01:43.39]Cứ vội vàng đi
[01:44.38]Trao cho anh bao yêu thương 
[01:45.38]Rồi em lại bỏ đi
[01:46.55]Gieo cho anh 
[01:47.23]Bao nhiêu niềm đau
[01:47.79]Rồi em mau 
[01:48.46]Rời bỏ anh xa anh
[01:49.21]Ai quay mặt 
[01:49.55]Lặng lẽ quên mau
[01:50.92]
Uhhh..
[01:51.81]Em yêu quên thật rồi
[01:53.27]
Uhhh..
[01:53.97]Chẳng một lời 
[01:54.55]Chia li quên rồi
[01:55.68]Em yêu quên rồi quên rồi

[01:57.43]Vậy thì người cứ bước 
[01:58.52]Đi xa nơi này
[01:59.43]Ánh bình minh 
[02:00.39]Sẽ không còn nơi đây
[02:02.57]Bước đi xa nơi này
[02:03.63]Những lời yêu 
[02:04.57]Sẽ không còn nơi đây
[02:06.26]
[02:07.01]Phải tự đứng lên mà thôi
[02:08.90]Che nhẹ đi những 
[02:09.81]Niềm đau và nỗi buồn
[02:11.23]Xung quanh anh 
[02:11.75]Giờ đây cô đơn 
[02:12.86]Mình anh ôm giấc mơ

[02:14.42]Vậy thì người cứ bước 
[02:15.51]Đi xa nơi này
[02:16.42]Ánh bình minh 
[02:17.38]Sẽ không còn nơi đây
[02:19.56]Bước đi xa nơi này
[02:20.62]Những lời yêu 
[02:21.56]Sẽ không còn nơi đây
[02:23.25]
[02:24.00]Phải tự đứng lên mà thôi
[02:25.89]Che nhẹ đi những 
[02:26.80]Niềm đau và nỗi buồn
[02:28.22]Xung quanh anh 
[02:28.74]Giờ đây cô đơn 
[02:29.85]Mình anh ôm giấc mơ
[02:31.75]
[02:32.09]Nhìn em bước ra đi xa dần
[02:33.90]

Ehhhhhh.
[02:36.25]Nhìn em bước ra đi xa dần
[02:38.13]
Ehhhhhh.
[02:39.88]Nhìn em nhìn em bước đi
[02:42.30]
Ehhhhhh.
[02:44.64]Nhìn em bước ra đi xa dần
[02:46.64]
Ehhhhhh.
[02:48.90]Nhìn em bước ra đi xa dần

[02:49.58]Nắng ấm xa dần rồi

[02:51.47]Nắng ấm xa dần rồi
[02:53.66]Nắng ấm xa dần bỏ rơi
[02:55.15]Để lại những giấc mơ
[02:56.61]Giữ lại đi giữ giữ lại đi
[02:58.02]Nắng ấm xa dần rồi
[03:00.08]Nắng ấm xa dần rồi
[03:02.15]Nắng ấm xa dần xa dần
[03:04.13]Theo những tiếng cười
[03:05.13]Hãy mang đi 
[03:06.35]Giúp những nỗi buồn
[03:07.89]', 695)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:XYt1PjovFybk', 'Khuôn Mặt Đáng Thương', 'khuon mat dang thuong', 'Sơn Tùng M-TP', 271, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/2/e/6/c/2e6cc492ff910c1804673bc5a21be89e.jpg', '[ar: Son Tung M-TP]
[ti: Khuon Mat Dang Thuong]
[al: mp3.zing.vn]
[by: wanwan1910]
[length: 04:30]
[id: lzphnvca]
[00:00.00]Bài Hát: Khuôn Mặt Đáng Thương
[00:02.00]Ca Sĩ: Sơn Tùng M-TP
[00:04.00]
[00:16.52]Đôi môi này đi về ai
[00:18.36]
[00:19.41]Ok đêm nay sẽ là 1 đêm thật tuyệt
[00:21.24]
[00:21.49]Yes, Sir-Sơn Tùng M.T.P 
[00:25.34]Thơm mơ màng
[00:26.44]mùi hương theo làn gió 
[00:27.74]cuốn em đi 
[00:28.99]Kề bên vai ai từng đêm
[00:30.58]Yeah yeah yeah Kể cả giờ này anh
[00:33.18]Yeah yeah yeah Ai giấu em đi
[00:35.56]Mang em về liệu rằng em 
[00:37.31]còn nhớ tiếng yêu không
[00:38.87]ở đây ngay phía sau này
[00:40.74]Yeah yeah yeah Lại một mình anh thôi 
[00:43.35]Níu giữ lại những âm thầm và nhớ

[00:45.49]Đừng khiến đôi môi dịu êm 
[00:47.18]phải chịu ngàn giọt nước mắt đắng cay
[00:50.17]Hạt mưa rơi, tình chơi vơi
[00:52.16]giấc mơ này biết đi về đâu
[00:55.27]Bài hát khi xưa anh viết 
[00:57.13]bây giờ phải đặt dấu chấm hết thôi 
[01:00.24]Cuối con đường
[01:02.03]Anh xin một điều ước 
[01:04.31]cho em bình yên vững bước chân

[01:05.12]Cánh hoa úa tàn
[01:06.58]bước chân khép màn
[01:07.69]bóng ai xa ngút ngàn
[01:09.03]nước mắt rơi ướt tràn
[01:10.18]Sầu làn mi khép tình buồn 
[01:12.19]nay em biết đi về chốn đâu
[01:13.90]Khuôn mặt đáng thương
[01:15.36]Tấm thân héo mòn
[01:16.61]nỗi đau có còn
[01:17.69]đắng cay em đã chọn
[01:19.19]chính em đã chọn
[01:20.07]Rời làn hơi ấm tình này 
[01:22.26]không thấm biết ai còn nhớ ai
[01:24.26]khuôn mặt đáng thương
[01:25.50]cánh hoa úa tàn

[01:26.53]Em giờ không phải em mà 
[01:27.58]Anh thì không phải em mà 
[01:28.71]Khuôn mặt em yêu vẫn thế 
[01:29.63]nhưng giờ cảm xúc đã quá nhạt phai mà
[01:31.20]Nơi đâu cho anh cảm xúc thăng hoa
[01:32.44]Nơi đâu anh nhìn khoảng cách đôi ta
[01:33.67]Không gian nơi không chắc 
[01:34.31]cho em nhìn ra một con người 
[01:35.61]đến từ hôm qua
[01:36.48]Em giờ đang ở nơi nào
[01:37.42]Cứ xoá em đi đến nơi nào
[01:38.66]Bên cạnh ai kia ở nơi nào
[01:39.90]Đôi chân em lang thang đến nơi nào

[01:41.39]Nói cho anh nghe 
[01:42.70]Nói cho anh nghe
[01:43.93]Bước đi vội vàng 
[01:45.12]Em đang đi bên ai đó
[01:46.49]Khiến đôi môi dịu êm 
[01:47.73]phải chịu ngàn giọt nước mắt đắng cay
[01:50.54]Hạt mưa rơi, tình chơi vơi
[01:52.60]giấc mơ này biết đi về đâu
[01:55.96]Bài hát khi xưa anh viết 
[01:57.84]bây giờ phải đặt dấu chấm hết thôi 
[02:00.57]Cuối con đường
[02:02.61]Anh xin một điều ước 
[02:04.61]cho em bình yên vững bước chân

[02:06.56]Cánh hoa úa tàn
[02:07.38]bước chân khép màn
[02:08.46]bóng ai xa ngút ngàn
[02:09.82]nước mắt rơi ướt tràn
[02:10.76]Sầu làn mi khép tình buồn 
[02:12.88]nay em biết đi về chốn đâu
[02:14.96]Khuôn mặt đáng thương
[02:16.24]Tấm thân héo mòn
[02:17.49]nỗi đau có còn
[02:18.48]đắng cay em đã chọn
[02:19.99]chính em đã chọn
[02:21.19]Rời làn hơi ấm tình này 
[02:23.19]không thấm biết ai còn nhớ ai
[02:25.18]khuôn mặt đáng thương
[02:26.02]Cánh hoa úa tàn
[02:26.25]
[02:26.30]cánh hoa úa tàn
[02:26.93]
[02:30.77]Cánh hoa úa tàn
[02:32.27]
[02:34.22]
[02:36.07]Cánh hoa úa tàn
[02:37.72]
[02:40.98]Cánh hoa úa tàn
[02:43.08]
[02:46.77]Yeah Yeah La La Uh Oh
[02:54.34]

[03:07.03]Ngày đó, ánh mắt ấy sẽ mãi mãi
[03:09.23]không bên em nữa đâu 
[03:11.84]Làn gió, ánh mắt ấy sẽ mãi mãi 
[03:14.26]không bên em gửi trao
[03:16.82]Lời đó, ánh mắt ấy sẽ mãi mãi 
[03:19.18]không bên em nữa đâu
[03:21.93]Ở đó, chắc có nhớ 
[03:23.23]có khóc vấn vương vì ai
[03:27.04]Người đó, ánh mắt ấy sẽ mãi mãi 
[03:29.21]không bên em nữa đâu 
[03:31.95]Làn gió, ánh mắt ấy sẽ mãi mãi 
[03:34.32]không bên em gửi trao
[03:37.06]Lời đó, ánh mắt ấy sẽ mãi vẫn
[03:39.28]không bên em nữa đâu
[03:42.03]Ở đó, chắc có nhớ 
[03:43.40]có khóc vấn vương vì ai
[03:47.34]
[03:51.89]Cánh hoa úa tàn
[03:53.79]
[03:57.23]Khuôn mặt đáng thương
[04:01.57]
[04:03.52]Oh oh yeah yeah Yeah
[04:12.43]', 425)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:wzGj8kLgRLRf', 'Cơn Mưa Ngang Qua', 'con mua ngang qua', 'Sơn Tùng M-TP', 234, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/covers/5/b/5baa8ca8d0dd072524767a4aff6d400c_1349715671.jpg', '[ar: M-TP]
[ti: Con Mua Ngang Qua]
[al: mp3.zing.vn]
[by: ZingMp3 Lyric]
[length: 03:54]
[id: qqsonhsw]
[00:00.00]Bài Hát: Cơn Mưa Ngang Qua
[00:02.00]Ca Sĩ: Sơn Tùng M-TP
[00:04.00]
[00:12.70]Cơn mưa ngang qua
[00:14.70]Cơn mưa đi ngang qua
[00:16.69]Đừng làm rơi thêm thêm thêm
[00:18.64]Từng giọt lệ đâu đây
[00:19.91]Bao câu ca anh tặng em
[00:23.31]Tình yêu em mang 
[00:24.82]Cuốn mất đi bao nhiêu câu ca
[00:26.82]Và còn lại đây 
[00:28.07]Cơn mưa đau thương trong màn đêm
[00:31.40]Phải lẻ loi
[00:32.09]Gồng mình bước qua niềm đau 
[00:33.84]Khi em rời xa
[00:34.96]Cơn mưa rồi thêm
[00:36.12]Lại thêm lại thêm
[00:36.93]Xé đi không gian ngập tràn nụ cười
[00:39.08]Nhìn lại nơi đây
[00:40.15]Thấy đêm giờ chìm xuống dưới
[00:42.29]Hố sâu vì em
[00:43.30]Chính em đã đổi thay
[00:45.20]Và đôi bàn tay ấm êm 
[00:46.58]Ngày nào còn lại giữ
[00:49.25]Vì em, vì em
[00:51.49]Vì em đã xa rồi 
[00:53.37]Tình anh chìm trong màn đêm
[00:55.46]Là vì em đã quên rồi 
[00:57.47]Tình anh chỉ như giấc mơ
[00:59.34]Em bước đi rồi
[01:00.59]ôi bao cơn mưa
[01:01.29]Em bước đi rồi
[01:02.53]Xin hãy xua tan đi em
[01:03.91]Bóng dáng hình em
[01:05.35]Em đã mãi rời xa
[01:08.14]My girl
[01:08.70]Em vội quên đi bao nhiêu
[01:09.99]My girl
[01:10.74]Em vội quên đi bao lâu
[01:12.01]My girl
[01:12.76]Em vội quên đi cuộc tình 
[01:14.11]Mà anh trao em
[01:14.98]Thôi thôi em đi đi đã hết rồi
[01:16.89]Em vội quên đi bao nhiêu 
[01:18.12]My girl
[01:18.94]Em vội quên đi bao lâu
[01:20.17]My girl 
[01:20.98]Em vội quên đi cuộc tình 
[01:22.32]Em quên quên quên quên quên
[01:25.32]
[01:26.81]Cơn mưa ngang qua mang em đi xa
[01:28.81]Cơn mưa ngang qua khiến em nhạt nhòa
[01:30.86]Chẳng một lời chào 
[01:31.80]Người vội rời bỏ đi không chia li 
[01:33.30]Cho con tim anh thêm bao yếu mềm
[01:34.86]Cơn mưa ngang qua 
[01:35.36]Cuốn đi bao yêu thương
[01:36.91]Cơn mưa ngang qua 
[01:37.33]Khiến con tim mất phương hướng
[01:38.90]Cơn mưa kia nặng hạt 
[01:39.65]ôi mưa thêm nặng hạt
[01:40.73]Em đã rời xa đôi bàn tay 
[01:41.98]Trong mưa bỏ anh em đi 
[01:43.99]Em đi rời xa bên tôi người ơi
[01:46.18]Và buông lơi giấc mơ em
[01:47.57]Cho em cho con tim tôi đau biết mấy
[01:50.16]Thôi cũng đã đến hồi kết 
[01:51.11]Thật rồi mà người thôi   
[01:52.30]Cũng đã đến hồi kết 
[01:53.17]đừng nhìn làm gì
[01:54.30]Anh sẽ quên đi một ai ai ai 
[01:56.43]Và rồi làm ngơ ngơ ngơ
[01:58.74]Vì em đã xa rồi 
[02:00.48]Tình anh chìm trong màn đêm
[02:02.50]Là vì em đã quên rồi 
[02:04.56]Tình anh chỉ như giấc mơ
[02:06.44]Em bước đi rồi
[02:07.59]ôi bao cơn mưa
[02:08.33]Em bước đi rồi
[02:09.72]Xin hãy xua tan đi em
[02:10.97]Bóng dáng hình em
[02:12.46]Em đã mãi rời xa
[02:15.09]My girl
[02:15.83]Em vội quên đi bao nhiêu
[02:17.04]My girl
[02:17.79]Em vội quên đi bao lâu
[02:19.10]My girl
[02:19.80]Em vội quên đi cuộc tình 
[02:21.24]Mà anh trao em
[02:21.98]Thôi thôi em đi đi đã hết rồi
[02:24.03]Em vội quên đi bao nhiêu 
[02:25.23]My girl
[02:26.00]Em vội quên đi bao lâu
[02:27.24]My girl 
[02:28.00]Em vội quên đi cuộc tình 
[02:29.34]Em quên quên quên quên quên
[02:32.09]
[02:32.97]Và rồi em đi qua bước qua
[02:37.30]Ở lại chốn đây với bao u buồn
[02:41.09]Để rồi tháng trôi qua
[02:43.33]Rồi năm trôi qua,thoáng qua
[02:46.96]
[02:48.05]Nụ cười em đang ở đâu người ơi
[02:51.91]Và bờ môi e đang ở đâu a tìm
[02:55.52]Lục tìm mà không thấy nụ cười em người 
[02:57.86]Hãy nói trả lời đi
[02:59.42]Vì sao em đi đi quên đi 
[03:01.65]Bao nhiêu giấc mơ bên anh xưa kia 
[03:03.58]Cơn mưa vẫn rơi rơi rơi
[03:05.50]Cơn mưa vẫn rơi rơi rơi 
[03:07.50]Cơn mưa vẫn rơi rơi 
[03:09.77]Cơn mưa vẫn rơi rơi rơi
[03:11.60]Cơn mưa vẫn rơi rơi 
[03:13.72]Cơn mưa vẫn rơi rơi rơi 
[03:15.69]Cơn mưa vẫn rơi rơi 
[03:17.69]Cơn mưa vẫn rơi rơi rơi
[03:20.08]My girl
[03:20.95]Em vội quên đi bao nhiêu
[03:22.14]My girl
[03:22.94]Em vội quên đi bao lâu
[03:24.13]My girl
[03:25.00]Em vội quên đi cuộc tình 
[03:26.33]Mà anh trao em
[03:27.11]Thôi thôi em đi đi đã hết rồi
[03:29.10]Em vội quên đi bao nhiêu 
[03:30.23]My girl
[03:31.04]Em vội quên đi bao lâu
[03:32.36]My girl 
[03:33.10]Em vội quên đi cuộc tình 
[03:34.54]Em quên quên quên quên quên
[03:36.53]', 882)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:VNRGA9G6YOst', 'Cho Tôi Lang Thang', 'cho toi lang thang', 'Ngọt, Đen', 259, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/covers/4/3/43e9ff1a1a86efd042cf639bd50fb9e0_1488961974.jpg', '[ar: Ngọt, Đen]
[ti: Cho Tôi Lang Thang]
[al: Cho Tôi Lang Thang (Single)]
[by: ZingMp3 Lyric]
[length: 04:18]
[00:00.00]Bài Hát: Cho Tôi Lang Thang
[00:02.00]Ca Sĩ: Ngọt, Đen
[00:04.00]
[00:21.31]Ngày lang thang
[00:22.31]Trong cơn mơ 
[00:22.77]Không veston không cà vạt 
[00:24.39]Không nhiều Than Thở 
[00:25.14]Dẫu mình hay mơ về Đà Lạt 
[00:26.89]Đi cho vai áo bạc màu 
[00:28.57]Những hành trình dài 
[00:29.51]Để khi ta thay áo mới 
[00:30.64]Nhìn lại đã thành hình hài 
[00:32.13]Không có đường nào khó
[00:33.50]Chỉ có chân ngại lối xa 
[00:34.87]Cuối con đường dần ló mặt trời lên 
[00:36.50]Xua màn tối qua 
[00:37.62]Nắng ươm ta màu đời
[00:38.56]Mai ta mọc thành cây cao 
[00:40.00]Và khi gió ru ca cội nguồn 
[00:41.18]Ôi mình hôm qua đây sao
[00:42.68]Vẫn bước đi cười trên môi 
[00:44.18]Làm giấy thông hành 
[00:45.17]Gói gém câu ca vào trong hành trang 
[00:46.48]Vì thiếu nó thấy không đành 
[00:47.80]Chất thêm chút bình yên
[00:49.17]Gói lại bằng tình duyên 
[00:50.47]Ta đi tìm mắt bão đời 
[00:51.47]Vì trong cơn bão mới nhẹ lòng mình yên 
[00:53.53]Cho riêng ta lang thang thôi 
[00:54.78]Để nhạc đời không lạc điệu 
[00:56.28]Lòng người sâu vời 
[00:57.22]Như bầu trời ai đo đạc liệu
[00:59.21]Xa bao nhiêu
[01:00.15]Giữa con đường vắng nắng tan hoang 
[01:01.53]Ngày nào còn khát 
[01:02.65]Thì ngày đó ta còn lang thang
[01:04.59]
[01:05.27]Nơi nhân gian sum vầy
[01:07.95]Em có nghe thấy
[01:10.51]Tiếng réo gọi tâm hồn
[01:13.32]Ngân vang đâu đây
[01:15.94]Nơi nhân gian sum vầy
[01:18.62]Em có nghe thấy
[01:21.18]Tiếng réo gọi tâm hồn
[01:23.80]Ngân vang đâu đây
[01:25.46]Nếu em hỏi cho đến bây giờ 
[01:26.46]Tại sao anh vẫn lông bông
[01:28.02]Vì anh muốn khoác bụi lên mình 
[01:29.21]Cho tâm hồn đỡ tồng ngồng
[01:30.64]Vì cuộc đời công bằng
[01:31.89]Cũng chỉ một lần để sống 
[01:33.20]Người ta mơ nhà mà cửa
[01:34.49]Còn anh mơ núi mơ sông 
[01:35.98]Rồi cũng sẽ đến một ngày 
[01:37.01]Tóc như mây mờ trắng xoá 
[01:38.70]Rồi sẽ tiếc khi trở về già 
[01:39.77]Tuổi đời bây giờ ngắn quá 
[01:41.26]Thanh xuân ngủ vùi chăn ấm
[01:42.57]Đời như mãi mùa đông 
[01:43.95]Nên anh trải đời trong nắng gió 
[01:45.13]Như con thú hoang sổ lồng 
[01:46.57]Đường nào đi được hết
[01:47.87]Trán nào còn nhăn mãi
[01:49.25]Muốn cùng em hút chung điếu thuốc 
[01:50.43]Để khói mù cả Căng Chải 
[01:51.87]Vì đất nước mình còn lạ
[01:53.30]Cần chi đâu nước ngoài 
[01:54.55]Đặt chân lên tất cả mọi miền 
[01:55.74]Là ước mơ ta ước hoài 
[01:57.29]Chẳng cho mình là lãng tử
[01:58.60]Đã đi được mấy đâu 
[01:59.92]Chẳng cho mình là nghệ sĩ
[02:01.23]Đã viết được mấy câu 
[02:02.60]Chẳng tự do đến mức 
[02:03.85]Buông lơi như dsk 
[02:05.29]Nhưng nếu một ngày đôi chân ngừng lại 
[02:06.41]Thì thà anh đi chết ngay
[02:07.95]
[02:09.29]Nơi nhân gian sum vầy
[02:11.97]Em có nghe thấy
[02:14.53]Tiếng réo gọi tâm hồn
[02:17.27]Ngân vang đâu đây
[02:19.89]Nơi nhân gian sum vầy
[02:22.54]Em có nghe thấy
[02:25.22]Tiếng réo gọi tâm hồn
[02:27.91]Ngân vang đâu đây
[02:29.65]Đi với anh đến cùng trời cuối đất 
[02:32.10]Và để nếm yêu thương 
[02:33.10]Không dùng lời muối mật 
[02:34.60]Mình sống chẳng tự cao
[02:35.91]Hay việc gì phải cúi mặt 
[02:37.48]Đi đi em còn do dự trời tối mất 
[02:40.10]Và anh đưa em đi 
[02:41.17]Đến nơi ít người qua 
[02:42.85]Nơi vầng trăng mỏng 
[02:43.79]Cong veo tít trời xa 
[02:45.54]Nơi những vì sao 
[02:46.47]Chẳng phải nép sau nóc nhà và
[02:48.08]Nơi đặt lưng xuống 
[02:49.05]Đầu chẳng nặng chuyện ngày qua 
[02:50.54]Có đường cong như mi em, 
[02:51.92]Có đường thẳng như sống mũi 
[02:53.23]Có đường ngắn có đường dài
[02:54.54]Đi về đâu ai đoán nổi 
[02:55.92]Anh sẽ đưa em qua hết
[02:57.03]Cho tóc bạc đi vì bụi 
[02:58.41]Vì có gian khó có mệt nhoài 
[02:59.78]Ta mới tạc ghi vào cội 
[03:01.47]Đi với anh
[03:02.41]Và tạm quên chuyện tương lai 
[03:04.02]Giờ đây quan trọng với anh là
[03:05.54]Tóc em còn đương dài 
[03:06.84]Môi em còn nồng thắm
[03:08.10]Và mắt em như sương mai 
[03:09.91]Đi thật xa để khi trở về
[03:10.96]Biết chắc mình thương ai
[03:13.08]Cho tôi đi theo với
[03:16.02]Nơi em đi về
[03:18.64]Về nơi đẹp trời hơn
[03:21.26]Tràn đầy đam mê
[03:23.94]Tôi quên đi năm tháng
[03:26.56]Yêu thương không còn
[03:29.24]Cần thêm mùi rượu vang
[03:31.93]Và đồ ăn ngon
[03:33.30]Vì ngày lang thang vẫn còn dài 
[03:34.48]Nên ta không kéo ga thật sâu 
[03:36.24]
[03:37.25]Vì đôi chân và mặt đường 
[03:38.43]Vốn dĩ nó không xa được nhau 
[03:41.29]Vì lá thì rơi sương thì đọng
[03:42.59]Đá bất động người thì đi 
[03:45.15]Nên tóc còn xanh máu còn nóng
[03:46.53]Nằm một chỗ để làm chi
[03:48.21]
[03:49.21]Vì lá thì rơi sương thì đọng
[03:50.51]Đá bất động người thì đi 
[03:52.14]
[03:53.14]Nên tóc còn xanh máu còn nóng
[03:54.44]Nằm một chỗ để làm chi
[03:56.01]
[03:57.16]Và nằm một chỗ để làm chi
[03:58.72]
[03:59.96]Và nằm một chỗ để làm chi
[04:02.02]', 716)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:qWxRnKgOaDty', 'MỘT NGÀY NÀO ĐÓ', 'mot ngay nao do', 'Nguyễn Hoàng Dũng, Nguyễn Đức Cường', 259, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/2/8/2/a/282aaf883d9b13f918e3433d0fd92a20.jpg', '[00:13.71] Một ngày nào đó
[00:15.31] Rồi mình lại đi với nhau
[00:17.47] Những trưa mây bay ngang đầu
[00:20.63] Là ngày chẳng còn thời gian
[00:22.77] Để tiêu pha nữa
[00:24.33] Chẳng tiếc những thứ hai ta chưa
[00:27.54] Một ngày nào đó
[00:29.14] Khi cơn mơ ngã ngũ rồi
[00:31.25] Sướng vui đi không khứ hồi
[00:34.48] Là ngày anh mang hình dung
[00:36.58] Mà anh yêu dấu
[00:38.21] Về đúng nơi anh mong như ngày đầu
[00:42.43] Anh như đang cô đơn trên con tàu
[00:44.95] Đếm những thứ vốn trôi qua rất mau
[00:48.67] Lòng mình còn thổn thức cuối chương một
[00:51.86] Vẫn đang chờ ngày mai
[00:55.06] Nhớ những lúc lái xe trong mưa chiều
[00:59.30] Nhớ ký ức năm xưa ta luôn yêu
[01:02.85] Cùng một ngàn lời hứa vẫn đang nợ
[01:05.53] Chắc nên hẹn ngày mai
[01:07.66] Ta chưa thể dừng lại
[01:10.30] Đời cứ cuốn ta hoài
[01:12.93] Ngày ngày một nhanh hơn
[01:16.67] Anh chưa đến không phải
[01:18.27] Vì anh đã quên
[01:19.87] Chờ thêm một chút thôi
[01:22.95] Một ngày nào đó
[01:24.54] Rồi mình lại đi với nhau
[01:26.68] Những trưa mây bay ngang đầu
[01:29.85] Là ngày chẳng còn thời gian
[01:31.98] Để tiêu pha nữa
[01:33.58] Chẳng tiếc những thứ hai ta chưa
[01:36.79] Một ngày nào đó
[01:38.38] Khi cơn mơ ngã ngũ rồi
[01:40.53] Sướng vui đi không khứ hồi
[01:43.70] Là ngày anh mang hình dung
[01:45.81] Mà anh yêu dấu
[01:47.95] Về đúng nơi anh mong như ngày đầu
[01:51.89] Anh chưa từng quên giấc mơ
[01:53.21] Trước khi chúng ta đã bị thấm đòn đời
[01:55.33] Kéo ga thật sâu
[01:56.40] Đến nơi thật xa
[01:57.19] Có khi chỉ để ngắm vòm trời
[01:58.78] Rất nhiều thời gian
[01:59.85] Rất nhiều niềm vui
[02:00.64] Hát ca chẳng cần lắm đồng lời
[02:02.23] Ánh đèn đường xa lướt qua thật mau
[02:04.09] Chỉ còn lại những chấm tròn rồi
[02:05.96] Những ngày đó thật là hoang dại
[02:07.82] Thật là cuồng điên
[02:08.52] Và cũng đói thường xuyên
[02:10.12] Anh luôn đi tìm ngày bình yên
[02:11.31] Kobukovu không vui không buồn
[02:12.48] Ta không quên những khi ta đã từng
[02:14.90] Gặp người lành người khó người đau
[02:16.48] Lao phăng qua nắng mưa bão bùng
[02:18.07] Và để biết sau cùng chỉ muốn cười sâu
[02:19.62] Chúng ta đều được cấu thành
[02:20.95] Bởi thật nhiều các nguyên tố
[02:22.80] Và hiện tại là kết quả
[02:24.12] Của hằng hà sa số những nguyên nhân
[02:26.27] Thế nên mỗi quyết định mỗi bước chân
[02:27.87] Đều tạo ra vô vàn những biến số
[02:29.45] Đã phải học đủ thứ ở trong đời
[02:31.32] Giờ phải học cả cách để yên tâm
[02:33.18] Nếu có một ngày nào mà thâm tâm
[02:34.76] Mình có thể được tự do bay lượn
[02:36.36] Là ngày ta trả lại thế giới này
[02:38.22] Tất cả những điều còn đang vay mượn
[02:40.08] Và hoàn thiện dáng dấp của khu vườn
[02:41.67] Trong tâm trí mà anh vẫn thường mơ
[02:43.53] Một ngày nào đó ta thấu tỏ chính mình
[02:45.14] Đến tận cùng kẽ tóc và đường tơ
[02:47.52] Đời cứ cuốn ta hoài
[02:50.97] Ngày ngày một nhanh hơn
[02:54.14] Anh chưa đến không phải vì anh đã quên
[02:57.34] Chờ thêm chút thôi
[03:00.55] Một ngày nào đó
[03:02.19] Rồi mình lại đi với nhau
[03:04.27] Những trưa mây bay ngang đầu
[03:06.96] Là ngày chẳng còn thời gian
[03:09.58] Để tiêu pha nữa
[03:11.18] Chẳng tiếc những thứ hai ta chưa
[03:14.38] Một ngày nào đó
[03:16.00] Khi cơn mơ ngã ngũ rồi
[03:18.09] Sướng vui đi không khứ hồi
[03:20.78] Là ngày anh mang hình dung
[03:23.41] Mà anh yêu dấu
[03:25.01] Về đúng nơi anh mong như ngày đầu
[03:28.06] Một ngày nào đó
[03:29.64] Rồi mình lại đi với nhau
[03:31.75] Những trưa mây bay ngang đầu
[03:34.99] Là ngày chẳng còn thời gian
[03:37.60] Để tiêu pha nữa
[03:39.19] Chẳng tiếc những thứ hai ta chưa
[03:42.38] Một ngày nào đó
[03:43.44] Khi cơn mơ ngã ngũ rồi
[03:46.10] Sướng vui đi không khứ hồi
[03:49.28] Là ngày anh mang hình dung
[03:51.43] Mà anh yêu dấu
[03:53.04] Về đúng nơi anh mong như ngày đầu
[03:59.03] Lâu rồi không ai viết thư
[04:00.36] Tự viết cho mình một bức
[04:02.22] Người gửi là mình hôm nay
[04:03.83] Người nhận là mình hôm trước
[04:05.68] Phóng xe ra ngay bưu điện
[04:07.28] Phong bao rồi lại dán tem
[04:09.14] Hôm sau người ta thông báo
[04:10.74] Hoàn đơn quá khó để tìm', 652)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:Aj9nmuJPIJiJ', 'Điều Phi Thường Nhỏ Bé', 'dieu phi thuong nho be', 'DTAP', 216, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/2/2/9/9/2299283c8a55165d3ed4958562990586.jpg', '[00:09.26] Đừng gọi tôi với những thứ không hay
[00:13.57] Vì tôi không như bao nhiêu điều bạn thấy
[00:18.71] Cuộc đời ta vốn chẳng giống nhau đâu
[00:23.11] Ai cũng mơ mộng
[00:24.72] Tôi cũng mơ mộng nhiều
[00:27.88] So u
[00:32.66] Từng bước trong những cơn mưa
[00:35.44] Không nhìn thấy lối
[00:37.84] Ngày mai nắng hong khô đi giọt nước mắt
[00:42.62] Tôi sẽ luôn đi tìm
[00:44.64] Khao khát riêng cho mình
[00:49.80] Vậy mình là ai trong bao nhiêu đây
[00:52.22] Hạt cát kia giữa sa mạc
[00:54.21] Rồi một ngày ta tan biến
[00:56.20] Như bao tinh tú kia giữa thiên hà
[00:59.00] Ai ơi ta có bao lâu
[01:01.39] Vậy sao không phải giờ đây
[01:04.17] Một lần ta là ta
[01:06.56] Dám bước theo ước mơ này
[01:09.34] Điều tuy rất phi thường
[01:11.34] Nhưng thật ra rất nhỏ bé
[01:13.73] Điều nhỏ bé phi thường
[01:15.73] Luôn nằm ở trong ta
[01:18.91] Đợi cho đến khi nào
[01:20.49] Vì đời nhanh như chớp mắt
[01:22.89] Đợi cho đến khi nào
[01:25.30] Mà chẳng phải hôm nay
[01:38.36] Lao vào đời và kiếm cơm
[01:39.91] Lao vào đời và kiếm cơm
[01:42.52] Mong là lo được cho no bụng
[01:44.91] Và cũng mong để lại
[01:45.71] Cho đời chút tiếng thơm
[01:46.78] Vui như cánh chim nhỏ
[01:47.84] Tập bay trong gian nan
[01:49.44] Dù có đôi khi hoang dại
[01:50.51] Nhưng mà không hoang mang
[01:52.05] Underground từ Nam
[01:52.85] Tới Trung cho tới Bắc
[01:53.83] Và bởi vì có nhạc Rap
[01:55.17] Mà cuộc đời sang trang
[01:57.25] Đi trên chính đôi chân
[01:58.04] Lao động bằng chính đôi tay
[01:59.36] Viết nhạc bằng chính cái đầu
[02:00.43] Đám ong thợ này rất hăng say
[02:01.75] Lấy vần thơ lấy nhịp beat
[02:03.10] Là các dụng cụ
[02:04.15] Âm nhạc là liều thuốc
[02:05.21] Không hề có tác dụng phụ
[02:06.54] Không hề nao núng
[02:07.60] Và cũng không hề chần chừ
[02:08.93] Cuộc đời gọi ta
[02:09.99] Ta xin làm người phục vụ
[02:11.60] Đi giữa trời mênh mông
[02:12.64] Thấy trong lòng sao bí quá
[02:13.71] Ta muốn làm người bình thường
[02:14.78] Cho cái đời thêm chiếc lá
[02:16.10] Ta không ngại đối mặt
[02:17.17] Với những lần vấp ngã và đớn đau
[02:18.49] Ngã chỗ nào đứng lên chỗ đó
[02:19.56] Như vậy khiến ta lớn mau
[02:20.89] Vậy mình là ai trong bao nhiêu đây
[02:23.28] Hạt cát kia giữa sa mạc
[02:25.56] Rồi một ngày ta tan biến
[02:27.55] Như bao tinh tú kia giữa thiên hà
[02:30.36] Ai ơi ta có bao lâu
[02:32.73] Vậy sao không phải giờ đây
[02:35.14] Một lần ta là ta
[02:37.52] Dám bước theo ước mơ này
[02:49.47] Vậy mình là ai trong bao nhiêu đây
[02:51.89] Hạt cát kia giữa sa mạc
[02:54.27] Rồi một ngày ta tan biến
[02:56.27] Như bao tinh tú kia giữa thiên hà
[02:59.07] Ai ơi ta có bao lâu
[03:01.45] Vậy sao không phải giờ đây
[03:04.24] Một lần ta là ta
[03:06.65] Dám bước theo ước mơ này
[03:09.02] Điều tuy rất phi thường
[03:11.00] Nhưng thật ra rất nhỏ bé
[03:13.78] Điều nhỏ bé phi thường
[03:15.39] Luôn nằm ở trong ta
[03:18.57] Đợi cho đến khi nào
[03:20.57] Vì đời nhanh như chớp mắt
[03:23.36] Đợi cho đến khi nào
[03:25.36] Mà chẳng phải hôm nay', 652)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:fzhMtPSlNWE', 'Forever Love', 'forever love', 'Nguyễn Quang Hưng', 450, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/4/a/f/3/4af32e36660ff8f3ea63f44e4248d681.jpg', '[00:21.04] Đường vào tim em hơi băng giá
[00:23.43] Em như người tình mùa đông
[00:26.11] Nếu có về đừng đi nhanh quá
[00:28.50] Sưởi ấm tim anh được không
[00:31.37] Nắng mưa nơi đây bao ngày
[00:32.98] Gió cũng đặt hết suy tư
[00:34.32] Lên đôi vai này
[00:35.53] Em muốn anh làm sao đây
[00:37.88] Đường quá rộng lại đi một mình
[00:39.58] Nếu mai này em có về
[00:40.62] Thì xin em đừng xa
[00:42.43] Vì nơi đây đông cũng gần qua
[00:45.10] Cuối thu đẹp như vậy
[00:46.43] Cảnh khơi nhạc như vậy
[00:47.42] Ngay cả Andree cũng muốn viết tình ca
[00:50.56] Tháng mười chờ thu vàng
[00:51.63] Rồi mai đây khi thu tàn
[00:52.76] Rồi buồn vui và sương gió
[00:54.09] Anh kể hết cho cây đàn
[00:55.39] Rồi quay về với kỷ niệm
[00:56.74] Rồi lang thang anh đi tìm
[00:57.81] Anh tìm lại bao niềm vui
[00:59.14] Anh quên trên môi em
[01:00.14] Hãy tựa vào vai anh đi em
[01:02.80] Cuộc sống sẽ trôi qua thật êm đềm
[01:05.21] Ta không còn sợ đối mặt với bóng tối
[01:07.63] Không còn lo ngại về định nghĩa thay đổi
[01:10.23] Ta vẫn nhìn thấy
[01:11.03] Bầu trời kia màu xanh
[01:12.83] Nhìn chặng đường
[01:13.36] Mà hai ta đã cùng đồng hành
[01:15.42] Không còn cô đơn
[01:16.00] Trong mùa đông giá lạnh
[01:17.84] Em vẫn xinh đẹp
[01:18.63] Như lần đầu gặp anh
[01:20.18] Bờ môi kia trao nụ hôn đậm sâu
[01:23.30] Trong giấc mơ ngọt ngào
[01:25.16] Cùng ngắm nhìn
[01:25.96] Những con sóng xô dạt dào
[01:28.09] Rồi lao vào giấc chiêm bao
[01:30.17] Anh đưa đôi tay
[01:30.71] Hái những ngôi sao thật cao
[01:32.78] Tìm về kỷ niệm đêm nao
[01:35.19] Sau lưng cánh tay ôm chặt
[01:37.87] Lặng yên giữa những khoảnh khắc
[01:39.82] Trái tim thuộc về nhau
[01:40.22] Nào mình cùng nhìn lại quá khứ
[01:42.84] Nhìn lại những giấc mơ
[01:45.23] Thấy những phút giây đó thật đậm sâu
[01:47.61] Thấy những ngón tay ta đan vào nhau
[01:50.47] Gần lại nơi đây mãi bên anh
[01:53.12] Những chiếc hôn ngọt ngào mỗi sáng
[01:55.53] Nói với em ngàn lời
[01:57.39] Xin đừng vội tan biến
[01:58.99] Khi ánh dương vừa lên
[02:01.09] Con trai thì yêu bằng mắt
[02:02.12] Con gái thì yêu bằng tai
[02:03.28] Một khi đã máu
[02:03.95] Thì đừng có hỏi bố cháu là ai
[02:05.65] Xinh hay là xấu
[02:06.58] Lung linh vẫn là gấu
[02:08.13] Em còn đang say sưa
[02:08.93] Nghe từng lời nhạc anh vắt tai
[02:10.74] Hai cặp mắt nhìn nhau
[02:11.56] Con tim anh như thắt lại
[02:13.13] Mỗi khi hình dung về em
[02:14.30] Khiến anh tập trung cao độ
[02:15.64] Anh quên mất đi luôn
[02:16.43] Sự tồn tại của chiếc đồng hồ
[02:17.60] I can''t let you go
[02:18.90] Baby let me know
[02:19.92] Dẫu có buông tay em ra
[02:20.95] Thì ta vẫn quay về bên nhau
[02:21.61] Màn đêm là tiếng gọi của nỗi cô đơn
[02:24.22] Nhưng khi bên em
[02:25.28] Anh sẽ thấy tốt hơn
[02:27.08] Đi qua từng khoảng trống
[02:28.41] Giữ lại những niềm vui
[02:29.61] Đi qua cả cuộc sống
[02:30.51] Và bỏ lại sau là những buồn tủi
[02:31.82] Em đi qua từng khoảng trống
[02:33.42] Giữ lại những niềm vui
[02:34.73] Đi qua cả cuộc sống
[02:35.53] Và bỏ lại sau là những buồn tủi
[02:36.80] Em, dẫu có buông tay em ra
[02:38.69] Thì ta vẫn quay về bên nhau
[02:40.15] Đen Vâu
[02:41.45] Nếu em đưa anh một nắm cát
[02:42.52] Anh sẽ không xây lâu đài
[02:43.69] Vì những con sóng sẽ đến
[02:45.12] Không hôm nay thì mai
[02:46.46] Người ta gọi anh là trai mơ
[02:47.53] Mơ một cách miệt mài
[02:48.86] Nhưng em biết đó chỉ là cách
[02:50.06] Quên đi nặng trĩu bờ vai
[02:51.39] Anh muốn được bay cùng em
[02:52.58] Hoặc cùng rơi
[02:53.64] Mình sẽ là hòn đất
[02:54.57] Hoặc áng mây giữa vùng trời
[02:56.16] Đi vào trong nắng cháy
[02:57.09] Căng buồm khi cơn giông tới
[02:58.92] Và mình sẽ bước cùng nhau
[03:00.12] Hoặc cùng bơi
[03:01.45] Oh, nếu em có tặng anh món quà
[03:02.64] Thì hãy tặng anh hơi thở em
[03:03.99] Để anh biết là mình còn gần
[03:05.19] Mỗi khi chợt dậy lúc nửa đêm
[03:06.65] Để anh biết mình phải về đâu
[03:07.72] Khi trôi quá xa giữa đời này
[03:09.18] Gần nhau
[03:10.21] Mình ngồi im lặng cũng thấy vui thay
[03:12.87] Anh vừa tan ca về
[03:14.21] Hơi mỏi, nhớ em
[03:15.77] Nghe giống mình chơi vơi
[03:16.71] Thổi gió về
[03:17.60] Hàng cây ven đường cũ lay đưa
[03:20.74] Và bỗng nhiên anh muốn biết
[03:21.48] Là người đã ngủ hay chưa
[03:22.57] Nào mình cùng nhìn lại quá khứ
[03:24.17] Nhìn lại những giấc mơ
[03:26.26] Thấy những phút giây đó thật đậm sâu
[03:28.65] Thấy những ngón tay ta đan vào nhau
[03:31.57] Gần lại nơi đây mãi bên anh
[03:33.97] Những chiếc hôn ngọt ngào mỗi sáng
[03:36.62] Nói với em ngàn lời
[03:38.48] Xin đừng vội tan biến
[03:40.07] Khi ánh dương vừa lên
[03:43.04] Anh hứa sẽ bên em hoài
[03:45.09] Cùng ăn hết nồi cơm ngày mai
[03:47.45] Cho đến khi đầu óc lú lẫn
[03:49.51] Hai ta không còn hàm nhai
[03:50.57] Ngay cả khi không còn tồn tại
[03:53.91] Anh đùa
[03:55.18] Thật ra
[03:55.71] Anh không dám hứa gì sâu xa
[03:57.25] Chứ hứa dù chuyện gì xảy ra
[03:58.51] Bên em là nắng hay mưa
[03:59.44] Thật sự em hết yêu hay là chưa
[04:00.77] Anh vẫn sẽ thương em hơn ngày qua
[04:02.20] Ý anh là
[04:03.00] Với anh em như một phần cuộc sống
[04:04.87] Thiếu em
[04:05.13] Ý nghĩa đời anh như trong tư thế bất động
[04:07.12] Nên xin đừng bao giờ hỏi
[04:08.33] Anh có yêu em thật không
[04:09.52] Vì em tự hiểu
[04:10.19] Do đâu anh từ chối
[04:11.11] Biết bao người thật lòng
[04:12.32] Yêu em thương em
[04:13.38] Anh chưa một ngày ngại khó khăn
[04:14.95] Nỗi buồn anh giữ, niềm vui gửi em
[04:16.14] Quên đi những nhọc nhằn
[04:17.20] Mọi điều tốt đẹp anh dành cho em
[04:18.40] Chỉ một ý nghĩa nói lên rằng
[04:19.99] Em cảm thấy hạnh phúc
[04:20.92] Là anh trở thành người may mắn
[04:22.46] Lần đầu gặp em
[04:23.52] Với môi xinh váy ngắn
[04:24.88] Yêu em ở bên em
[04:25.97] Là một phần của may mắn
[04:27.32] Sau lần đầu gặp
[04:28.59] Anh lại muốn có lần hai
[04:29.94] Gặp được em xong
[04:30.94] Không còn muốn gặp ai
[04:32.24] Anh không lãng mạn đâu
[04:33.67] Hay viết truyện ngôn tình
[04:34.89] Nhưng sẽ yêu em thật lâu
[04:36.19] Không cần chứng minh
[04:37.39] Anh mang Jordan và em là Nike
[04:40.01] Áo anh Versace, em diện đầm Fendi
[04:42.53] Từ quận 7 sang quận 4
[04:43.73] Anh sẽ đón em đi
[04:44.92] Cứ vô tư thong thả mặc kệ paparazzi
[04:47.45] Lên Bitexco shopping xem movie
[04:49.97] Ra phố đi bộ
[04:50.65] Chụp vài tấm hình selfie
[04:52.52] Hôm nay nhà hàng năm sao
[04:53.99] Mai mình ăn mì gõ
[04:55.18] Mốt cà phê bệt
[04:55.73] Ngày kia cà phê chảnh ***
[04:57.59] Cứ xem như thế giới này
[04:58.93] Chỉ có hai ta
[05:00.25] Mình sẽ hát bài này
[05:01.31] Như một bản tình ca
[05:02.78] Em là điều tuyệt vời nhất
[05:05.12] Nơi anh sẽ dừng chân
[05:07.53] Em là bình minh sáng
[05:09.66] Đánh thức tiếng yêu trong anh
[05:12.32] Còn lại câu hát dịu dàng
[05:14.45] Mãi đến cuối đời
[05:17.11] Chỉ cần mình em nghe
[05:19.24] Và hiểu từng nốt
[05:22.15] Cảm xúc trong anh anh anh anh
[05:25.08] Anh anh anh anh
[05:26.96] Cảm xúc trong anh anh anh anh
[05:30.15] Anh anh anh anh
[05:32.53] Cầm tay anh nhé
[05:34.38] Hạnh phúc nơi cuối con đường
[05:37.05] We belong together babe, oh oh
[05:41.77] Nơi yêu thương gọi tên em mãi
[05:43.11] Nào mình cùng nhìn lại quá khứ
[05:45.44] Nhìn lại những giấc mơ
[05:47.56] Thấy những phút giây đó thật đậm sâu
[05:50.16] Thấy những ngón tay ta đan vào nhau
[05:53.02] Gần lại nơi đây mãi bên anh
[05:55.44] Những chiếc hôn ngọt ngào mỗi sáng
[05:58.09] Nói với em ngàn lời
[05:59.95] Xin đừng vội tan biến
[06:01.56] Khi ánh dương vừa lên
[06:04.69] Tìm về nơi ánh dương
[06:07.07] Tìm về nơi ánh dương
[06:10.00] We belong together
[06:15.07] Mãi mãi bên anh
[06:20.37] Nói với em
[06:22.73] Anh muốn nói với em
[06:23.66] Nào mình cùng nhìn lại quá khứ
[06:26.52] Nhìn lại những giấc mơ
[06:28.65] Thấy những phút giây đó thật đậm sâu
[06:31.25] Thấy những ngón tay ta đan vào nhau
[06:33.90] Ở lại nơi đây mãi bên anh
[06:36.57] Những chiếc hôn ngọt ngào mỗi sáng
[06:39.15] Nói với em ngàn lời
[06:40.75] Xin đừng vội tan biến
[06:42.61] Khi ánh dương vừa lên', 452)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:E570cjfKE93E', 'Cô Gái Đứng Trước Giường', 'co gai dung truoc giuong', 'Nhật Nguyễn', 327, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/1/9/1/f/191f4dd0153cdb71d5a281a9ea69bb13.jpg', '[00:28.29] Em đứng đó trong bộ đồ trắng
[00:30.68] Xanh xao tím tái gương mặt
[00:41.05] Em đứng đó trong bộ đồ trắng
[00:43.45] Xanh xao tím tái gương mặt
[00:51.83] Tàn canh còn riêng anh
[00:58.20] Trong sương lạnh
[01:04.57] Nhịp tim dần buông lơi
[01:10.94] Nơi em vẫy gọi
[01:17.09] Và anh mơ thấy em về
[01:22.98] Lặng nhìn anh rất lâu
[01:27.20] Phía cuối góc giường
[01:30.37] Trăng và gió như đang
[01:31.97] Oán trách điều gì
[01:36.25] Sau hàng tóc ánh mắt
[01:38.34] Muốn nói điều chi
[01:41.42] Bao ước nguyện mới hôm nào
[01:48.85] Đẹp tựa bức tranh mộng mơ
[01:54.70] Ta lưu lạc chốn nao mịt mờ
[02:01.61] Ta lưu lạc chốn nao mịt mờ
[02:08.51] Ah ah
[02:10.64] Trăng treo trên cao nơi đỉnh núi
[02:13.32] Mang theo những tiếng thở dài yah
[02:17.01] Tiếng khóc chát chúa bên khoảng tối
[02:18.91] Khi sương rơi đêm hiu hắt
[02:20.51] Giữa bão tố em quay lại yeah
[02:23.30] Em đang trút hết cơn giận dữ
[02:25.29] Giết chết tình yêu
[02:26.88] Giữa những đám tàn tro
[02:28.86] Sắc màu lung linh trên hạt tuyết trắng
[02:32.07] Bỏ lại nơi đó
[02:34.05] Anh một mình vì cô đơn
[02:39.63] Vì cô đơn vì cô đơn yeah
[02:44.41] Kia ức trước mắt như dần đóng lại
[02:48.78] Tình yêu đó trôi theo băng tan
[02:52.77] Khi con thuyền đắm chìm uh-oh-oh-oh-oh
[02:59.56] Tàn đêm mơ thấy em về
[03:04.73] Lặng nhìn anh rất lâu
[03:09.63] Phía cuối góc giường
[03:12.27] Hiện trong lớp gương đen
[03:14.42] Tia chớp sáng lòa
[03:18.66] Từ trong mắt em hai tia máu vỡ oà
[03:24.49] Em như đang hờn trách anh nhiều đêm
[03:30.87] Ngọn lửa kia bừng sáng lên
[03:34.05] Những đốm tro tàn
[03:37.77] Và đêm nay giấc mơ lại về
[03:43.64] Và đêm nay giấc mơ lại về
[03:50.95] Những điều anh viết cho em
[03:52.27] Lâu dần đã chứa cả tập
[03:54.12] Anh muốn quay lại tìm em
[03:55.46] Như Lawrence ở xứ Ả Rập
[03:57.32] Anh không biết mình liệu có kịp
[03:58.65] Trở về khi nắng ló đỉnh đầu
[04:00.25] Dân tộc Kinh tôn giáo không
[04:01.84] Nhưng ai mà không có thỉnh cầu
[04:03.70] Anh tin em không ra đi
[04:05.02] Chỉ là tạm thời phải xa nhà
[04:06.88] Anh là một gã lông bông
[04:08.21] Em là quý cô mãi la cà
[04:10.08] Chúng mình vẫn đủ khờ dại
[04:11.40] Để mà có những cuộc chơi liều
[04:13.25] Chỉ là có khi phải trả giá
[04:14.59] Cho những lần đặt cược hơi nhiều
[04:16.44] Ngước lên trên cao
[04:17.78] Một vầng trăng tròn vành vạnh
[04:19.91] Cơn mưa năm xưa
[04:20.96] Giờ vẫn đang còn lành lạnh
[04:23.09] Nhiều lần mê sảng
[04:24.14] Tâm trí nửa tỉnh nửa mê
[04:25.74] Ơ sao đêm nay lại nhoè mắt
[04:27.33] Chắc là do tuyến lệ bị vỡ đê
[04:29.99] Mỗi lần nhớ em
[04:30.79] Là một lần khiến tim lại thót
[04:32.92] Và rồi nó đau
[04:33.72] Như chạm bằng miếng kim loại vót
[04:36.10] Bỏ mặc xác thân
[04:36.90] Chìm vào những đêm dài trót
[04:38.76] Thở dài cô đơn như tiếng chim dại hót
[04:42.49] Trong mơ có tiếng cười giòn tan
[04:44.89] Tóc em làm trời chiều óng ả
[04:47.28] Trong lòng như có mười hòn than
[04:49.39] Hãy nhấn chìm anh bằng sóng cả
[04:52.05] Sẽ không còn ai
[04:52.85] Để mà trông mong bên thềm
[04:54.70] Một giấc mơ qua tựa như
[04:56.30] Sông Đông êm đềm', 525)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:wdw5qNiHfe78', 'Đến Khi Nào', 'den khi nao', 'Khắc Việt', 236, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/covers/8/7/877623bb22204d537f95e583739fd415_1380862393.jpg', '[ar: Khac Viet]
[ti: Den Khi Nao]
[al: mp3.zing.vn]
[id: viulpwsb]
[00:00.00]Đến Khi Nào 
[00:02.00]Khắc Việt
[00:04.00]

[00:17.61]Đến khi nào mặt trời ngừng chiếu 
[00:20.73]
[00:22.11]Và nắng thôi soi trên con đường 
[00:24.36]
[00:25.31]Đến khi nào ta không còn bên nhau 
[00:28.41]
[00:29.84]Thì anh mới quên em 
[00:32.41]
[00:33.06]Đến khi nào 
[00:34.42]vòng tay ấm áp 
[00:36.31]
[00:37.37]Một người khác đang ôm em thật chặt 
[00:39.75]
[00:40.80]Đấy lúc là ngày anh ra đi 
[00:43.72]
[00:45.26]Và ta sẽ chia tay 
[00:47.62]
[00:48.35]Anh chỉ lo 
[00:49.82]anh ôm em chặt quá 
[00:51.50]
[00:53.21]Sợ em không xa được anh 
[00:55.43]
[00:56.15]Anh chỉ lo 
[00:57.61]khi em bên người khác 
[00:59.18]
[01:00.73]Tâm trí luôn nghĩ đến anh 
[01:03.05]
[01:04.01]Có ngàn lối trên đường em đi 
[01:06.94]
[01:08.15]Chọn một lối
[01:09.27] hay em đứng lại đây 
[01:10.84]
[01:11.69]Nếu gục ngã 
[01:13.14]em nhìn đằng sau nhé 
[01:14.80]
[01:16.08]Anh vẫn luôn đứng phía sau 
[01:18.84]
[01:19.52]dang rộng tay
[01:20.77]

[01:35.08]Đến khi nào mặt trời ngừng chiếu 
[01:38.20]
[01:39.58]Và nắng thôi soi trên con đường 
[01:41.83]
[01:42.78]Đến khi nào ta không còn bên nhau 
[01:45.88]
[01:47.31]Thì anh mới quên em 
[01:49.88]
[01:50.53]Đến khi nào 
[01:51.89]vòng tay ấm áp 
[01:53.78]
[01:54.84]Một người khác đang ôm em thật chặt 
[01:57.22]
[01:58.27]Đấy lúc là ngày anh ra đi 
[02:01.19]
[02:02.73]Và ta sẽ chia tay 
[02:05.09]
[02:05.82]Anh chỉ lo 
[02:07.29]anh ôm em chặt quá 
[02:08.97]
[02:10.68]Sợ em không xa được anh 
[02:12.90]
[02:13.62]Anh chỉ lo 
[02:15.08]khi em bên người khác 
[02:16.65]
[02:18.20]Tâm trí luôn nghĩ đến anh 
[02:20.52]
[02:21.48]Có ngàn lối trên đường em đi 
[02:24.41]
[02:25.62]Chọn một lối
[02:26.74] hay em đứng lại đây 
[02:28.31]
[02:29.16]Nếu gục ngã 
[02:30.61]em nhìn đằng sau nhé 
[02:32.27]
[02:33.55]Anh vẫn luôn đứng phía sau 
[02:36.31]
[02:36.99]dang rộng tay
[02:38.24]

[02:56.22]Anh chỉ lo 
[02:57.69]anh ôm em chặt quá 
[02:59.37]
[03:01.08]Sợ em không xa được anh 
[03:03.30]
[03:04.02]Anh chỉ lo 
[03:05.48]khi em bên người khác 
[03:07.05]
[03:08.60]Tâm trí luôn nghĩ đến anh 
[03:10.92]
[03:11.88]Có ngàn lối trên đường em đi 
[03:14.81]
[03:16.02]Chọn một lối
[03:17.14] hay em đứng lại đây 
[03:18.71]
[03:19.56]Nếu gục ngã 
[03:21.01]em nhìn đằng sau nhé 
[03:22.67]
[03:23.95]Anh vẫn luôn đứng phía sau 
[03:26.71]
[03:31.30]dang rộng tay
[03:33.42]', 520)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:iu8mQBz49Nd7', 'Đến Khi Nào', 'den khi nao', 'Khắc Việt', 232, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/1/7/5/d/175d0835cf69a3fc4449d35206ec67fe.jpg', '[00:16.31] Đến khi nào mặt trời ngừng chiếu
[00:20.05] Và nắng thôi soi trên con đường
[00:23.24] Đến khi nào ta không còn bên nhau
[00:28.03] Thì anh mới quên em
[00:31.23] Đến khi nào vòng tay ấm áp
[00:35.52] Một người khác đang ôm em thật chặt
[00:38.68] Đấy lúc là ngày anh ra đi
[00:43.48] Và ta sẽ chia tay
[00:46.65] Anh chỉ lo anh ôm em chặt quá
[00:50.91] Sợ em không xa được anh
[00:54.11] Anh chỉ lo khi em bên người khác
[00:58.36] Tâm trí luôn nghĩ đến anh
[01:02.06] Có ngàn lối trên đường em đi
[01:06.35] Chọn một lối hay em đứng lại đây
[01:10.03] Nếu gục ngã em nhìn đằng sau nhé
[01:14.30] Anh vẫn luôn đứng phía sau dang rộng tay
[01:33.95] Đến khi nào mặt trời ngừng chiếu
[01:37.14] Và nắng thôi soi trên con đường
[01:40.88] Đến khi nào ta không còn bên nhau
[01:44.61] Thì anh mới quên em
[01:48.33] Đến khi nào vòng tay ấm áp
[01:52.57] Một người khác đang ôm em thật chặt
[01:56.32] Đấy lúc là ngày anh ra đi
[02:00.61] Và ta sẽ chia tay
[02:04.34] Anh chỉ lo anh ôm em chặt quá
[02:08.08] Sợ em không xa được anh
[02:11.25] Anh chỉ lo khi em bên người khác
[02:16.06] Tâm trí luôn nghĩ đến anh
[02:19.79] Có ngàn lối trên đường em đi
[02:23.53] Chọn một lối hay em đứng lại đây
[02:27.24] Nếu gục ngã em nhìn đằng sau nhé
[02:31.47] Anh vẫn luôn đứng phía sau dang rộng tay
[02:54.99] Anh chỉ lo anh ôm em chặt quá
[02:58.72] Sợ em không xa được anh
[03:01.91] Anh chỉ lo khi em bên người khác
[03:06.16] Tâm trí luôn nghĩ đến anh
[03:09.84] Có ngàn lối trên đường em đi
[03:13.60] Chọn một lối hay em đứng lại đây
[03:17.32] Nếu gục ngã em nhìn đằng sau nhé
[03:22.14] Anh vẫn luôn đứng phía sau dang rộng tay', 891)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:Y7hd6JOiUFzU', 'Đen Đá Không Đường', 'den da khong duong', 'Lyly', 182, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/8/c/9/f/8c9f583a79f97a92cd585c8c2d526cfc.jpg', '[00:00.00]Bài hát: Đen Đá Không Đường
[00:02.00]Ca sĩ: AMEE
[00:04.00]
[00:12.21]Chiều nay qua quán quen
[00:15.40]Chẳng biết uống gì
[00:17.52]Nghĩ nghĩ suy suy
[00:19.02]Cà phê hay nước cam?
[00:22.15]Sữa tươi hay trà?
[00:24.03]Mà thôi mới uống hôm qua
[00:25.91]Rồi em trông thấy anh
[00:29.05]Ánh mắt nụ cười làm em bỗng lười nghĩ suy
[00:33.99]Thôi! Cho em Đen Đá Không Đường! 
[00:38.39]Còn cho anh ly trà sữa trân châu đen
[00:39.84]Vì em đã gặp anh
[00:46.40]Vì em đã gặp anh
[00:53.16]Anh chính là đường đấy! It’s You...Ooh oh
[01:00.05]Anh chính là đường đấy! It’s You...Ooh oh
[01:07.88]Trời đang nắng tự dưng lại mưa
[01:09.88]Mùa đông liệu đã về chưa 
[01:11.25]Hay là anh đã mang mặt trời đi mất
[01:14.22]Cà phê đắng thật đắng
[01:15.72]Mà em vẫn khen rằng ngon thật ngon
[01:18.11]Dù em đây chỉ thích đồ ngọt
[01:21.06]Tại sao hả?
[01:22.43]Vì em đã gặp anh
[01:29.18]Vì em đã gặp anh
[01:36.00]Anh chính là đường đấy! It’s You...Ooh oh
[01:42.93]Anh chính là đường đấy! It’s You...Ooh oh
[01:47.53]
[02:04.13]You’re my sugar sugar! Ooh it’s you
[02:08.27]Sugar sugar
[02:10.83]You’re my sugar sugar! Ooh it’s you
[02:15.47]Sugar sugar 
[02:17.35]Anh chính là đường đấy! It’s You...Ooh oh
[02:23.99]Anh chính là đường đấy! It’s You...Ooh oh
[02:30.94]Anh chính là đường đấy! It’s You...Ooh oh
[02:38.25]Anh chính là đường đấy! It’s You...Ooh oh
[02:43.75]
[02:44.87]Chiều nay qua quán quen
[02:47.93]Ánh mắt nụ cười làm em bỗng lười nghĩ suy
[02:52.87]Thôi! Cho em Đen Đá Không Đường!
[02:57.18]', 379)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:khMSCPgJRfLe', 'Đến Là Duyên Đi Là Phận', 'den la duyen di la phan', 'TVk', 384, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/banner/a/5/e/d/a5edc19ef945cbe93e59b3f7dce89f68.jpg', '[00:35.35] Nhớ lúc ấy em đi
[00:38.54] Em ơi em đang nghĩ gì
[00:42.97] Mà sao anh nghe
[00:44.56] Tận sâu tim anh đau lắm
[00:50.94] Lúc ấy em nói chi
[00:54.54] Yêu anh em đổi lấy gì
[00:57.71] Là câu cuối cùng
[01:00.44] Khiến anh chết lặng nhìn theo
[01:05.21] Ừ thì anh sai
[01:06.90] Khi anh không có gì ở trong tay
[01:10.88] Mỗi khi giông tố
[01:13.39] Chỉ biết thương người thôi mà
[01:16.18] Ngày qua anh vẫn đang cố
[01:20.96] Tội nghiệp thân em nên thôi em nhé
[01:25.36] Cứ đi tìm cho mình hạnh phúc mới
[01:29.62] Anh ở đây được rồi
[01:31.62] Không sao đâu mà em ơi
[01:36.42] Vì anh tay trắng
[01:38.40] Chẳng thể cùng người
[01:40.79] Bước qua ngày mưa
[01:42.38] Giông tố cuộc đời
[01:44.77] Sương gió phủ đầy
[01:47.57] Đời trai gập ghềnh em ơi
[01:52.02] Gặp nhau dẫu biết đó cũng là phận
[01:56.40] Mà sao chữ duyên cứ mãi lận đận
[02:00.39] Nhắm mắt buông xuôi
[02:03.17] Để em cho hạnh phúc mới
[02:07.95] Ngày mai nắng đến ta chẳng gặp lại
[02:11.94] Ánh dương đời anh ai cướp đi rồi
[02:16.32] Anh sẽ nhớ em nhiều
[02:18.98] Vì anh còn thương nhiều lắm
[02:23.82] Ngày em bên ai về phía mặt trời
[02:27.86] Bão giông từ đâu kéo đến về phòng
[02:31.87] Giữa bốn bức tường
[02:34.67] Mưa rơi trong lòng em ơi
[03:11.28] Ừ thì anh sai
[03:12.85] Khi anh không có gì ở trong tay
[03:16.85] Mỗi khi giông tố
[03:19.48] Chỉ biết thương người
[03:21.88] Ngày qua anh vẫn đang cố
[03:27.05] Tội nghiệp thân em nên thôi em nhé
[03:31.03] Cứ đi tìm cho mình hạnh phúc mới
[03:35.43] Anh ở đây được rồi
[03:37.81] Không sao đâu mà em ơi
[03:42.47] Vì anh tay trắng
[03:44.47] Chẳng thể cùng người
[03:46.47] Bước qua ngày mưa
[03:48.47] Giông tố cuộc đời
[03:50.87] Sương gió phủ đầy
[03:53.65] Đời trai gập ghềnh em ơi
[03:58.04] Gặp nhau dẫu biết đó cũng là phận
[04:02.07] Mà sao chữ duyên cứ mãi lận đận
[04:06.45] Nhắm mắt buông xuôi
[04:09.26] Để em cho hạnh phúc mới
[04:13.79] Ngày mai nắng đến ta chẳng gặp lại
[04:17.84] Ánh dương đời anh ai cướp đi rồi
[04:22.21] Anh sẽ nhớ em nhiều
[04:25.01] Vì anh còn thương nhiều lắm
[04:29.78] Ngày em bên ai về phía mặt trời
[04:33.76] Bão giông từ đâu kéo đến về phòng
[04:37.76] Giữa bốn bức tường
[04:40.53] Mưa rơi trong lòng em ơi
[04:45.66] Vì anh tay trắng
[04:47.25] Chẳng thể cùng người
[04:49.33] Bước qua ngày mưa
[04:51.33] Giông tố cuộc đời
[04:53.97] Sương gió phủ đầy
[04:56.37] Đời trai gập ghềnh em ơi
[05:01.17] Gặp nhau dẫu biết đó cũng là phận
[05:05.17] Mà sao chữ duyên cứ mãi lận đận
[05:09.55] Nhắm mắt buông xuôi
[05:11.94] Để em cho hạnh phúc mới
[05:16.70] Ngày mai nắng đến ta chẳng gặp lại
[05:20.74] Ánh dương đời anh ai cướp đi rồi
[05:25.12] Anh sẽ nhớ em nhiều
[05:27.92] Vì anh còn thương nhiều lắm
[05:32.72] Ngày em bên ai về phía mặt trời
[05:36.72] Bão giông từ đâu kéo đến về phòng
[05:41.08] Giữa bốn bức tường
[05:43.49] Mưa rơi trong lòng em ơi
[05:49.07] Giữa bốn bức tường
[05:51.84] Mưa rơi trong lòng em ơi', 187)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:v67bI74jR5k5', 'Đến Trước Nhưng Đứng Sau', 'den truoc nhung dung sau', 'Thanh Hưng', 329, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/8/c/3/a/8c3a5b8986591e6208b3c0346cc3cff9.jpg', '[00:31.22] Đến bây giờ anh vẫn
[00:33.85] Trách bản thân anh
[00:37.59] Không biết dành thời gian
[00:40.77] Để tim em lạnh
[00:45.01] Chỉ lo sợ cuộc sống
[00:48.20] Sẽ chẳng an lành
[00:51.95] Mà quên đi tình yêu
[00:54.97] Đã đến lúc mỏng manh
[00:59.74] Rồi người đó xuất hiện
[01:03.46] Thay đổi mọi chuyện
[01:07.71] Dù biết mà anh chẳng dám đối diện
[01:14.62] Lùi bước một lần
[01:18.35] Đổi lấy em được hạnh phúc trăm lần
[01:24.18] Dù sẽ đớn đau bản thân
[01:28.96] Là anh tìm thấy và đến trước
[01:32.68] Nắm lấy yêu thương duyên trời đã ban
[01:36.93] Nhưng để em chờ lâu
[01:39.05] Niềm tin đối với anh
[01:41.18] Không như lúc đầu
[01:43.84] Là anh sai là anh sai
[01:47.57] Ở bên anh lận đận tương lai
[01:51.80] Anh quên rằng chẳng ai
[01:54.46] Chờ mãi được ai
[01:58.18] Và người đến trước
[02:00.32] Giờ lại đứng sau lưng
[02:02.99] Trách than duyên trời đã tan
[02:06.15] Cuộc sống và tình yêu
[02:08.29] Luôn khiến anh phải lựa chọn
[02:13.06] Ở cuối con đường phía trước
[02:16.78] Sẽ có người cùng em bước
[02:21.03] Hoa không nắng nên tàn
[02:23.14] Yêu không gắng thôi đành lìa tan
[02:58.01] Rồi người đó xuất hiện
[03:01.20] Thay đổi mọi chuyện
[03:05.45] Dù biết mà anh chẳng dám đối diện
[03:12.88] Lùi bước một lần
[03:16.07] Đổi lấy em được hạnh phúc trăm lần
[03:21.92] Dù sẽ đớn đau bản thân
[03:26.70] Là anh tìm thấy và đến trước
[03:30.42] Nắm lấy yêu thương duyên trời đã ban
[03:34.67] Nhưng để em chờ lâu
[03:37.32] Niềm tin đối với anh
[03:38.91] Không như lúc đầu
[03:42.11] Là anh sai là anh sai
[03:45.71] Ở bên anh lận đận tương lai
[03:49.96] Anh quên rằng chẳng ai
[03:52.62] Chờ mãi được ai
[03:55.83] Và người đến trước
[03:58.49] Giờ lại đứng sau lưng
[04:00.59] Trách than duyên trời đã tan
[04:04.31] Cuộc sống và tình yêu
[04:06.44] Luôn khiến anh phải lựa chọn
[04:10.69] Ở cuối con đường phía trước
[04:14.94] Sẽ có người cùng em bước
[04:19.21] Hoa không nắng nên tàn
[04:21.31] Yêu không gắng thôi đành lìa tan
[04:26.11] Là anh sai là anh sai
[04:29.81] Ở bên anh lận đận tương lai
[04:34.06] Anh quên rằng chẳng ai
[04:37.24] Chờ mãi được ai
[04:40.46] Ở cuối con đường phía trước
[04:44.73] Sẽ có người cùng em bước
[04:48.44] Hoa không nắng nên tàn
[04:50.56] Yêu không gắng thôi đành lìa tan
[04:55.34] Hoa không nắng nên tàn
[04:58.00] Yêu không gắng thôi đành lìa tan', 399)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:oUUXYWTOam92', 'Trạm Dừng Chân', 'tram dung chan', 'Nguyễn Hoàng Long, Nguyễn Đức Cường', 290, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/1/a/3/2/1a32f5011fd3098dab2269d6272cacb4.jpg', '[00:00.00]Bài hát: Trạm Dừng Chân
[00:02.00]Ca sĩ: Kimmese, Đen
[00:04.00]
[00:14.12]Trạm dừng chân của đôi ta trước mặt
[00:17.84]Em tăng ga bỏ lại phía sau
[00:21.43]Từng nhịp tim níu đau thương chìm sâu
[00:24.54]Tiếng yêu kia chưa nói
[00:25.95]Đã biến tan vào nhau
[00:28.69]Ngọn đèn đêm với góc phố yên lặng
[00:32.38]Tay em không che được bão giông
[00:35.94]Cầu vồng lên
[00:36.64]Lấp kín những khoảng trống
[00:39.17]Sau lưng em
[00:39.88]Chỉ còn là giấc mơ mùa đông
[00:42.58]Chẳng còn những ký ức
[00:43.69]Ngày nắng lên bên em
[00:45.97]Bầu trời xanh mang đến
[00:47.32]Những giấc mơ êm đềm
[00:49.53]Từng lời ca em  viết
[00:50.88]Bài hát khắc tên anh
[00:53.27]Đốt đi kỷ niệm
[00:54.59]Anh đang cố đốt đi để quên
[00:56.90]Dặm đường xa phía trước
[00:58.20]Chẳng bước đi bên nhau
[01:00.49]Trạm dừng chân kia sẽ
[01:01.76]Chôn dấu những nỗi đau
[01:04.14]Ngọn đèn khuya hiu hắt
[01:05.46]Trong bóng đêm một màu
[01:08.72]Ta sẽ đi tới đâu...
[01:11.83]
[01:34.22]Lạc vào đêm
[01:34.86]Tiếng mưa cuối chân trời
[01:37.67]Chợt một phút giây trôi...
[01:39.50]Bỏ lại tia nắng rơi
[01:41.39]Đường về là bóng đêm che mờ lối
[01:44.45]Con tim đang vội vàng
[01:45.89]Tìm kiếm nơi nghỉ ngơi
[01:48.65]Ngọn đèn đêm với góc phố yên lặng
[01:52.70]Tay em không che được bão giông
[01:55.97]Cầu vồng lên
[01:56.61]Lấp kín những khoảng trống
[01:59.23]Sau lưng anh
[01:59.98]Chỉ còn là giấc mơ mùa đông
[02:02.26]
[02:02.38]Chẳng còn những ký ức
[02:03.49]Ngày nắng lên bên em
[02:05.77]Bầu trời xanh mang đến
[02:07.12]Những giấc mơ êm đềm
[02:09.33]Từng lời ca em viết
[02:10.68]Bài hát khắc tên anh
[02:13.07]Đốt đi kỷ niệm
[02:14.39]Anh đang cố đốt đi để quên
[02:16.70]Dặm đường xa phía trước
[02:18.00]Chẳng bước đi bên nhau
[02:20.29]Trạm dừng chân kia sẽ
[02:21.56]Chôn dấu những nỗi đau
[02:23.94]Ngọn đèn khuya hiu hắt
[02:25.26]Trong bóng đêm một màu
[02:28.52]Ta sẽ đi tới đâu...
[02:31.63]
[02:46.76]Và chúng ta đã không còn bé bỏng
[02:48.56]Có niềm đau tưởng chừng xé lòng
[02:50.44]Vài hơi thuốc làm anh thấy khé họng
[02:52.06]Đừng bao giờ hỏi là anh có khỏe không
[02:53.70]Dạo này đêm về anh vẫn thường khó ngủ
[02:55.54]Căn phòng thật trống dù có giường có tủ
[02:57.40]Đi ăn một mình cố nuốt cho đủ no
[02:59.09]Nỗi buồn nỗi buồn
[03:00.08]Anh giữ như thủ kho
[03:01.52]Anh thấy mông lung buổi la cà
[03:03.27]Con đường chỉ có mỗi ta và
[03:05.01]Gió thu se lạnh nổi da gà
[03:06.77]Lao đi trong gió thổi la đà
[03:08.69]Em có nhớ không
[03:09.47]Những buổi chiều vàng nhạt ấy
[03:10.47]Ta nép vào nhau
[03:11.27]Như đôi chim sợ lạc bầy
[03:12.33]Nắng tàn trên tóc em
[03:13.19]Vàng ánh như râu ngô
[03:14.13]Ta chạm vào nhau
[03:14.94]Bằng những ngón màu nâu thô
[03:16.05]Đã từng cần nhau
[03:16.83]Như bóng đèn cần nguồn điện
[03:17.78]Chia nhau điều vui
[03:18.55]Và chia cả nỗi muộn phiền
[03:19.58]Nếu mà mọi thứ
[03:20.30]Có thể đóng thành bưu kiện
[03:21.35]Anh sẽ gửi em
[03:22.13]Những mùa nhung nhớ luân phiên
[03:23.29]Gửi em ngày cũ
[03:23.99]Anh không mong trôi qua nhanh
[03:25.01]Những ngày anh đi
[03:25.81]Trong chơi vơi như pa-tanh
[03:26.91]Những đêm huyền dịu
[03:27.83]Đêm óng ánh như sa-tanh
[03:28.84]Ngày em đẹp nhất là ngày em xa anh
[03:29.78]Chẳng còn những ký ức
[03:30.44]
[03:30.89]Ngày nắng lên bên em
[03:33.17]Bầu trời xanh mang đến
[03:34.52]Những giấc mơ êm đềm
[03:36.73]Từng lời ca em viết
[03:38.08]Bài hát khắc tên anh
[03:40.47]Đốt đi kỷ niệm
[03:41.79]Anh đang cố đốt đi để quên
[03:44.10]Dặm đường xa phía trước
[03:45.40]Chẳng bước đi bên nhau
[03:47.69]Trạm dừng chân kia sẽ
[03:48.96]Chôn dấu những nỗi đau
[03:51.34]Ngọn đèn khuya hiu hắt
[03:52.66]Trong bóng đêm một màu
[03:55.92]Ta sẽ đi tới đâu...
[04:00.89]', 174)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:di5zpFQIfwk', 'Đến Đây Bên Anh', 'den day ben anh', 'Cloud 5, Dangrangto', 241, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/f/0/1/f/f01f6c2c9253dffcafbeea4474475d89.jpg', '[00:15.16] Hãy đến đây bên anh
[00:16.95] Trong một chiều mùa thu
[00:19.34] Và những chiếc lá xanh
[00:20.85] Vẫn đang rung rinh đùa vui
[00:23.50] Chẳng cần quá lâu
[00:24.31] Để anh biết đã yêu mất rồi
[00:27.69] Chỉ cần là đôi môi của em
[00:29.28] Anh hôn nhẹ lên vào mỗi tối
[00:31.60] Tụi bạn cứ trêu dã man
[00:33.65] Nói ta thật đẹp đôi
[00:35.97] Chẳng để ý anh xua tay
[00:37.57] Nhưng mà thấy cũng vui khi nghĩ tới
[00:40.14] Vậy liệu em có muốn ở đây bên anh
[00:42.21] Khi trong lòng chơi vơi
[00:43.99] Anh chỉ cần thế thôi
[00:45.79] Anh sẽ thôi không lắng lo nữa đâu
[00:49.10] Là bởi vì anh đã có em bên đời rồi
[00:51.18] Thì còn lắng lo chi
[00:53.54] Nguyện trao hết tấm chân tình này
[00:55.39] Chẳng cần đắn đo
[00:57.25] Người đừng đùa với cảm xúc
[00:58.51] Khi em buồn thì đều chạm tới nơi anh
[01:01.37] Ai cũng sẽ có những lúc hơi tiêu cực
[01:03.68] Để khoảng lặng ấy trôi nhanh
[01:05.65] When I''m with you
[01:09.68] You made me feel
[01:14.07] Đưa em đi ăn bún real
[01:18.24] Girl anh không nói điêu
[01:20.28] Youngboy đã biết yêu
[01:40.30] Đã rất cố gắng
[01:41.10] Nhưng vẫn không thể nào mà ngủ được
[01:42.38] Phòng còn sáng đèn
[01:44.37] Cứ thế tít mắt khi anh nhớ ra
[01:45.96] Mai thức dậy lại được gặp dáng em
[01:48.55] Lv Saint Laurent Gucci Balenci
[01:50.85] Chọn gì khoác lên
[01:52.64] Trông anh mướt mướt đến nhức nách
[01:54.16] Em gặp thì cứ phải gọi là god damn
[01:56.49] God damn
[01:57.02] Baby girl em bước từ trong ngõ
[01:59.00] Với đôi má hồng
[02:01.08] Trao cho riêng anh một nụ cười tỏa nắng
[02:02.97] Con tim này rã đông
[02:05.21] Hold me tight my tiny love
[02:07.34] Anh sẽ chẳng hai lòng
[02:08.87] Và sẽ chẳng để em phải dỗi hờn
[02:10.74] Hỏi anh cũng nhớ em mà phải không
[02:13.14] Anh đã nghĩ kĩ
[02:14.31] Chỉ định trao con tim mà mất luôn lí trí
[02:16.36] Như là ngàn bông hoa for me
[02:18.44] Anh chẳng thể nào lãng quên được
[02:20.30] Mùi hương này
[02:21.56] Tình yêu anh qua ngày vẫn cứ lớn hơn
[02:24.33] Em chính là lí do
[02:26.40] Em chính là người đó
[02:29.11] Là bởi vì anh đã có em bên đời rồi
[02:31.50] Thì còn lắng lo chi
[02:33.55] Nguyện trao hết tấm chân tình này
[02:35.67] Chẳng cần đắn đo
[02:37.53] Người đừng đùa với cảm xúc
[02:38.79] Khi em buồn thì đều chạm tới nơi anh
[02:41.63] Ai cũng sẽ có những lúc hơi tiêu cực
[02:43.75] Để khoảng lặng ấy trôi nhanh
[02:45.79] When I''m with you
[02:49.91] You made me feel
[02:54.35] Đưa em đi ăn bún real
[02:58.26] Girl anh không nói điêu
[03:00.66] Youngboy đã biết yêu
[03:02.61] Là bởi vì anh đã quá
[03:37.57] Hãy đến đây bên anh
[03:39.36] Trong một chiều mùa thu
[03:41.69] Và những chiếc lá xanh
[03:43.29] Vẫn đang rung rinh đùa vui
[03:45.87] Chẳng cần quá lâu
[03:46.66] Để anh biết đã yêu mất rồi
[03:49.96] Chỉ cần là đôi môi của em
[03:51.56] Anh hôn nhẹ lên vào mỗi tối', 808)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:BdMqkaVH2iJz', '(anh vẫn yêu mỗi em) đến giây cuối cùng', 'anh van yeu moi em den giay cuoi cung', 'Hứa Kim Tuyền, Trần Dũng Khánh', 206, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/3/e/f/6/3ef6ad15f294a89431d20e01c1e003c7.jpg', '[00:00.24] Và anh muốn nói với cả thế giới
[00:02.65] Anh chỉ có nàng
[00:05.02] Hứa với cả thế giới
[00:06.62] Yêu chỉ mỗi nàng
[00:08.20] Hồn sa vào ánh mắt lấp lánh nắng
[00:10.19] Khi chiều nhuốm vàng
[00:12.62] Bỗng dưng khiến cho
[00:13.39] Trái tim của anh nhận ra
[00:18.99] Nàng là điều diệu kỳ trong anh
[00:20.60] Là bầu trời ngập tràn tia nắng
[00:22.57] Là vị thần đã hồi sinh anh
[00:24.19] Từ trăm ngàn cơn khổ đau anh mang
[00:26.17] Bằng những lời dịu dàng em trao
[00:28.19] Và những điều ngọt ngào theo sau
[00:29.76] Anh đã biết trái tim anh
[00:31.37] Thuộc về nơi em
[00:33.76] Muốn mỗi sáng thức giấc
[00:34.94] Được nhìn thấy có em trong vòng tay
[00:37.37] Cứ mỗi tối chớp mắt
[00:38.96] Là mình sẽ khép môi hôn nồng say
[00:41.33] Anh sẽ không quên đời này
[00:42.94] Anh hứa sẽ không quên
[00:44.54] Mình đã yêu em nhiều như thế
[00:47.32] Và anh muốn nói với cả thế giới
[00:50.11] Anh chỉ có nàng
[00:52.51] Hứa với cả thế giới
[00:54.11] Yêu chỉ mỗi nàng
[00:55.68] Hồn sa vào ánh mắt lấp lánh nắng
[00:57.69] Khi chiều nhuốm vàng
[01:00.10] Bỗng dưng khiến cho
[01:01.30] Trái tim của anh biết ra
[01:02.78] Là anh muốn nói với cả thế giới
[01:05.15] Anh chỉ có nàng
[01:07.54] Hứa với cả thế giới
[01:09.15] Anh chỉ yêu nàng
[01:10.75] Đến khi sông ngừng trôi
[01:12.35] Mưa thôi rơi thêm nữa
[01:13.53] Con tim chẳng còn đập nữa
[01:15.52] Anh vẫn yêu mỗi em
[01:17.93] Đến giây cuối cùng
[01:30.99] Anh vẫn yêu mỗi em
[01:33.39] Đến giây cuối cùng
[01:34.94] Nhớ những lúc chuyện trò thâu đêm
[01:36.56] Được cầm tay em và nghe em nói
[01:38.57] Những thứ ngỡ bình thường trong anh
[01:40.17] Lại y như đang
[01:41.39] Được ban phép nhiệm mầu
[01:42.57] You take my pain away
[01:44.17] All my sorrows fade away
[01:46.17] Tim của anh ở đây
[01:47.35] Anh xin tình nguyện trao tay
[01:49.75] Nói với anh thêm đi
[01:50.94] Về cuộc sống đã mang em đi
[01:53.33] Những nỗi đau hoen mi
[01:54.93] Và cả những chuỗi ngày hoang phí
[01:57.31] Anh sẽ đền lại cho em
[01:58.91] Bằng cả vốn lẫn lời cho xem
[02:00.89] Baby, tin anh được không em
[02:03.70] Và anh muốn nói với cả thế giới
[02:06.48] Anh chỉ có nàng
[02:08.88] Hứa với cả thế giới
[02:10.06] Yêu chỉ mỗi nàng
[02:12.07] Hồn sa vào ánh mắt lấp lánh nắng
[02:14.05] Khi chiều nhuốm vàng
[02:16.07] Bỗng dưng khiến cho
[02:17.30] Trái tim của anh biết ra
[02:18.88] Là anh muốn nói với cả thế giới
[02:21.67] Anh chỉ có nàng
[02:24.05] Hứa với cả thế giới
[02:25.27] Anh chỉ yêu nàng
[02:27.26] Đến khi sông ngừng trôi
[02:28.44] Mưa thôi rơi thêm nữa
[02:29.63] Con tim chẳng còn đập nữa
[02:31.63] Anh vẫn yêu mỗi em
[02:34.03] Đến giây cuối cùng
[02:51.30] I swear I''ll love you
[02:52.87] ''til the last one moment
[02:54.88] I swear I''ll love you
[02:56.06] ''til the last one second
[02:58.86] I swear I''ll love you
[03:00.07] ''til the last breath taken
[03:02.09] Anh vẫn yêu mỗi em
[03:04.48] Đến giây cuối cùng
[03:06.88] I swear I''ll love you
[03:08.07] ''til the last one moment
[03:10.07] I swear I''ll love you
[03:11.64] ''til the last one second
[03:14.07] I swear I''ll love you
[03:15.25] ''til the last breath taken
[03:17.65] Anh vẫn yêu mỗi em
[03:20.04] Đến giây cuối cùng', 324)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:vEeFGLlJl5F0', 'Mùa Đông Chưa Bao Giờ Tới', 'mua dong chua bao gio toi', 'Tạ Anh Thắng', 267, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/6/5/d/e/65de412be610bb2218ca927a3a596b12.jpg', '[00:31.56] Nếu những ký ức tan biến theo mưa
[00:38.61] Trong tim anh
[00:40.13] Còn mang lời yêu lúc xưa
[00:44.40] Những điều cũ đã qua
[00:47.62] Ngày mai là một ngày mới
[00:51.13] Liệu anh có tìm được
[00:52.91] Một làn hơi ấm trong mùa đông
[00:58.14] Nếu em từng nghĩ mai này có nhau
[01:01.67] Nếu em từng cố chôn vùi nỗi đau
[01:05.20] Có không hạnh phúc đến mãi sau
[01:07.92] Dù là trong giấc mơ
[01:11.96] Giá như ngày đó ta đừng cách xa
[01:15.22] Giá như mọi thứ
[01:16.48] Chỉ là giấc mơ qua
[01:18.73] Chiếc hôn vội vàng
[01:20.49] Ánh mắt dịu dàng đã mãi nơi đâu
[01:25.26] Mùa đông chưa bao giờ tới
[01:30.54] Vì hai chúng ta hai nơi
[01:34.06] Nỗi nhớ thêm chơi vơi
[01:36.27] Đôi bờ mi ướt nhòe
[01:41.22] Bao yêu thương chôn giấu
[01:44.38] Buốt giá kéo dài thật lâu
[01:47.85] Anh mang theo ánh nắng
[01:51.07] Tới nơi cuối trời
[02:29.33] Nếu em từng nghĩ mai này có nhau
[02:32.60] Nếu anh từng cố chôn vùi nỗi đau
[02:36.13] Có không hạnh phúc đến mãi sau
[02:38.89] Dù là trong giấc mơ
[02:43.18] Giá như ngày đó ta đừng cách xa
[02:46.45] Giá như mọi thứ
[02:47.46] Chỉ là giấc mơ qua
[02:49.72] Chiếc hôn vội vàng
[02:51.77] Ánh mắt dịu dàng đã mãi nơi đâu
[02:56.28] Mùa đông chưa bao giờ tới
[03:01.80] Vì hai chúng ta hai nơi
[03:05.05] Nỗi nhớ thêm chơi vơi
[03:07.53] Đôi bờ mi ướt nhòe
[03:12.26] Bao yêu thương chôn giấu
[03:15.24] Buốt giá kéo dài thật lâu
[03:19.00] Anh mang theo ánh nắng
[03:21.71] Tới nơi cuối trời
[03:25.96] Đêm chôn sâu bao yêu dấu
[03:29.23] Bóng dáng người giờ nơi đâu
[03:32.49] Chỉ thấy nước mắt của em
[03:35.46] Đong đầy trên mi
[03:39.47] Người gieo bao nhiêu thương nhớ
[03:42.99] Mãi mãi chỉ là cơn mơ
[03:46.53] Mùa đông chưa bao giờ tới trong đời', 741)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:urDmURjTVqRJ', 'Những Lời Hứa Bỏ Quên', 'nhung loi hua bo quen', 'Dear Jane', 236, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/3/e/3/0/3e30ea0ec7af4a085cbe11dfe3ab8477.jpg', '[00:14.51] Nếu hai ta không quên ngày ấy
[00:17.97] Ngày những đôi môi trao nhau không lời
[00:21.48] Thì niềm đau cũng đã
[00:23.91] Trôi hết đi qua bao
[00:26.03] Tháng năm anh với em
[00:29.22] Lúc gặp nhau
[00:30.02] Con tim nói không nên lời
[00:32.95] Khi thời gian trôi như mây bay về trời
[00:36.45] Và anh đem theo giấc mơ này để rồi
[00:40.22] Mong ngày ta chung đôi
[00:42.34] Anh sẽ nhớ mong một người
[00:45.03] Là chính em
[00:46.36] Sẽ nhớ thương thật nhiều
[00:48.50] Điều vấn vương
[00:49.84] Đôi mắt xưa còn đượm buồn
[00:52.52] Theo vệt thời gian
[00:54.11] Đã hoá tan theo làn sương
[00:56.54] Hai ta có trên đường đời
[00:59.23] Nhìn thấy nhau
[01:00.57] Trong phút giây nghẹn ngào
[01:02.45] Thì anh chẳng thể bước tới
[01:06.47] Chào em như trong giấc mơ
[01:10.00] Giấc mơ ta được có nhau
[01:25.76] Nếu hai ta không quên ngày ấy
[01:28.94] Ngày những đôi môi trao nhau không lời
[01:32.69] Thì niềm đau cũng đã
[01:35.13] Trôi hết đi qua bao
[01:37.03] Tháng năm anh với em
[01:40.25] Lúc gặp nhau con tim nói không nên lời
[01:44.27] Khi thời gian trôi như mây bay về trời
[01:47.70] Và anh đem theo giấc mơ này để rồi
[01:51.17] Mong ngày ta chung đôi
[01:53.57] Anh sẽ nhớ mong một người
[01:56.01] Là chính em
[01:57.36] Sẽ nhớ thương thật nhiều
[01:59.77] Điều vấn vương
[02:01.12] Đôi mắt xưa còn đượm buồn
[02:03.79] Theo vệt thời gian
[02:05.12] Đã hoá tan theo làn sương
[02:07.52] Hai ta có trên đường đời
[02:10.49] Nhìn thấy nhau
[02:11.56] Trong phút giây nghẹn ngào
[02:13.72] Thì anh chẳng thể bước tới
[02:17.75] Chào em như trong giấc mơ
[02:20.95] Giấc mơ ta được có nhau
[02:27.12] Thức giấc lúc khi trời mưa
[02:29.80] Chuyện yêu thương ùa về
[02:31.40] Khiến anh nhận ra
[02:34.34] Kí ức đón đưa ngày xưa
[02:37.02] Chuyện đôi ta nghẹn ngào
[02:38.89] Tình cờ đợi chờ
[02:40.70] Để được quay lại giây phút đầu
[03:01.14] Anh sẽ nhớ mong một người
[03:03.58] Là chính em
[03:04.91] Sẽ nhớ thương thật nhiều
[03:07.33] Điều vấn vương
[03:08.65] Đôi mắt xưa còn đượm buồn
[03:11.34] Theo vệt thời gian
[03:12.67] Đã hoá tan theo làn sương
[03:15.09] Hai ta có trên đường đời
[03:17.78] Nhìn thấy nhau
[03:19.13] Trong phút giây nghẹn ngào
[03:21.27] Thì anh chẳng thể bước tới
[03:25.30] Chào em như trong giấc mơ
[03:28.49] Giấc mơ ta được có nhau', 118)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:z4CrPlzJOHV3', 'bình yên', 'binh yen', 'Hoàng Thái Vũ, Đỗ Văn Đại', 196, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/3/e/3/0/3e30ea0ec7af4a085cbe11dfe3ab8477.jpg', '[00:01.12] Em như dòng nước trong veo
[00:04.85] Băng qua ngọn núi vòng vèo
[00:09.03] Chỉ cần có em hàn huyên
[00:12.43] Cùng anh kể chuyện
[00:17.16] Cơn mưa rơi bất chợt dần tan
[00:21.36] Để nhường cho ánh nắng nghiêng
[00:24.83] Đoạn đường em bước đi ngang qua đây
[00:28.81] Hửng nắng
[00:32.80] Khi hai ta gần lại một chút
[00:36.51] Hai dòng sông
[00:37.58] Chiếu sáng ánh trăng vàng
[00:40.52] Ánh mắt thướt tha như có đôi lời
[00:44.50] Với anh
[00:48.22] Em như dòng nước trong veo
[00:50.89] Xoá hết ưu phiền
[00:51.98] Băng qua ngọn núi vòng vèo
[00:54.62] Bạn đồng hành chỉ cần có em hàn huyên
[00:59.61] Cùng anh kể chuyện
[01:01.94] Chỉ cần có em
[01:04.06] Anh tìm một thoáng bình yên
[01:06.73] Vang lên trong đầu
[01:07.80] Giờ đang ở đâu tìm ở đâu
[01:10.66] Giữa thế giới bao la
[01:12.28] Giữ lấy trong tầm tay
[01:13.87] Để gió cuốn không thổi bay
[01:16.48] Vì anh đã tìm thấy bình yên
[01:20.14] Đạp xe bên hoàng hôn
[01:21.47] Và em luôn ngồi sau
[01:22.27] Tay ôm bụng anh
[01:23.32] Vu vơ bài ca em hay quên lời
[01:26.19] Và đôi khi là những câu hỏi trên trời
[01:28.06] Anh ơi trẻ con chơi kìa vui nhỉ
[01:30.15] Anh ơi ở bên kia là hoa gì
[01:31.95] Ơ hay tại sao chim được bay
[01:33.50] Tại sao cây màu xanh
[01:34.30] Sao nhìn ai cũng vội vã vậy
[01:36.15] Đôi khi anh nghĩ bình yên thật xa xỉ
[01:37.70] Có lẽ lúc đó bên cạnh chưa là em
[01:40.10] Cho đến khi anh thấy
[01:40.91] Đôi mắt đen lay láy
[01:41.71] Thầm nhìn anh
[01:42.25] Một buổi sáng anh ngủ quên
[01:43.52] Chưa ai bao giờ nói cho anh
[01:44.85] Tình yêu là gì
[01:45.65] Anh cứ ngỡ như những cơn sóng ầm ĩ
[01:47.76] Nhưng thật ra em dịu êm như mặt hồ
[01:49.30] Và tĩnh như màn đêm
[01:50.36] Đôi lúc khiến anh thầm nghĩ
[01:51.42] Liệu không phải em
[01:53.94] Lòng tin là thứ anh không đặt cược
[01:55.47] Nếu không phải em
[01:57.89] Bình yên là thứ anh không chạm được
[01:59.59] Bởi vì cách em nhìn
[02:00.51] Và cảm giác em hôn
[02:01.45] Hơn tất cả những gì
[02:02.25] Anh có thể định nghĩa
[02:03.54] Anh cảm ơn nỗi đau
[02:04.47] Đã cho em đến sau
[02:05.37] Vì mọi điều anh tìm kiếm là đây
[02:07.70] Khi hai ta gần lại một chút
[02:10.89] Hai dòng sông chiếu sáng ánh trăng vàng
[02:14.87] Ánh mắt thướt tha như có đôi lời
[02:18.86] Với anh
[02:22.57] Em như dòng nước trong veo
[02:25.24] Xoá hết ưu phiền
[02:26.31] Băng qua ngọn núi vòng vèo
[02:29.21] Bạn đồng hành
[02:29.96] Chỉ cần có em hàn huyên
[02:33.94] Cùng anh kể chuyện
[02:36.53] Chỉ cần có em
[02:38.39] Anh tìm một thoáng bình yên
[02:41.06] Vang lên trong đầu
[02:42.11] Giờ đang ở đâu tìm ở đâu
[02:44.97] Giữa thế giới bao la
[02:46.50] Giữ lấy trong tầm tay
[02:48.05] Để gió cuốn không thổi bay
[02:50.70] Vì anh đã tìm thấy bình yên', 575)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:XwnuuLEHyBLq', 'Lạ Lùng', 'la lung', 'Vũ.', 261, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/1/8/7/0/1870f0cf683b82ae09e9355360598204.jpg', '[ar: Vũ.]
[ti: Lạ Lùng]
[by: ZingMp3 Lyric]
[length: 04:21]
[00:00.00]Bài hát: Lạ Lùng
[00:02.00]Ca sĩ: Vũ
[00:04.00]
[00:18.17]Kìa màn đêm 
[00:20.13]Hiu hắt mang tên em 
[00:24.03]Quay về trong ký ức 
[00:27.91]Của anh qua thời gian 
[00:31.66]
[00:33.39]Chiều lặng im 
[00:35.31]Nghe gió đung đưa cây 
[00:39.14]Như là bao nỗi nhớ 
[00:42.89]Cuốn anh trôi về đâu 
[00:47.50]
[00:48.60]Này gió đừng hát 
[00:52.37]Và mang nỗi nhớ chạy đi 
[00:56.63]Quên âu lo quên hết 
[00:59.38]Suy tư một đời 
[01:01.85]
[01:04.18]Mưa trong anh sẽ vơi 
[01:07.68]Nhưng đôi môi đang vấn vương 
[01:11.75]Chỉ tình cờ nhìn em rồi mang theo 
[01:15.53]Những cơn đau thét gào 
[01:19.31]
[01:19.79]Lạ lùng em tới hãy tới 
[01:24.99]Bên anh trong chiều đông xa vắng 
[01:28.48]Mà sao giờ đây nhìn 
[01:30.68]Lại chẳng còn thấy em 
[01:34.45]
[01:34.82]Lạ lùng em hỡi 
[01:38.03]Gió hát lên câu ca làm 
[01:41.77]Anh thao thức 
[01:43.69]Mà bao say mê nồng nàn 
[01:46.35]Giờ đã phai màu 
[01:49.91]
[01:50.90]Kìa nắng ngập tràn 
[01:54.64]Nhưng giấc mơ lại vừa bay đi 
[01:58.66]Gạt hết cuộc đời lẻ loi 
[02:01.72]Thôi mình anh 
[02:05.05]Lại ngồi nhớ em
[02:08.02]

[02:26.72]Kìa màn đêm 
[02:28.68]Hiu hắt mang tên em 
[02:32.58]Quay về trong ký ức 
[02:36.46]Của anh qua thời gian 
[02:40.21]
[02:41.92]Chiều lặng im 
[02:43.86]Nghe gió đung đưa cây 
[02:47.66]Như là bao nỗi nhớ 
[02:51.40]Cuốn anh trôi về đâu 
[02:56.05]
[02:57.15]Này gió đừng hát 
[03:00.92]Và mang nỗi nhớ chạy đi 
[03:05.14]Quên âu lo quên hết 
[03:07.93]Suy tư một đời 
[03:10.40]
[03:12.73]Mưa trong anh sẽ vơi 
[03:16.23]Nhưng đôi môi đang vấn vương 
[03:20.30]Chỉ tình cờ nhìn em rồi mang theo 
[03:24.08]Những cơn đau thét gào 
[03:27.86]
[03:28.34]Lạ lùng em tới hãy tới 
[03:33.50]Bên anh trong chiều đông xa vắng 
[03:37.00]Mà sao giờ đây nhìn 
[03:39.23]Lại chẳng còn thấy em 
[03:43.00]
[03:43.37]Lạ lùng em hỡi 
[03:46.58]Gió hát lên câu ca làm 
[03:50.28]Anh thao thức 
[03:52.24]Mà bao say mê nồng nàn 
[03:54.83]Giờ đã phai màu 
[03:58.40]
[03:59.45]Kìa nắng ngập tràn 
[04:03.19]Nhưng giấc mơ lại vừa bay đi 
[04:06.56]
[04:07.26]Gạt hết cuộc đời lẻ loi 
[04:10.27]Thôi mình anh 
[04:13.60]Lại ngồi nhớ em
[04:16.57]', 703)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:xjBUuqxu52Yt', 'Bước Qua Mùa Cô Đơn', 'buoc qua mua co don', 'Vũ', 279, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/6/5/6/7/6567e3b8fbf430a461c7dfe1e1a3ea75.jpg', '[00:07.48] Chào cơn mưa
[00:09.72] Làm sao cứ kéo ta quay lại
[00:13.47] Những rung động con tim
[00:15.23] Lần đầu hai ta gặp gỡ
[00:21.73] Chào hàng cây
[00:23.98] Làm sao cố níu tay nhau lại
[00:27.68] Để thấy nồng nàn
[00:29.43] Đang về trên đôi mắt em
[00:34.28] Chợt nhìn đôi bàn tay em run
[00:37.28] Nắm lấy bờ vai rất lâu
[00:41.71] Cuối thu với anh là ngày khiến
[00:45.47] Hai hàng mi rối bời
[00:48.21] Vì ngày ấy gặp nhau
[00:50.41] Không ai dám nói một câu
[00:53.66] Chào nhau
[00:56.11] Cứ đắm đuối
[00:57.86] Cứ thế hát bài hát chia xa
[01:05.01] Mùa thu rơi vào em
[01:08.01] Vào trong giấc mơ hôm qua
[01:12.76] Mùa thu ôm mình em
[01:15.25] Chạy xa vòng tay vội vã
[01:20.26] Lời em nói ngày xưa đâu đây
[01:23.96] Vẫn âm thầm chìm vào trong mây
[01:27.41] Đến bao giờ dặn lòng
[01:30.12] Anh không mong nhớ
[01:33.86] Mùa thu rơi vào em
[01:36.37] Vào trong chiếc hôn ngây thơ
[01:41.11] Mùa thu không cần anh
[01:43.37] Vì em giờ đây còn mãi hững hờ
[01:48.82] Ngày mai kia nếu có phút giây
[01:51.33] Vô tình thấy nhau sẽ nói câu gì
[01:55.77] Hay ta chỉ nhìn lặng lẽ đi qua
[02:18.11] Chào cơn mưa
[02:20.36] Làm sao cứ kéo ta quay lại
[02:23.86] Những rung động con tim
[02:25.61] Lần đầu hai ta gặp gỡ
[02:32.31] Chào hàng cây
[02:34.51] Làm sao cố níu tay nhau lại
[02:38.21] Để thấy nồng nàn
[02:39.71] Đang về trên đôi mắt em
[02:44.66] Chợt nhìn đôi bàn tay em run
[02:47.67] Nắm lấy bờ vai rất lâu
[02:52.16] Cuối thu với anh là ngày khiến
[02:56.11] Hai hàng mi rối bời
[02:58.81] Vì ngày ấy gặp nhau
[03:01.05] Không ai dám nói một câu
[03:04.06] Chào nhau
[03:06.55] Cứ đắm đuối
[03:08.26] Chẳng hề chia xa
[03:12.76] Mùa thu rơi vào em
[03:15.02] Vào trong giấc mơ hôm qua
[03:19.95] Mùa thu ôm mình em
[03:22.40] Rời xa vòng tay vội vã
[03:27.40] Lời em nói ngày xưa đâu đây
[03:30.90] Vẫn âm thầm chìm vào trong mây
[03:34.35] Đến bao giờ dặn lòng
[03:37.10] Anh không mong nhớ
[03:41.11] Mùa thu rơi vào em
[03:43.34] Vào trong chiếc hôn ngây thơ
[03:48.09] Mùa thu không cần anh
[03:50.60] Vì em giờ đây còn mãi hững hờ
[03:55.59] Ngày mai kia nếu có phút giây
[03:58.31] Vô tình thấy nhau sẽ nói câu gì
[04:07.60] Hay ta chỉ nhìn lặng lẽ đi qua', 826)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:6O8vsOeipvyy', 'Vui Lắm Nha', 'vui lam nha', 'Hoon, Jombie', 304, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/d/2/0/3/d203dca5fe9fbb2190b3b9b729936c53.jpg', '[00:15.78] Nhớ đã hứa sẽ cưới rồi nha
[00:18.04] Hết năm nay mà không thấy
[00:19.80] Tôi mách ba ba mắng anh giờ
[00:23.02] Tới tối thức giấc lóng ngóng chờ ai
[00:25.48] Mong một mai anh tới
[00:26.98] Mang theo điệu hò đưa dâu
[00:30.50] Mai đây ta về chung nhà
[00:32.26] Em sẽ lo trồng rau
[00:34.28] Nấu những bát cơm chiều
[00:35.53] Chờ anh thế thôi
[00:36.79] Trên dưới vui mừng
[00:38.30] Ngày mình cùng chung đôi
[00:40.56] Là ngày 25 ta uống rượu giao bôi
[00:44.59] Mình ở bên nhau
[00:45.85] Cuộc đời vui lắm nha
[00:47.86] Cột dây tơ duyên
[00:49.62] Hai đứa thương tới già
[00:51.88] Anh nói mai này
[00:53.39] Em sẽ no ấm cùng anh
[00:55.40] Hỡi khoan hò khoan
[00:57.14] Em nguyện yêu thật lòng
[00:59.15] Mình ở bên nhau
[01:00.92] Đời lại vui biết bao
[01:02.93] Cột dây tơ duyên
[01:04.69] Hai đứa thương quá trời
[01:06.70] Em đã đem lòng
[01:08.46] Yêu lấy anh hỡi chàng ơi
[01:10.47] Lỡ mai về xa anh
[01:12.48] Chờ em theo với
[01:46.14] Đón em về làng
[01:46.89] Đám cưới như ngày hội
[01:48.15] Bò dê heo quay gà dặm hỏi
[01:50.35] Năm trăm mâm bàn đầy mồi
[01:52.12] Đồ ăn vừa nóng vừa thổi
[01:53.88] Xe chở cả hàng đoàn
[01:55.63] Theo anh về
[01:56.08] Em sẽ không còn cực khổ
[01:57.33] Nói là làm
[01:57.81] Do cái tính anh đàng hoàng
[01:59.58] Pháo hoa bấm nút là nổ
[02:01.35] Vòng kiềng vàng bạc nặng cổ
[02:05.08] Người chúc đến hàng ngàn
[02:06.84] Cúi đầu ba lạy
[02:07.59] Quỳ trước bàn thờ tổ
[02:09.10] Cho em làm bà hoàng
[02:10.62] Xe nào em thích đỗ đâu thì đỗ
[02:12.88] Em hỏa anh thì thổ
[02:14.33] Nhà là phải có ruby bì hổ
[02:16.59] Nhớ đã hứa sẽ cưới rồi nha
[02:19.11] Hết năm nay mà không thấy
[02:20.63] Là tôi mách ba ba mắng anh giờ
[02:23.91] Tới tối thức giấc lóng ngóng chờ ai
[02:26.41] Mong một mai anh tới
[02:28.18] Mang theo điệu hò đưa dâu
[02:31.44] Mai đây ta về chung nhà
[02:33.44] Em sẽ lo trồng rau
[02:35.19] Nấu những bát cơm chiều
[02:36.45] Chờ anh thế thôi
[02:37.92] Trên dưới vui mừng
[02:39.18] Ngày mình cùng chung đôi
[02:41.43] Là ngày 25 ta uống rượu giao bôi
[02:45.47] Mình ở bên nhau
[02:46.73] Cuộc đời vui lắm nha
[02:48.99] Cột dây tơ duyên
[02:50.49] Hai đứa thương tới già
[02:52.51] Anh nói mai này
[02:54.26] Em sẽ no ấm cùng anh
[02:56.28] Hỡi khoan hò khoan
[02:58.05] Em nguyện yêu thật lòng
[03:00.05] Mình ở bên nhau
[03:01.82] Đời lại vui biết bao
[03:03.84] Cột dây tơ duyên
[03:05.35] Hai đứa thương quá trời
[03:07.60] Em đã đem lòng
[03:09.36] Yêu lấy anh hỡi chàng ơi
[03:11.37] Lỡ mai về xa
[03:13.13] Anh chờ em theo với
[03:46.78] Hai đứa phu thê
[03:49.80] Tát cạn khô nước biển đông dạt dào
[03:54.09] Ra thăm ruộng đồng
[03:55.59] Ruộng bao la lúa thơm
[03:57.61] Là em thương bấy nhiêu anh hỡi
[04:00.35] Mình ở bên nhau
[04:01.61] Cuộc đời vui lắm nha
[04:03.88] Cột dây tơ duyên
[04:05.39] Hai đứa thương tới già
[04:07.65] Anh nói mai này
[04:09.16] Em sẽ no ấm cùng anh
[04:11.44] Hỡi khoan hò khoan
[04:12.94] Em nguyện yêu thật lòng
[04:15.21] Mình ở bên nhau
[04:16.70] Đời lại vui biết bao
[04:18.72] Cột dây tơ duyên
[04:20.47] Hai đứa thương quá trời
[04:22.48] Em đã đem lòng
[04:24.49] Yêu lấy anh hỡi chàng ơi
[04:26.24] Lỡ mai về xa anh
[04:28.24] Chờ em theo với', 995)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:sI58MZHH1jg4', 'Đông Kiếm Em', 'dong kiem em', 'Vũ.', 246, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/5/6/1/c/561c11ea6c1f171609b2458924b08a6f.jpg', '[ar: Vũ]
[ti: Đông Kiếm Em]
[by: ZingMp3 Lyric]
[length: 04:06]
[id: bowapwcu]
[00:00.00]Bài Hát: Đông Kiếm Em
[00:02.00]Ca Sĩ: Vũ.
[00:04.00]
[00:17.73]Tôi hát cho màu xanh mãi xanh
[00:23.80]Cho một người lặng im biết yêu
[00:29.70]
[00:30.10]Và tôi viết cho mùa yêu xốn xang
[00:38.00]Cho một đời nhớ thương vẹn nguyên
[00:44.06]
[00:46.55]Cô đơn đến thế
[00:48.34]Mưa rơi lách tách kì cục đợi ai
[00:53.60]Sâu trong ánh mắt
[00:55.34]Tôi ngu ngơ mơ thời gian dừng trôi

[01:02.89]Còn lại đây nhớ mong 
[01:06.50]Còn lại tôi với ai
[01:10.06]Giờ này em chắc đang ngủ say
[01:15.83]Hay là em còn đang khóc một mình
[01:20.37]Như làn sương muộn màng
[01:23.88]Lạc trong đêm 
[01:26.65]Con tim em lạc trong đêm
[01:30.88]
[01:31.38]Lòng phiêu du nhớ em 
[01:34.90]Mùa thu đã đi qua
[01:38.55]Đông kiếm em mùa đông kiếm em
[01:45.79]
[01:52.37]Mùa đông kiếm em đông kiếm em
[02:00.25]

[02:04.27]Tôi biết em sợ đông sẽ tan
[02:10.45]Em lạ lùng gọi trong vô vọng
[02:15.98]
[02:16.64]Và tôi nhớ ly cà phê sắp tan
[02:24.75]Biết bao giờ là em sẽ về
[02:30.57]

[02:33.18]Cô đơn đến thế
[02:34.97]Mưa rơi lách tách kì cục đợi ai
[02:40.26]Sâu trong ánh mắt
[02:41.99]Tôi ngu ngơ mơ thời gian dừng trôi

[02:49.58]Còn lại đây nhớ mong 
[02:53.18]Còn lại tôi với ai
[02:56.69]Giờ này em chắc đang ngủ say
[03:02.46]Hay là em còn đang khóc một mình
[03:07.00]Như làn sương muộn màng
[03:10.51]Lạc trong đêm 
[03:13.28]Con tim em lạc trong đêm
[03:17.51]
[03:18.01]Lòng phiêu du nhớ em 
[03:21.55]Mùa thu đã đi qua
[03:25.18]Đông kiếm em mùa đông kiếm em
[03:32.22]
[03:39.00]Mùa đông kiếm em đông kiếm em
[03:46.57]
[03:50.04]Còn lại tôi với ai 
[03:53.60]Còn lại bao nhớ mong
[03:57.16]Giờ này em chắc đang ngủ say
[04:03.96]', 999)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:s219Wi38R3d0', 'Phút Ban Đầu', 'phut ban dau', 'Vũ.', 243, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/1/e/1/5/1e1560eb93cdae9d6871886aec7e27cf.jpg', '[ar: Vũ.]
[ti: Phút Ban Đầu]
[by: ZingMp3 Lyric]
[length: 04:03]
[00:00.00]Bài Hát: Phút Ban Đầu
[00:02.00]Ca Sĩ: Vũ.
[00:04.00]
[00:31.85]Có ô cửa sổ nhỏ 
[00:35.65]Lay hồn ai qua đây 
[00:39.21]Có giấc mơ là lạ 
[00:42.71]Chạm vào trong ký ức 
[00:45.69]Dường như mây đang khóc 
[00:48.03]Mưa đang hát 
[00:49.86]Hát bài hát mùa yêu
[00:52.80]Dường như mây đang đến 
[00:55.19]Mưa đi mất 
[00:56.91]Tôi xao xuyến 
[00:59.51]
[01:00.25]Em lưu luyến 
[01:03.81]
[01:08.36]Gần em tôi thấy 
[01:10.20]Bâng khuâng bâng khuâng 
[01:11.94]Con tim này như chết lặng 
[01:16.38]Bóng dáng người như cơn gió lạ 
[01:20.02]Mang em về gần lại bên tôi 
[01:22.92]Dường như góc phố 
[01:24.63]Thênh thang thênh thang 
[01:26.47]Sương đêm làm ướt tóc ai
[01:30.84]Đừng để nỗi nhớ miên man 
[01:32.90]Trong đêm thật dài 
[01:34.59]Hãy để khoảnh khắc tim tôi 
[01:36.59]Tim em thật gần 
[01:39.67]
[01:41.29]Với nhau
[01:44.39]
[01:57.98]Có cơn mưa đầu mùa 
[02:01.46]Xa thật xa chốn ấy 
[02:05.02]Có cô em ngày nào 
[02:08.47]Tóc ngắn ngang vai 
[02:11.59]Dường như mây đang khóc 
[02:13.83]Mưa đang hát 
[02:15.56]Hát bài hát mùa yêu
[02:18.61]Dường như mây đang đến 
[02:20.83]Mưa đi mất 
[02:22.77]Tôi xao xuyến 
[02:25.70]
[02:26.24]Em lưu luyến 
[02:27.87]
[02:31.76]Gần em tôi thấy 
[02:33.55]Bâng khuâng bâng khuâng 
[02:35.26]Con tim này như chết lặng 
[02:39.70]Bóng dáng người như cơn gió lạ 
[02:43.21]Mang em về gần lại bên tôi 
[02:45.89]Dường như góc phố 
[02:47.51]Thênh thang thênh thang 
[02:49.26]Sương đêm làm ướt tóc ai
[02:53.42]Đừng để nỗi nhớ miên man 
[02:55.32]Trong đêm thật dài 
[02:56.97]Hãy để khoảnh khắc tim tôi 
[02:58.93]Tim em thật gần 
[03:01.71]
[03:03.90]Với nhau
[03:05.86]

[03:26.50]Và tôi mong sớm mai đây 
[03:29.08]Khi em bên tôi 
[03:30.89]Cùng say giấc nồng 
[03:34.48]Khẽ nhắc về mùa yêu thương đã qua 
[03:38.19]Khẽ tìm lại giây phút ban đầu 
[03:42.70]
[03:44.16]Nhé em
[03:47.07]', 407)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:CwzTW4GClxec', 'Vừa Hận Vừa Yêu', 'vua han vua yeu', 'Trung Tự', 157, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/2/4/2/f/242f7184dc49f8c778fce0d8cac9b449.jpg', '[00:10.70] Ta đã có những hẹn ước
[00:13.39] Em có nhớ ngày đó không
[00:15.79] Ta vẫn cứ cố gắng chung bước
[00:18.13] Dù phía trước là bão giông
[00:20.92] Nên xin em đừng phản bội anh
[00:23.32] Cho bao nhiêu nỗi đau kia thêm dài
[00:25.73] Anh đau trong lòng vì nhạt phai
[00:28.13] Em có nghĩ mình sẽ quay lại
[00:30.92] Đường dài đường dài em đi
[00:33.29] Nó đã có hình bóng ai rồi
[00:35.70] Đêm nay em chờ đợi ai
[00:38.10] Sao mà em lại nhắn tin nhầm
[00:40.80] Có phải anh vừa hận vừa yêu
[00:43.19] Có phải anh luôn luôn nuông chiều
[00:45.96] Nên em coi là điều hiển nhiên
[00:48.37] Nên em coi là rác ven đường
[00:51.07] Có phải anh vừa hận vừa yêu
[00:53.46] Có phải anh luôn luôn nuông chiều
[00:55.86] Nên em coi là điều hiển nhiên
[00:58.24] Nên em coi là rác ven đường
[01:01.03] Trái tim em là bất khả
[01:03.83] Nhưng đó là em nói điêu
[01:06.24] Sau lưng anh anh biết mà
[01:08.63] Em hôn môi ai rất nhiều
[01:11.42] Nên xin em đừng phản bội anh
[01:13.81] Cho bao nhiêu nỗi đau kia thêm dài
[01:16.19] Anh đau trong lòng vì nhạt phai
[01:18.59] Em có nghĩ mình sẽ quay lại
[01:21.40] Đường dài đường dài em đi
[01:23.79] Nó đã có hình bóng ai rồi
[01:26.57] Đêm nay em chờ đợi ai
[01:28.56] Sao mà em lại nhắn tin nhầm
[01:31.36] Có phải anh vừa hận vừa yêu
[01:33.74] Có phải anh luôn luôn nuông chiều
[01:36.46] Nên em coi là điều hiển nhiên
[01:38.85] Nên em coi là rác ven đường
[01:41.26] Có phải anh vừa hận vừa yêu
[01:43.63] Có phải anh luôn luôn nuông chiều
[01:46.44] Nên em coi là điều hiển nhiên
[01:48.82] Nên em coi là rác ven đường
[01:51.61] Nên xin em đừng phản bội anh
[01:54.00] Cho bao nhiêu nỗi đau kia thêm dài
[01:56.38] Anh đau trong lòng vì nhạt phai
[01:59.18] Em có nghĩ mình sẽ quay lại
[02:01.99] Có phải anh vừa hận vừa yêu
[02:03.98] Có phải anh luôn luôn nuông chiều
[02:06.37] Nên em coi là điều hiển nhiên
[02:09.17] Nên em coi là rác ven đường
[02:11.59] Có phải anh vừa hận vừa yêu
[02:14.37] Có phải anh luôn luôn nuông chiều
[02:16.78] Nên em coi là điều hiển nhiên
[02:19.17] Nên em coi là rác ven đường', 275)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:msgiw3jkGCSN', 'Mùa Mưa Ngâu Nằm Cạnh', 'mua mua ngau nam canh', 'Vũ.', 271, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/8/b/1/7/8b175b041e34d9a2e19e7a3fc614121b.jpg', '[ar: Vũ]
[ti: Mùa Mưa Ngâu Nằm Cạnh]
[al: Mùa Mưa Ngâu Nằm Cạnh]
[by: kidrainzboy]
[length: 04:31]
[id: bkhohhci]
[00:00.54]Chẳng phải em là hồn của cây
[00:06.21]
[00:07.38]Mang câu hát đem đi đùa vui
[00:12.73]
[00:14.85]Sống trong bao câu chuyện buồn
[00:19.53]
[00:21.41]Tìm đâu đam mê say đắm ơi
[00:26.50]
[00:27.60]Chẳng phải em là vải màu nâu
[00:33.12]
[00:34.18]Mang hơi ấm cho anh 
[00:37.33]Ngày mưa rơi đầy
[00:40.62]
[00:41.77]Cuối câu chuyện là đêm trắng
[00:48.00]Mình em cô đơn
[00:54.82]Cơn mưa chiều qua chờ ai
[01:01.26]Cuốn theo em bỏ rơi
[01:06.11]
[01:07.84]Tiếc đôi vai gầy
[01:11.01]Bên phố đông
[01:13.86]Một mùa mưa ngâu ngây ngất
[01:20.48]Trôi đi thật nhanh là em
[01:24.90]
[01:26.81]Ướt đôi môi lẻ loi
[01:31.42]
[01:33.17]Tiếc thương ân tình
[01:36.52]Em yêu mưa
[01:39.31]Và mùa mưa ngâu
[01:41.98]
[01:44.71]Nằm cạnh em
[01:48.21]
[02:12.14]Chẳng phải em là hồn của cây
[02:17.67]
[02:19.10]Mang câu hát đem đi đùa vui
[02:24.14]
[02:26.45]Sống trong bao câu chuyện buồn
[02:31.40]
[02:33.02]Tìm đâu đam mê say đắm ơi
[02:38.93]Chẳng phải em là vải màu nâu
[02:44.45]
[02:45.51]Mang hơi ấm cho anh 
[02:48.66]Ngày mưa rơi đầy
[02:51.95]
[02:53.10]Cuối câu chuyện là đêm trắng
[02:59.33]Mình em cô đơn
[03:06.15]Và cơn mưa chiều qua chờ anh
[03:12.59]Cuốn theo em bỏ rơi
[03:17.44]
[03:19.17]Tiếc đôi vai gầy
[03:22.55]Bên phố đông
[03:25.30]Một mùa mưa ngâu ngây ngất
[03:32.00]Trôi đi thật nhanh là em
[03:36.84]
[03:38.40]Ướt đôi môi lẻ loi
[03:42.80]
[03:44.80]Tiếc thương ân tình
[03:47.90]Em yêu mưa
[03:50.78]Và mùa mưa ngâu
[03:53.92]
[03:56.23]Nằm cạnh em
[04:00.06]', 876)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:tOmVpVl7FulX', 'Lời Yêu Em', 'loi yeu em', 'Vũ.', 285, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/4/9/4/7/4947cbd5655ce6b2dd3a21ae92041364.jpg', '[ar: Vũ.]
[ti: Lời Yêu Em]
[length: 04:45]
[00:00.00]Bài Hát: Lời Yêu Em
[00:04.23]Ca Sĩ: Vũ.
[00:08.05]
[00:24.91]Dịu dàng từng lời hát
[00:28.66]Ngân lên khi sắc dương tan
[00:33.29]Mình anh cứ ngồi nơi đây
[00:36.41]Đợi chờ ai đợi chờ ai
[00:39.10]Mà bao nhiêu vấn vương
[00:42.41]Khi chiều qua
[00:43.85]Rồi một ngày em đến
[00:47.78]Anh gặp em nơi cuối sân trường
[00:52.22]Thời gian cứ chầm chậm trôi
[00:55.10]Để cho anh để cho anh
[00:57.72]Yêu em nhưng không dám bên em
[01:02.41]
[01:03.41]Dù thời gian, có trôi
[01:07.91]Và em, khi đã xa vời
[01:12.35]Nhưng sao không thể quên
[01:14.83]Không thể quên ... hình bóng ấy?
[01:19.47]
[01:20.84]Và đột nhiên anh thấy nhớ
[01:23.10]Những con đường dài
[01:25.03]Và đột nhiên anh thấy nhớ
[01:27.10]Những tháng ngày
[01:29.53]Hát vang từng khúc ca
[01:31.66]Lá la là lá la
[01:33.72]Ta ngồi bên nhau
[01:36.47]
[01:37.91]Và ngày đâu hay anh biết
[01:39.97]Đã yêu em thật rồi
[01:41.97]Và người người ơi anh thấy
[01:43.97]Thấy tim anh bồi hồi
[01:46.22]Là những khi gần với nhau
[01:48.41]Giữ lại từng phút giây
[01:50.54]Anh không dám nói
[01:54.85]Lời yêu em
[01:58.16]
[02:12.41]Dịu dàng từng lời hát
[02:15.78]Ngân lên khi sắc dương tan
[02:20.03]Mình anh cứ ngồi nơi đây
[02:22.72]Đợi chờ ai đợi chờ ai
[02:25.10]Mà bao nhiêu vấn vương
[02:28.41]Khi chiều qua
[02:29.41]Rồi một ngày em đến
[02:33.16]Anh gặp em nơi cuối sân trường
[02:37.47]Thời gian cứ chầm chậm trôi
[02:40.04]Để cho anh để cho anh
[02:42.34]Yêu em nhưng không dám bên em
[02:47.36]
[02:47.43]Và đột nhiên anh thấy nhớ
[02:49.24]Những con đường dài
[02:51.38]Và đột nhiên anh thấy nhớ
[02:53.63]Những tháng ngày
[02:55.88]Hát vang từng khúc ca
[02:58.19]Lá la là lá la
[03:00.19]Ta ngồi bên nhau
[03:02.69]
[03:04.57]Và ngày đâu hay anh biết
[03:06.69]Đã yêu em thật rồi
[03:08.88]Và người người ơi anh thấy
[03:10.76]Thấy tim anh bồi hồi
[03:13.01]Là những khi gần với nhau
[03:15.19]Giữ lại từng phút giây
[03:17.19]Anh không dám nói
[03:21.57]Lời yêu em
[03:24.57]
[03:29.95]Vì anh vẫn luôn yêu em
[03:33.70]Chỉ mỗi em thôi ...
[03:37.45]
[03:58.28]Và ngày đâu hay anh biết
[04:01.47]Đã yêu em thật rồi
[04:03.90]Và người người ơi anh thấy
[04:06.53]Thấy tim anh bồi hồi
[04:09.11]Là những khi gần với nhau
[04:11.48]Giữ lại từng phút giây
[04:14.42]Anh không dám nói
[04:19.11]
[04:19.67]Lời yêu em
[04:22.92]', 199)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:wQeXJ6nheQ2O', 'Vũ Trụ Trong Anh', 'vu tru trong anh', 'Sinike', 237, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/4/1/4/8/41488217c31b072ee8950b9993bd8825.jpg', '[00:27.68] Hạnh phúc của anh
[00:29.63] Là được nhìn em cười tươi
[00:32.39] Mỗi khi đêm về
[00:34.41] Những muộn phiền
[00:36.42] Xóa tan chỉ trong vài giây
[00:40.45] Được ở bên em chở che
[00:44.41] Lo lắng cho em mỗi ngày
[00:47.68] Cùng em đi qua bao mùa mưa nắng
[00:53.44] Chẳng ở đâu xa mà cạnh anh
[00:56.95] Em là cả thế gian đây rồi
[00:59.96] Dẫu vui buồn
[01:01.96] Trái tim vẫn luôn bình yên
[01:05.97] Chỉ có em thôi không cần giàu sang
[01:09.24] Cũng thành một mái ấm gia đình
[01:12.76] Người tôi yêu là cô gái
[01:15.27] Tốt nhất trên trần gian
[01:25.56] Dù trái đất đến lúc tận thế
[01:28.84] Anh vẫn yêu em không đổi thay
[01:32.07] Chỉ thương em
[01:34.08] Đến hết cả cuộc đời này
[01:38.35] Dành tất cả thanh xuân vì anh
[01:41.62] Luôn động viên theo từng bước chân
[01:44.90] Trên đời này
[01:46.93] Không còn ai để thế thay
[02:16.40] Chẳng ở đâu xa mà cạnh anh
[02:20.16] Em là cả thế gian đây rồi
[02:23.17] Dẫu vui buồn
[02:25.18] Trái tim vẫn luôn bình yên
[02:29.45] Chỉ có em thôi không cần giàu sang
[02:32.68] Cũng thành một mái ấm gia đình
[02:35.94] Người tôi yêu là cô gái
[02:38.47] Tốt nhất trên trần gian
[02:42.48] Dù trái đất đến lúc tận thế
[02:45.50] Anh vẫn yêu em không đổi thay
[02:48.76] Chỉ thương em
[02:50.79] Đến hết cả cuộc đời này
[02:55.31] Dành tất cả thanh xuân vì anh
[02:58.32] Luôn động viên theo từng bước chân
[03:01.59] Trên đời này
[03:03.60] Không còn ai để thế thay
[03:07.86] Dù trái đất đến lúc tận thế
[03:11.14] Anh vẫn yêu em không đổi thay
[03:14.40] Chỉ thương em
[03:16.41] Đến hết cả cuộc đời này
[03:20.69] Người mang đến cho anh niềm vui
[03:23.91] Là nàng thơ anh tìm bấy lâu
[03:26.67] Nếu có kiếp sau
[03:29.17] Mong mình lại tìm thấy nhau
[03:33.19] Nếu có kiếp sau
[03:35.64] Mong mình vẫn là của nhau', 130)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:RNcLbetdjn43', 'Đợi', 'doi', 'Vũ.', 329, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/a/d/8/f/ad8fb5fcb3aab4950d8d89b919902de6.jpg', '[ar: Vũ.]
[ti: Đợi]
[by: ZingMp3 Lyric]
[length: 05:29]
[00:00.00]Bài Hát: Đợi
[00:02.00]Ca Sĩ: Vũ.
[00:04.00]
[00:44.39]Tìm bài ca buồn 
[00:46.93]
[00:47.48]Khi giấc mơ nay còn đâu 
[00:51.80]Bao sắc hồng vì cánh chim 
[00:57.43]Đang mải miết bay thật nhanh 
[01:02.81]
[01:06.33]Tìm vài câu chuyện 
[01:10.03]Từ lâu đã vỡ tan theo thời gian 
[01:14.38]Qua tiếng nhạc 
[01:17.63]Vì lúc xưa 
[01:19.98]Anh từng nhớ một người 
[01:23.74]Làm anh thao thức đêm 
[01:28.96]Ừ thì đấy một thời 
[01:32.26]Cùng sống trong điệu nhạc 
[01:34.47]Cùng nắm tay em đi khắp muôn nơi 
[01:39.05]
[01:40.11]Nhìn lại anh giờ đây 
[01:41.72]Chìm trong màn đêm 
[01:43.85]Ly cà phê dần tan cùng anh 
[01:45.69]Theo năm tháng theo em theo em
[01:52.24]Em như làn mây như lời hát 
[01:57.38]
[01:57.94]Em mơ màng theo trời nắng lên 
[02:03.56]Nhớ em nghẹn ngào 
[02:06.40]Nhớ em nhẹ nhàng 
[02:08.67]Xin em 
[02:10.11]Thôi đừng đi quá xa 
[02:14.79]Em như màu xanh mang hạnh phúc 
[02:20.37]Em buông tự do chạy với anh 
[02:26.03]Nhớ em nghẹn ngào 
[02:28.86]Nhớ em nhẹ nhàng 
[02:31.20]Xin em 
[02:34.47]Thôi đừng đi quá xa
[02:42.54]
[03:01.97]Tìm bài ca buồn 
[03:04.51]
[03:05.40]Khi giấc mơ nay còn đâu 
[03:09.72]Bao sắc hồng vì cánh chim 
[03:15.35]Đang mải miết bay thật nhanh 
[03:20.73]
[03:24.25]Tìm vài câu chuyện 
[03:27.95]Từ lâu đã vỡ tan theo thời gian 
[03:32.30]Qua tiếng nhạc 
[03:35.55]Vì lúc xưa 
[03:37.90]Anh từng nhớ một người 
[03:41.66]Làm anh thao thức đêm 
[03:46.88]Ừ thì đấy một thời 
[03:50.18]Cùng sống trong điệu nhạc 
[03:52.39]Cùng nắm tay em đi khắp muôn nơi 
[03:56.97]
[03:58.03]Nhìn lại anh giờ đây 
[03:59.64]Chìm trong màn đêm 
[04:01.73]Ly cà phê dần tan cùng anh 
[04:03.61]Theo năm tháng theo em theo em
[04:10.16]Em như làn mây như lời hát 
[04:15.30]
[04:15.86]Em mơ màng theo trời nắng lên 
[04:21.48]Nhớ em nghẹn ngào 
[04:24.24]Nhớ em nhẹ nhàng 
[04:26.56]Xin em 
[04:27.90]Thôi đừng đi quá xa 
[04:32.68]Em như màu xanh mang hạnh phúc 
[04:38.28]Em buông tự do chạy với anh 
[04:43.87]Nhớ em nghẹn ngào 
[04:46.73]Nhớ em nhẹ nhàng 
[04:48.98]Xin em 
[04:51.14]
[04:52.34]Thôi đừng đi quá xa
[05:00.34]', 209)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:fStrvB6ZezM9', 'Đi Để Trở Về', 'di de tro ve', 'Tiên Cookie', 209, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/covers/7/9/799e9e176dcb79e3406d48930ce108cf_1483416113.jpg', '[00:02.20] Tôi đang ở một nơi rất xa
[00:04.71] Nơi không có khói bụi thành phố
[00:08.70] Ở một nơi đẹp như mơ
[00:12.21] Trên cao êm êm mây trắng bay
[00:14.70] Lặng nhìn biển rộng sóng vỗ
[00:18.71] Cuộc đời tôi là những chuyến đi dài
[00:22.20] Vượt suối thác vượt núi dốc
[00:24.70] Dù chênh vênh có xá gì
[00:27.21] Có biết bao thứ tươi đẹp
[00:28.95] Vẫn cứ ở đó đang chờ tôi
[00:32.20] Người xung quanh ở nơi đây
[00:34.71] Thật dễ mến dẫu mới gặp
[00:37.20] Ánh mắt lấp lánh hiền hoà chào tôi
[00:39.96] Chào người bạn mới
[00:41.95] Từng chặng đường dài mà ta qua
[00:44.70] Giờ ngồi một mình lại thấy nhớ
[00:47.20] Ngày ngày mặt trời rạng ngời vươn cao
[00:49.95] Lên từ trên mái nhà
[00:52.20] Từng chặng đường dài mà ta qua
[00:54.70] Đều để lại kỷ niệm quý giá
[00:57.21] Để lại một điều rằng càng đi xa
[00:59.95] Ta càng thêm nhớ nhà
[01:02.46] Đi thật xa để trở về
[01:04.95] Đi thật xa để trở về
[01:07.45] Có một nơi để trở về
[01:09.46] Đi đi để trở về
[01:17.20] Tôi đang ở một nơi rất xa
[01:19.71] Nơi không có khói bụi thành phố
[01:23.70] Ở một nơi đẹp như mơ
[01:27.21] Trên cao êm êm mây trắng bay
[01:29.70] Lặng nhìn biển rộng sóng vỗ
[01:33.71] Cuộc đời tôi là những chuyến đi dài
[01:37.20] Vượt suối thác vượt núi dốc
[01:39.70] Dù chênh vênh có xá gì
[01:42.21] Có biết bao thứ tươi đẹp
[01:43.95] Vẫn cứ ở đó đang chờ tôi
[01:47.20] Người xung quanh ở nơi đây
[01:49.71] Thật dễ mến dẫu mới gặp
[01:52.20] Ánh mắt lấp lánh hiền hoà chào tôi
[01:54.96] Chào người bạn mới
[01:57.21] Từng chặng đường dài mà ta qua
[01:59.70] Giờ ngồi một mình lại thấy nhớ
[02:02.20] Ngày ngày mặt trời rạng ngời vươn cao
[02:04.95] Lên từ trên mái nhà
[02:07.20] Từng chặng đường dài mà ta qua
[02:09.70] Đều để lại kỷ niệm quý giá
[02:12.21] Để lại một điều rằng càng đi xa
[02:14.95] Ta càng thêm nhớ nhà
[02:17.46] Đi thật xa để trở về
[02:19.95] Đi thật xa để trở về
[02:22.45] Có một nơi để trở về
[02:24.46] Đi đi để trở về
[02:27.17] Cuộc đời thật đẹp khi được đi
[02:29.67] Muôn nơi xa xôi rộng lớn
[02:32.16] Nhưng ta vẫn có nơi để trở về
[02:34.91] Sau mỗi chuyến đi
[02:37.16] Điều kỳ diệu là con người ta
[02:39.66] Đi xa hơn để trưởng thành hơn
[02:42.17] Không quên mang theo bên cạnh
[02:43.91] Hành trang nỗi nhớ gia đình
[02:49.67] Từng chặng đường dài mà ta qua
[02:52.17] Giờ ngồi một mình lại thấy nhớ
[02:54.66] Ngày ngày mặt trời rạng ngời vươn cao
[02:57.42] Lên từ trên mái nhà
[02:59.66] Từng chặng đường dài mà ta qua
[03:02.16] Đều để lại kỷ niệm quý giá
[03:04.67] Để lại một điều rằng càng đi xa
[03:07.42] Ta càng thêm nhớ nhà
[03:09.91] Đi thật xa để trở về
[03:12.41] Đi thật xa để trở về
[03:15.16] Có một nơi để trở về
[03:16.92] Đi đi để trở về', 669)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:Ag0jE6e8P9y4', 'Yêu Thương Ngày Đó (Yêu Em Bất Chấp OST)', 'yeu thuong ngay do yeu em bat chap ost', 'SOOBIN', 263, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/d/0/b/8/d0b8df07498b8f0549eed787569efba6.jpg', '[00:29.08] Một mình lang thang
[00:29.84] Trên con phố vắng
[00:32.83] Nhớ bao yêu thương
[00:34.33] Ngày mình bên nhau
[00:36.58] Giờ mình anh nơi đây
[00:38.83] Cô đơn lặng lẽ
[00:43.33] Nơi phương xa
[00:44.45] Em có hạnh phúc
[00:47.45] Có thấy vui như anh
[00:48.97] Từng khiến em nở nụ cười
[00:53.08] Bồi hồi con tim anh nhận ra
[00:58.33] Khi em đưa đôi tay xa cách
[01:02.46] Là khi cơn mưa nặng hạt
[01:05.83] Chỉ còn anh nơi đây
[01:08.08] Lặng nhìn theo bóng em rời xa
[01:12.58] Tình yêu đôi khi
[01:14.08] Không như ta ước muốn
[01:16.71] Có mấy ai khi yêu
[01:18.58] Trao hết trọn con tim
[01:20.83] Một người ra đi
[01:22.71] Một người hoen mi
[01:24.21] Với bao lưu luyến
[01:27.58] Giờ còn lại đây
[01:28.75] Bao nhiêu câu hứa
[01:31.33] Có biết chăng em quên
[01:33.21] Nhưng với anh vẫn là
[01:35.83] Là ngày hôm qua
[01:37.33] Chỉ cần em quay về đây
[01:46.33] Liệu rằng nơi phương xa em thấy
[01:50.08] Gió khẽ đưa thương yêu
[01:51.89] Như muốn em quay về
[01:54.14] Ngày mình chia ly
[01:56.01] Một mình anh nơi đây
[01:57.89] Ấp ôm nỗi nhớ
[02:00.89] Tìm về nơi yêu thương xa cách
[02:05.02] Chỉ mong em luôn luôn
[02:06.89] Mãi nhớ nơi đây
[02:08.77] Nguyện cầu cơn mưa
[02:10.64] Sẽ làm em quay về đây
[02:15.52] Khi em đưa đôi tay xa cách
[02:19.94] Là khi cơn mưa nặng hạt
[02:23.31] Chỉ còn anh nơi đây
[02:25.56] Lặng nhìn theo bóng em rời xa
[02:30.06] Tình yêu đôi khi
[02:31.56] Không như ta ước muốn
[02:34.19] Có mấy ai khi yêu
[02:36.06] Trao hết trọn con tim
[02:38.69] Một người ra đi
[02:40.19] Một người hoen mi
[02:41.69] Với bao lưu luyến
[02:45.06] Giờ còn lại đây
[02:46.26] Bao nhiêu câu hứa
[02:48.81] Có biết chăng em quên
[02:50.69] Nhưng với anh vẫn là
[02:53.31] Là ngày hôm qua
[02:54.81] Chỉ cần em quay về đây
[03:01.47] Dòng người như đông hơn
[03:03.35] Trên phố vắng
[03:05.60] Có bước chân ai đi
[03:07.10] Ngỡ em quay về
[03:09.35] Đợi chờ nơi đây
[03:10.47] Lặng nhìn nơi xa
[03:12.35] Theo nỗi nhớ mong
[03:16.02] Có biết bao yêu thương
[03:18.65] Sẽ mãi nơi này
[03:22.40] Bồi hồi trong tim
[03:24.27] Thầm lặng đơn côi
[03:26.15] Giờ em đã mãi xa
[03:33.58] Tình yêu đôi khi
[03:34.71] Không như ta ước muốn
[03:36.96] Có mấy ai khi yêu
[03:38.83] Trao hết trọn con tim
[03:41.08] Một người ra đi
[03:42.96] Một người hoen mi
[03:44.46] Với bao lưu luyến
[03:47.83] Giờ còn lại đây
[03:48.99] Bao nhiêu câu hứa
[03:51.58] Có biết chăng em quên
[03:53.46] Nhưng với anh vẫn là
[03:55.71] Là ngày hôm qua
[03:57.58] Chỉ cần em quay về đây
[04:03.58] Là ngày hôm qua
[04:05.83] Chỉ cần em quay về đây', 145)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:vgc9p0CC1paA', 'Giá Như', 'gia nhu', 'SOOBIN', 144, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/cover/5/0/b/c/50bcdc396b6f63a7aa2e4e7e40df1015.jpg', '[00:07.11] Khi nắng nhẹ nhàng buông
[00:09.91] Là đôi ta cách rời
[00:15.07] Em nói mình dừng đi
[00:17.47] Dù anh đã hết lời
[00:22.67] Suốt bấy lâu nay
[00:23.87] Ta không còn trò chuyện thâu đêm
[00:26.26] Suốt bấy lâu
[00:27.04] Hai ta không cùng cạn và say mềm
[00:29.84] Cứ thế im lặng dần
[00:31.83] Cứ thế phai nhạt dần
[00:33.83] Tình yêu là thế
[00:35.00] Đâu có dễ nói cho nhau
[00:37.82] Giá như em muốn anh
[00:39.43] Là người sẽ cho em vơi đi bộn bề
[00:43.84] Để lại thấy ta bên nhau quên lối về
[00:47.41] Chẳng lẽ trái tim em sắt đá như vậy
[00:50.52] Em không bận tâm hay sao
[00:52.54] Giá như em muốn anh
[00:55.32] Cởi lòng với em khi hai ta giận hờn
[00:59.30] Để mình sẽ không bao giờ
[01:00.90] Nếm nỗi cô đơn
[01:02.81] Và giá mà em muốn như vậy
[01:10.75] Anh vẫn ở đây
[01:12.35] Nhưng không thể giữ
[01:14.35] Ta chia làm đôi
[01:15.52] Để những đau nhói tan thành mây
[01:18.32] Mưa rơi từ khi nào
[01:19.92] Trôi đi những dấu chân
[01:21.91] Theo từng bước ta qua
[01:24.61] Đã muốn vứt đi
[01:27.71] Nhưng lại chìm trong suy nghĩ
[01:31.31] Giá như ta không biết nhau
[01:33.30] Thì ngày xưa chuyện sẽ khác
[01:35.29] Sẽ chẳng có hôm nay
[01:36.89] Vì tổn thương anh luôn mang
[01:44.84] Giá như em muốn anh
[02:00.41] Giá như em muốn anh
[02:02.40] Cởi lòng với em khi hai ta giận hờn
[02:10.69] Giá mà em thật muốn vậy', 1095)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:f2dPgY1PuBrR', 'Ngày Mai Em Đi (Touliver Mix)', 'ngay mai em di touliver mix', 'Thái Thịnh', 218, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_jpeg/covers/6/9/695bf88c1edaf82e0bd5e085716e5598_1501654149.jpg', '[ar: Touliver, Lê Hiếu, Soobin Hoàng Sơn]
[ti: Ngày Mai Em Đi (Touliver Mix)]
[al: Ngày Mai Em Đi (Touliver Mix)]
[by: ZingMp3 Lyric]
[length: 03:38]
[00:00.00]Bài Hát: Ngày Mai Em Đi (Touliver Mix)
[00:02.00]Ca Sĩ: Touliver, Lê Hiếu, Soobin Hoàng Sơn
[00:04.00]
[00:19.90]Sao không ôm anh 
[00:20.69]Như lần đầu tiên em đến 
[00:24.57]Sao không hôn anh 
[00:25.37]Như ngày nào còn lưu luyến 
[00:29.39]Sao không vui lên 
[00:30.21]Như một thời bao thương mến 
[00:33.35]Màu mắt em sao hôm nay muộn phiền 
[00:36.90]
[00:39.09]Ta chia tay nhau 
[00:39.84]Khi tình này đang say đắm 
[00:43.84]Mai em ra đi 
[00:44.66]Ngôi nhà này hoang vu lắm 
[00:48.67]Anh gom cho em 
[00:49.48]Hết bao kỷ niệm êm ấm 
[00:52.55]Mọi thứ nơi đây đều thuộc về em 
[00:56.02]
[00:57.45]Ngày xưa cho nhau tình yêu đó
[00:59.69]Em mong em là tất cả 
[01:02.33]Và muốn anh luôn bên em 
[01:03.83]Không bao giờ lìa xa 
[01:06.95]Mộng ước ngây ngô lúc ban đầu 
[01:09.32]Nay đã xa rồi 
[01:12.01]Tiễn đưa em về một tình yêu mới 
[01:15.25]
[01:16.58]Ngày mai anh không còn em nữa
[01:18.90]Không cho ai là tất cả 
[01:21.51]Để nỗi cô đơn theo anh 
[01:23.02]Đến một miền trời xa 
[01:26.20]Để nhắc cho anh biết bao ngày 
[01:28.57]Hạnh phúc êm đềm 
[01:31.16]Nhắc cho anh 
[01:32.08]Luôn còn lại một trái tim
[01:35.52]Yêu em 
[01:37.90]
[01:58.28]Mai em ra đi 
[01:59.10]Em phải thật vui lên nhé 
[02:02.74]Em không yêu anh 
[02:03.92]Đâu cần buồn tênh đến thế 
[02:07.42]Không nên quan tâm 
[02:08.66]Đến cuộc đời anh hoang phế 
[02:11.67]Mình hãy quên đi bao câu hẹn thề 
[02:15.20]
[02:17.10]Xem như hai ta 
[02:18.28]Không thuộc về nhau em nhé 
[02:21.92]Khi xưa yêu nhau 
[02:23.10]Như một trò chơi thơ bé 
[02:26.76]Ba năm qua đi
[02:28.08]Lớn khôn rồi đành là thế 
[02:30.92]Đành rẽ chia hai người về hai lối
[02:34.05]
[02:35.73]Ngày xưa cho nhau tình yêu đó
[02:38.03]Em mong em là tất cả 
[02:40.73]Và muốn anh luôn bên em 
[02:42.22]Không bao giờ lìa xa 
[02:45.41]Mộng ước ngây ngô lúc ban đầu 
[02:47.73]Nay đã phai rồi 
[02:50.43]Tiễn đưa em về một tình yêu mới 
[02:53.38]
[02:54.94]Ngày mai anh không còn em nữa
[02:57.19]Không cho ai là tất cả 
[02:59.95]Để nỗi cô đơn theo anh 
[03:01.38]Đến một miền trời xa 
[03:04.50]Để nhắc cho anh biết bao ngày 
[03:06.87]Hạnh phúc êm đềm 
[03:09.56]Nhắc cho anh 
[03:10.44]Luôn còn lại một trái tim
[03:13.83]Yêu em 
[03:16.32]', 587)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

INSERT INTO SONGS (external_id, title, normalized_title, composer, duration_seconds, thumbnail_url, lyric, search_count)
VALUES ('zing:song:sIx64sLXwmGS', 'Chuyến Đi Của Năm (Đi Để Trở Về 2)', 'chuyen di cua nam di de tro ve 2', 'Tiên Cookie', 296, 'https://photo-resize-zmp3.zmdcdn.me/w240_r1x1_png/covers/5/3/531a73c8d39095bfe95abc2bd5881aa7_1514540094.png', '[ar: Soobin Hoàng Sơn]
[ti: Đi Để Trở Về]
[by: fuyuhuynh012]
[length: 03:47]
[id: bwcnkpcu]
[00:05.03]Bài Hát: Chuyến Đi Của Năm
[00:08.97]Ca Sĩ: Soobin Hoàng Sơn
[00:12.53]
[00:22.03]Sớm nay
[00:25.03]Sáng xuân tinh mơ 
[00:26.16]Vội vàng lên chuyến xe
[00:30.10]Chuyến xe đưa tôi
[00:31.47]Trở về nhà với mẹ
[00:35.60]Lòng tôi đã xôn xao
[00:37.41]Nôn nao từ tận tối qua
[00:40.29]Tôi như đứa trẻ.
[00:42.87]Cầu mong rằng 
[00:45.85]Bánh xe lăn nhanh
[00:47.10]Lăn đều hơn nữa nhé
[00:50.97]Trái tim tôi đang 
[00:52.47]Đập vội vàng lắm rồi
[00:56.65]Một năm đã đi xa
[00:58.27]Đi bao nhiêu là chuyến đi
[01:01.08]Không giống chuyến này
[01:03.49]Vì tôi 
[01:05.15]Tôi đang về quê
[01:07.69]Chuyến này đặc biệt
[01:09.44]Vì tôi biết có người đợi
[01:13.88]Chờ tôi
[01:15.44]Nhớ tôi và mong ngóng tôi
[01:19.25]Thế nên phải mau về thôi
[01:21.76]Mùa xuân ngoài kia nở ngập tràn hoa
[01:26.07]
[01:27.32]Ngồi nhiều giờ trên xe
[01:29.25]Tôi ngắm nhìn mọi người xung quanh
[01:32.51]Đều bồi hồi rất muốn nhanh về đến nhà
[01:36.73]Nhớ những gì một năm đã qua
[01:40.23]Từng chặng đường đã xa
[01:42.92]Kỉ niệm buồn lẫn vui 
[01:44.60]Ta từng trải qua.
[01:47.17]
[01:48.09]Chuyện của mình trong năm
[01:50.01]Tôi muốn kể hơn bao giờ hết
[01:53.26]Kể rằng mình đã sống yêu đời thế nào
[01:57.26]Không chuyến nào bằng được chuyến đi
[02:01.15]Về nhà cùng gia đình
[02:03.82]Lòng rộn ràng biết bao
[02:05.45]Đi để trở về.
[02:08.64]
[02:29.70]Cầu mong rằng 
[02:32.82]Bánh xe lăn nhanh
[02:34.14]Lăn đều hơn nữa nhé
[02:38.01]Trái tim tôi đang 
[02:39.42]Đập vội vàng lắm rồi
[02:43.57]Một năm đã đi xa
[02:45.14]Đi bao nhiêu là chuyến đi
[02:48.18]Không giống chuyến này
[02:50.36]Vì tôi 
[02:52.05]Tôi đang về quê
[02:54.74]Chuyến này đặc biệt
[02:56.43]Vì tôi biết có người chờ
[03:00.99]Chờ tôi
[03:02.75]Nhớ tôi và mong ngóng tôi
[03:06.05]Thế nên phải mau về thôi
[03:08.58]Mùa xuân ngoài kia nở ngập tràn hoa
[03:12.70]
[03:14.20]Ngồi nhiều giờ trên xe
[03:16.18]Tôi ngắm nhìn mọi người xung quanh
[03:19.33]Đều bồi hồi rất muốn nhanh về đến nhà
[03:23.27]Nhớ những gì một năm đã qua
[03:27.27]Từng chặng đường đã xa
[03:29.83]Kỉ niệm buồn lẫn vui 
[03:31.58]Ta từng trải qua.
[03:33.79]
[03:35.02]Chuyện của mình trong năm
[03:37.19]Tôi muốn kể hơn bao giờ hết
[03:40.27]Kể rằng mình đã sống yêu đời thế nào
[03:44.15]Không chuyến nào bằng được chuyến đi
[03:48.08]Về nhà cùng gia đình
[03:50.71]Lòng rộn ràng biết bao
[03:52.40]Đi để trở về.
[03:54.58]
[03:56.08]Đời nhiều cuộc phiêu du
[03:58.15]Nhưng chuyến mà ta mong đợi nhất
[04:01.02]Chẳng phải là 
[04:02.08]Chuyến đi về nhà hay sao?
[04:05.46]Đi xa để trở về 
[04:07.84]Chính nơi gia đình chờ mong ta
[04:11.59]Không thể chờ nữa đâu
[04:13.33]Ta phải về thôi
[04:15.90]
[04:16.90]Chuyện của mình trong năm
[04:18.71]Tôi muốn kể hơn bao giờ hết
[04:22.02]Kể rằng mình đã sống yêu đời thế nào
[04:25.90]Không chuyến nào bằng được chuyến đi
[04:29.83]Về nhà cùng gia đình
[04:32.46]Lòng rộn ràng biết bao
[04:34.15]Đi để trở về.
[04:36.29]
[04:37.72]Lòng rộn ràng biết bao
[04:42.92]
[04:45.79]Đi để trở về
[04:48.92]', 268)
ON CONFLICT (external_id) DO UPDATE SET
    title = EXCLUDED.title,
    composer = EXCLUDED.composer,
    duration_seconds = EXCLUDED.duration_seconds,
    thumbnail_url = EXCLUDED.thumbnail_url,
    lyric = EXCLUDED.lyric;

-- 4. SONG_ARTISTS (Liên kết ca sĩ - bài hát)
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:d7qtCrRV89zn' AND a.external_id = 'zing:artist:Son-Tung-M-TP'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:USJgi8Pq9Ouf' AND a.external_id = 'zing:artist:Son-Tung-M-TP'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:yTPspCIlrm0y' AND a.external_id = 'zing:artist:Son-Tung-M-TP'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:wd5Yipfmfk8o' AND a.external_id = 'zing:artist:Son-Tung-M-TP'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:fITftN1bwVlE' AND a.external_id = 'zing:artist:Son-Tung-M-TP'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:qYD31k5TVTCQ' AND a.external_id = 'zing:artist:Son-Tung-M-TP'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:xt25l2WR1SyF' AND a.external_id = 'zing:artist:Son-Tung-M-TP'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:um7LDE6F9vJl' AND a.external_id = 'zing:artist:Son-Tung-M-TP'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:woP4j7NXbgG' AND a.external_id = 'zing:artist:Son-Tung-M-TP'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:XYt1PjovFybk' AND a.external_id = 'zing:artist:Son-Tung-M-TP'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:wzGj8kLgRLRf' AND a.external_id = 'zing:artist:Son-Tung-M-TP'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:VNRGA9G6YOst' AND a.external_id = 'zing:artist:Ngot'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:qWxRnKgOaDty' AND a.external_id = 'zing:artist:Nguyen-Hoang-Dung'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:Aj9nmuJPIJiJ' AND a.external_id = 'zing:artist:DTAP'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:fzhMtPSlNWE' AND a.external_id = 'zing:artist:Lil-Knight-IW78EF9C'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:E570cjfKE93E' AND a.external_id = 'zing:artist:LK-USUK'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:wdw5qNiHfe78' AND a.external_id = 'zing:artist:Khac-Viet'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:iu8mQBz49Nd7' AND a.external_id = 'zing:artist:Yanbi'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:Y7hd6JOiUFzU' AND a.external_id = 'zing:artist:AMEE'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:khMSCPgJRfLe' AND a.external_id = 'zing:artist:Chau-Khai-Phong'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:v67bI74jR5k5' AND a.external_id = 'zing:artist:Van-Vo-Ngoc-Nhan'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:oUUXYWTOam92' AND a.external_id = 'zing:artist:Kimmese'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:di5zpFQIfwk' AND a.external_id = 'zing:artist:Cloud-5'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:BdMqkaVH2iJz' AND a.external_id = 'zing:artist:S-TRONG'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:vEeFGLlJl5F0' AND a.external_id = 'zing:artist:Uyen-Linh'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:urDmURjTVqRJ' AND a.external_id = 'zing:artist:Vu'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:z4CrPlzJOHV3' AND a.external_id = 'zing:artist:Vu'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:XwnuuLEHyBLq' AND a.external_id = 'zing:artist:Vu'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:xjBUuqxu52Yt' AND a.external_id = 'zing:artist:Vu'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:6O8vsOeipvyy' AND a.external_id = 'zing:artist:Huong-Ly'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:sI58MZHH1jg4' AND a.external_id = 'zing:artist:Vu'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:s219Wi38R3d0' AND a.external_id = 'zing:artist:Vu'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:CwzTW4GClxec' AND a.external_id = 'zing:artist:Trung-Tu'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:msgiw3jkGCSN' AND a.external_id = 'zing:artist:Vu'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:tOmVpVl7FulX' AND a.external_id = 'zing:artist:Vu'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:wQeXJ6nheQ2O' AND a.external_id = 'zing:artist:Hoang-Lan-Singer'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:RNcLbetdjn43' AND a.external_id = 'zing:artist:Vu'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:fStrvB6ZezM9' AND a.external_id = 'zing:artist:SOOBIN'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:Ag0jE6e8P9y4' AND a.external_id = 'zing:artist:SOOBIN'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:vgc9p0CC1paA' AND a.external_id = 'zing:artist:SOOBIN'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:f2dPgY1PuBrR' AND a.external_id = 'zing:artist:Touliver'
ON CONFLICT (song_id, artist_id) DO NOTHING;
INSERT INTO SONG_ARTISTS (song_id, artist_id)
SELECT s.song_id, a.artist_id
FROM SONGS s, ARTISTS a
WHERE s.external_id = 'zing:song:sIx64sLXwmGS' AND a.external_id = 'zing:artist:SOOBIN'
ON CONFLICT (song_id, artist_id) DO NOTHING;

-- 5. PLAYLISTS (Mỗi user có 1 playlist FAVORITES và 1-2 playlists cá nhân)
INSERT INTO PLAYLISTS (user_id, name, description, type)
SELECT user_id, 'Favorites', 'Danh sách bài hát yêu thích', 'FAVORITES'
FROM USERS WHERE username = 'alice'
ON CONFLICT DO NOTHING;
INSERT INTO PLAYLISTS (user_id, name, description, type)
SELECT user_id, 'Favorites', 'Danh sách bài hát yêu thích', 'FAVORITES'
FROM USERS WHERE username = 'bob'
ON CONFLICT DO NOTHING;
INSERT INTO PLAYLISTS (user_id, name, description, type)
SELECT user_id, 'Favorites', 'Danh sách bài hát yêu thích', 'FAVORITES'
FROM USERS WHERE username = 'dong'
ON CONFLICT DO NOTHING;
INSERT INTO PLAYLISTS (user_id, name, description, type)
SELECT user_id, 'Favorites', 'Danh sách bài hát yêu thích', 'FAVORITES'
FROM USERS WHERE username = 'hiep'
ON CONFLICT DO NOTHING;
INSERT INTO PLAYLISTS (user_id, name, description, type)
SELECT user_id, 'Favorites', 'Danh sách bài hát yêu thích', 'FAVORITES'
FROM USERS WHERE username = 'minh'
ON CONFLICT DO NOTHING;
INSERT INTO PLAYLISTS (user_id, name, description, type)
SELECT user_id, 'Favorites', 'Danh sách bài hát yêu thích', 'FAVORITES'
FROM USERS WHERE username = 'phuong'
ON CONFLICT DO NOTHING;
INSERT INTO PLAYLISTS (user_id, name, description, type)
SELECT user_id, 'Favorites', 'Danh sách bài hát yêu thích', 'FAVORITES'
FROM USERS WHERE username = 'quang'
ON CONFLICT DO NOTHING;
INSERT INTO PLAYLISTS (user_id, name, description, thumbnail_url, custom_thumbnail, type)
SELECT user_id, 'Top V-Pop Thịnh Hành', 'Các bản hit V-Pop gây bão trên bảng xếp hạng', 'https://photo-resize-zmp3.zmdcdn.me/w320_r1x1_jpeg/cover/c/0/8/d/c08dc42fbb1b93cf4d88e04e2ce2d790.jpg', TRUE, 'NORMAL'
FROM USERS WHERE username = 'alice'
ON CONFLICT DO NOTHING;
INSERT INTO PLAYLISTS (user_id, name, description, thumbnail_url, custom_thumbnail, type)
SELECT user_id, 'Giai Điệu Acoustic Chill', 'Những giai điệu mộc mạc lắng đọng cho ngày mưa', 'https://photo-resize-zmp3.zmdcdn.me/w320_r1x1_jpeg/cover/a/8/8/b/a88b56ea6d93b169ea2dfc904e5d6d84.jpg', TRUE, 'NORMAL'
FROM USERS WHERE username = 'alice'
ON CONFLICT DO NOTHING;
INSERT INTO PLAYLISTS (user_id, name, description, thumbnail_url, custom_thumbnail, type)
SELECT user_id, 'Tập Trung Học Bài & Code', 'Nhạc nhẹ nhàng không lời và lofi giúp tập trung', 'https://photo-resize-zmp3.zmdcdn.me/w320_r1x1_jpeg/cover/d/3/2/5/d325faaeef46e8c459fbe9f7063da59f.jpg', TRUE, 'NORMAL'
FROM USERS WHERE username = 'bob'
ON CONFLICT DO NOTHING;
INSERT INTO PLAYLISTS (user_id, name, description, thumbnail_url, custom_thumbnail, type)
SELECT user_id, 'Rap Việt Đỉnh Cao', 'Tổng hợp các bài rap chất lừ từ Đen Vâu, HIEUTHUHAI', 'https://photo-resize-zmp3.zmdcdn.me/w320_r1x1_jpeg/cover/6/7/b/5/67b5e40733d1b849e7b233cf374ec36e.jpg', TRUE, 'NORMAL'
FROM USERS WHERE username = 'dong'
ON CONFLICT DO NOTHING;
INSERT INTO PLAYLISTS (user_id, name, description, thumbnail_url, custom_thumbnail, type)
SELECT user_id, 'Đêm Khuya Tâm Trạng', 'Những bản tình ca thổn thức khi thành phố về đêm', 'https://photo-resize-zmp3.zmdcdn.me/w320_r1x1_jpeg/cover/8/9/8/3/89839446d7f9502bfa4e321b1a7d6560.jpg', TRUE, 'NORMAL'
FROM USERS WHERE username = 'dong'
ON CONFLICT DO NOTHING;
INSERT INTO PLAYLISTS (user_id, name, description, thumbnail_url, custom_thumbnail, type)
SELECT user_id, 'Ballad Buồn Tan Nát Con Tim', 'Những khúc ca da diết chạm đáy cảm xúc', 'https://photo-resize-zmp3.zmdcdn.me/w320_r1x1_jpeg/cover/3/b/1/e/3b1ecad7e7e6dcce8ecb8bbcebcbe096.jpg', TRUE, 'NORMAL'
FROM USERS WHERE username = 'hiep'
ON CONFLICT DO NOTHING;
INSERT INTO PLAYLISTS (user_id, name, description, thumbnail_url, custom_thumbnail, type)
SELECT user_id, 'Cà Phê Sáng Cuối Tuần', 'Bắt đầu ngày mới đầy năng lượng cùng âm nhạc tươi vui', 'https://photo-resize-zmp3.zmdcdn.me/w320_r1x1_jpeg/cover/e/a/e/6/eae6ff35fa4537c35d9a9fefb09f471e.jpg', TRUE, 'NORMAL'
FROM USERS WHERE username = 'minh'
ON CONFLICT DO NOTHING;
INSERT INTO PLAYLISTS (user_id, name, description, thumbnail_url, custom_thumbnail, type)
SELECT user_id, 'Nhạc Nắng Chiều Hoàng Hôn', 'Chill nhẹ nhàng thư giãn sau giờ tan trường', 'https://photo-resize-zmp3.zmdcdn.me/w320_r1x1_jpeg/cover/7/4/3/9/74391ea2803b879e61db1d06575d5069.jpg', TRUE, 'NORMAL'
FROM USERS WHERE username = 'phuong'
ON CONFLICT DO NOTHING;
INSERT INTO PLAYLISTS (user_id, name, description, thumbnail_url, custom_thumbnail, type)
SELECT user_id, 'Workout & Gym Energy', 'Nhạc sôi động bùng nổ tiếp thêm năng lượng tập luyện', 'https://photo-resize-zmp3.zmdcdn.me/w320_r1x1_jpeg/cover/4/0/b/1/40b157fe5e69e7be59b9e6e872e48232.jpg', TRUE, 'NORMAL'
FROM USERS WHERE username = 'quang'
ON CONFLICT DO NOTHING;

-- 6. PLAYLIST_SONGS (Gán bài hát vào các playlist mẫu)
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:d7qtCrRV89zn'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.name = 'Top V-Pop Thịnh Hành' AND s.external_id = 'zing:song:d7qtCrRV89zn'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'bob' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:USJgi8Pq9Ouf'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.name = 'Giai Điệu Acoustic Chill' AND s.external_id = 'zing:song:USJgi8Pq9Ouf'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:yTPspCIlrm0y'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 3
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'bob' AND p.name = 'Tập Trung Học Bài & Code' AND s.external_id = 'zing:song:yTPspCIlrm0y'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'hiep' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:wd5Yipfmfk8o'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 4
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.name = 'Rap Việt Đỉnh Cao' AND s.external_id = 'zing:song:wd5Yipfmfk8o'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'minh' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:fITftN1bwVlE'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 5
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.name = 'Đêm Khuya Tâm Trạng' AND s.external_id = 'zing:song:fITftN1bwVlE'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'phuong' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:qYD31k5TVTCQ'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'hiep' AND p.name = 'Ballad Buồn Tan Nát Con Tim' AND s.external_id = 'zing:song:qYD31k5TVTCQ'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'quang' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:xt25l2WR1SyF'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'minh' AND p.name = 'Cà Phê Sáng Cuối Tuần' AND s.external_id = 'zing:song:xt25l2WR1SyF'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:um7LDE6F9vJl'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 3
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'phuong' AND p.name = 'Nhạc Nắng Chiều Hoàng Hôn' AND s.external_id = 'zing:song:um7LDE6F9vJl'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'bob' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:woP4j7NXbgG'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 4
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'quang' AND p.name = 'Workout & Gym Energy' AND s.external_id = 'zing:song:woP4j7NXbgG'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:XYt1PjovFybk'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 5
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.name = 'Top V-Pop Thịnh Hành' AND s.external_id = 'zing:song:XYt1PjovFybk'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'hiep' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:wzGj8kLgRLRf'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.name = 'Giai Điệu Acoustic Chill' AND s.external_id = 'zing:song:wzGj8kLgRLRf'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'minh' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:VNRGA9G6YOst'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'bob' AND p.name = 'Tập Trung Học Bài & Code' AND s.external_id = 'zing:song:VNRGA9G6YOst'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'phuong' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:qWxRnKgOaDty'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 3
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.name = 'Rap Việt Đỉnh Cao' AND s.external_id = 'zing:song:qWxRnKgOaDty'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'quang' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:Aj9nmuJPIJiJ'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 4
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.name = 'Đêm Khuya Tâm Trạng' AND s.external_id = 'zing:song:Aj9nmuJPIJiJ'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 3
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:fzhMtPSlNWE'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 5
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'hiep' AND p.name = 'Ballad Buồn Tan Nát Con Tim' AND s.external_id = 'zing:song:fzhMtPSlNWE'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 3
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'bob' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:E570cjfKE93E'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'minh' AND p.name = 'Cà Phê Sáng Cuối Tuần' AND s.external_id = 'zing:song:E570cjfKE93E'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 3
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:wdw5qNiHfe78'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'phuong' AND p.name = 'Nhạc Nắng Chiều Hoàng Hôn' AND s.external_id = 'zing:song:wdw5qNiHfe78'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 3
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'hiep' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:iu8mQBz49Nd7'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 3
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'quang' AND p.name = 'Workout & Gym Energy' AND s.external_id = 'zing:song:iu8mQBz49Nd7'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 3
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'minh' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:Y7hd6JOiUFzU'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 4
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.name = 'Top V-Pop Thịnh Hành' AND s.external_id = 'zing:song:Y7hd6JOiUFzU'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 3
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'phuong' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:khMSCPgJRfLe'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 5
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.name = 'Giai Điệu Acoustic Chill' AND s.external_id = 'zing:song:khMSCPgJRfLe'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 3
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'quang' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:v67bI74jR5k5'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'bob' AND p.name = 'Tập Trung Học Bài & Code' AND s.external_id = 'zing:song:v67bI74jR5k5'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 4
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:oUUXYWTOam92'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.name = 'Rap Việt Đỉnh Cao' AND s.external_id = 'zing:song:oUUXYWTOam92'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 4
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'bob' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:di5zpFQIfwk'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 3
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.name = 'Đêm Khuya Tâm Trạng' AND s.external_id = 'zing:song:di5zpFQIfwk'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 4
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:BdMqkaVH2iJz'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 4
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'hiep' AND p.name = 'Ballad Buồn Tan Nát Con Tim' AND s.external_id = 'zing:song:BdMqkaVH2iJz'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 4
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'hiep' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:vEeFGLlJl5F0'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 5
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'minh' AND p.name = 'Cà Phê Sáng Cuối Tuần' AND s.external_id = 'zing:song:vEeFGLlJl5F0'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 4
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'minh' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:urDmURjTVqRJ'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'phuong' AND p.name = 'Nhạc Nắng Chiều Hoàng Hôn' AND s.external_id = 'zing:song:urDmURjTVqRJ'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 4
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'phuong' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:z4CrPlzJOHV3'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'quang' AND p.name = 'Workout & Gym Energy' AND s.external_id = 'zing:song:z4CrPlzJOHV3'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 4
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'quang' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:XwnuuLEHyBLq'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 3
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.name = 'Top V-Pop Thịnh Hành' AND s.external_id = 'zing:song:XwnuuLEHyBLq'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 5
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:xjBUuqxu52Yt'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 4
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.name = 'Giai Điệu Acoustic Chill' AND s.external_id = 'zing:song:xjBUuqxu52Yt'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 5
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'bob' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:6O8vsOeipvyy'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 5
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'bob' AND p.name = 'Tập Trung Học Bài & Code' AND s.external_id = 'zing:song:6O8vsOeipvyy'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 5
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:sI58MZHH1jg4'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.name = 'Rap Việt Đỉnh Cao' AND s.external_id = 'zing:song:sI58MZHH1jg4'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 5
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'hiep' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:s219Wi38R3d0'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.name = 'Đêm Khuya Tâm Trạng' AND s.external_id = 'zing:song:s219Wi38R3d0'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 5
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'minh' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:CwzTW4GClxec'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 3
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'hiep' AND p.name = 'Ballad Buồn Tan Nát Con Tim' AND s.external_id = 'zing:song:CwzTW4GClxec'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 5
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'phuong' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:msgiw3jkGCSN'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 4
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'minh' AND p.name = 'Cà Phê Sáng Cuối Tuần' AND s.external_id = 'zing:song:msgiw3jkGCSN'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 5
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'quang' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:tOmVpVl7FulX'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 5
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'phuong' AND p.name = 'Nhạc Nắng Chiều Hoàng Hôn' AND s.external_id = 'zing:song:tOmVpVl7FulX'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 6
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:wQeXJ6nheQ2O'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'quang' AND p.name = 'Workout & Gym Energy' AND s.external_id = 'zing:song:wQeXJ6nheQ2O'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 6
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'bob' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:RNcLbetdjn43'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.name = 'Top V-Pop Thịnh Hành' AND s.external_id = 'zing:song:RNcLbetdjn43'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 6
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:fStrvB6ZezM9'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 3
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'alice' AND p.name = 'Giai Điệu Acoustic Chill' AND s.external_id = 'zing:song:fStrvB6ZezM9'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 6
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'hiep' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:Ag0jE6e8P9y4'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 4
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'bob' AND p.name = 'Tập Trung Học Bài & Code' AND s.external_id = 'zing:song:Ag0jE6e8P9y4'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 6
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'minh' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:vgc9p0CC1paA'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 5
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.name = 'Rap Việt Đỉnh Cao' AND s.external_id = 'zing:song:vgc9p0CC1paA'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 6
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'phuong' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:f2dPgY1PuBrR'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 1
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'dong' AND p.name = 'Đêm Khuya Tâm Trạng' AND s.external_id = 'zing:song:f2dPgY1PuBrR'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 6
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'quang' AND p.type = 'FAVORITES' AND s.external_id = 'zing:song:sIx64sLXwmGS'
ON CONFLICT (playlist_id, song_id) DO NOTHING;
INSERT INTO PLAYLIST_SONGS (playlist_id, song_id, position)
SELECT p.playlist_id, s.song_id, 2
FROM PLAYLISTS p, SONGS s, USERS u
WHERE p.user_id = u.user_id AND u.username = 'hiep' AND p.name = 'Ballad Buồn Tan Nát Con Tim' AND s.external_id = 'zing:song:sIx64sLXwmGS'
ON CONFLICT (playlist_id, song_id) DO NOTHING;

-- 7. SEARCH_HISTORY (Lịch sử tìm kiếm mẫu của người dùng)
INSERT INTO SEARCH_HISTORY (user_id, keyword, normalized_keyword, search_type, result_count, searched_at)
SELECT user_id, 'Sơn Tùng M-TP', 'son tung m tp', 'ARTIST', 10, NOW() - INTERVAL '1 hours'
FROM USERS WHERE username = 'alice';
INSERT INTO SEARCH_HISTORY (user_id, keyword, normalized_keyword, search_type, result_count, searched_at)
SELECT user_id, 'Đen Vâu', 'den vau', 'SONG', 12, NOW() - INTERVAL '4 hours'
FROM USERS WHERE username = 'bob';
INSERT INTO SEARCH_HISTORY (user_id, keyword, normalized_keyword, search_type, result_count, searched_at)
SELECT user_id, 'Bước Qua Mùa Cô Đơn', 'buoc qua mua co don', 'SONG', 14, NOW() - INTERVAL '7 hours'
FROM USERS WHERE username = 'dong';
INSERT INTO SEARCH_HISTORY (user_id, keyword, normalized_keyword, search_type, result_count, searched_at)
SELECT user_id, 'Chúng Ta Của Hiện Tại', 'chung ta cua hien tai', 'ARTIST', 16, NOW() - INTERVAL '10 hours'
FROM USERS WHERE username = 'hiep';
INSERT INTO SEARCH_HISTORY (user_id, keyword, normalized_keyword, search_type, result_count, searched_at)
SELECT user_id, 'HIEUTHUHAI', 'hieuthuhai', 'SONG', 18, NOW() - INTERVAL '13 hours'
FROM USERS WHERE username = 'minh';
INSERT INTO SEARCH_HISTORY (user_id, keyword, normalized_keyword, search_type, result_count, searched_at)
SELECT user_id, 'Waiting For You', 'waiting for you', 'SONG', 20, NOW() - INTERVAL '16 hours'
FROM USERS WHERE username = 'phuong';
INSERT INTO SEARCH_HISTORY (user_id, keyword, normalized_keyword, search_type, result_count, searched_at)
SELECT user_id, 'See Tình', 'see tinh', 'ARTIST', 22, NOW() - INTERVAL '19 hours'
FROM USERS WHERE username = 'quang';
INSERT INTO SEARCH_HISTORY (user_id, keyword, normalized_keyword, search_type, result_count, searched_at)
SELECT user_id, 'Nơi Này Có Anh', 'noi nay co anh', 'SONG', 24, NOW() - INTERVAL '22 hours'
FROM USERS WHERE username = 'alice';
INSERT INTO SEARCH_HISTORY (user_id, keyword, normalized_keyword, search_type, result_count, searched_at)
SELECT user_id, 'Bật Tình Yêu Lên', 'bat tinh yeu len', 'SONG', 26, NOW() - INTERVAL '25 hours'
FROM USERS WHERE username = 'bob';
INSERT INTO SEARCH_HISTORY (user_id, keyword, normalized_keyword, search_type, result_count, searched_at)
SELECT user_id, 'Ngủ Một Mình', 'ngu mot minh', 'ARTIST', 28, NOW() - INTERVAL '28 hours'
FROM USERS WHERE username = 'dong';

-- 8. SEARCH_STATISTICS (Thống kê từ khóa hot / trending)
INSERT INTO SEARCH_STATISTICS (keyword, normalized_keyword, search_type, search_count, last_searched_at)
VALUES ('Sơn Tùng M-TP', 'son tung m tp', 'ARTIST', 1500, NOW() - INTERVAL '0 minutes')
ON CONFLICT (normalized_keyword, search_type) DO UPDATE SET
    search_count = EXCLUDED.search_count,
    last_searched_at = EXCLUDED.last_searched_at;
INSERT INTO SEARCH_STATISTICS (keyword, normalized_keyword, search_type, search_count, last_searched_at)
VALUES ('Đen Vâu', 'den vau', 'SONG', 1390, NOW() - INTERVAL '15 minutes')
ON CONFLICT (normalized_keyword, search_type) DO UPDATE SET
    search_count = EXCLUDED.search_count,
    last_searched_at = EXCLUDED.last_searched_at;
INSERT INTO SEARCH_STATISTICS (keyword, normalized_keyword, search_type, search_count, last_searched_at)
VALUES ('Bước Qua Mùa Cô Đơn', 'buoc qua mua co don', 'SONG', 1280, NOW() - INTERVAL '30 minutes')
ON CONFLICT (normalized_keyword, search_type) DO UPDATE SET
    search_count = EXCLUDED.search_count,
    last_searched_at = EXCLUDED.last_searched_at;
INSERT INTO SEARCH_STATISTICS (keyword, normalized_keyword, search_type, search_count, last_searched_at)
VALUES ('Chúng Ta Của Hiện Tại', 'chung ta cua hien tai', 'ARTIST', 1170, NOW() - INTERVAL '45 minutes')
ON CONFLICT (normalized_keyword, search_type) DO UPDATE SET
    search_count = EXCLUDED.search_count,
    last_searched_at = EXCLUDED.last_searched_at;
INSERT INTO SEARCH_STATISTICS (keyword, normalized_keyword, search_type, search_count, last_searched_at)
VALUES ('HIEUTHUHAI', 'hieuthuhai', 'SONG', 1060, NOW() - INTERVAL '60 minutes')
ON CONFLICT (normalized_keyword, search_type) DO UPDATE SET
    search_count = EXCLUDED.search_count,
    last_searched_at = EXCLUDED.last_searched_at;
INSERT INTO SEARCH_STATISTICS (keyword, normalized_keyword, search_type, search_count, last_searched_at)
VALUES ('Waiting For You', 'waiting for you', 'SONG', 950, NOW() - INTERVAL '75 minutes')
ON CONFLICT (normalized_keyword, search_type) DO UPDATE SET
    search_count = EXCLUDED.search_count,
    last_searched_at = EXCLUDED.last_searched_at;
INSERT INTO SEARCH_STATISTICS (keyword, normalized_keyword, search_type, search_count, last_searched_at)
VALUES ('See Tình', 'see tinh', 'ARTIST', 840, NOW() - INTERVAL '90 minutes')
ON CONFLICT (normalized_keyword, search_type) DO UPDATE SET
    search_count = EXCLUDED.search_count,
    last_searched_at = EXCLUDED.last_searched_at;
INSERT INTO SEARCH_STATISTICS (keyword, normalized_keyword, search_type, search_count, last_searched_at)
VALUES ('Nơi Này Có Anh', 'noi nay co anh', 'SONG', 730, NOW() - INTERVAL '105 minutes')
ON CONFLICT (normalized_keyword, search_type) DO UPDATE SET
    search_count = EXCLUDED.search_count,
    last_searched_at = EXCLUDED.last_searched_at;
INSERT INTO SEARCH_STATISTICS (keyword, normalized_keyword, search_type, search_count, last_searched_at)
VALUES ('Bật Tình Yêu Lên', 'bat tinh yeu len', 'SONG', 620, NOW() - INTERVAL '120 minutes')
ON CONFLICT (normalized_keyword, search_type) DO UPDATE SET
    search_count = EXCLUDED.search_count,
    last_searched_at = EXCLUDED.last_searched_at;
INSERT INTO SEARCH_STATISTICS (keyword, normalized_keyword, search_type, search_count, last_searched_at)
VALUES ('Ngủ Một Mình', 'ngu mot minh', 'ARTIST', 510, NOW() - INTERVAL '135 minutes')
ON CONFLICT (normalized_keyword, search_type) DO UPDATE SET
    search_count = EXCLUDED.search_count,
    last_searched_at = EXCLUDED.last_searched_at;

-- 9. CẬP NHẬT SEQUENCES (Đảm bảo tự tăng không bị đụng ID)
SELECT setval('users_user_id_seq', COALESCE((SELECT MAX(user_id) FROM USERS), 1));
SELECT setval('artists_artist_id_seq', COALESCE((SELECT MAX(artist_id) FROM ARTISTS), 1));
SELECT setval('songs_song_id_seq', COALESCE((SELECT MAX(song_id) FROM SONGS), 1));
SELECT setval('playlists_playlist_id_seq', COALESCE((SELECT MAX(playlist_id) FROM PLAYLISTS), 1));
SELECT setval('search_history_history_id_seq', COALESCE((SELECT MAX(history_id) FROM SEARCH_HISTORY), 1));
SELECT setval('search_statistics_statistic_id_seq', COALESCE((SELECT MAX(statistic_id) FROM SEARCH_STATISTICS), 1));
