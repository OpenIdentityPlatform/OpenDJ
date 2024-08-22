FROM eclipse-temurin:21-jre-jammy

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
    OPENDJ_USER="opendj" \
    OPENDJ_JAVA_ARGS="-server -XX:+UseContainerSupport"

ARG VERSION=@project_version@

WORKDIR /opt

RUN  apt-get update \
 && apt-get install -y --no-install-recommends curl unzip \
 && curl -L https://github.com/OpenIdentityPlatform/OpenDJ/releases/download/$VERSION/opendj-$VERSION.zip --output opendj-$VERSION.zip \
 && unzip opendj-$VERSION.zip \
 && apt-get remove -y --purge curl unzip \
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

HEALTHCHECK --interval=30s --timeout=30s --start-period=1s --retries=3 CMD opendj/bin/ldapsearch --hostname localhost --port $LDAPS_PORT --bindDN "$ROOT_USER_DN" --bindPassword $ROOT_PASSWORD --useSsl --trustAll --baseDN "$BASE_DN" --searchScope base "(objectClass=*)" 1.1 || exit 1

ENTRYPOINT ["/opt/opendj/run.sh"]
