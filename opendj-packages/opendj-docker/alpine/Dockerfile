FROM java:8-alpine

MAINTAINER Open Identity Platform Community <open-identity-platform-opendj@googlegroups.com>

ARG ADD_BASE_ENTRY="--addBaseEntry"

ARG PORT=1389

ARG LDAPS_PORT=1636

ARG BASE_DN="dc=example,dc=com"

ARG ROOT_USER_DN="cn=Directory Manager"

ARG ROOT_PASSWORD=password

ARG VERSION=@project_version@

WORKDIR /opt

RUN apk add --update wget unzip

RUN wget --quiet \
  https://github.com/OpenIdentityPlatform/OpenDJ/releases/download/$VERSION/opendj-$VERSION.zip && \
  unzip opendj-$VERSION.zip && \
  rm -r opendj-$VERSION.zip

RUN /opt/opendj/setup --cli -p $PORT --ldapsPort $LDAPS_PORT --enableStartTLS \
  --generateSelfSignedCertificate --baseDN "$BASE_DN" -h localhost --rootUserDN "$ROOT_USER_DN" \
  --rootUserPassword "$ROOT_PASSWORD" --acceptLicense --no-prompt --doNotStart $ADD_BASE_ENTRY

CMD ["/opt/opendj/bin/start-ds", "--nodetach"]
