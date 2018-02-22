FROM java:8

MAINTAINER Open Identity Platform <open-identity-platform@googlegroups.com>

WORKDIR /opt

RUN apt-get install -y wget unzip

RUN wget --quiet https://github.com/OpenIdentityPlatform/OpenDJ/releases/download/4.0.3/opendj-4.0.3.zip && unzip opendj-4.0.3.zip && rm -r opendj-4.0.3.zip

ENV PORT 1389

ENV LDAPS_PORT 1636

ENV BASE_DN dc=example,dc=com

ENV ROOT_USER_DN cn=Directory Manager

ENV ROOT_PASSWORD password

RUN /opt/opendj/setup --cli -p $PORT --ldapsPort $LDAPS_PORT --enableStartTLS --generateSelfSignedCertificate --baseDN "$BASE_DN" -h localhost --rootUserDN "$ROOT_USER_DN" --rootUserPassword $ROOT_PASSWORD --acceptLicense --no-prompt --doNotStart

CMD ["/opt/opendj/bin/start-ds", "--nodetach"]
