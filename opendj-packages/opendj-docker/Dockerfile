FROM java:8

MAINTAINER Open Identity Platform <open-identity-platform@googlegroups.com>

ENV PORT 1389

ENV LDAPS_PORT 1636

ENV BASE_DN dc=example,dc=com

ENV ROOT_USER_DN cn=Directory Manager

ENV ROOT_PASSWORD password

ENV git_version 4.1.1

WORKDIR /opt

RUN apt-get install -y wget unzip

RUN wget --quiet https://github.com/OpenIdentityPlatform/OpenDJ/releases/download/$git_version/opendj-$git_version.zip && unzip opendj-$git_version.zip && rm -r opendj-$git_version.zip

RUN /opt/opendj/setup --cli -p $PORT --ldapsPort $LDAPS_PORT --enableStartTLS --generateSelfSignedCertificate --baseDN "$BASE_DN" -h localhost --rootUserDN "$ROOT_USER_DN" --rootUserPassword $ROOT_PASSWORD --acceptLicense --no-prompt --doNotStart

CMD ["/opt/opendj/bin/start-ds", "--nodetach"]
