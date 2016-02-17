<!--
  The contents of this file are subject to the terms of the Common Development and
  Distribution License (the License). You may not use this file except in compliance with the
  License.

  You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
  specific language governing permission and limitations under the License.

  When distributing Covered Software, include this CDDL Header Notice in each file and include
  the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
  Header, with the fields enclosed by brackets [] replaced by your own identifying
  information: "Portions copyright [year] [name of copyright owner]".

  Copyright 2016 ForgeRock AS.
  -->

OpenDJ SDK
==========

The **OpenDJ LDAP SDK** provides a set of modern, developer-friendly Java APIs as part of the
[OpenDJ](http://opendj.forgerock.org) product suite, which is actively maintained and supported by
[ForgeRock](http://www.forgerock.com). The product suite includes the client SDK alongside command-line tools and
sample code, a 100% pure Java directory server, and more. You can use **OpenDJ SDK** to create client applications
for use with any server that complies with the
[RFC 4510: LDAP - Technical Specification Road Map](http://tools.ietf.org/html/rfc4510).

The **OpenDJ LDAP SDK** brings you easy-to-use connection management, connection pooling, load balancing, and all the
standard LDAP operations to read and write directory entries. **OpenDJ LDAP SDK** also lets you build applications with
capabilities defined in additional draft and experimental RFCs that are supported by modern LDAP servers.

Documentation
=============

Javadoc for this module can be found [here](http://opendj.forgerock.org/opendj-core/apidocs/index.html). Read the
[developer guide](http://opendj.forgerock.org/doc/dev-guide/index.html) for a deeper understanding of LDAP application
development, as well as a detailed overview of LDAP itself.

Get the OpenDJ LDAP SDK
=======================

You can start developing your LDAP applications now by obtaining the **OpenDJ LDAP SDK** using any of the following
methods:

Maven
-----

By far the simplest method is to develop your application using Maven and add the following settings to your pom.xml:

```xml
<repositories>
  <repository>
    <id>forgerock-staging-repository</id>
    <name>ForgeRock Release Repository</name>
    <url>http://maven.forgerock.org/repo/releases</url>
    <snapshots>
      <enabled>false</enabled>
    </snapshots>
  </repository>
  <repository>
    <id>forgerock-snapshots-repository</id>
    <name>ForgeRock Snapshot Repository</name>
    <url>http://maven.forgerock.org/repo/snapshots</url>
    <releases>
      <enabled>false</enabled>
    </releases>
  </repository>
</repositories>
```

The following dependencies will load both the [OpenDJ Core APIs](opendj-core) and the [OpenDJ Grizzly](opendj-grizzly)
network transport. Remember to override the version according to your needs:

```xml
<dependencies>
  <dependency>
    <groupId>org.forgerock.opendj</groupId>
    <artifactId>opendj-core</artifactId>
    <version>3.0.0</version>
  </dependency>
  <dependency>
    <groupId>org.forgerock.opendj</groupId>
    <artifactId>opendj-grizzly</artifactId>
    <version>3.0.0</version>
  </dependency>
</dependencies>
```

In some use-cases, such as developing LDAP unit tests or embedded LDAP applications, the network transport is not
required, in which case you can simply declare a dependency on the OpenDJ core APIs:

```xml
<dependencies>
  <dependency>
    <groupId>org.forgerock.opendj</groupId>
    <artifactId>opendj-core</artifactId>
    <version>3.0.0</version>
  </dependency>
</dependencies>
```

Build it yourself
-----------------

You need `git` and `maven` in order to get the source code and build it:

```bash
git clone ssh://git@stash.forgerock.org:7999/opendj/opendj-sdk.git
cd opendj-sdk
mvn clean install
```

Getting started
===============

The following example shows how the **OpenDJ LDAP SDK** may be used to connect to a Directory Server, authenticate, and
then perform a search. The search results are output as LDIF to the standard output:

```java
// Create an LDIF writer which will write the search results to stdout.
final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);

// Connect and bind to the server.
final LDAPConnectionFactory factory = new LDAPConnectionFactory(hostName, port);
final Connection connection = factory.getConnection();
connection.bind(userName, password.toCharArray());

// Read the entries and output them as LDIF.
final ConnectionEntryReader reader = connection.search(baseDN, scope, filter, attributes);
while (reader.hasNext()) {
    if (!reader.isReference()) {
        final SearchResultEntry entry = reader.readEntry();
        writer.writeComment("Search result entry: " + entry.getName());
        writer.writeEntry(entry);
    } else {
        final SearchResultReference ref = reader.readReference();
        writer.writeComment("Search result reference: " + ref.getURIs());
    }
}
writer.flush();
connection.close();
```

License
=======

**OpenDJ LDAP SDK** is licensed under [CDDL 1.0](legal-notices/CDDLv1_0.txt) (COMMON DEVELOPMENT AND DISTRIBUTION
LICENSE Version 1.0)
