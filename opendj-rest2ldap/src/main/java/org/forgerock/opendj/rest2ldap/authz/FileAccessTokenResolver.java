/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap.authz;

import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.*;
import static org.forgerock.opendj.rest2ldap.authz.Utils.newAccessTokenException;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.promise.Promises.newExceptionPromise;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.forgerock.openig.oauth2.AccessTokenInfo;
import org.forgerock.openig.oauth2.AccessTokenException;
import org.forgerock.openig.oauth2.AccessTokenResolver;
import org.forgerock.http.util.Json;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.Promise;

/** A file access token resolver which should only be used for test purpose.*/
final class FileAccessTokenResolver implements AccessTokenResolver {

    private final String folderPath;

    FileAccessTokenResolver(final String folderPath) {
        this.folderPath = checkNotNull(folderPath);
    }

    @Override
    public Promise<AccessTokenInfo, AccessTokenException> resolve(final Context context, final String token) {
        final JsonValue accessToken;
        try (final InputStream stream = new FileInputStream(new File(folderPath, token))) {
            accessToken = new JsonValue(Json.readJsonLenient(stream));
        } catch (final IOException e) {
            return newExceptionPromise(newAccessTokenException(ERR_OAUTH2_FILE_NO_TOKEN.get(token) , e));
        }

        try {
            final AccessTokenInfo result = new AccessTokenInfo(accessToken, token,
                    accessToken.get("scope").required().asSet(String.class),
                    accessToken.get("expireTime").required().asLong());
            return newResultPromise(result);
        } catch (final JsonValueException e) {
            return newExceptionPromise(
                    newAccessTokenException(ERR_OAUTH2_FILE_INVALID_JSON_TOKEN.get(token, e.getMessage()), e));
        }
    }
}
