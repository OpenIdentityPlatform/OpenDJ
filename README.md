# <img alt="OpenDJ Logo" src="https://github.com/OpenIdentityPlatform/OpenDJ/raw/master/logo.png" width="300"/>
[![Latest release](https://img.shields.io/github/release/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/releases)
[![Build](https://github.com/OpenIdentityPlatform/OpenDJ/actions/workflows/build.yml/badge.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/actions/workflows/build.yml)
[![Deploy](https://github.com/OpenIdentityPlatform/OpenDJ/actions/workflows/deploy.yml/badge.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/actions/workflows/deploy.yml)
[![Issues](https://img.shields.io/github/issues/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/issues)
[![Last commit](https://img.shields.io/github/last-commit/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/commits/master)
[![License](https://img.shields.io/badge/license-CDDL-blue.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/blob/master/LICENSE.md)
[![Downloads](https://img.shields.io/github/downloads/OpenIdentityPlatform/OpenDJ/total.svg)](https://github.com/OpenIdentityPlatform/OpenDJ/releases)
[![Docker](https://img.shields.io/docker/pulls/openidentityplatform/opendj.svg)](https://hub.docker.com/r/openidentityplatform/opendj)
[![Top language](https://img.shields.io/github/languages/top/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ)
[![Code size in bytes](https://img.shields.io/github/languages/code-size/OpenIdentityPlatform/OpenDJ.svg)](https://github.com/OpenIdentityPlatform/OpenDJ)

OpenDJ is an [LDAPv3](http://tools.ietf.org/html/rfc4510) compliant directory service, which has been developed 
for the Java platform, providing a high performance, highly available, and secure store for the identities managed 
by your organization. Its easy installation process, combined with the power of the Java platform makes OpenDJ
the simplest, fastest directory to deploy and manage and allow store LDAPv3 database in [SQL JDBC database](https://github.com/OpenIdentityPlatform/OpenDJ/wiki/How-To#store-ldap-catalog-data-in-jdbc-databse) or [NoSQL Cassandra/Scylla cluster](https://github.com/OpenIdentityPlatform/OpenDJ/wiki/How-To#store-ldap-catalog-data-in-cassandra-nosql-cluster).

An open source, lightweight, embeddable directory that can easily share real-time customer, device, and user identity data across enterprise, cloud, social, and mobile environments.
* Massive data scale and high availability provide developers with ultra-lightweight ways to access identity data
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

Java 11 or later required

## How-to build
For windows use:
```bash
git config --system core.longpaths true
```

```bash
git clone --recursive  https://github.com/OpenIdentityPlatform/OpenDJ.git
mvn clean install -f OpenDJ
```

## How-to run after build
```bash
cd OpenDJ/opendj-server-legacy/target/package/opendj
./setup
bin/start-ds
bin/stop-ds
```

## Support 
* OpenDJ Community [documentation](https://github.com/OpenIdentityPlatform/OpenDJ/wiki)
* OpenDJ Community [discussions](https://github.com/OpenIdentityPlatform/OpenDJ/discussions)
* OpenDJ Community [issues](https://github.com/OpenIdentityPlatform/OpenDJ/issues)
* OpenDJ [commercial support](https://github.com/OpenIdentityPlatform/.github/wiki/Approved-Vendor-List)

## Thanks 🥰
* Sun OpenDS
* Oracle OpenDS
* Forgerock OpenDJ

## Contributing
Please, make [Pull request](https://github.com/OpenIdentityPlatform/OpenDJ/pulls)

<a href="https://opencollective.com/OpenDJ/tiers" target="_blank">
  <!--img src="https://contributors-img.web.app/image?repo=OpenIdentityPlatform/OpenDJ" /-->
  <img src="https://opencollective.com/OpenDJ/contributors.svg?width=890&button=true" />
</a>

## Backers
Thank you to all our backers! [Become a backer 🙏](https://opencollective.com/OpenDJ/tiers)

<a href="https://opencollective.com/OpenDJ/tiers" target="_blank">
 <img src="https://opencollective.com/OpenDJ/backers.svg?width=890">
</a>

## Sponsors
Support this project by becoming a sponsor. Your logo will show up here with a link to your website. [Become a sponsor ❤️](https://opencollective.com/OpenDJ/tiers)

<a href="https://opencollective.com/OpenDJ/tiers" target="_blank">
 <img src="https://opencollective.com/OpenDJ/sponsors.svg?width=890">
</a>
