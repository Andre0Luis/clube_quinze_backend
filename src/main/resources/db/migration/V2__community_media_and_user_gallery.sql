CREATE TABLE community_post_media (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    position INT NOT NULL,
    image_url VARCHAR(1024),
    image_base64 LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_media_post FOREIGN KEY (post_id) REFERENCES community_posts (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_community_post_media_position ON community_post_media (post_id, position);

INSERT INTO community_post_media (post_id, position, image_url, image_base64)
SELECT id, 0, image_url, image_base64
FROM community_posts
WHERE image_url IS NOT NULL OR image_base64 IS NOT NULL;

ALTER TABLE community_posts DROP COLUMN image_url;
ALTER TABLE community_posts DROP COLUMN image_base64;

ALTER TABLE usuarios ADD COLUMN profile_picture_url VARCHAR(1024);
ALTER TABLE usuarios ADD COLUMN profile_picture_base64 LONGTEXT;

CREATE TABLE user_gallery_photos (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    position INT NOT NULL,
    image_url VARCHAR(1024),
    image_base64 LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gallery_user FOREIGN KEY (user_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_user_gallery_position ON user_gallery_photos (user_id, position);
