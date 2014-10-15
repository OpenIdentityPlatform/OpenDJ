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
 *      Copyright 2013 ForgeRock AS
 */
package org.forgerock.opendj.ldap.controls;

import org.forgerock.opendj.ldap.ByteString;

/**
 * The persistent search request control for Active Directory as defined by
 * Microsoft. This control allows a client to receive notification of changes
 * that occur in an Active Directory server.
 * <br/>
 *
 * <pre>
 * Connection connection = ...;
 *
 * SearchRequest request =
 *         Requests.newSearchRequest("dc=example,dc=com",
 *                 SearchScope.WHOLE_SUBTREE, "(objectclass=*)", "cn",
 *                 "isDeleted", "whenChanged", "whenCreated").addControl(
 *                 ADNotificationRequestControl.newControl(true));
 *
 * ConnectionEntryReader reader = connection.search(request);
 *
 * while (reader.hasNext()) {
 *     if (!reader.isReference()) {
 *         SearchResultEntry entry = reader.readEntry(); // Entry that changed
 *
 *         Boolean isDeleted = entry.parseAttribute("isDeleted").asBoolean();
 *         if (isDeleted != null && isDeleted) {
 *             // Handle entry deletion
 *         }
 *         String whenCreated = entry.parseAttribute("whenCreated").asString();
 *         String whenChanged = entry.parseAttribute("whenChanged").asString();
 *         if (whenCreated != null && whenChanged != null) {
 *             if (whenCreated.equals(whenChanged)) {
 *                 //Handle entry addition
 *             } else {
 *                 //Handle entry modification
 *             }
 *         }
 *     } else {
 *         reader.readReference(); //read and ignore reference
 *     }
 * }
 *
 * </pre>
 *
 * @see <a href="http://msdn.microsoft.com/en-us/library/windows/desktop/aa772153(v=vs.85).aspx">
 *      Change Notifications in Active Directory Domain Services</a>
 */
public final class ADNotificationRequestControl implements Control {

    /**
     * The OID for the Microsoft Active Directory persistent search request
     * control. The control itself is empty and the changes are returned as
     * attributes, such as "isDeleted", "whenChanged", "whenCreated".
     */
    public static final String OID = "1.2.840.113556.1.4.528";

    /**
     * The name of the isDeleted attribute as defined in the Active Directory
     * schema. If the value of the attribute is <code>TRUE</code>, the object
     * has been marked for deletion.
     */
    public static final String IS_DELETED_ATTR = "isDeleted";

    /**
     * The name of the whenCreated attribute as defined in the Active Directory
     * schema. Holds the date of the creation of the object in GeneralizedTime
     * format.
     */
    public static final String WHEN_CREATED_ATTR = "whenCreated";

    /**
     * The name of the whenChanged attribute as defined in the Active Directory
     * schema. Holds the date of the last modification of the object in
     * GeneralizedTime format.
     */
    public static final String WHEN_CHANGED_ATTR = "whenChanged";

    /**
     * The name of the objectGUID attribute as defined in the Active Directory
     * schema. This is the unique identifier of an object stored in binary
     * format.
     */
    public static final String OBJECT_GUID_ATTR = "objectGUID";

    /**
     * The name of the uSNChanged attribute as defined in the Active Directory
     * schema. This attribute can be used to determine whether the current
     * state of the object on the server reflects the latest changes that the
     * client has received.
     */
    public static final String USN_CHANGED_ATTR = "uSNChanged";

    private final boolean isCritical;

    private ADNotificationRequestControl(final boolean isCritical) {
        this.isCritical = isCritical;
    }

    /**
     * Creates a new Active Directory change notification request control.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored
     * @return The new control.
     */
    public static ADNotificationRequestControl newControl(final boolean isCritical) {
        return new ADNotificationRequestControl(isCritical);
    }

    /** {@inheritDoc} */
    @Override
    public String getOID() {
        return OID;
    }

    /** {@inheritDoc} */
    @Override
    public ByteString getValue() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasValue() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCritical() {
        return isCritical;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ADNotificationRequestControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(")");
        return builder.toString();
    }
}
