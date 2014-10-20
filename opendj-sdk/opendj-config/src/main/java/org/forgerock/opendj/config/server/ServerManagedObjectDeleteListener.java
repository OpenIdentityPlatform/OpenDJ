/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.config.server;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.Configuration;

import java.util.List;

/**
 * This interface defines the methods that a Directory Server configurable
 * component should implement if it wishes to be able to receive notifications
 * when an existing server managed object is deleted.
 *
 * @param <T>
 *            The type of server managed object that this listener should be
 *            notified about.
 */
public interface ServerManagedObjectDeleteListener<T extends Configuration> {

    /**
     * Indicates whether the proposed deletion of an existing server managed
     * object is acceptable to this delete listener.
     *
     * @param mo
     *            The server managed object that will be deleted.
     * @param unacceptableReasons
     *            A list that can be used to hold messages about why the
     *            provided server managed object is not acceptable.
     * @return Returns <code>true</code> if the proposed deletion is acceptable,
     *         or <code>false</code> if it is not.
     */
    boolean isConfigurationDeleteAcceptable(ServerManagedObject<? extends T> mo,
            List<LocalizableMessage> unacceptableReasons);

    /**
     * Deletes an existing server managed object from this delete listener.
     *
     * @param mo
     *            The existing server managed object that will be deleted.
     * @return Returns information about the result of deleting the server
     *         managed object.
     */
    ConfigChangeResult applyConfigurationDelete(ServerManagedObject<? extends T> mo);
}
