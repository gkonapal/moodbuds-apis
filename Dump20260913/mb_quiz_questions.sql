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
-- Table structure for table `quiz_questions`
--

DROP TABLE IF EXISTS `quiz_questions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_questions` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `question_text` varchar(500) NOT NULL,
  `question_order` int unsigned NOT NULL COMMENT '1–10',
  `path_key` char(1) DEFAULT NULL COMMENT 'NULL = universal; A/B/C/D = path-specific',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_qq_path_order` (`path_key`,`question_order`),
  CONSTRAINT `fk_qq_path` FOREIGN KEY (`path_key`) REFERENCES `quiz_paths` (`path_key`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quiz_questions`
--

LOCK TABLES `quiz_questions` WRITE;
/*!40000 ALTER TABLE `quiz_questions` DISABLE KEYS */;
INSERT INTO `quiz_questions` VALUES (1,'Whats your vibe today?',1,NULL,1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(2,'Where are you heading today?',2,'A',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(3,'How do you want people to see you?',3,'A',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(4,'Pick a color you are drawn to:',4,'A',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(5,'Choose your style:',5,'A',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(6,'What sounds perfect right now?',2,'B',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(7,'Pick your comfort level:',3,'B',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(8,'Choose your mood color:',4,'B',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(9,'Your ideal weekend:',5,'B',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(10,'What made you smile today?',2,'C',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(11,'Pick an energy word:',3,'C',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(12,'Choose a pattern:',4,'C',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(13,'Your ideal vibe:',5,'C',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(14,'Who are you dressing for?',2,'D',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(15,'Whats your approach?',3,'D',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(16,'Pick your go-to shade:',4,'D',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(17,'Your intention:',5,'D',1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(18,'What fit do you prefer?',6,NULL,1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(19,'What fabric matters most?',7,NULL,1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(20,'Your personality in one word:',8,NULL,1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(21,'Which pattern would you choose?',9,NULL,1,'2026-07-05 10:59:40','2026-07-05 10:59:40'),(22,'How minimal do you like your look?',10,NULL,1,'2026-07-05 10:59:40','2026-07-05 10:59:40');
/*!40000 ALTER TABLE `quiz_questions` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-13 19:56:30
