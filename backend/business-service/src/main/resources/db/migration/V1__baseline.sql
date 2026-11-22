-- V1 baseline of tounsiguide_business (S3-B09), taken from the schema Hibernate built with ddl-auto=update on 2026-11-21.
-- Existing databases are baselined at version 1 (spring.flyway.baseline-on-migrate) and skip this file;
-- an empty database is created from it. Every later change goes in a new V<n>__description.sql file.

-- Tables are created in alphabetical order, so foreign keys are checked only at the end.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `business_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` enum('PAGE_UPDATED','POST_HIDDEN','POST_UNHIDDEN','REQUEST_APPROVED','REQUEST_REJECTED','REQUEST_SUBMITTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_name` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `page_id` bigint DEFAULT NULL,
  `post_id` bigint DEFAULT NULL,
  `reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_page` (`page_id`),
  KEY `idx_audit_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `business_page` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active_location_id` bigint DEFAULT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(254) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_id` bigint NOT NULL,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `opening_hours` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_id` bigint DEFAULT NULL,
  `status` enum('ACTIVE','DELETED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  `verified_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `website` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business_page_active_location` (`active_location_id`),
  KEY `idx_business_page_location` (`location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `page_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `added_at` datetime(6) NOT NULL,
  `page_id` bigint NOT NULL,
  `role` enum('EDITOR','OWNER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_name` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_page_member_page_user` (`page_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `verification_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `contact` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `decided_at` datetime(6) DEFAULT NULL,
  `decided_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `decided_by_name` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `decision_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `evidence` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `location_id` bigint NOT NULL,
  `page_id` bigint DEFAULT NULL,
  `proposed_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `requester_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `requester_name` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_request_status` (`status`),
  KEY `idx_request_requester` (`requester_id`),
  KEY `idx_request_location` (`location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wall_visibility_override` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_name` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `changed_at` datetime(6) NOT NULL,
  `hidden` bit(1) NOT NULL,
  `page_id` bigint NOT NULL,
  `post_id` bigint NOT NULL,
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wall_override_page_post` (`page_id`,`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
