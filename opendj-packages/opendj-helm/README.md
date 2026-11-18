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
  --set auth.existingSecret=directory-root --wait --timeout 15m
helm test directory --namespace directory
```

The Docker Hub URL is `oci://registry-1.docker.io/openidentityplatform/opendj-helm`. Set `--set replication.enabled=true,replicaCount=2` for two replicas. In OpenShift set `securityContext.profile=openshift` to allow the SCC to assign the UID and volume group. The default StorageClass provisions one `ReadWriteOnce` claim per pod. Claims are retained after scale-in or uninstall.

See the [Installation Guide](https://doc.openidentityplatform.org/opendj/install-guide/chap-install#install-helm), [replication and resize procedure](https://doc.openidentityplatform.org/opendj/admin-guide/chap-replication#helm-replica-resize), [TLS Secret settings](https://doc.openidentityplatform.org/opendj/admin-guide/chap-change-certs#helm-certificates), and [Kubernetes walkthrough](https://doc.openidentityplatform.org/opendj/how-to/kubernetes#how-to-kubernetes).
