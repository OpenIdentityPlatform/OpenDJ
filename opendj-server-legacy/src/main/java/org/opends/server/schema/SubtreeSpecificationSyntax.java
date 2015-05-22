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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.schema;

import static org.opends.server.schema.SchemaConstants.*;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.SubtreeSpecification;


/**
 * This class defines the subtree specification attribute syntax,
 * which is used to specify the scope of sub-entries (RFC 3672).
 */
public final class SubtreeSpecificationSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Creates a new instance of this syntax. Note that the only thing
   * that should be done here is to invoke the default constructor for
   * the superclass. All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public SubtreeSpecificationSyntax() {
    // No implementation required.
  }

  /** {@inheritDoc} */
  @Override
  public void initializeSyntax(AttributeSyntaxCfg configuration, ServerContext serverContext)
      throws ConfigException
  {
    // Add the subtree specification syntax to the "new" schema
    SchemaUpdater schemaUpdater = serverContext.getSchemaUpdater();
    SchemaBuilder builder = schemaUpdater.getSchemaBuilder().buildSyntax(SYNTAX_SUBTREE_SPECIFICATION_OID)
      .description(SYNTAX_SUBTREE_SPECIFICATION_DESCRIPTION)
      .implementation(new SubtreeSpecificationSyntaxImpl())
      .addToSchema();
    schemaUpdater.updateSchema(builder.toSchema());
  }

  /** {@inheritDoc} */
  @Override
  public Syntax getSDKSyntax(Schema schema)
  {
    return schema.getSyntax(SchemaConstants.SYNTAX_SUBTREE_SPECIFICATION_OID);
  }

  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return The common name for this attribute syntax.
   */
  @Override
  public String getName() {

    return SYNTAX_SUBTREE_SPECIFICATION_NAME;
  }

  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return The OID for this attribute syntax.
   */
  @Override
  public String getOID() {

    return SYNTAX_SUBTREE_SPECIFICATION_OID;
  }

  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return A description for this attribute syntax.
   */
  @Override
  public String getDescription() {

    return SYNTAX_SUBTREE_SPECIFICATION_DESCRIPTION;
  }

  /**
   * Indicates whether the provided value is acceptable for use in an
   * attribute with this syntax. If it is not, then the reason may be
   * appended to the provided buffer.
   *
   * @param value
   *          The value for which to make the determination.
   * @param invalidReason
   *          The buffer to which the invalid reason should be appended.
   * @return <CODE>true</CODE> if the provided value is acceptable for
   *         use with this syntax, or <CODE>false</CODE> if not.
   */
  @Override
  public boolean valueIsAcceptable(ByteSequence value,
                                   LocalizableMessageBuilder invalidReason) {

    // Use the subtree specification code to make this determination.
    try {
      SubtreeSpecification.valueOf(DN.rootDN(), value.toString());

      return true;
    } catch (DirectoryException e) {
      logger.traceException(e);

      invalidReason.append(e.getMessageObject());
      return false;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isBEREncodingRequired()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHumanReadable()
  {
    return true;
  }
}
