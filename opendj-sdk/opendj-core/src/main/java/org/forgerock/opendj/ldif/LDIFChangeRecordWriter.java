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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2012-2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldif;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;

import org.forgerock.util.Reject;

/**
 * An LDIF change record writer writes change records using the LDAP Data
 * Interchange Format (LDIF) to a user defined destination.
 * <p>
 * The following example reads changes from LDIF, and writes the changes to the
 * directory server.
 *
 * <pre>
 * InputStream ldif = ...;
 * LDIFChangeRecordReader reader = new LDIFChangeRecordReader(ldif);
 *
 * Connection connection = ...;
 * connection.bind(...);
 *
 * ConnectionChangeRecordWriter writer =
 *         new ConnectionChangeRecordWriter(connection);
 * while (reader.hasNext()) {
 *     ChangeRecord changeRecord = reader.readChangeRecord();
 *     writer.writeChangeRecord(changeRecord);
 * }
 * </pre>
 *
 * @see <a href="http://tools.ietf.org/html/rfc2849">RFC 2849 - The LDAP Data
 *      Interchange Format (LDIF) - Technical Specification </a>
 */
public final class LDIFChangeRecordWriter extends AbstractLDIFWriter implements ChangeRecordWriter {

    /**
     * Returns the LDIF string representation of the provided change record.
     *
     * @param change
     *            The change record.
     * @return The LDIF string representation of the provided change record.
     */
    public static String toString(final ChangeRecord change) {
        final StringWriter writer = new StringWriter(128);
        try {
            new LDIFChangeRecordWriter(writer).setAddUserFriendlyComments(true).writeChangeRecord(
                    change).close();
        } catch (final IOException e) {
            // Should never happen.
            throw new IllegalStateException(e);
        }
        return writer.toString();
    }

    /**
     * Creates a new LDIF change record writer which will append lines of LDIF
     * to the provided list.
     *
     * @param ldifLines
     *            The list to which lines of LDIF should be appended.
     */
    public LDIFChangeRecordWriter(final List<String> ldifLines) {
        super(ldifLines);
    }

    /**
     * Creates a new LDIF change record writer whose destination is the provided
     * output stream.
     *
     * @param out
     *            The output stream to use.
     */
    public LDIFChangeRecordWriter(final OutputStream out) {
        super(out);
    }

