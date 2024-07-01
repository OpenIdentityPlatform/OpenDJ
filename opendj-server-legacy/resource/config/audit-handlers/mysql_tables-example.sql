SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='TRADITIONAL';

CREATE SCHEMA IF NOT EXISTS `audit` DEFAULT CHARACTER SET utf8 COLLATE utf8_bin ;
USE `audit` ;
-- -----------------------------------------------------
-- Table `audit`.`ldapaccess`
-- -----------------------------------------------------
CREATE  TABLE IF NOT EXISTS `audit`.`ldapaccess` (
  `id` VARCHAR(56) NOT NULL ,
  `timestamp_` VARCHAR(29) NULL COMMENT 'Date format: 2011-09-09T14:58:17.654+02:00' ,
  `transactionid` VARCHAR(255) NULL ,
  `eventname` VARCHAR(255) ,
  `userid` VARCHAR(255) NULL ,
  `server_ip` VARCHAR(40) ,
  `server_port` VARCHAR(5) ,
  `client_host` VARCHAR(255) ,
  `client_ip` VARCHAR(40) ,
  `client_port` VARCHAR(5) ,
  `request_protocol` VARCHAR(255) ,
  `request_operation` VARCHAR(255) ,
  `request_attr` VARCHAR(255) NULL, 
  `request_attrs` VARCHAR(255) NULL , 
  `request_authType` VARCHAR(255) NULL , 
  `request_connId` INTEGER , 
  `request_msgId` INTEGER , 
  `request_controls` VARCHAR(255) NULL , 
  `request_deleteOldRDN` BOOLEAN NULL , 
  `request_dn` VARCHAR(255) NULL , 
  `request_filter` VARCHAR(255) NULL , 
  `request_idToAbandon` VARCHAR(255) NULL , 
  `request_message` VARCHAR(255) NULL , 
  `request_name` VARCHAR(255) NULL , 
  `request_newRDN` VARCHAR(255) NULL , 
  `request_newSup` VARCHAR(255) NULL , 
  `request_oid` VARCHAR(255) NULL , 
  `request_opType` VARCHAR(255) NULL , 
  `request_operation` VARCHAR(255) NULL , 
  `request_protocol` VARCHAR(255) NULL , 
  `request_scope` VARCHAR(255) NULL , 
  `request_version` VARCHAR(255) NULL , 
  `response_additionalItems` VARCHAR(255) NULL , 
  `response_controls` VARCHAR(255) NULL , 
  `response_failureReason` VARCHAR(255) NULL , 
  `response_maskedMessage` VARCHAR(255) NULL , 
  `response_maskedResult` VARCHAR(255) NULL , 
  `response_nentries` INTEGER NULL , 
  `response_reason` VARCHAR(255) NULL , 
  `response_status` VARCHAR(10) NULL ,
  `response_statuscode` VARCHAR(255) NULL ,
  `response_elapsedtime` VARCHAR(255) NULL ,
  `response_elapsedtimeunits` VARCHAR(255) NULL ,
  PRIMARY KEY (`id`),
  INDEX `idx_ldapaccess_dn` (`request_dn` ASC),
  INDEX `idx_ldapaccess_userid` (`userid` ASC) )
ENGINE = InnoDB;

-- -----------------------------------------------------
-- Table `audit`.`httpaccess`
-- -----------------------------------------------------
CREATE  TABLE IF NOT EXISTS `audit`.`httpaccess` (
  `id` VARCHAR(56) NOT NULL ,
  `timestamp_` VARCHAR(29) NULL COMMENT 'Date format: 2011-09-09T14:58:17.654+02:00' ,
  `transactionid` VARCHAR(255) NULL ,
  `eventname` VARCHAR(255) ,
  `userid` VARCHAR(255) NULL ,
  `server_ip` VARCHAR(40) ,
  `server_port` VARCHAR(5) ,
  `client_host` VARCHAR(255) ,
  `client_ip` VARCHAR(40) ,
  `client_port` VARCHAR(5) ,
  `request_protocol` VARCHAR(255) NULL ,
  `request_operation` VARCHAR(255) NULL ,
  `request_secure` BOOLEAN NULL ,
  `request_method` VARCHAR(7) NULL ,
  `request_path` VARCHAR(255) NULL ,
  `request_queryparameters` MEDIUMTEXT NULL ,
  `request_headers` MEDIUMTEXT NULL ,
  `request_cookies` MEDIUMTEXT NULL ,
  `response_headers` MEDIUMTEXT NULL ,
  `response_status` VARCHAR(10) NULL ,
  `response_statuscode` VARCHAR(255) NULL ,
  `response_elapsedtime` VARCHAR(255) NULL ,
  `response_elapsedtimeunits` VARCHAR(255) NULL ,
  PRIMARY KEY (`id`),
  INDEX `idx_httpaccess_userid` (`userid` ASC) )
ENGINE = InnoDB;

SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;

-- -------------------------------------------
-- audit database user
-- ------------------------------------------
GRANT ALL PRIVILEGES on audit.* TO audit IDENTIFIED BY 'audit';
GRANT ALL PRIVILEGES on audit.* TO audit@'%' IDENTIFIED BY 'audit';
GRANT ALL PRIVILEGES on audit.* TO audit@localhost IDENTIFIED BY 'audit';
