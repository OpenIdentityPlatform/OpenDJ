# <img alt="OpenDJ Logo" src="https://github.com/OpenIdentityPlatform/OpenDJ/raw/master/logo.png" width="300"/>
[![Latest release](https://img.shields.io/github/release/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/releases)
[![Build Status](https://travis-ci.com/OpenIdentityPlatform/OpenDJ.svg)](https://travis-ci.com/OpenIdentityPlatform/OpenDJ)
[![Issues](https://img.shields.io/github/issues/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/issues)
[![Last commit](https://img.shields.io/github/last-commit/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/commits/master)
[![License](https://img.shields.io/badge/license-CDDL-blue.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/blob/master/LICENSE.md)
[![Downloads](https://img.shields.io/github/downloads/OpenIdentityPlatform/OpenDJ/total.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/releases)
[![Docker](https://shields.beevelop.com/docker/pulls/openidentityplatform/opendj.svg)](https://hub.docker.com/r/openidentityplatform/opendj)
[![Gitter](https://img.shields.io/gitter/room/nwjs/nw.js.svg)](https://gitter.im/OpenIdentityPlatform/OpenDJ)
[![Top language](https://img.shields.io/github/languages/top/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ)
[![Code size in bytes](https://img.shields.io/github/languages/code-size/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ)

OpenDJ is an [LDAPv3](http://tools.ietf.org/html/rfc4510) compliant directory service, which has been developed 
for the Java platform, providing a high performance, highly available, and secure store for the identities managed 
by your organization. Its easy installation process, combined with the power of the Java platform makes OpenDJ
the simplest, fastest directory to deploy and manage.

An open source, lightweight, embeddable directory that can easily share real-time customer, device, and user identity data across enterprise, cloud, social, and mobile environments.
* Massive data scale and high availability providings developers with ultra-lightweight ways to access identity data
* High Performance - ms response times & tens of thousands of w/r per sec
* Multi Master replication for high availability

As well as the expected LDAP access OpenDJ lets you access directory data as JSON resources over HTTP making it super convenient for web and phone apps.

## License
This project is licensed under the Common Development and Distribution License (CDDL). The following text applies to 
both this file, and should also be included in all files in the project.

## Downloads 
* [OpenDJ DEB, RPM, MSI, ZIP all available](https://github.com/OpenIdentityPlatform/OpenDJ/releases/latest) (Debian,Redhat/Centos/Windows/All OS)
* [OpenDJ Docker](https://hub.docker.com/r/openidentityplatform/opendj/) (All OS) 
  * [OpenDJ OpenShift](https://github.com/OpenIdentityPlatform/OpenDJ/tree/master/opendj-packages/opendj-openshift-template)

Java 1.8+ required

## How-to build
For windows use:
```bash
git config --system core.longpaths true
```

```bash
git clone --recursive  https://github.com/OpenIdentityPlatform/OpenDJ.git
#mvn clean install -f OpenDJ/commons
mvn clean install -f OpenDJ
```

## How-to run after build
```bash
cd OpenDJ/opendj-server-legacy/target/package/opendj
./setup
bin/start-ds
bin/stop-ds
```
See the wiki for [Full Installation guide, Administration guide, and Developers guide](https://github.com/OpenIdentityPlatform/OpenDJ/wiki)

## Support and Mailing List Information
* OpenDJ Community Wiki: https://github.com/OpenIdentityPlatform/OpenDJ/wiki
* OpenDJ Community Mailing List: open-identity-platform-opendj@googlegroups.com
* OpenDJ Community Archive: https://groups.google.com/d/forum/open-identity-platform-opendj
* OpenDJ Community on Gitter: https://gitter.im/OpenIdentityPlatform/OpenDJ
* OpenDJ Commercial support RFP: support@3a-systems.ru (English, Russian)

## Contributing
Please, make [Pull request](https://github.com/OpenIdentityPlatform/OpenDJ/pulls)

## Thanks
* Sun OpenDS
* Oracle OpenDS
* Forgerock OpenDJ
