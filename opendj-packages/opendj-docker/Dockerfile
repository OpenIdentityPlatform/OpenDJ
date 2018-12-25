FROM java:8

MAINTAINER Open Identity Platform Community <open-identity-platform-opendj@googlegroups.com>

ARG ADD_BASE_ENTRY="--addBaseEntry"

ARG PORT=1389

ARG LDAPS_PORT=1636

ARG BASE_DN="dc=example,dc=com"

ARG ROOT_USER_DN="cn=Directory Manager"

ARG ROOT_PASSWORD=password

ARG VERSION=@project_version@

ARG OPENDJ_USER="opendj"

WORKDIR /opt

RUN apt-get install -y wget unzip

RUN wget --quiet \
  https://github.com/OpenIdentityPlatform/OpenDJ/releases/download/$VERSION/opendj-$VERSION.zip && \
  unzip opendj-$VERSION.zip && \
  rm -r opendj-$VERSION.zip

RUN useradd -m -r -u 1001 -g root $OPENDJ_USER

RUN chgrp -R 0 /opt/opendj && \
  chmod -R g=u /opt/opendj

USER $OPENDJ_USER

RUN /opt/opendj/setup --cli -p $PORT --ldapsPort $LDAPS_PORT --enableStartTLS \
  --generateSelfSignedCertificate --baseDN "$BASE_DN" -h localhost --rootUserDN "$ROOT_USER_DN" \
  --rootUserPassword "$ROOT_PASSWORD" --acceptLicense --no-prompt --doNotStart $ADD_BASE_ENTRY

EXPOSE $PORT $LDAPS_PORT 4444

CMD ["/opt/opendj/bin/start-ds", "--nodetach"
