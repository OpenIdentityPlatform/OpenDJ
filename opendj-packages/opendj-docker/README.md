# How-to:
Build docker image:
```bash
docker build . -t openidentityplatform/opendj
```
Run image
```bash
docker run -d -p 1389:1389 -p 1636:1636 -p 4444:4444 --name opendj openidentityplatform/opendj
```

##  Environment Variables

|Variable|Default Value|Description|
|--------|-------------|-----------|
|ADD_BASE_ENTRY|--addBaseEntry|if set, creates base DN entry|
|PORT|1389|LDAP Listener Port|
|LDAPS_PORT|1636|LDAPS Listener Port|
|BASE_DN|dc=example,dc=com|OpenDJ Base DN |
|ROOT_USER_DN|cn=Directory Manager|Initial root user DN|
|ROOT_PASSWORD|password|Initial root user password|
|SECRET_VOLUME|-|Mounted keystore volume, if present copies keystore over|
|MASTER_SERVER|-|Replication master server|
|VERSION|4.3.1|OpenDJ version|
|OPENDJ_USER|-|user which runs OpenDJ|
|OPENDJ_REPLICATION_TYPE|-|OpenDJ Replication type, valid values are: <ul><li>simple - standart replication</li><li>srs - standalone replication servers</li><li>sdsr - Standalone Directory Server Replicas</li><li>rg - Replication Groups</li></ul>Other values will be ignored|
|OPENDJ_SSL_OPTIONS|--generateSelfSignedCertificate|you can replace ssl options at here, like : "--usePkcs12keyStore /opt/domain.pfx --keyStorePassword domain"