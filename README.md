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

# OpenDJ Server


OpenDJ is an [LDAPv3](http://tools.ietf.org/html/rfc4510) compliant directory service, which has been developed 
for the Java platform, providing a high performance, highly available, and secure store for the identities managed 
by your organization. Its easy installation process, combined with the power of the Java platform makes OpenDJ
the simplest, fastest directory to deploy and manage.

The project is led by ForgeRock who integrate the [OpenAM][openam_project_page], [OpenIDM][project_page], 
[OpenDJ][opendj_project_page], [OpenICF][openicf_project_page], and [OpenIG][openig_project_page] open source projects 
to provide a quality-assured [ForgeRock Identity Platform][identity_platform]. Support, professional services, and 
training are available for the Identity Platform, providing stability and safety for the management of your digital 
identities.

To find out more about the services ForgeRock provides, visit [www.forgerock.com][commercial_site].

To view the OpenDJ project page, which also contains all of the documentation, visit
 [https://forgerock.org/opendj/][project_page]. 
 
For a great place to start, take a look at the [OpenDJ Install Guide][install_guide].

For further help and discussion, visit the [community forums][community_forum]. 

# Getting the OpenDJ Directory Server

You can obtain the OpenDJ software in one of two ways;

## Download It 

The easiest way to try OpenDJ is to download the binary file and follow the [Installation Guide][install_guide]. 

You can download either:

1. An [enterprise release build][enterprise_builds].
2. The [nightly build][nightly_builds] which contains the latest features and bug fixes, but may also contain 
_in progress_ unstable features.

## Build The Source Code

In order to build the project from the command line follow these steps:

### Prepare your Environment
To build OpenDJ you will need the following installed on the machine you're going to build on:

Software               | Required Version
---------------------- | ----------------
Java JDK Version       | 7 and above (see below)
Git                    | 1.7.6 and above
Maven                  | 3.0.4 and above

ForgeRock does not support the use of Java 9 for running OpenDJ in production.

You should also set the following environment variables for the majority of versions;

JAVA_HOME - set to the directory in which your SDK is installed  
MAVEN_OPTS  - When building with Java 7 set this to '-Xmx1g -XX:MaxPermSize=512m'. Java 8 and above does not support 
MaxPermSize so set this to '-Xmx1g'.

### Getting the Code

The central project repository lives on the ForgeRock Bitbucket Server at 
[https://stash.forgerock.org/projects/OPENDJ][central_repo].

Mirrors exist elsewhere (for example GitHub) but all contributions to the project are managed by using pull requests 
to the central repository.

There are two ways to get the code - if you want to run the code unmodified you can clone the central repo (or a 
reputable mirror):

```
git clone https://stash.forgerock.org/scm/opendj/opendj.git
```

If, however, you are considering contributing bug fixes, enhancements, or modifying the code you should fork the project
 and then clone your private fork, as described below:

1. Create an account on [BackStage][backstage] - You can use these credentials to create pull requests, report bugs,
 and download the enterprise release builds.
2. Log in to the Bitbucket Server using your BackStage account credentials. 
3. Fork the `opendj` project. This will create a fork for you in your own area of Bitbucket Server. Click on your
 profile icon then select 'view profile' to see all your forks. 
4. Clone your fork to your machine.

Obtaining the code this way will allow you to create pull requests later. 

### Building the Code

The OpenDJ build process and dependencies are managed by Maven. The first time you build the project, Maven will pull 
down all the dependencies and Maven plugins required by the build, which can take a significant amount of time. 
Subsequent builds will be much faster!

```
$ cd $REPO_HOME/opendj
$ mvn clean install
```

The OpenDJ Directory Server binaries will be built in `opendj-server-legacy/target/package`

## Getting Started With OpenDJ

ForgeRock provide a comprehensive set of documents for OpenDJ, including a 
[installation guide][install_guide].

- [Documentation for enterprise builds][enterprise_docs].
- [Draft docs for nightly builds and self built code][nightly_docs]


## Contributing

There are many ways to contribute to the OpenDJ project. You can contribute to the [OpenDJ Docs Project][docs_project], 
report or [submit bug fixes][issue_tracking], or [contribute extensions][contribute] such as custom authentication 
modules, authentication scripts, policy scripts, dev ops scripts, and more.

## Versioning

ForgeRock produce an enterprise point release build. These builds use the versioning format X.0.0 (for example 3.0.0 or 
4.0.0 would be enterprise releases) and are produced yearly. These builds are free to use for trials, proof of concept 
projects and so on. A license is required to use these builds in production.

Users with support contracts have access to sustaining releases that contain bug and security fixes. These builds use 
the versioning format 3.0.x. 3.0.1 and 3.5.1 for example, would both be sustaining releases.  Users with support 
contracts also get access to quality-assured interim releases, such as the forthcoming OpenDJ 3.5.0. 

## Authors

See the list of [contributors][contributors] who participated in this project.

## License

This project is licensed under the Common Development and Distribution License (CDDL). The following text applies to 
both this file, and should also be included in all files in the project:

> The contents of this file are subject to the terms of the Common Development and  Distribution License (the License). 
> You may not use this file except in compliance with the License.  
>   
> You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the specific language governing 
> permission and limitations under the License.  
>  
> When distributing Covered Software, include this CDDL Header Notice in each file and include the License file at 
> legal/CDDLv1.0.txt. If applicable, add the following below the CDDL Header, with the fields enclosed by brackets [] 
> replaced by your own identifying information: "Portions copyright [year] [name of copyright owner]".  
>   
> Copyright 2016 ForgeRock AS.    


## All the Links!
To save you sifting through the readme looking for 'that link'...

- [ForgeRock's commercial website][commercial_site]
- [ForgeRock's community website][community_site]
- [ForgeRock's BackStage server][backstage] 
- [ForgeRock Identity Platform][identity_platform]
- [OpenAM Project Page][openam_project_page]
- [OpenDJ Project Page][opendj_project_page]
- [OpenIDM Project Page][project_page]
- [OpenICF Project Page][openicf_project_page]
- [OpenIG Project Page][openig_project_page]
- [Community Forums][community_forum]
- [Install Guide][install_guide]
- [Enterprise Build Downloads][enterprise_builds]
- [Enterprise Documentation][enterprise_docs]
- [Nightly Build Downloads][nightly_builds]
- [Nightly Documentation][nightly_docs]
- [Central Project Repository][central_repo]
- [Issue Tracking][issue_tracking]
- [Contributors][contributors]
- [Coding Standards][coding_standards]
- [Contributions][contribute]
- [How to Buy][how_to_buy]

[commercial_site]: https://www.forgerock.com
[community_site]: https://www.forgerock.org
[backstage]: https://backstage.forgerock.com
[identity_platform]: https://www.forgerock.com/platform/
[openam_project_page]: https://forgerock.org/openam/
[opendj_project_page]: https://forgerock.org/opendj/
[openig_project_page]: https://forgerock.org/openig/
[project_page]: https://forgerock.org/opendj/
[openicf_project_page]: https://forgerock.org/openicf/
[community_forum]: https://forgerock.org/forum/fr-projects/opendj/
[install_guide]: https://forgerock.org/opendj/doc/bootstrap/install-guide/index.html
[enterprise_builds]: https://backstage.forgerock.com/#!/downloads/OpenDJ/OpenDJ%20Enterprise#browse
[enterprise_docs]: https://backstage.forgerock.com/#!/docs/opendj
[nightly_builds]: https://forgerock.org/downloads/opendj-builds/
[nightly_docs]: https://forgerock.org/documentation/opendj/
[central_repo]: https://stash.forgerock.org/projects/OPENDJ/repos/opendj/browse
[issue_tracking]: https://bugster.forgerock.org/jira/browse/OPENDJ/?selectedTab=com.atlassian.jira.jira-projects-plugin:summary-panel
[docs_project]: https://stash.forgerock.org/projects/OPENDJ/repos/opendj-docs/browse
[contributors]: https://stash.forgerock.org/plugins/servlet/graphs?graph=contributors&projectKey=OPENDJ&repoSlug=opendj&refId=all-branches&type=c&group=weeks
[coding_standards]: https://wikis.forgerock.org/confluence/display/devcom/Coding+Style+and+Guidelines
[how_to_buy]: https://www.forgerock.com/platform/how-buy/
[contribute]: https://forgerock.org/projects/contribute/

## Acknowledgments

* Sun Microsystems.
* The founders of ForgeRock.
* The good things in life.
