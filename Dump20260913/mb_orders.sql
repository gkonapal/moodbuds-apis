-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: mb
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `order_number` varchar(30) NOT NULL COMMENT 'Human-readable: MB-2026-000001',
  `user_id` bigint unsigned NOT NULL,
  `idempotency_key` varchar(128) DEFAULT NULL,
  `idempotency_fingerprint` char(64) DEFAULT NULL,
  `status` enum('PENDING_PAYMENT','PAYMENT_FAILED','PROCESSING','CONFIRMED','PACKED','SHIPPED','OUT_FOR_DELIVERY','DELIVERED','CANCELLED','RETURN_INITIATED','RETURN_PICKUP_SCHEDULED','RETURN_RECEIVED','REFUND_INITIATED','PARTIALLY_REFUNDED','REFUNDED') NOT NULL DEFAULT 'PENDING_PAYMENT',
  `shipping_address_id` bigint unsigned DEFAULT NULL COMMENT 'Reference to saved address; may be deleted later',
  `shipping_address_snapshot_full` json NOT NULL COMMENT 'Immutable snapshot of address at order time',
  `payment_method` enum('UPI','CREDIT_CARD','DEBIT_CARD','NET_BANKING','WALLET','COD') DEFAULT NULL,
  `coupon_id` bigint unsigned DEFAULT NULL,
  `subtotal` int unsigned NOT NULL COMMENT 'Sum of line items before discounts (paise)',
  `coupon_discount` int unsigned NOT NULL DEFAULT '0',
  `shipping_cost` int unsigned NOT NULL DEFAULT '0',
  `shipping_pricing_status` enum('PENDING','FINALIZED') NOT NULL DEFAULT 'FINALIZED',
  `shipping_pricing_source` varchar(50) NOT NULL DEFAULT 'FREE_SHIPPING_TEMPORARY',
  `gst_amount` int unsigned NOT NULL DEFAULT '0',
  `total_amount` int unsigned NOT NULL COMMENT 'Final amount charged to customer',
  `payment_expires_at` datetime NOT NULL,
  `cancellation_reason` varchar(255) DEFAULT NULL,
  `cancelled_at` datetime DEFAULT NULL,
  `reservation_released_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `order_number` (`order_number`),
  UNIQUE KEY `uq_orders_number` (`order_number`),
  UNIQUE KEY `uq_orders_customer_idempotency` (`user_id`,`idempotency_key`),
  KEY `idx_orders_user` (`user_id`),
  KEY `idx_orders_status` (`status`),
  KEY `idx_orders_created_at` (`created_at`),
  KEY `fk_orders_coupon` (`coupon_id`),
  KEY `fk_orders_address` (`shipping_address_id`),
  KEY `idx_orders_payment_expiry` (`status`,`payment_expires_at`,`reservation_released_at`),
  CONSTRAINT `fk_orders_address` FOREIGN KEY (`shipping_address_id`) REFERENCES `user_addresses` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_orders_coupon` FOREIGN KEY (`coupon_id`) REFERENCES `coupons` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_orders_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-13 19:56:31
