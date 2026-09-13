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
-- Table structure for table `quiz_option_mood_weights`
--

DROP TABLE IF EXISTS `quiz_option_mood_weights`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_option_mood_weights` (
  `option_id` int unsigned NOT NULL,
  `mood_id` int unsigned NOT NULL,
  `score` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '0–5; higher = stronger signal for this mood',
  PRIMARY KEY (`option_id`,`mood_id`),
  KEY `fk_qomw_mood` (`mood_id`),
  CONSTRAINT `fk_qomw_mood` FOREIGN KEY (`mood_id`) REFERENCES `moods` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_qomw_option` FOREIGN KEY (`option_id`) REFERENCES `quiz_options` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quiz_option_mood_weights`
--

LOCK TABLES `quiz_option_mood_weights` WRITE;
/*!40000 ALTER TABLE `quiz_option_mood_weights` DISABLE KEYS */;
INSERT INTO `quiz_option_mood_weights` VALUES (1001,2,5),(1001,4,2),(1002,3,4),(1002,8,2),(1003,1,5),(1003,9,1),(1004,5,2),(1004,7,4),(1005,2,2),(1005,4,4),(1006,2,1),(1006,6,5),(1007,2,1),(1007,5,5),(1008,2,4),(1008,9,2),(1009,2,5),(1009,4,1),(1010,2,2),(1010,3,3),(1011,2,4),(1011,5,2),(1012,2,3),(1012,9,2),(1013,2,4),(1013,9,1),(1014,2,3),(1014,4,1),(1015,2,4),(1015,5,2),(1016,8,1),(1016,9,4),(1017,2,5),(1017,4,1),(1018,2,3),(1018,6,2),(1019,3,4),(1019,5,1),(1020,1,1),(1020,9,5),(1021,3,4),(1021,8,1),(1022,3,1),(1022,8,4),(1023,3,1),(1023,6,4),(1024,1,1),(1024,3,3),(1025,3,4),(1025,8,1),(1026,3,1),(1026,9,3),(1027,1,1),(1027,8,3),(1028,3,1),(1028,6,3),(1029,1,2),(1029,9,2),(1030,3,1),(1030,9,3),(1031,1,1),(1031,3,4),(1032,3,1),(1032,8,3),(1033,1,1),(1033,8,3),(1034,1,1),(1034,3,3),(1035,3,1),(1035,6,3),(1036,3,1),(1036,6,3),(1037,1,4),(1037,5,1),(1038,1,4),(1038,3,1),(1039,1,4),(1039,8,1),(1040,1,3),(1040,9,1),(1041,1,4),(1041,3,1),(1042,1,4),(1042,9,1),(1043,1,3),(1043,5,1),(1044,1,3),(1044,8,1),(1045,1,4),(1045,3,1),(1046,1,3),(1046,3,1),(1047,1,4),(1047,5,1),(1048,1,3),(1048,9,1),(1049,1,4),(1049,5,1),(1050,1,3),(1050,8,1),(1051,1,4),(1051,3,1),(1052,1,3),(1052,6,1),(1053,2,1),(1053,7,4),(1054,1,1),(1054,7,4),(1055,2,3),(1055,7,1),(1056,3,1),(1056,7,3),(1057,2,1),(1057,7,4),(1058,2,4),(1058,3,1),(1059,3,1),(1059,7,3),(1060,2,3),(1060,5,1),(1061,2,1),(1061,7,3),(1062,2,4),(1062,9,1),(1063,7,1),(1063,9,3),(1064,2,3),(1064,7,1),(1065,5,1),(1065,7,4),(1066,2,1),(1066,7,3),(1067,2,3),(1067,3,1),(1068,1,1),(1068,7,3),(1069,2,3),(1069,9,1),(1070,1,1),(1070,9,3),(1071,3,3),(1071,6,1),(1072,2,1),(1072,6,3),(1073,1,1),(1073,8,3),(1074,2,3),(1074,4,1),(1075,3,1),(1075,6,3),(1076,3,1),(1076,8,3),(1077,2,3),(1077,5,1),(1078,8,3),(1078,9,1),(1079,2,3),(1079,4,1),(1080,1,1),(1080,3,3),(1081,1,2),(1081,3,1),(1082,4,1),(1082,9,2),(1083,1,1),(1083,9,3),(1084,1,1),(1084,9,4),(1085,1,1),(1085,9,4),(1086,3,1),(1086,9,3),(1087,1,3),(1087,3,1),(1088,2,1),(1088,5,3);
/*!40000 ALTER TABLE `quiz_option_mood_weights` ENABLE KEYS */;
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
