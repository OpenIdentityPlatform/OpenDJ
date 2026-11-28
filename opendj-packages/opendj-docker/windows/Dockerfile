# The contents of this file are subject to the terms of the Common Development and
# Distribution License (the License). You may not use this file except in compliance with the
# License.
#
# You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
# specific language governing permission and limitations under the License.
#
# When distributing Covered Software, include this CDDL Header Notice in each file and include
# the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
# Header, with the fields enclosed by brackets [] replaced by your own identifying
# information: "Portions copyright [year] [name of copyright owner]".
#
# Copyright 2026 3A Systems, LLC.

FROM eclipse-temurin:25-jre-windowsservercore-ltsc2022

LABEL org.opencontainers.image.authors="Open Identity Platform Community"
LABEL org.opencontainers.image.source="https://github.com/OpenIdentityPlatform/OpenDJ"

ENV PORT=1389 LDAPS_PORT=1636 ADMIN_PORT=4444 BASE_DN=dc=example,dc=com ROOT_USER_DN="cn=Directory Manager"

WORKDIR C:/opendj
COPY opendj/ ./
COPY run.ps1 healthcheck.ps1 ./

EXPOSE 1389/tcp 1636/tcp 4444/tcp
VOLUME ["C:/opendj/data"]

HEALTHCHECK --interval=10s --timeout=20s --start-period=2m --retries=3 CMD ["powershell.exe", "-NoProfile", "-NonInteractive", "-File", "C:/opendj/healthcheck.ps1"]
ENTRYPOINT ["powershell.exe", "-NoProfile", "-NonInteractive", "-File", "C:/opendj/run.ps1"]
