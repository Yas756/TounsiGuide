-- V1 baseline of tounsiguide_community (S3-B09), taken from the schema Hibernate built with ddl-auto=update on 2026-11-21.
-- Existing databases are baselined at version 1 (spring.flyway.baseline-on-migrate) and skip this file;
-- an empty database is created from it. Every later change goes in a new V<n>__description.sql file.

-- Tables are created in alphabetical order, so foreign keys are checked only at the end.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `author_name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `post_id` bigint NOT NULL,
  `publication_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PUBLISHED',
  `restricted_at` datetime(6) DEFAULT NULL,
  `restricted_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `restriction_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_comments_post` (`post_id`),
  CONSTRAINT `fk_comments_post` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `media` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `comment_id` bigint DEFAULT NULL,
  `content_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `owner_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `position` int NOT NULL,
  `post_id` bigint DEFAULT NULL,
  `size_bytes` bigint NOT NULL,
  `storage_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('IMAGE','VIDEO') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_media_post` (`post_id`),
  KEY `idx_media_comment` (`comment_id`),
  KEY `idx_media_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `moderation_decisions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` enum('DISMISS','RESTORE','RESTRICT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_name` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reports_closed` int NOT NULL,
  `target_id` bigint NOT NULL,
  `target_type` enum('COMMENT','POST') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_moderation_target` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `posts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `author_name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `location_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `google_place_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `place_address` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `place_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `deleted_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `publication_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PUBLISHED',
  `restricted_at` datetime(6) DEFAULT NULL,
  `restricted_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `restriction_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `module_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `decided_at` datetime(6) DEFAULT NULL,
  `decided_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `decided_by_name` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `decision_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `detail` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `post_id` bigint NOT NULL,
  `reason` enum('FALSE_INFO','OFFENSIVE','OTHER','SPAM') COLLATE utf8mb4_unicode_ci NOT NULL,
  `reporter_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reporter_name` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('ACTIONED','DISMISSED','OPEN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` bigint NOT NULL,
  `target_type` enum('COMMENT','POST') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_reports_status` (`status`),
  KEY `idx_reports_target` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `topic_modules` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `decided_at` datetime(6) DEFAULT NULL,
  `decided_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `decided_by_name` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `decision_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `icon` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `proposed_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `proposed_by_name` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `slug` varchar(70) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('APPROVED','ARCHIVED','PROPOSED','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_topic_modules_slug` (`slug`),
  KEY `idx_topic_modules_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
