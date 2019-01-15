FROM java:8

MAINTAINER Open Identity Platform Community <open-identity-platform-opendj@googlegroups.com>

ENV ADD_BASE_ENTRY="--addBaseEntry"

ENV PORT=1389

ENV LDAPS_PORT=1636

ENV BASE_DN=${BASE_DN:-"dc=example,dc=com"}

ENV ROOT_USER_DN=${ROOT_USER_DN:-"cn=Directory Manager"}

ENV ROOT_PASSWORD=${ROOT_PASSWORD:-"password"}

ENV SECRET_VOLUME=${SECRET_VOLUME}

ENV MASTER_SERVER=${MASTER_SERVER}

ENV VERSION=@project_version@

#ENV VERSION=4.3.1

ENV OPENDJ_USER="opendj"

WORKDIR /opt

RUN wget --show-progress --progress=bar:force:noscroll --quiet \
  https://github.com/OpenIdentityPlatform/OpenDJ/releases/download/$VERSION/opendj-$VERSION.zip && \
  unzip opendj-$VERSION.zip && \
  rm -r opendj-$VERSION.zip

RUN echo "/opt/opendj/data" > /opt/opendj/instance.loc  && \
    mkdir -p /opt/opendj/data/lib/extensions

ADD bootstrap/ /opt/opendj/bootstrap/

ADD run.sh /opt/opendj/run.sh

RUN useradd -m -r -u 1001 -G root,sudo $OPENDJ_USER

RUN chgrp -R 0 /opt/opendj && \
    chmod -R g=u /opt/opendj

USER $OPENDJ_USER

RUN /opt/opendj/setup --cli -p $PORT --ldapsPort $LDAPS_PORT --enableStartTLS \
  --generateSelfSignedCertificate --baseDN "$BASE_DN" -h localhost --rootUserDN "$ROOT_USER_DN" \
  --rootUserPassword "$ROOT_PASSWORD" --acceptLicense --no-prompt --doNotStart $ADD_BASE_ENTRY

EXPOSE $PORT $LDAPS_PORT 4444

ENTRYPOINT ["/opt/opendj/run.sh"]
