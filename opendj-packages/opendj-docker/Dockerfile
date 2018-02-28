FROM java:8

MAINTAINER Open Identity Platform Community <open-identity-platform-opendj@googlegroups.com>

ENV PORT 1389

ENV LDAPS_PORT 1636

ENV BASE_DN dc=example,dc=com

ENV ROOT_USER_DN cn=Directory Manager

ENV ROOT_PASSWORD password

ENV VERSION @project_version@

WORKDIR /opt

RUN apt-get install -y wget unzip

RUN wget --quiet https://github.com/OpenIdentityPlatform/OpenDJ/releases/download/$VERSION/opendj-$VERSION.zip && unzip opendj-$VERSION.zip && rm -r opendj-$VERSION.zip

RUN /opt/opendj/setup --cli -p $PORT --ldapsPort $LDAPS_PORT --enableStartTLS --generateSelfSignedCertificate --baseDN "$BASE_DN" -h localhost --rootUserDN "$ROOT_USER_DN" --rootUserPassword $ROOT_PASSWORD --acceptLicense --no-prompt --doNotStart

CMD ["/opt/opendj/bin/start-ds", "--nodetach"]
