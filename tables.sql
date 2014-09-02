CREATE TABLE `whloot_loot` (
  `opID` int(9) unsigned NOT NULL,
  `itemID` int(10) unsigned NOT NULL,
  `name` varchar(100) DEFAULT NULL,
  `quantity` bigint(20) unsigned NOT NULL,
  PRIMARY KEY (`opID`, `itemID`),
  KEY `itemID` (`itemID`),
  KEY `opID` (`opID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `whloot_members` (
  `memberID` int(5) unsigned zerofill NOT NULL AUTO_INCREMENT,
  `name` char(20) CHARACTER SET latin1 NOT NULL,
  PRIMARY KEY (`memberID`),
  KEY `memberID` (`memberID`),
  KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `whloot_participation` (
  `opID` int(9) unsigned NOT NULL,
  `memberID` int(5) unsigned zerofill NOT NULL,
  PRIMARY KEY (`opID`,`memberID`),
  KEY `opID` (`opID`),
  KEY `memberID` (`memberID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `whloot_transactions` (
  `transactionID` int(10) unsigned NOT NULL DEFAULT '0',
  `transactionTS` datetime DEFAULT NULL,
  `itemID` int(10) unsigned DEFAULT NULL,
  `quantity` int(20) unsigned DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  PRIMARY KEY (`transactionID`),
  KEY `transactionTS` (`transactionTS`),
  KEY `itemID` (`itemID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `whloot_item_popularity` (
  `itemID` int(11) NOT NULL,
  `popularity` int(10) unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`itemID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

INSERT INTO whloot_members (name) VALUES ("Lars"), ("Alex"), ("Jakob"), ("Chrischi"), ("Ercan"), ("Paris");

INSERT INTO whloot_item_popularity (itemID, popularity) VALUES (29668, 10);