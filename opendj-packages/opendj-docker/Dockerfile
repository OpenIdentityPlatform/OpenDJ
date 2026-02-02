FROM eclipse-temurin:25-jre-jammy

LABEL org.opencontainers.image.authors="Open Identity Platform Community"

ENV ADD_BASE_ENTRY="--addBaseEntry"
ENV PORT=1389
ENV LDAPS_PORT=1636
ENV ADMIN_PORT=4444
ENV BASE_DN="dc=example,dc=com"
ENV ROOT_USER_DN="cn=Directory Manager"
ENV ROOT_PASSWORD="password"
#ENV SECRET_VOLUME
ENV OPENDJ_SSL_OPTIONS="--generateSelfSignedCertificate"
#ENV MASTER_SERVER
#ENV OPENDJ_REPLICATION_TYPE
ENV OPENDJ_USER="opendj"
#ENV OPENDJ_JAVA_ARGS=""
ENV BACKEND_TYPE="je"
ENV BACKEND_DB_DIRECTORY="db"
#ENV SETUP_ARGS

ARG VERSION

WORKDIR /opt

#COPY opendj-*.zip .

RUN  apt-get update \
 && apt-get install -y --no-install-recommends curl unzip \
 && if [ -z "$VERSION" ] ; then VERSION="$(curl -i -o - --silent https://api.github.com/repos/OpenIdentityPlatform/OpenDJ/releases/latest | grep -m1 "\"name\"" | cut -d\" -f4)"; fi \
 && if [ ! -f "/opt/opendj-*.zip" ]; then echo file exists && curl -L https://github.com/OpenIdentityPlatform/OpenDJ/releases/download/$VERSION/opendj-$VERSION.zip --output opendj-$VERSION.zip; fi \
 && unzip opendj-*.zip \
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

EXPOSE $PORT/tcp $LDAPS_PORT/tcp $ADMIN_PORT/tcp

USER $OPENDJ_USER

HEALTHCHECK --interval=30s --timeout=30s --start-period=1s --retries=3 CMD opendj/bin/ldapsearch --hostname localhost --port $LDAPS_PORT --bindDN "$ROOT_USER_DN" --bindPassword $ROOT_PASSWORD --useSsl --trustAll --baseDN "$BASE_DN" --searchScope base "(objectClass=*)" 1.1 || exit 1

ENTRYPOINT ["/opt/opendj/run.sh"]
