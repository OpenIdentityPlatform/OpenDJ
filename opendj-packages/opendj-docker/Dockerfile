FROM openjdk:8-jre-slim

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

ENV OPENDJ_USER="opendj"

WORKDIR /opt

ADD target/opendj-*.zip .
RUN unzip opendj-*.zip && rm -r opendj-*.zip

ADD bootstrap/ /opt/opendj/bootstrap/

ADD run.sh /opt/opendj/run.sh

RUN groupadd $OPENDJ_USER 

RUN useradd -m -r -u 1001 -g $OPENDJ_USER $OPENDJ_USER \
    && install -d -o $OPENDJ_USER /opt/opendj/data

RUN chown -R $OPENDJ_USER:$OPENDJ_USER /opt/opendj && \
    chmod -R g=u /opt/opendj && \
    chmod +x /opt/opendj/run.sh \
     /opt/opendj/bootstrap/setup.sh \
     /opt/opendj/bootstrap/replicate.sh

EXPOSE $PORT $LDAPS_PORT 4444

USER $OPENDJ_USER

ENTRYPOINT ["/opt/opendj/run.sh"]
