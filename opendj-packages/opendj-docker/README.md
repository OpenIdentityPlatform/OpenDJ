# How-to:

Build docker image:

```bash
docker build -t openidentityplatform/opendj .
```

Run image

```bash
docker run -d -p 1389:1389 -p 1636:1636 -p 4444:4444 --name opendj openidentityplatform/opendj
```

## Health check

The image reports itself `healthy` once the server answers on `LDAPS_PORT` *and* the whole
bootstrap has succeeded - the instance, the `userRoot` backend over `BASE_DN`, whatever
`ADD_BASE_ENTRY` and `SAMPLE_DATA` asked to be imported into it, and the replication asked
for by `MASTER_SERVER`. Waiting for that status is therefore enough before the first search
of what the bootstrap was told to create:

```bash
docker run -d --name opendj -e ADD_BASE_ENTRY=--addBaseEntry openidentityplatform/opendj
timeout 5m bash -c 'until [ "$(docker inspect -f "{{.State.Health.Status}}" opendj)" = healthy ]; do sleep 5; done'
```

In Compose the same is `depends_on: { opendj: { condition: service_healthy } }`. Note that
without `ADD_BASE_ENTRY` nothing creates the base entry, so `BASE_DN` is an empty suffix on
a healthy container - the health check itself searches the root DSE, which every instance
serves whatever it was set up to hold.

The health check does not bind as the root user: `ROOT_PASSWORD` is only the initial root
password, and a probe binding with it would turn the container `unhealthy` once that password
is changed. It reads the root DSE anonymously instead. An instance that rejects
unauthenticated requests (`reject-unauthenticated-requests:true`) answers that search with
`53 (Unwilling to Perform)`; for such an instance set `HEALTHCHECK_BIND_DN` to an account the
probe may bind as and `HEALTHCHECK_BIND_PASSWORD_FILE` to a file in the container holding its
password - the probe reads it from there, so it never shows on a command line:

```bash
docker run -d --name opendj -v /path/to/secrets:/var/secrets/healthcheck:ro \
  -e HEALTHCHECK_BIND_DN="uid=monitor,ou=people,dc=example,dc=com" \
  -e HEALTHCHECK_BIND_PASSWORD_FILE=/var/secrets/healthcheck/password \
  openidentityplatform/opendj
```

Images before this one probed as the root user, so an existing instance that rejects
unauthenticated requests was healthy with them. Started on this image without these two
variables, the same instance is probed anonymously and turns `unhealthy` although it serves:
set them before the upgrade.

The server answering is not enough on a first start: the bootstrap starts the server, and
once it is done that server is stopped and started again in the foreground, so a client
that only waits for the port can have its first requests fail in between. A replica set up
with `MASTER_SERVER` tries a master it cannot connect to again, every 10 s for up to 5
minutes, so a master that is down when the replica reaches it does not fail its replication
setup; one that stops while `dsreplication enable` is writing to it still does.

With `OPENDJ_REPLICATION_TYPE=srs`, start the directory server replicas one at a time, each
once the previous one is healthy: every replica pushes its data to the replicas connected at
that moment, and one that is restarting at the end of its own first start misses it.

A bootstrap that imports `SAMPLE_DATA` can take minutes on a small container, which is what
the start period allows for. A bootstrap that fails - or an upgrade that fails when starting
over an instance that is already there - never reports healthy: what failed is in `docker
logs`, and where the server is up at all the container is left running to be looked at,
turning `unhealthy` once the start period is over.

The server runs as PID 1 of the container, and a JVM does not reap the processes left
behind to it - those of a health check that ran past its timeout, say. Run the container
with `docker run --init` (`init: true` in Compose) to put a PID 1 in front of the server
that reaps them and passes SIGTERM on to it.

## Certificates

With the default `OPENDJ_SSL_OPTIONS` the instance serves LDAPS and StartTLS with a
self-signed certificate from `config/keystore`, whose password is in `config/keystore.pin`.
To serve your own certificate, mount a directory holding a `keystore` (JKS or PKCS12) and
its `keystore.pin` at `SECRET_VOLUME`, and a `truststore` next to them if clients present
certificates. With `--generateSelfSignedCertificate` setup binds the connection handlers to
no alias, even when a `--certNickname` is given as well, so the key entry of the keystore
may have any alias. Only when `OPENDJ_SSL_OPTIONS` sets up a keystore of its own
(`--useJavaKeystore`, `--usePkcs12keyStore`) with a `--certNickname` does the key have to be
under that alias:

```bash
docker run -d --name opendj -v opendj-data:/opt/opendj/data \
  -v "$PWD/secrets":/var/secrets/opendj:ro openidentityplatform/opendj
```

