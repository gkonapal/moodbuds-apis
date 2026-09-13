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
-- Table structure for table `moods`
--

DROP TABLE IF EXISTS `moods`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `moods` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT 'Happy, Confident, Cool, etc.',
  `slug` varchar(140) NOT NULL,
  `tagline` varchar(300) DEFAULT NULL COMMENT 'Shown on mood result / PLP banner',
  `personality_tagline` varchar(300) DEFAULT NULL COMMENT 'Used on quiz result screen',
  `banner_image_url` varchar(500) DEFAULT NULL COMMENT 'PLP mood banner image',
  `color` varchar(40) DEFAULT NULL COMMENT 'e.g. black',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_moods_slug` (`slug`),
  KEY `idx_moods_active` (`is_active`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `moods`
--

LOCK TABLES `moods` WRITE;
/*!40000 ALTER TABLE `moods` DISABLE KEYS */;
INSERT INTO `moods` VALUES (1,'Happy','happy','Bright & cheerful','You radiate positivity',NULL,NULL,1,'2026-07-05 10:53:38','2026-08-28 11:05:46'),(2,'Confident','confident','Powerful & poised','You command the room',NULL,NULL,1,'2026-07-05 10:53:38','2026-08-28 11:05:46'),(3,'Cool','cool','Laidback & stylish','You keep it effortless',NULL,NULL,1,'2026-07-05 10:53:38','2026-08-28 11:05:46'),(4,'Professional','professional','Sharp & formal','Polished and composed',NULL,NULL,1,'2026-07-05 10:53:38','2026-08-28 11:05:46'),(5,'Party','party','Loud & fun','Ready to celebrate',NULL,NULL,1,'2026-07-05 10:53:38','2026-08-28 11:05:46'),(6,'Energetic','energetic','Active & bold','Always on the move',NULL,NULL,1,'2026-07-05 10:53:38','2026-08-28 11:05:46'),(7,'Romantic','romantic','Soft & attractive','Charming and elegant',NULL,NULL,1,'2026-07-05 10:53:38','2026-08-28 11:05:46'),(8,'Calm','calm','Subtle & relaxed','Collected and mellow',NULL,NULL,1,'2026-07-05 10:53:38','2026-08-28 11:05:46'),(9,'Minimal','minimal','Clean & understated','Less is more',NULL,NULL,1,'2026-07-05 10:53:38','2026-08-28 11:05:46');
/*!40000 ALTER TABLE `moods` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-13 19:56:29
