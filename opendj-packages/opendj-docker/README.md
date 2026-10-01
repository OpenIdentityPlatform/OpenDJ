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
`ADD_BASE_ENTRY` and `SAMPLE_DATA` asked to be imported into it, and, where replication is
asked for, the join of the replication topology (see [Replication](#replication)). Waiting
for that status is therefore enough before the first search of what the bootstrap was told
to create:

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
that only waits for the port can have its first requests fail in between.

A bootstrap that imports `SAMPLE_DATA` can take minutes on a small container, which is what
the start period allows for. A bootstrap that fails - or an upgrade that fails when starting
over an instance that is already there - never reports healthy: what failed is in `docker
logs`, and where the server is up at all the container is left running to be looked at,
turning `unhealthy` once the start period is over.

The server runs as PID 1 of the container, and a JVM does not reap the processes left
behind to it - those of a health check that ran past its timeout, say. Run the container
with `docker run --init` (`init: true` in Compose) to put a PID 1 in front of the server
that reaps them and passes SIGTERM on to it.

## Replication

With `OPENDJ_REPLICATION_TYPE=simple`, the container joins its replication topology in the
background, next to the running server, on every start - not as a step of the first
bootstrap, so a join that could not complete is tried again on the next start, and
membership that changed while no container ran is repaired. `REPLICATION_PEERS` lists every
server of the topology by DNS name, comma separated; all of them share `BASE_DN`,
`ROOT_USER_DN`, `ROOT_PASSWORD`, `ADMIN_PORT` and `REPLICATION_PORT`. A Kubernetes
StatefulSet derives the list from its ordinals
(`<sts>-0.<headless svc>,…,<sts>-N-1.<headless svc>`), so it needs no registry; plain
`docker run` passes the container names, each container started with `--hostname` equal to
its `--name` (or with `MYHOSTNAME` set to it). `MASTER_SERVER` keeps working as a
one-element list, and may name the master by an address or an `/etc/hosts` alias as before:
the master recognises itself by those as well.

A server recognises itself in the list by a name that equals its `hostname -f`, or that is
its `hostname -f` cut at a dot (or the other way round) - a pod whose FQDN is
`<sts>-N.<headless svc>.<namespace>.svc.cluster.local` is listed as
`<sts>-N.<headless svc>` - and by one of its own addresses or a name `/etc/hosts` gives one
of them (an `--add-host` or a `hostAliases` entry for itself). Names are compared whole:
`opendj-1` is not `opendj-10`, and `opendj-0.opendj.east` is not `opendj-0.opendj.west`.
List DNS names rather than addresses in `REPLICATION_PEERS`: the servers register in
`cn=admin data` by name, and a server listed by address alone would be taken for one that
left the topology and removed from it (see below). A server registered by an address - a
master that `MASTER_SERVER=<address>` named, which its replicas registered under that address
- is never removed that way, as nothing tells it from a listed name: moving such a topology
to a `REPLICATION_PEERS` of names keeps the master, and once it really is gone, it is
removed by hand.

The join decides from what is there, not from an exit code: this server is a member once
its configuration holds the replication domain for `BASE_DN` and it is registered in
`cn=admin data`. Until then it runs `dsreplication enable` through the listed peers,
`REPLICATION_RETRY_COUNT` times every `REPLICATION_RETRY_INTERVAL` seconds, each enable
bounded by `REPLICATION_ATTEMPT_TIMEOUT` seconds, and the container reports itself healthy
only once the join succeeded and the volume holds the data of the topology - across
restarts too. A server whose volume holds that data is ready as soon as it serves, without
waiting for its peers or for its join, so a whole cluster restart does not deadlock under
`OrderedReady`, and a changed root password does not keep it unready either (the join,
which binds with `ROOT_PASSWORD`, then only logs that it cannot).

A volume the container bootstraps is marked before the bootstrap starts, and initialized
from the topology once the join succeeds, whether or not `BASE_DN` already holds entries - so
a bootstrap that failed or was killed half-way is initialized as well, never taken for data
of the topology: every fresh volume holds what
`ADD_BASE_ENTRY` or `SAMPLE_DATA` imported, which says nothing about which server holds
the data of the topology. `dsreplication initialize` is a full import of `BASE_DN` and runs
without a bound unless `REPLICATION_INITIALIZE_TIMEOUT` sets one. Each server publishes to
its peers whether its volume still waits for that data (`pending`) or holds it (`ready`), in
the local entry `cn=Docker Join,cn=config`, and a pending server joins and initializes only
through a ready one. So servers may start together - Compose, or a StatefulSet with
`podManagementPolicy: Parallel` - without any of them taking the bootstrap data of another
fresh one for the topology's. Servers whose enables involve the same server take turns: two
`dsreplication enable` runs through one server at once leave the loser a member only in
part, for good. The turn is the entry `cn=Docker Join Lock,cn=config`, which a join adds
before its enable on the peer it enables through and on its own server, and removes after
it; one left behind by a killed join is taken over once it is older than
`REPLICATION_ATTEMPT_TIMEOUT` plus a minute, or at once by a later join of the server that
left it. A join that finds a turn taken tries the next peer, and waits a random part of
`REPLICATION_RETRY_INTERVAL` on top of it between rounds, so two joins that each hold the
other's turn do not keep meeting. Only the first entry of `REPLICATION_PEERS` may decide that
there is no topology yet and seed it with its own data: at once when every other peer
answers and waits for data as well, or once its retries are exhausted without any peer
that could hold the data answering - a ready one, a server of an earlier image that
publishes no state but replicates `BASE_DN`, or one that refuses the bind with
`ROOT_PASSWORD` (only a server past its bootstrap has another root password). The residual risk of that rule: with every other server
down *and* the volume of the first peer lost, the first peer seeds an empty topology and the
others initialize from it; keep backups accordingly. A volume that joined but whose
initialize never completed keeps waiting for a ready peer on every start, so under
`OrderedReady` a `-0` in that state stays unready until a peer that holds the data is
started by hand - which is where its data has to come from anyway.

With `REPLICATION_PEERS` set explicitly, a joined server also removes every server that is
registered in the topology but no longer listed: its entries in `cn=admin data` and its
values in every local replication server list - those of `BASE_DN`, and of `cn=schema` and
`cn=admin data`, which `dsreplication enable` replicates next to it. A scale-down therefore
needs no `preStop` hook - `dsreplication disable` on termination would take the server out
on every rolling restart, and cannot clean up a server that is already gone. It also adds
every listed peer registered in the topology to the local lists that lack it, so joins that
ran at the same time cannot leave two replication servers that know nothing of each other.
With only `MASTER_SERVER` set nothing is removed or added, so servers joined by hand stay.

A server that was removed this way and is scaled up again on the volume it kept still holds
the data and its replication configuration, but no peer registers it any more, and
`dsreplication enable` would not register it again. Its join takes its replication
configuration down and enables it anew, and from that moment until the enable has registered
it again the container does not report itself healthy, across restarts too: writes it took
meanwhile would replicate nowhere. It does so only when at least one peer that replicates
`BASE_DN` answered and none of them registers it - a peer that cannot be asked decides
nothing.

The one-shot types `srs`, `sdsr` and `rg` keep their previous behaviour - they run once,
during the first bootstrap only - and are deprecated in favour of `simple`. Like `simple`,
they need one `ADMIN_PORT` and one `REPLICATION_PORT` on every server: the tools reach the
master on the ports of the container they run in (images before this one used 4444 and
8989 on both sides, whatever the container was set up with). With
`OPENDJ_REPLICATION_TYPE=srs`, start the directory server replicas one at a time, each
once the previous one is healthy: every replica pushes its data to the replicas connected
at that moment, and one that is restarting at the end of its own first start misses it.

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
| MASTER_SERVER           | -                               | Replication master server; with `simple` it works as a one-element `REPLICATION_PEERS`                                                                                                                                                                  |
| REPLICATION_PEERS       | value of MASTER_SERVER          | every server of the replication topology by DNS name, comma separated; the first entry may seed a new topology, and servers no longer listed are removed from it, see [Replication](#replication) |
| REPLICATION_PORT        | 8989                            | replication port, the same on every server of the topology                                                                                                                                                                                              |
| REPLICATION_RETRY_COUNT | 30                              | rounds of join attempts through every peer before the join gives up: the first peer of `REPLICATION_PEERS` then seeds the topology, any other server stays `unhealthy`                                                                                  |
| REPLICATION_RETRY_INTERVAL | 10                           | seconds between rounds of join attempts, plus a random part of as much again                                                                                                                                                                             |
| REPLICATION_ATTEMPT_TIMEOUT | 120                             | seconds a single `dsreplication enable` may take before it is killed and tried again; it can hang on a peer that stops mid-operation, so a value that is not a positive number falls back to 120 |
| REPLICATION_INITIALIZE_TIMEOUT | 0                               | seconds a single `dsreplication initialize` - a full import of `BASE_DN` - may take before it is killed and tried again; `0` sets no bound |
| VERSION                 | -                               | OpenDJ version                                                                                                                                                                                                                                          |
| OPENDJ_USER             | opendj                          | user which runs OpenDJ                                                                                                                                                                                                                                  |
| OPENDJ_REPLICATION_TYPE | -                               | OpenDJ Replication type, valid values are: <ul><li>simple - standard replication, joined in the background on every start, see [Replication](#replication)</li><li>srs - standalone replication servers (one-shot, deprecated)</li><li>sdsr - Standalone Directory Server Replicas (one-shot, deprecated)</li><li>rg - Replication Groups (one-shot, deprecated)</li></ul>Other values will be ignored |
| OPENDJ_SSL_OPTIONS      | --generateSelfSignedCertificate | you can replace ssl options at here, like : "--usePkcs12keyStore /opt/domain.pfx --keyStorePassword domain"                                                                                                                                             |
| OPENDJ_JAVA_ARGS        | -server                         | extra instance java args                                                                                                                                                                                                                                |
| BACKEND_TYPE            | je                              | OpenDJ backend type, see [dsconfig create-backend](https://doc.openidentityplatform.org/opendj/reference/dsconfig-subcommands-ref#dsconfig-create-backend) documentation                                                                                |
| BACKEND_DB_DIRECTORY    | db                              | OpenDJ `db-directory` attribute for backend                                                                                                                                                                                                             |
| SETUP_ARGS              | -                               | extra setup args                                                                                                                                                                                                                                        |
| HEALTHCHECK_BIND_DN     | -                               | DN the health check binds as, for an instance that rejects unauthenticated requests; unset, the health check searches the root DSE anonymously |
| HEALTHCHECK_BIND_PASSWORD_FILE | -                               | file in the container holding the password of `HEALTHCHECK_BIND_DN` |