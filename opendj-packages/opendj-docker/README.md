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
| SECRET_VOLUME           | -                               | Mounted keystore volume, if present copies keystore over                                                                                                                                                                                                |
| MASTER_SERVER           | -                               | Replication master server                                                                                                                                                                                                                               |
| VERSION                 | -                               | OpenDJ version                                                                                                                                                                                                                                          |
| OPENDJ_USER             | opendj                          | user which runs OpenDJ                                                                                                                                                                                                                                  |
| OPENDJ_REPLICATION_TYPE | -                               | OpenDJ Replication type, valid values are: <ul><li>simple - standart replication</li><li>srs - standalone replication servers</li><li>sdsr - Standalone Directory Server Replicas</li><li>rg - Replication Groups</li></ul>Other values will be ignored |
| OPENDJ_SSL_OPTIONS      | --generateSelfSignedCertificate | you can replace ssl options at here, like : "--usePkcs12keyStore /opt/domain.pfx --keyStorePassword domain"                                                                                                                                             |
| OPENDJ_JAVA_ARGS        | -server                         | extra instance java args                                                                                                                                                                                                                                |
| BACKEND_TYPE            | je                              | OpenDJ backend type, see [dsconfig create-backend](https://doc.openidentityplatform.org/opendj/reference/dsconfig-subcommands-ref#dsconfig-create-backend) documentation                                                                                |
| BACKEND_DB_DIRECTORY    | db                              | OpenDJ `db-directory` attribute for backend                                                                                                                                                                                                             |
| SETUP_ARGS              | -                               | extra setup args                                                                                                                                                                                                                                        |