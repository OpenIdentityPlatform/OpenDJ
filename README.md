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

OpenDJ Server
=============

**OpenDJ** is an [LDAPv3](http://tools.ietf.org/html/rfc4510) compliant directory service, which is actively maintained
and supported by [ForgeRock](http://www.forgerock.com). The **OpenDJ Directory Server** has been developed for the Java
platform, providing a high performance, highly available, and secure store for the identities managed by your
organization. Its easy installation process, combined with the power of the Java platform makes **OpenDJ** the
simplest, fastest directory to deploy and manage.

Get the OpenDJ Directory Server
===============================

You can obtain the **OpenDJ Directory Server** using any of the following methods:

Download
--------

By far the simplest method is to download a build from [ForgeRock BackStage](https://backstage.forgerock.com/#!/),
or a nightly build from the [ForgeRock community resource center](https://forgerock.org/)

Build it yourself
-----------------

You need `git` and `maven` in order to get the source code and build it:

```bash
git clone ssh://git@stash.forgerock.org:7999/opendj/opendj.git
cd opendj
mvn clean install
```

Getting started
===============

Once you have obtained a copy of the **OpenDJ Directory Server**, read the
[Installation Guide](http://opendj.forgerock.org/doc/bootstrap/install-guide/index.html) for further instructions.

Documentation
=============

The following documentation is available online:

* [Installation Guide](http://opendj.forgerock.org/doc/bootstrap/install-guide/index.html)
* [Administration Guide](http://opendj.forgerock.org/doc/bootstrap/admin-guide/index.html)
* [Configuration Reference Guide](http://opendj.forgerock.org/opendj-server-legacy/configref/index.html)
* [Reference Guide](http://opendj.forgerock.org/doc/bootstrap/reference/index.html)
* [Release Notes](http://opendj.forgerock.org/doc/bootstrap/release-notes/index.html)

License
=======

**OpenDJ Directory Server** is licensed under [CDDL 1.0](legal-notices/CDDLv1_0.txt) (COMMON DEVELOPMENT AND
DISTRIBUTION LICENSE Version 1.0)
