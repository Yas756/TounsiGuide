-- V1 baseline of tounsiguide_location (S3-B09), taken from the schema Hibernate built with ddl-auto=update on 2026-11-21.
-- Existing databases are baselined at version 1 (spring.flyway.baseline-on-migrate) and skip this file;
-- an empty database is created from it. Every later change goes in a new V<n>__description.sql file.

-- Tables are created in alphabetical order, so foreign keys are checked only at the end.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `association_decisions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `actor_role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `from_status` enum('CONFIRMED','DISPUTED','PROPOSED','REJECTED') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `to_status` enum('CONFIRMED','DISPUTED','PROPOSED','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `association_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_association_decisions_association` (`association_id`),
  CONSTRAINT `FK6ddh54nuv305lesgxo3g6l3ia` FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `association_evidence` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `actor_name` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `comment_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `direction` enum('OPPOSE','SUPPORT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` enum('AUTHOR_CONFIRMED','AUTHOR_TAG','BUSINESS_DISPUTE','COMPETING_CLAIM','MEMBER_DISPUTE','MEMBER_SUGGESTION','PAGE_MENTION','TEXT_MENTION') COLLATE utf8mb4_unicode_ci NOT NULL,
  `weight` int NOT NULL,
  `association_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_association_evidence_association` (`association_id`),
  CONSTRAINT `FK10cukfnhmudiweqk9v0sbxc5v` FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `associations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `analysis` text COLLATE utf8mb4_unicode_ci,
  `author_claim` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by_name` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `decided_at` datetime(6) DEFAULT NULL,
  `decided_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `decision_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `forced` bit(1) NOT NULL,
  `origin` enum('ADMIN','AUTHOR','MEMBER','MENTION') COLLATE utf8mb4_unicode_ci NOT NULL,
  `post_author_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `post_deleted` bit(1) NOT NULL,
  `post_id` bigint NOT NULL,
  `score` int NOT NULL,
  `status` enum('CONFIRMED','DISPUTED','PROPOSED','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `location_id` bigint NOT NULL,
  `post_deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_associations_post_location` (`post_id`,`location_id`),
  KEY `idx_associations_post` (`post_id`),
  KEY `idx_associations_status` (`status`),
  KEY `FKiluaub2jlf3g2cphrow389smf` (`location_id`),
  CONSTRAINT `FKiluaub2jlf3g2cphrow389smf` FOREIGN KEY (`location_id`) REFERENCES `locations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `location_aliases` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `alias` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `normalized_alias` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `location_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_location_aliases_normalized` (`normalized_alias`),
  KEY `FK1ntbqp422pdu9nu9xpaah6gun` (`location_id`),
  CONSTRAINT `FK1ntbqp422pdu9nu9xpaah6gun` FOREIGN KEY (`location_id`) REFERENCES `locations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `locations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `city` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `external_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `normalized_city` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `normalized_name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source` enum('DIRECTORY','MAP') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_locations_normalized_name` (`normalized_name`),
  KEY `idx_locations_external_id` (`external_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
