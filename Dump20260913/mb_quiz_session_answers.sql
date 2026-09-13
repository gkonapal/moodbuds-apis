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
-- Table structure for table `quiz_session_answers`
--

DROP TABLE IF EXISTS `quiz_session_answers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_session_answers` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `quiz_session_id` char(36) NOT NULL,
  `question_id` int unsigned NOT NULL,
  `option_id` int unsigned NOT NULL,
  `answered_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_session_question` (`quiz_session_id`,`question_id`),
  KEY `idx_qsa_session` (`quiz_session_id`),
  KEY `fk_qsa_question` (`question_id`),
  KEY `fk_qsa_option` (`option_id`),
  CONSTRAINT `fk_qsa_option` FOREIGN KEY (`option_id`) REFERENCES `quiz_options` (`id`),
  CONSTRAINT `fk_qsa_question` FOREIGN KEY (`question_id`) REFERENCES `quiz_questions` (`id`),
  CONSTRAINT `fk_qsa_session` FOREIGN KEY (`quiz_session_id`) REFERENCES `quiz_sessions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quiz_session_answers`
--

LOCK TABLES `quiz_session_answers` WRITE;
/*!40000 ALTER TABLE `quiz_session_answers` DISABLE KEYS */;
INSERT INTO `quiz_session_answers` VALUES (1,'550e8400-e29b-41d4-a716-446655440000',1,1001,'2026-07-05 11:20:01'),(2,'550e8400-e29b-41d4-a716-446655440000',2,1005,'2026-07-05 11:20:01'),(3,'550e8400-e29b-41d4-a716-446655440000',3,1009,'2026-07-05 11:20:01'),(4,'550e8400-e29b-41d4-a716-446655440000',4,1013,'2026-07-05 11:20:01'),(5,'550e8400-e29b-41d4-a716-446655440000',5,1017,'2026-07-05 11:20:01'),(6,'550e8400-e29b-41d4-a716-446655440000',18,1069,'2026-07-05 11:20:01'),(7,'550e8400-e29b-41d4-a716-446655440000',19,1074,'2026-07-05 11:20:01'),(8,'550e8400-e29b-41d4-a716-446655440000',20,1077,'2026-07-05 11:20:01'),(9,'550e8400-e29b-41d4-a716-446655440000',21,1084,'2026-07-05 11:20:01'),(10,'550e8400-e29b-41d4-a716-446655440000',22,1088,'2026-07-05 11:20:01');
/*!40000 ALTER TABLE `quiz_session_answers` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-13 19:56:28
