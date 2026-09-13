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
-- Table structure for table `gst_invoices`
--

DROP TABLE IF EXISTS `gst_invoices`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gst_invoices` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `order_id` bigint unsigned NOT NULL,
  `invoice_number` varchar(50) NOT NULL COMMENT 'Format: MB-INV-2026-000001',
  `invoice_date` date NOT NULL,
  `seller_gstin` varchar(20) NOT NULL,
  `seller_name` varchar(200) NOT NULL,
  `seller_address` text NOT NULL,
  `customer_name` varchar(200) NOT NULL,
  `customer_address` text NOT NULL,
  `customer_gstin` varchar(20) DEFAULT NULL,
  `subtotal` int unsigned NOT NULL COMMENT 'Pre-tax total (paise)',
  `cgst_amount` int unsigned NOT NULL DEFAULT '0' COMMENT 'Central GST (paise)',
  `sgst_amount` int unsigned NOT NULL DEFAULT '0' COMMENT 'State GST (paise)',
  `igst_amount` int unsigned NOT NULL DEFAULT '0' COMMENT 'Integrated GST for inter-state (paise)',
  `total_tax` int unsigned NOT NULL DEFAULT '0',
  `total_amount` int unsigned NOT NULL,
  `pdf_url` varchar(500) DEFAULT NULL COMMENT 'CDN URL to generated invoice PDF',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `order_id` (`order_id`),
  UNIQUE KEY `invoice_number` (`invoice_number`),
  UNIQUE KEY `uq_gst_invoices_number` (`invoice_number`),
  KEY `idx_gst_invoices_order` (`order_id`),
  KEY `idx_gst_invoices_date` (`invoice_date`),
  CONSTRAINT `fk_gst_invoices_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gst_invoices`
--

LOCK TABLES `gst_invoices` WRITE;
/*!40000 ALTER TABLE `gst_invoices` DISABLE KEYS */;
/*!40000 ALTER TABLE `gst_invoices` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-13 19:56:32
