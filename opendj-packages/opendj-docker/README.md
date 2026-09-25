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

A bootstrap that imports `SAMPLE_DATA` can take minutes on a small container, which is what
the start period allows for. A bootstrap that fails - or an upgrade that fails when starting
over an instance that is already there - never reports healthy: what failed is in `docker
logs`, and where the server is up at all the container is left running to be looked at,
turning `unhealthy` once the start period is over.

## Certificates

With the default `OPENDJ_SSL_OPTIONS` the instance serves LDAPS and StartTLS with a
self-signed certificate from `config/keystore`, whose password is in `config/keystore.pin`.
To serve your own certificate, mount a directory holding a `keystore` (JKS or PKCS12) and
its `keystore.pin` at `SECRET_VOLUME`, and a `truststore` next to them if clients present
certificates:

```bash
docker run -d --name opendj -v opendj-data:/opt/opendj/data \
  -v "$PWD/secrets":/var/secrets/opendj:ro openidentityplatform/opendj
```

Every `key*` and `trust*` file of that directory is copied into `/opt/opendj/data/config`
before the server starts - on the first start and on every later one, so a certificate
renewed on the volume reaches an instance kept on a persistent volume. While the server
runs, the directory is checked again every `SECRET_VOLUME_REFRESH` seconds and a changed
file is copied again. The server reads the copied keystore when it starts, so a certificate
renewed while it runs is served from its next restart. The administration connector and
replication keep keys of their own and are not affected.

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
| ROOT_PASSWORD           | password                        | Initial root user password                                                                                                                                                                                                                              |
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