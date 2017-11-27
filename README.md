# <img alt="OpenDJ Logo" src="https://github.com/OpenIdentityPlatform/OpenDJ/raw/master/logo.png" width="300"/>
[![Build Status](https://travis-ci.org/OpenIdentityPlatform/OpenDJ.svg)](https://travis-ci.org/OpenIdentityPlatform/OpenDJ)
[![Build Status](https://img.shields.io/badge/license-CDDL-blue.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/blob/master/legal-notices/CDDLv1_0.txt)
[![Gitter](https://img.shields.io/gitter/room/nwjs/nw.js.svg)](http://gitter.im/OpenIdentityPlatform)
[![GitHub top language](https://img.shields.io/github/languages/top/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ)
[![GitHub code size in bytes](https://img.shields.io/github/languages/code-size/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ)

OpenDJ is an [LDAPv3](http://tools.ietf.org/html/rfc4510) compliant directory service, which has been developed 
for the Java platform, providing a high performance, highly available, and secure store for the identities managed 
by your organization. Its easy installation process, combined with the power of the Java platform makes OpenDJ
the simplest, fastest directory to deploy and manage.

## License
This project is licensed under the Common Development and Distribution License (CDDL). The following text applies to 
both this file, and should also be included in all files in the project:

## How-to build
```bash
git clone --recursive  https://github.com/OpenIdentityPlatform/OpenDJ.git
mvn clean install -f forgerock-parent
mvn clean install -f OpenDJ
```
BIN: OpenDJ/opendj-server-legacy/target/package/opendj-4.0.0-SNAPSHOT.zip

## How-to run after build
```bash
cd OpenDJ/opendj-server-legacy/target/package/opendj
./setup
bin/start-ds
bin/stop-ds
```

## Support and Mailing List Information
* Community Mailing List: open-identity-platform-opendj@googlegroups.com
* Community Archive: https://groups.google.com/d/forum/open-identity-platform-opendj
* Commercial support RFP: support@openam.org.ru (English, Russian)

## Contributing
Please, make [Pull request](https://github.com/OpenIdentityPlatform/OpenDJ/pulls)

## Thanks
* Sun OpenDS
* Forgerock OpenDJ
