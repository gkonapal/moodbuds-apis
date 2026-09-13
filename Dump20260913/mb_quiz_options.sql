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
-- Table structure for table `quiz_options`
--

DROP TABLE IF EXISTS `quiz_options`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_options` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `question_id` int unsigned NOT NULL,
  `option_key` char(1) NOT NULL COMMENT 'A, B, C, D',
  `option_text` varchar(500) NOT NULL,
  `routes_to_path` char(1) DEFAULT NULL COMMENT 'Only for Q1: which path does this option start',
  `sort_order` int unsigned NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_option` (`question_id`,`option_key`),
  KEY `idx_qo_question` (`question_id`),
  KEY `fk_qo_routes_path` (`routes_to_path`),
  CONSTRAINT `fk_qo_question` FOREIGN KEY (`question_id`) REFERENCES `quiz_questions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_qo_routes_path` FOREIGN KEY (`routes_to_path`) REFERENCES `quiz_paths` (`path_key`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=1089 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quiz_options`
--

LOCK TABLES `quiz_options` WRITE;
/*!40000 ALTER TABLE `quiz_options` DISABLE KEYS */;
INSERT INTO `quiz_options` VALUES (1001,1,'A','I feel powerful and unstoppable','A',1,1),(1002,1,'B','I just want to chill','B',2,1),(1003,1,'C','I\'m feeling happy and positive','C',3,1),(1004,1,'D','I want to impress someone','D',4,1),(1005,2,'A','Business meeting',NULL,1,1),(1006,2,'B','Gym / Workout',NULL,2,1),(1007,2,'C','Party night',NULL,3,1),(1008,2,'D','Casual outing',NULL,4,1),(1009,3,'A','Leader',NULL,1,1),(1010,3,'B','Stylish',NULL,2,1),(1011,3,'C','Bold',NULL,3,1),(1012,3,'D','Focused',NULL,4,1),(1013,4,'A','Black',NULL,1,1),(1014,4,'B','Navy',NULL,2,1),(1015,4,'C','Red',NULL,3,1),(1016,4,'D','White',NULL,4,1),(1017,5,'A','Sharp & fitted',NULL,1,1),(1018,5,'B','Relaxed but strong',NULL,2,1),(1019,5,'C','Trendy',NULL,3,1),(1020,5,'D','Minimal clean',NULL,4,1),(1021,6,'A','Beach vibe',NULL,1,1),(1022,6,'B','Netflix & relax',NULL,2,1),(1023,6,'C','Road trip',NULL,3,1),(1024,6,'D','Coffee with friends',NULL,4,1),(1025,7,'A','Oversized',NULL,1,1),(1026,7,'B','Regular fit',NULL,2,1),(1027,7,'C','Soft fabric',NULL,3,1),(1028,7,'D','Lightweight breathable',NULL,4,1),(1029,8,'A','Pastel',NULL,1,1),(1030,8,'B','Grey',NULL,2,1),(1031,8,'C','Blue',NULL,3,1),(1032,8,'D','Earth tones',NULL,4,1),(1033,9,'A','Do nothing',NULL,1,1),(1034,9,'B','Music + chill',NULL,2,1),(1035,9,'C','Explore city',NULL,3,1),(1036,9,'D','Long drive',NULL,4,1),(1037,10,'A','Success',NULL,1,1),(1038,10,'B','Friends',NULL,2,1),(1039,10,'C','Family',NULL,3,1),(1040,10,'D','Just life',NULL,4,1),(1041,11,'A','Vibrant',NULL,1,1),(1042,11,'B','Bright',NULL,2,1),(1043,11,'C','Playful',NULL,3,1),(1044,11,'D','Cheerful',NULL,4,1),(1045,12,'A','Graphic prints',NULL,1,1),(1046,12,'B','Bold typography',NULL,2,1),(1047,12,'C','Color splash',NULL,3,1),(1048,12,'D','Simple positive quote',NULL,4,1),(1049,13,'A','Fun',NULL,1,1),(1050,13,'B','Light',NULL,2,1),(1051,13,'C','Expressive',NULL,3,1),(1052,13,'D','Energetic',NULL,4,1),(1053,14,'A','Date night',NULL,1,1),(1054,14,'B','Someone special',NULL,2,1),(1055,14,'C','Self-confidence',NULL,3,1),(1056,14,'D','Social media',NULL,4,1),(1057,15,'A','Elegant',NULL,1,1),(1058,15,'B','Stylish',NULL,2,1),(1059,15,'C','Mysterious',NULL,3,1),(1060,15,'D','Trendy',NULL,4,1),(1061,16,'A','Maroon',NULL,1,1),(1062,16,'B','Black',NULL,2,1),(1063,16,'C','White',NULL,3,1),(1064,16,'D','Deep blue',NULL,4,1),(1065,17,'A','Attract attention',NULL,1,1),(1066,17,'B','Look classy',NULL,2,1),(1067,17,'C','Stand out',NULL,3,1),(1068,17,'D','Feel special',NULL,4,1),(1069,18,'A','Slim',NULL,1,1),(1070,18,'B','Regular',NULL,2,1),(1071,18,'C','Oversized',NULL,3,1),(1072,18,'D','Athletic',NULL,4,1),(1073,19,'A','Comfort',NULL,1,1),(1074,19,'B','Premium feel',NULL,2,1),(1075,19,'C','Stretch',NULL,3,1),(1076,19,'D','Breathable',NULL,4,1),(1077,20,'A','Bold',NULL,1,1),(1078,20,'B','Calm',NULL,2,1),(1079,20,'C','Focused',NULL,3,1),(1080,20,'D','Creative',NULL,4,1),(1081,21,'A','Graphic',NULL,1,1),(1082,21,'B','Stripes',NULL,2,1),(1083,21,'C','Plain',NULL,3,1),(1084,21,'D','Minimal print',NULL,4,1),(1085,22,'A','Very minimal',NULL,1,1),(1086,22,'B','Some accents',NULL,2,1),(1087,22,'C','Color pops',NULL,3,1),(1088,22,'D','Bold statements',NULL,4,1);
/*!40000 ALTER TABLE `quiz_options` ENABLE KEYS */;
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
