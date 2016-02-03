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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.forgerock.opendj.ldap.DN;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi domain DB cursor that only returns updates for the domains which have
 * been enabled for the external changelog.
 */
public final class ECLMultiDomainDBCursor implements DBCursor<UpdateMsg>
{
  private final ECLEnabledDomainPredicate predicate;
  private final MultiDomainDBCursor cursor;
  private final List<DN> eclDisabledDomains = new ArrayList<>();

  /**
   * Builds an instance of this class filtering updates from the provided cursor.
   *
   * @param predicate
   *          tells whether a domain is enabled for the external changelog
   * @param cursor
   *          the cursor whose updates will be filtered
   */
  public ECLMultiDomainDBCursor(ECLEnabledDomainPredicate predicate, MultiDomainDBCursor cursor)
  {
    this.predicate = predicate;
    this.cursor = cursor;
  }

  /** {@inheritDoc} */
  @Override
  public UpdateMsg getRecord()
  {
    return cursor.getRecord();
  }

  /**
   * Returns the data associated to the cursor that returned the current record.
   *
   * @return the data associated to the cursor that returned the current record.
   */
  public DN getData()
  {
    return cursor.getData();
  }

  /**
   * Removes a replication domain from this cursor and stops iterating over it.
   * Removed cursors will be effectively removed on the next call to
   * {@link #next()}.
   *
   * @param baseDN
   *          the replication domain's baseDN
   */
  public void removeDomain(DN baseDN)
  {
    cursor.removeDomain(baseDN);
  }

  /**
   * Returns whether the cursor should be reinitialized because a domain became re-enabled.
   *
   * @return whether the cursor should be reinitialized
   */
  public boolean shouldReInitialize()
  {
    for (DN domainDN : eclDisabledDomains)
    {
      if (predicate.isECLEnabledDomain(domainDN))
      {
        eclDisabledDomains.clear();
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean next() throws ChangelogException
  {
    if (!cursor.next())
    {
      return false;
    }
    // discard updates from non ECL enabled domains by removing the disabled domains from the cursor
    DN domain = cursor.getData();
    while (domain != null && !predicate.isECLEnabledDomain(domain))
    {
      cursor.removeDomain(domain);
      eclDisabledDomains.add(domain);
      domain = cursor.getData();
    }
    return domain != null;
  }

  @Override
  public void close()
  {
    cursor.close();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + " cursor=[" + cursor + ']';
  }
}
