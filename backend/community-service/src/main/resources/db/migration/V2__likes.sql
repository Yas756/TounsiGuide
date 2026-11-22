-- S3-US11: likes on posts and comments (one per member and target).
CREATE TABLE `likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `target_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` bigint NOT NULL,
  `user_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_likes_target_user` (`target_type`, `target_id`, `user_id`),
  KEY `idx_likes_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
