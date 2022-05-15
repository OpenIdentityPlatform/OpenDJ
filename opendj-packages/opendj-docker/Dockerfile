FROM openjdk:11-jre-slim

MAINTAINER Open Identity Platform Community <open-identity-platform-opendj@googlegroups.com>

ENV ADD_BASE_ENTRY="--addBaseEntry" \
    PORT=1389 \
    LDAPS_PORT=1636 \
    ADMIN_PORT=4444 \
    BASE_DN=${BASE_DN:-"dc=example,dc=com"} \
    ROOT_USER_DN=${ROOT_USER_DN:-"cn=Directory Manager"} \
    ROOT_PASSWORD=${ROOT_PASSWORD:-"password"} \
    SECRET_VOLUME=${SECRET_VOLUME} \
    OPENDJ_SSL_OPTIONS=${SSL_OPTIONS:-"--generateSelfSignedCertificate"} \
    MASTER_SERVER=${MASTER_SERVER} \
    OPENDJ_REPLICATION_TYPE=${OPENDJ_REPLICATION_TYPE} \
    OPENDJ_USER="opendj"

ARG VERSION=@project_version@

WORKDIR /opt

RUN apt-get update \
 && apt-get install -y --no-install-recommends wget unzip \
 && wget --quiet https://github.com/OpenIdentityPlatform/OpenDJ/releases/download/$VERSION/opendj-$VERSION.zip \
 && unzip opendj-$VERSION.zip \
 && apt-get remove -y --purge unzip \
 && apt-get remove -y --purge wget \
 && rm -rf /var/lib/apt/lists/* \
 && rm -r opendj-*.zip \
 && groupadd $OPENDJ_USER \
 && useradd -m -r -u 1001 -g $OPENDJ_USER $OPENDJ_USER \
 && install -d -o $OPENDJ_USER /opt/opendj/data \
 && chown -R $OPENDJ_USER:$OPENDJ_USER /opt/opendj \
 && chmod -R g=u /opt/opendj

COPY --chown=$OPENDJ_USER:$OPENDJ_USER bootstrap/ /opt/opendj/bootstrap/
COPY --chown=$OPENDJ_USER:$OPENDJ_USER run.sh /opt/opendj/run.sh

RUN chmod +x /opt/opendj/run.sh /opt/opendj/bootstrap/setup.sh /opt/opendj/bootstrap/replicate.sh

EXPOSE $PORT $LDAPS_PORT $ADMIN_PORT

USER $OPENDJ_USER

ENTRYPOINT ["/opt/opendj/run.sh"]
