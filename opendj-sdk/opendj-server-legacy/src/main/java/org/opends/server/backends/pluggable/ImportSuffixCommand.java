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
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.LDIFImportConfig;

/**
 * Command that describes how a suffix should be imported. Gives the strategy to use and the data to
 * drive the import operation of a single suffix.
 */
public class ImportSuffixCommand
{
  /** Strategy for importing a suffix. */
  public static enum SuffixImportStrategy {
    /**
     * Create a {@link Suffix} specifying just the {@link EntryContainer} for the baseDN, no include or exclude
     * branches are needed, normally used for append or clear backend modes.
     */
    APPEND_OR_REPLACE,
    /** Do not create a {@link Suffix}. */
    SKIP_SUFFIX,
    /** Before creating a {@link Suffix}, clear the {@link EntryContainer} of the baseDN. */
    CLEAR_SUFFIX,
    /** Create a temporary {@link EntryContainer} to merge LDIF with original data. */
    MERGE_DB_WITH_LDIF,
    /**
     * Create a {@link Suffix} specifying include and exclude branches and optionally a source {@link EntryContainer}.
     */
    INCLUDE_EXCLUDE_BRANCHES
  }

  private List<DN> includeBranches;
  private List<DN> excludeBranches;
  private SuffixImportStrategy strategy = SuffixImportStrategy.APPEND_OR_REPLACE;

  List<DN> getIncludeBranches()
  {
    return includeBranches;
  }

  List<DN> getExcludeBranches()
  {
    return excludeBranches;
  }

  SuffixImportStrategy getSuffixImportStrategy()
  {
    return strategy;
  }

  ImportSuffixCommand(DN baseDN, LDIFImportConfig importCfg) throws DirectoryException
  {
    strategy = decideSuffixStrategy(baseDN, importCfg);
  }

  private SuffixImportStrategy decideSuffixStrategy(DN baseDN, LDIFImportConfig importCfg)
      throws DirectoryException
  {
    if (importCfg.appendToExistingData() || importCfg.clearBackend())
    {
      return SuffixImportStrategy.APPEND_OR_REPLACE;
    }
    if (importCfg.getExcludeBranches().contains(baseDN))
    {
      // This entire base DN was explicitly excluded. Skip.
      return SuffixImportStrategy.SKIP_SUFFIX;
    }
    excludeBranches = getDescendants(baseDN, importCfg.getExcludeBranches());
    if (!importCfg.getIncludeBranches().isEmpty())
    {
      includeBranches = getDescendants(baseDN, importCfg.getIncludeBranches());
      if (includeBranches.isEmpty())
      {
        // There are no branches in the explicitly defined include list under this base DN.
        // Skip this base DN altogether.
        return SuffixImportStrategy.SKIP_SUFFIX;
      }

      // Remove any overlapping include branches.
      Iterator<DN> includeBranchIterator = includeBranches.iterator();
      while (includeBranchIterator.hasNext())
      {
        DN includeDN = includeBranchIterator.next();
        if (!isAnyNotEqualAndAncestorOf(includeBranches, includeDN))
        {
          includeBranchIterator.remove();
        }
      }

      // Remove any exclude branches that are not are not under a include branch
      // since they will be migrated as part of the existing entries
      // outside of the include branches anyways.
      Iterator<DN> excludeBranchIterator = excludeBranches.iterator();
      while (excludeBranchIterator.hasNext())
      {
        DN excludeDN = excludeBranchIterator.next();
        if (!isAnyAncestorOf(includeBranches, excludeDN))
        {
          excludeBranchIterator.remove();
        }
      }

      if (excludeBranches.isEmpty() && includeBranches.size() == 1 && includeBranches.get(0).equals(baseDN))
      {
        // This entire base DN is explicitly included in the import with
        // no exclude branches that we need to migrate.
        // Just clear the entry container.
        return SuffixImportStrategy.CLEAR_SUFFIX;
      }
      return SuffixImportStrategy.MERGE_DB_WITH_LDIF;
    }
    return SuffixImportStrategy.INCLUDE_EXCLUDE_BRANCHES;
  }

  private List<DN> getDescendants(DN baseDN, Set<DN> dns)
  {
    final List<DN> results = new ArrayList<>();
    for (DN dn : dns)
    {
      if (baseDN.isAncestorOf(dn))
      {
        results.add(dn);
      }
    }
    return results;
  }

  private boolean isAnyAncestorOf(List<DN> dns, DN childDN)
  {
    for (DN dn : dns)
    {
      if (dn.isAncestorOf(childDN))
      {
        return true;
      }
    }
    return false;
  }

  private boolean isAnyNotEqualAndAncestorOf(List<DN> dns, DN childDN)
  {
    for (DN dn : dns)
    {
      if (!dn.equals(childDN) && dn.isAncestorOf(childDN))
      {
        return false;
      }
    }
    return true;
  }
}