    /**
     * Creates a new LDIF change record writer whose destination is the provided
     * character stream writer.
     *
     * @param writer
     *            The character stream writer to use.
     */
    public LDIFChangeRecordWriter(final Writer writer) {
        super(writer);
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws IOException {
        close0();
    }

    /** {@inheritDoc} */
    @Override
    public void flush() throws IOException {
        flush0();
    }

    /**
     * Specifies whether or not user-friendly comments should be added whenever
     * distinguished names or UTF-8 attribute values are encountered which
     * contained non-ASCII characters. The default is {@code false}.
     *
     * @param addUserFriendlyComments
     *            {@code true} if user-friendly comments should be added, or
     *            {@code false} otherwise.
     * @return A reference to this {@code LDIFEntryWriter}.
     */
    public LDIFChangeRecordWriter setAddUserFriendlyComments(final boolean addUserFriendlyComments) {
        this.addUserFriendlyComments = addUserFriendlyComments;
        return this;
    }

    /**
     * Specifies whether or not all operational attributes should be excluded
     * from any change records that are written to LDIF. The default is
     * {@code false}.
     *
     * @param excludeOperationalAttributes
     *            {@code true} if all operational attributes should be excluded,
     *            or {@code false} otherwise.
     * @return A reference to this {@code LDIFChangeRecordWriter}.
     */
    public LDIFChangeRecordWriter setExcludeAllOperationalAttributes(
            final boolean excludeOperationalAttributes) {
        this.excludeOperationalAttributes = excludeOperationalAttributes;
        return this;
    }

    /**
     * Specifies whether or not all user attributes should be excluded from any
     * change records that are written to LDIF. The default is {@code false}.
     *
     * @param excludeUserAttributes
     *            {@code true} if all user attributes should be excluded, or
     *            {@code false} otherwise.
     * @return A reference to this {@code LDIFChangeRecordWriter}.
     */
    public LDIFChangeRecordWriter setExcludeAllUserAttributes(final boolean excludeUserAttributes) {
        this.excludeUserAttributes = excludeUserAttributes;
        return this;
    }

    /**
     * Excludes the named attribute from any change records that are written to
     * LDIF. By default all attributes are included unless explicitly excluded.
     *
     * @param attributeDescription
     *            The name of the attribute to be excluded.
     * @return A reference to this {@code LDIFChangeRecordWriter}.
     */
    public LDIFChangeRecordWriter setExcludeAttribute(
            final AttributeDescription attributeDescription) {
        Reject.ifNull(attributeDescription);
        excludeAttributes.add(attributeDescription);
        return this;
    }

    /**
     * Excludes all change records which target entries beneath the named entry
     * (inclusive) from being written to LDIF. By default all change records are
     * written unless explicitly excluded or included.
     *
     * @param excludeBranch
     *            The distinguished name of the branch to be excluded.
     * @return A reference to this {@code LDIFChangeRecordWriter}.
     */
    public LDIFChangeRecordWriter setExcludeBranch(final DN excludeBranch) {
        Reject.ifNull(excludeBranch);
        excludeBranches.add(excludeBranch);
        return this;
    }

    /**
     * Ensures that the named attribute is not excluded from any change records
     * that are written to LDIF. By default all attributes are included unless
     * explicitly excluded.
     *
     * @param attributeDescription
     *            The name of the attribute to be included.
     * @return A reference to this {@code LDIFChangeRecordWriter}.
     */
    public LDIFChangeRecordWriter setIncludeAttribute(
            final AttributeDescription attributeDescription) {
        Reject.ifNull(attributeDescription);
        includeAttributes.add(attributeDescription);
        return this;
    }

    /**
     * Ensures that all change records which target entries beneath the named
     * entry (inclusive) are written to LDIF. By default all change records are
     * written unless explicitly excluded or included.
     *
     * @param includeBranch
     *            The distinguished name of the branch to be included.
     * @return A reference to this {@code LDIFChangeRecordWriter}.
     */
    public LDIFChangeRecordWriter setIncludeBranch(final DN includeBranch) {
        Reject.ifNull(includeBranch);
        includeBranches.add(includeBranch);
        return this;
    }

    /**
     * Specifies the column at which long lines should be wrapped. A value less
     * than or equal to zero (the default) indicates that no wrapping should be
     * performed.
     *
     * @param wrapColumn
     *            The column at which long lines should be wrapped.
     * @return A reference to this {@code LDIFEntryWriter}.
     */
    public LDIFChangeRecordWriter setWrapColumn(final int wrapColumn) {
        this.wrapColumn = wrapColumn;
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LDIFChangeRecordWriter writeChangeRecord(final AddRequest change) throws IOException {
        Reject.ifNull(change);

        // Skip if branch containing the entry is excluded.
        if (isBranchExcluded(change.getName())) {
            return this;
        }

        writeKeyAndValue("dn", change.getName().toString());
        writeControls(change.getControls());
        writeLine("changetype: add");
        for (final Attribute attribute : change.getAllAttributes()) {
            // Filter the attribute if required.
            if (isAttributeExcluded(attribute.getAttributeDescription())) {
                continue;
            }

            final String attributeDescription = attribute.getAttributeDescriptionAsString();
            for (final ByteString value : attribute) {
                writeKeyAndValue(attributeDescription, value);
            }
        }

        // Make sure there is a blank line after the entry.
        impl.println();

        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LDIFChangeRecordWriter writeChangeRecord(final ChangeRecord change) throws IOException {
        Reject.ifNull(change);

        // Skip if branch containing the entry is excluded.
        if (isBranchExcluded(change.getName())) {
            return this;
        }

        final IOException e = change.accept(ChangeRecordVisitorWriter.getInstance(), this);
        if (e != null) {
            throw e;
        }
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LDIFChangeRecordWriter writeChangeRecord(final DeleteRequest change) throws IOException {
        Reject.ifNull(change);

        // Skip if branch containing the entry is excluded.
        if (isBranchExcluded(change.getName())) {
            return this;
        }

        writeKeyAndValue("dn", change.getName().toString());
        writeControls(change.getControls());
        writeLine("changetype: delete");

        // Make sure there is a blank line after the entry.
        impl.println();

        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LDIFChangeRecordWriter writeChangeRecord(final ModifyDNRequest change)
            throws IOException {
        Reject.ifNull(change);

        // Skip if branch containing the entry is excluded.
        if (isBranchExcluded(change.getName())) {
            return this;
        }

        writeKeyAndValue("dn", change.getName().toString());
        writeControls(change.getControls());

        /*
         * Write the changetype. Some older tools may not support the "moddn"
         * changetype, so only use it if a newSuperior element has been
         * provided, but use modrdn elsewhere.
         */
        if (change.getNewSuperior() != null) {
            writeLine("changetype: moddn");
        } else {
            writeLine("changetype: modrdn");
        }

        writeKeyAndValue("newrdn", change.getNewRDN().toString());
        writeKeyAndValue("deleteoldrdn", change.isDeleteOldRDN() ? "1" : "0");
        if (change.getNewSuperior() != null) {
            writeKeyAndValue("newsuperior", change.getNewSuperior().toString());
        }

        // Make sure there is a blank line after the entry.
        impl.println();

        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LDIFChangeRecordWriter writeChangeRecord(final ModifyRequest change) throws IOException {
        Reject.ifNull(change);

        // If there aren't any modifications, then there's nothing to do.
        if (change.getModifications().isEmpty()) {
            return this;
        }

        // Skip if branch containing the entry is excluded.
        if (isBranchExcluded(change.getName())) {
            return this;
        }

        writeKeyAndValue("dn", change.getName().toString());
        writeControls(change.getControls());
        writeLine("changetype: modify");

        for (final Modification modification : change.getModifications()) {
            final ModificationType type = modification.getModificationType();
            final Attribute attribute = modification.getAttribute();
            final String attributeDescription = attribute.getAttributeDescriptionAsString();

            // Filter the attribute if required.
            if (isAttributeExcluded(attribute.getAttributeDescription())) {
                continue;
            }

            writeKeyAndValue(type.toString(), attributeDescription);
            for (final ByteString value : attribute) {
                writeKeyAndValue(attributeDescription, value);
            }
            writeLine("-");
        }

        // Make sure there is a blank line after the entry.
        impl.println();

        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LDIFChangeRecordWriter writeComment(final CharSequence comment) throws IOException {
        writeComment0(comment);
        return this;
    }

}