Every `key*` and `trust*` file of that directory is copied into `/opt/opendj/data/config`
before the server starts - on the first start and on every later one, so a certificate
renewed on the volume reaches an instance kept on a persistent volume. While the server
runs, the directory is checked again every `SECRET_VOLUME_REFRESH` seconds and a changed
file is copied again. The server loads a copied keystore or truststore on the next TLS
handshake, so a renewed certificate is served without a restart, a new password in
`keystore.pin` or `truststore.pin` included; connections already open keep the certificate
they were set up with. While it runs, the server takes a renewed keystore only if it holds
its key under an alias the previous one used as well - cert-manager keeps the alias from
one renewal to the next. A keystore with only new aliases is served from the next restart,
and until then the server logs that it could not load it. So does a keystore caught by a
handshake between its copy and that of its `.pin` file, until the second file is copied as
well. The administration connector and replication keep keys of their own and are not
affected.

On Kubernetes, the PEM files of a `kubernetes.io/tls` Secret cannot be used as they are:
OpenDJ reads keystores, not PEM. cert-manager can add a PKCS12 keystore to the Secret it
issues, and a projected volume mounts it under the names above:

```yaml
# the Certificate
spec:
  secretName: opendj-tls
  keystores:
    pkcs12:
      create: true
      passwordSecretRef: { name: opendj-keystore-password, key: password }
---
# the pod template of the StatefulSet
volumes:
  - name: secrets
    projected:
      sources:
        - secret:
            name: opendj-tls
            items: [{ key: keystore.p12, path: keystore }]
        - secret:
            name: opendj-keystore-password
            items: [{ key: password, path: keystore.pin }]
containers:
  - name: opendj
    volumeMounts:
      - { name: secrets, mountPath: /var/secrets/opendj, readOnly: true }
```

Mount the volume as a whole, not with `subPath`: Kubernetes does not update files mounted
with `subPath` when the Secret changes.

## Environment Variables

| Variable                | Default Value                   | Description                                                                                                                                                                                                                                             |
|-------------------------|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ADD_BASE_ENTRY          |                                 | if set --addBaseEntry , creates base DN entry                                                                                                                                                                                                           |
| SAMPLE_DATA             | -                               | with ADD_BASE_ENTRY set, imports that many generated users under BASE_DN instead of the base entry alone                                                                                                                                                 |
| PORT                    | 1389                            | LDAP Listener Port                                                                                                                                                                                                                                      |
| LDAPS_PORT              | 1636                            | LDAPS Listener Port                                                                                                                                                                                                                                     |
| BASE_DN                 | dc=example,dc=com               | OpenDJ Base DN                                                                                                                                                                                                                                          |
| ROOT_USER_DN            | cn=Directory Manager            | Initial root user DN                                                                                                                                                                                                                                    |
| ROOT_PASSWORD           | password                        | Initial root user password; the bootstrap fails if it contains a line break (CR or LF)                                                                                                                                                                  |
| SECRET_VOLUME           | /var/secrets/opendj             | Mounted keystore volume, if present its `key*` and `trust*` files are copied into the instance on every start, see [Certificates](#certificates)                                                                                                        |
| SECRET_VOLUME_REFRESH   | 60                              | While the server runs, `SECRET_VOLUME` is checked again every that many seconds and changed files are copied again; `0` copies them on start only                                                                                                       |
| MASTER_SERVER           | -                               | Replication master server                                                                                                                                                                                                                               |
| VERSION                 | -                               | OpenDJ version                                                                                                                                                                                                                                          |
| OPENDJ_USER             | opendj                          | user which runs OpenDJ                                                                                                                                                                                                                                  |
| OPENDJ_REPLICATION_TYPE | -                               | OpenDJ Replication type, valid values are: <ul><li>simple - standart replication</li><li>srs - standalone replication servers</li><li>sdsr - Standalone Directory Server Replicas</li><li>rg - Replication Groups</li></ul>Other values will be ignored |
| OPENDJ_SSL_OPTIONS      | --generateSelfSignedCertificate | you can replace ssl options at here, like : "--usePkcs12keyStore /opt/domain.pfx --keyStorePassword domain"                                                                                                                                             |
| OPENDJ_JAVA_ARGS        | -server                         | extra instance java args                                                                                                                                                                                                                                |
| BACKEND_TYPE            | je                              | OpenDJ backend type, see [dsconfig create-backend](https://doc.openidentityplatform.org/opendj/reference/dsconfig-subcommands-ref#dsconfig-create-backend) documentation                                                                                |
| BACKEND_DB_DIRECTORY    | db                              | OpenDJ `db-directory` attribute for backend                                                                                                                                                                                                             |
| SETUP_ARGS              | -                               | extra setup args                                                                                                                                                                                                                                        |
| HEALTHCHECK_BIND_DN     | -                               | DN the health check binds as, for an instance that rejects unauthenticated requests; unset, the health check searches the root DSE anonymously |
| HEALTHCHECK_BIND_PASSWORD_FILE | -                               | file in the container holding the password of `HEALTHCHECK_BIND_DN` |