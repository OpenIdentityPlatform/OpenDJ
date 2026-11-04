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

Copyright 2026 3A Systems, LLC.
-->

# Maintaining the How-To guides

Edit `src/main/asciidoc/how-to`, not the generated site modules or the former wiki tutorials. Each task has one maintained procedure. Link to the Installation, Administration or Developer's Guide when that procedure already exists there.

The `how-to/index.adoc` book includes its chapters using `include::./name.adoc[]`. The Maven `documents` list selects guides for preprocessing, Antora conversion and PDF generation. Register navigation and the generated PDF filename separately in `opendj/antora.yml` in the documentation-site repository. Do not hand-edit the generated `modules` tree there: deployment replaces it.

Use `xref:../guide/chapter.adoc#anchor[Title]` for cross-guide links and `xref:./chapter.adoc[Title]` for another How-To. Each chapter must define any attributes it uses, because the site renders chapters separately. Use `subs="+attributes"` on a code block only when it intentionally substitutes an AsciiDoc attribute. Keep language expressions such as Java placeholders literal.

## Wiki migration inventory

Source: [OpenDJ wiki](https://github.com/OpenIdentityPlatform/OpenDJ/wiki), revision `d0a4db8af19b58815710a5eca7ebc6c0d188c9e0`.

| Former wiki page | Maintained destination |
| --- | --- |
| How To Run OpenDJ in Kubernetes | [how-to/kubernetes](https://doc.openidentityplatform.org/opendj/how-to/kubernetes) |
| How to Setup OpenDJ with BCFKS FIPS Key Store Type support | [how-to/bcfks](https://doc.openidentityplatform.org/opendj/how-to/bcfks) |
| Using a Relational Database as an LDAP Backend | [how-to/jdbc-backend](https://doc.openidentityplatform.org/opendj/how-to/jdbc-backend) |
| How to Run OpenDJ based Embedded LDAP in Spring Boot Application | [how-to/embedded-ldap](https://doc.openidentityplatform.org/opendj/how-to/embedded-ldap) |
| REST Access to OpenDJ Directory Server | [how-to/rest-access](https://doc.openidentityplatform.org/opendj/how-to/rest-access) |
| How to delegate password reset permissions | [how-to/delegate-password-reset](https://doc.openidentityplatform.org/opendj/how-to/delegate-password-reset) |
| How to generate test LDIF data and import to LDAP server OpenDJ | [how-to/generate-test-data](https://doc.openidentityplatform.org/opendj/how-to/generate-test-data) |
| TIP: How to Persist OpenDJ Docker Container Data Between Restarts | [install-guide/chap-install#install-docker](https://doc.openidentityplatform.org/opendj/install-guide/chap-install#install-docker) |
| How To Guides | [how-to/](https://doc.openidentityplatform.org/opendj/how-to/) |
| How To | How-To task directory and the recipes below |

| Recipe in the former How To collection | Destination |
| --- | --- |
| Get last version; setup server | Installation Guide: command-line installation |
| Clean and reinstall server | Installation Guide: uninstall, then install |
| Check server status | Administration Guide: monitoring |
| Backup and restore | Administration Guide: backup and restore |
| Initialize replication; disable replication | Administration Guide: replication |
| List indexes; rebuild degraded indexes | Administration Guide: indexing |
| List TLS protocols/ciphers; restrict TLS | Administration Guide: TLS protocols and cipher suites |
| Use a certificate with Docker | Connection handlers: certificate preparation; Docker image README: OPENDJ_SSL_OPTIONS |
| Cassandra backend | how-to/cassandra-backend.adoc |
| JDBC backend | how-to/jdbc-backend.adoc |
| Debug instance | how-to/debug-instance.adoc |
| Jira integration | Existing link to Atlassian's instructions, retained in how-to/tasks.adoc |

The staged wiki changes are provided in [`wiki-how-to-migration.patch`](wiki-how-to-migration.patch). Apply the patch to a checkout of the wiki only after the publication checks below pass.

## Publication order

1. Merge and deploy the OpenDJ documentation sources, then register the new navigation and PDF link in the site repository once the generated modules are available. Preview both changes together before publication.
2. Verify all destinations above, the How-To menu, PDF download and search results on the published site.
3. Replace the eight former tutorial bodies with short relocation notices, and the How To collection with a link directory. Keep their filenames so existing wiki URLs remain useful. Keep How To Guides as a link to the new guide.

Prepare wiki notices locally, but do not publish them before their destinations exist. The wiki history retains the original text. Do not maintain or synchronize a second full copy of the articles.
