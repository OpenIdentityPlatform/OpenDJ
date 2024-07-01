-- -----------------------------------------------------
-- Table auditaccess
-- -----------------------------------------------------
PROMPT Creating Table httpaccess ...
CREATE TABLE httpaccess (
  id VARCHAR2(56 CHAR) NOT NULL,
  timestamp_ VARCHAR2(29 CHAR) NOT NULL,
  transactionid VARCHAR2(255 CHAR) NOT NULL,
  eventname VARCHAR2(255 CHAR),
  userid VARCHAR2(255 CHAR),
  server_ip VARCHAR2(40 CHAR),
  server_port VARCHAR2(5 CHAR),
  client_host VARCHAR2(255 CHAR),
  client_ip VARCHAR2(40 CHAR),
  client_port VARCHAR2(5 CHAR),
  request_protocol VARCHAR2(255 CHAR) NULL ,
  request_operation VARCHAR2(255 CHAR) NULL ,
  request_secure VARCHAR2(255 CHAR) NULL ,
  request_method VARCHAR2(7 CHAR) NULL ,
  request_path VARCHAR2(255 CHAR) NULL ,
  request_queryparameters CLOB(2M) NULL ,
  request_headers CLOB NULL ,
  request_cookies CLOB NULL ,
  response_headers CLOB NULL ,
  response_status VARCHAR2(10 CHAR) NULL ,
  response_statuscode VARCHAR2(255 CHAR) NULL ,
  response_elapsedtime VARCHAR2(255 CHAR) NULL ,
  response_elapsedtimeunits VARCHAR2(255 CHAR) NULL
);


COMMENT ON COLUMN httpaccess.timestamp IS 'Date format: 2011-09-09T14:58:17.654+02:00'
;

PROMPT Creating Primary Key Constraint PRIMARY_ACCESS on table httpaccess ...
ALTER TABLE httpaccess
ADD CONSTRAINT PRIMARY_ACCESS PRIMARY KEY
(
  id
)
ENABLE
;

-- -----------------------------------------------------
-- Table auditauthentication
-- -----------------------------------------------------
PROMPT Creating TABLE ldapaccess ...
CREATE TABLE ldapaccess (
  id VARCHAR2(56 CHAR) NOT NULL,
  timestamp_ VARCHAR2(29 CHAR) NOT NULL,
  transactionid VARCHAR2(255 CHAR) NOT NULL,
  eventname VARCHAR2(255 CHAR),
  userid VARCHAR2(255 CHAR),
  server_ip VARCHAR2(40 CHAR),
  server_port VARCHAR2(5 CHAR),
  client_host VARCHAR2(255 CHAR),
  client_ip VARCHAR2(40 CHAR),
  client_port VARCHAR2(5 CHAR),
  request_protocol VARCHAR(255) ,
  request_operation VARCHAR(255) ,
  request_attr VARCHAR(255) NULL, 
  request_attrs VARCHAR(255) NULL , 
  request_authType VARCHAR(255) NULL , 
  request_connId INTEGER , 
  request_msgId INTEGER , 
  request_controls VARCHAR(255) NULL , 
  request_deleteOldRDN BOOLEAN NULL , 
  request_dn VARCHAR(255) NULL , 
  request_filter VARCHAR(255) NULL , 
  request_idToAbandon VARCHAR(255) NULL , 
  request_message VARCHAR(255) NULL , 
  request_name VARCHAR(255) NULL , 
  request_newRDN VARCHAR(255) NULL , 
  request_newSup VARCHAR(255) NULL , 
  request_oid VARCHAR(255) NULL , 
  request_opType VARCHAR(255) NULL , 
  request_operation VARCHAR(255) NULL , 
  request_protocol VARCHAR(255) NULL , 
  request_scope VARCHAR(255) NULL , 
  request_version VARCHAR(255) NULL , 
  response_additionalItems VARCHAR(255) NULL , 
  response_controls VARCHAR(255) NULL , 
  response_failureReason VARCHAR(255) NULL , 
  response_maskedMessage VARCHAR(255) NULL , 
  response_maskedResult VARCHAR(255) NULL , 
  response_nentries INTEGER NULL , 
  response_reason VARCHAR(255) NULL , 
  response_status VARCHAR(10) NULL ,
  response_statuscode VARCHAR(255) NULL ,
  response_elapsedtime VARCHAR(255) NULL ,
  response_elapsedtimeunits VARCHAR(255) NULL ,
);

COMMENT ON COLUMN ldapaccess.timestamp IS 'Date format: 2011-09-09T14:58:17.654+02:00'
;

PROMPT Creating PRIMARY KEY CONSTRAINT PRIMARY_AUTHENTICATION ON TABLE ldapaccess ...
ALTER TABLE ldapaccess
ADD CONSTRAINT PRIMARY_AUTHENTICATION PRIMARY KEY
(
  userid,
  request_dn
)
ENABLE
;

COMMIT;
