<!--
The contents of this file are subject to the terms of the Common Development and
Distribution License (the License). You may not use this file except in compliance with the
License.

You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
specific language governing permission and limitations under the License.

When distributing Covered Software, include this CDDL Header Notice in each file and include
the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
Header, with the fields enclosed by brackets [] replaced by your own identifying
information: "Portions copyright [year] [name of copyright owner]".

Copyright 2026 3A Systems, LLC.
-->

# OpenDJ Helm chart

Deploy OpenDJ in Kubernetes with one persistent volume per pod. The chart is published in Docker Hub and GHCR under the OpenDJ release version.

Set `OPENDJ_VERSION` to a release that publishes the chart. Create a Secret with a non-empty root password with no line terminator before installation.

```sh
kubectl create namespace directory
kubectl -n directory create secret generic directory-root --from-file=password=root-password.txt
helm install directory oci://ghcr.io/openidentityplatform/charts/opendj-helm \
  --version "$OPENDJ_VERSION" --namespace directory \
  --set auth.existingSecret=directory-root \
  --set image.repository=ghcr.io/openidentityplatform/opendj/opendj --wait --timeout 15m
helm test directory --namespace directory
```

For Docker Hub, use `oci://registry-1.docker.io/openidentityplatform/opendj-helm` with the default `image.repository`. Set `--set replication.enabled=true,replicaCount=2` for two replicas. In OpenShift set `securityContext.profile=openshift` to allow the SCC to assign the UID and volume group. The default StorageClass provisions one `ReadWriteOnce` claim per pod. Claims are retained after scale-in or uninstall.

See the [Installation Guide](https://doc.openidentityplatform.org/opendj/install-guide/chap-install#install-helm), [replication and resize procedure](https://doc.openidentityplatform.org/opendj/admin-guide/chap-replication#helm-replica-resize), [TLS Secret settings](https://doc.openidentityplatform.org/opendj/admin-guide/chap-change-certs#helm-certificates), and [Kubernetes walkthrough](https://doc.openidentityplatform.org/opendj/how-to/kubernetes#how-to-kubernetes).

## Install a snapshot archive

Package/Deploy uploads an `opendj-helm-<version>-SNAPSHOT.tgz` chart. Its `appVersion` identifies the snapshot, and the default public image repository carries release tags. Use an image built with the server ZIP and Docker files from the same snapshot sources, and tag it in a registry accessible to the cluster. Set `SNAPSHOT_CHART` to the downloaded archive path and `SNAPSHOT_IMAGE_REPOSITORY` and `SNAPSHOT_IMAGE_TAG` to that image's repository and tag. Use the namespace and password Secret from the release installation example above.

```sh
helm install directory "$SNAPSHOT_CHART" --namespace directory \
  --set auth.existingSecret=directory-root \
  --set-string image.repository="$SNAPSHOT_IMAGE_REPOSITORY" \
  --set-string image.tag="$SNAPSHOT_IMAGE_TAG" --wait --timeout 15m
helm test directory --namespace directory
```
