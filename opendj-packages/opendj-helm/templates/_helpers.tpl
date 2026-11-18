{{- /*
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
*/ -}}


{{- define "opendj.fullname" -}}
{{- $original := printf "opendj-%s" .Release.Name -}}
{{- $name := replace "." "-" $original -}}
{{- if or (gt (len $name) 52) (ne $original $name) -}}
{{- printf "%s-%s" ($name | trunc 43 | trimSuffix "-") ($original | sha256sum | trunc 8) -}}
{{- else -}}
{{- $name -}}
{{- end -}}
{{- end -}}

{{- define "opendj.selectorLabels" -}}
app.kubernetes.io/name: opendj
app.kubernetes.io/instance: {{ .Release.Name | quote }}
{{- end -}}

{{- define "opendj.labels" -}}
{{ include "opendj.selectorLabels" . }}
app.kubernetes.io/managed-by: {{ .Release.Service | quote }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name (.Chart.Version | replace "+" "_") | trunc 63 | trimSuffix "-" | quote }}
{{- end -}}

{{- define "opendj.image" -}}
{{- printf "%s:%s" .Values.image.repository (default .Chart.AppVersion .Values.image.tag) -}}
{{- end -}}

{{- define "opendj.podSecurityContext" -}}
seccompProfile:
  type: RuntimeDefault
{{- if eq .Values.securityContext.profile "kubernetes" }}
fsGroup: 1001
{{- end }}
{{- end -}}

{{- define "opendj.containerSecurityContext" -}}
runAsNonRoot: true
allowPrivilegeEscalation: false
capabilities:
  drop:
    - ALL
{{- if eq .Values.securityContext.profile "kubernetes" }}
runAsUser: 1001
{{- end }}
{{- end -}}

{{- define "opendj.configData" -}}
BASE_DN: {{ .Values.directory.baseDn | quote }}
ROOT_USER_DN: {{ .Values.auth.rootDn | quote }}
ADD_BASE_ENTRY: {{ ternary "--addBaseEntry" "" .Values.directory.createBaseEntry | quote }}
{{- end -}}
