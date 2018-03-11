# <img alt="OpenDJ Logo" src="https://github.com/OpenIdentityPlatform/OpenDJ/raw/master/logo.png" width="300"/>
[![Latest release](https://img.shields.io/github/release/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/releases/latest)
[![Build Status](https://travis-ci.org/OpenIdentityPlatform/OpenDJ.svg)](https://travis-ci.org/OpenIdentityPlatform/OpenDJ)
[![Issues](https://img.shields.io/github/issues/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/issues)
[![Last commit](https://img.shields.io/github/last-commit/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/commits/master)
[![License](https://img.shields.io/badge/license-CDDL-blue.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/blob/master/LICENSE.md)
[![Gitter](https://img.shields.io/gitter/room/nwjs/nw.js.svg)](https://gitter.im/OpenIdentityPlatform/OpenDJ)
[![Top language](https://img.shields.io/github/languages/top/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ)
[![Code size in bytes](https://img.shields.io/github/languages/code-size/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ)

OpenDJ is an [LDAPv3](http://tools.ietf.org/html/rfc4510) compliant directory service, which has been developed 
for the Java platform, providing a high performance, highly available, and secure store for the identities managed 
by your organization. Its easy installation process, combined with the power of the Java platform makes OpenDJ
the simplest, fastest directory to deploy and manage.

## License
This project is licensed under the Common Development and Distribution License (CDDL). The following text applies to 
both this file, and should also be included in all files in the project.

## Downloads 
* [OpenDJ ZIP](https://github.com/OpenIdentityPlatform/OpenDJ/releases/latest) (All OS)
* [OpenDJ Docker](https://hub.docker.com/r/openidentityplatform/opendj/) (All OS)
* [OpenDJ DEB](https://github.com/OpenIdentityPlatform/OpenDJ/releases/latest) (Debian)
* [OpenDJ RPM](https://github.com/OpenIdentityPlatform/OpenDJ/releases/latest) (Redhat/Centos)
* [OpenDJ MSI](https://github.com/OpenIdentityPlatform/OpenDJ/releases/latest) (Windows/Wine)

Java 1.8+ required

## How-to build
```bash
git clone --recursive  https://github.com/OpenIdentityPlatform/OpenDJ.git
mvn clean install -f OpenDJ/forgerock-parent
mvn clean install -f OpenDJ
```

## How-to run after build
```bash
cd OpenDJ/opendj-server-legacy/target/package/opendj
./setup
bin/start-ds
bin/stop-ds
```

## Support and Mailing List Information
* OpenDJ Community Mailing List: open-identity-platform-opendj@googlegroups.com
* OpenDJ Community Archive: https://groups.google.com/d/forum/open-identity-platform-opendj
* OpenDJ Community on Gitter: https://gitter.im/OpenIdentityPlatform/OpenDJ
* OpenDJ Commercial support RFP: support@openam.org.ru (English, Russian)

## Contributing
Please, make [Pull request](https://github.com/OpenIdentityPlatform/OpenDJ/pulls)

## Thanks
* Sun OpenDS
* Oracle OpenDS
* Forgerock OpenDJ
