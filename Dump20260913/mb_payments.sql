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
-- Table structure for table `payments`
--

DROP TABLE IF EXISTS `payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payments` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `order_id` bigint unsigned NOT NULL,
  `idempotency_key` varchar(128) DEFAULT NULL,
  `gateway` enum('RAZORPAY','CASHFREE','COD','MANUAL') NOT NULL,
  `gateway_order_id` varchar(255) DEFAULT NULL COMMENT 'Gateway-side order ID',
  `gateway_payment_id` varchar(255) DEFAULT NULL COMMENT 'Gateway-side payment/transaction ID',
  `method` enum('UPI','CREDIT_CARD','DEBIT_CARD','NET_BANKING','WALLET','COD') DEFAULT NULL,
  `status` enum('INITIATED','PENDING','SUCCESS','FAILED','EXPIRED','REFUNDED','PARTIALLY_REFUNDED') NOT NULL DEFAULT 'INITIATED',
  `amount` int unsigned NOT NULL COMMENT 'Amount in paise',
  `currency` char(3) NOT NULL DEFAULT 'INR',
  `gateway_fee` int unsigned NOT NULL DEFAULT '0' COMMENT 'Gateway transaction fee in paise',
  `gateway_response` json DEFAULT NULL COMMENT 'Full raw webhook/callback payload stored',
  `initiated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_payments_order_idempotency` (`order_id`,`idempotency_key`),
  UNIQUE KEY `uq_payments_gateway_order` (`gateway_order_id`),
  UNIQUE KEY `uq_payments_gateway_payment` (`gateway_payment_id`),
  KEY `idx_payments_order` (`order_id`),
  KEY `idx_payments_gateway_payment_id` (`gateway_payment_id`),
  KEY `idx_payments_status` (`status`),
  CONSTRAINT `fk_payments_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `payments`
--

LOCK TABLES `payments` WRITE;
/*!40000 ALTER TABLE `payments` DISABLE KEYS */;
/*!40000 ALTER TABLE `payments` ENABLE KEYS */;
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
