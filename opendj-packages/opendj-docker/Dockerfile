FROM java:8

MAINTAINER Open Identity Platform Community <open-identity-platform-opendj@googlegroups.com>

ENV ADD_BASE_ENTRY="--addBaseEntry"

ENV PORT=1389

ENV LDAPS_PORT=1636

ENV BASE_DN=${BASE_DN:-"dc=example,dc=com"}

ENV ROOT_USER_DN=${ROOT_USER_DN:-"cn=Directory Manager"}

ENV ROOT_PASSWORD=${ROOT_PASSWORD:-"password"}

ENV SECRET_VOLUME=${SECRET_VOLUME}

ENV OPENDJ_SSL_OPTIONS=${SSL_OPTIONS:-"--generateSelfSignedCertificate"}

ENV MASTER_SERVER=${MASTER_SERVER}

ENV OPENDJ_REPLICATION_TYPE=${OPENDJ_REPLICATION_TYPE}

ARG VERSION=@project_version@

ENV OPENDJ_USER="opendj"

WORKDIR /opt

RUN wget --show-progress --progress=bar:force:noscroll --quiet \
  https://github.com/OpenIdentityPlatform/OpenDJ/releases/download/$VERSION/opendj-$VERSION.zip && \
  unzip opendj-$VERSION.zip && \
  rm -r opendj-$VERSION.zip

ADD bootstrap/ /opt/opendj/bootstrap/

ADD run.sh /opt/opendj/run.sh

RUN useradd -m -r -u 1001 -G root,sudo $OPENDJ_USER

RUN chgrp -R 0 /opt/opendj && \
    chmod -R g=u /opt/opendj && \
    chmod +x /opt/opendj/run.sh \
     /opt/opendj/bootstrap/setup.sh \
     /opt/opendj/bootstrap/replicate.sh

EXPOSE $PORT $LDAPS_PORT 4444

USER $OPENDJ_USER

ENTRYPOINT ["/opt/opendj/run.sh"]
