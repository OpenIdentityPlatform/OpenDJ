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
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.rest2ldap.authz;

import static org.forgerock.json.JsonValueFunctions.*;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.*;
import static org.forgerock.opendj.rest2ldap.authz.Utils.newAccessTokenException;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.promise.Promises.newExceptionPromise;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.forgerock.http.oauth2.AccessTokenException;
import org.forgerock.http.oauth2.AccessTokenInfo;
import org.forgerock.http.oauth2.AccessTokenResolver;
import org.forgerock.http.util.Json;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.Promise;

/**
 * A file access token resolver which should only be used for test purpose.
 * <p>
 * The token is the name of a file directly inside the token folder. The token arrives verbatim from the
 * {@code Authorization} header of an unauthenticated request, so a token which is not a plain file name (it
 * contains a path separator, or it resolves to a file outside the folder) is refused before any file is opened.
 */
final class FileAccessTokenResolver implements AccessTokenResolver {

    private final Path folder;

    FileAccessTokenResolver(final String folderPath) {
        this.folder = Paths.get(checkNotNull(folderPath)).toAbsolutePath().normalize();
    }

    @Override
    public Promise<AccessTokenInfo, AccessTokenException> resolve(final Context context, final String token) {
        final Path tokenFile = tokenFile(token);
        if (tokenFile == null) {
            return newExceptionPromise(newAccessTokenException(ERR_OAUTH2_FILE_INVALID_TOKEN_NAME.get(token)));
        }

        final JsonValue accessToken;
        try (final InputStream stream = Files.newInputStream(tokenFile)) {
            accessToken = new JsonValue(Json.readJsonLenient(stream));
        } catch (final IOException e) {
            return newExceptionPromise(newAccessTokenException(ERR_OAUTH2_FILE_NO_TOKEN.get(token) , e));
        }

        try {
            final AccessTokenInfo result = new AccessTokenInfo(accessToken, token,
                    accessToken.get("scope").required().as(setOf(String.class)),
                    accessToken.get("expireTime").required().asLong());
            return newResultPromise(result);
        } catch (final JsonValueException e) {
            return newExceptionPromise(
                    newAccessTokenException(ERR_OAUTH2_FILE_INVALID_JSON_TOKEN.get(token, e.getMessage()), e));
        }
    }

    /**
     * Returns the file named by the token inside the token folder, or {@code null} when the token is not a plain
     * file name. Separators are refused whatever the platform treats as one, and the resolved path must still have
     * the folder as its parent and the token as its name once normalized, so that {@code .}, {@code ..}, an empty
     * token or an absolute path never select anything outside the folder.
     */
    private Path tokenFile(final String token) {
        if (token.indexOf('/') >= 0 || token.indexOf('\\') >= 0) {
            return null;
        }
        final Path file;
        try {
            file = folder.resolve(token).normalize();
        } catch (final InvalidPathException e) {
            return null;
        }
        final Path name = file.getFileName();
        return folder.equals(file.getParent()) && name != null && token.equals(name.toString()) ? file : null;
    }
}
