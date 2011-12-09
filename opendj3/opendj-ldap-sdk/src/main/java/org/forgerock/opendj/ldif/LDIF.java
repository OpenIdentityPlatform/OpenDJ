/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *      Copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.ldif;



import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.requests.*;



/**
 * This class contains common utility methods for creating and manipulating
 * readers and writers.
 */
public final class LDIF
{
  /**
   * Compares the content of {@code source} to the content of {@code target} and
   * writes the differences to {@code output}. This method does not close the
   * provided readers and writer.
   * <p>
   * <b>NOTE:</b> this method reads the content of {@code source} and
   * {@code target} into memory before calculating the differences, and is
   * therefore not suited for use in cases where a very large number of entries
   * are to be compared.
   *
   * @param source
   *          The entry reader containing the source entries to be compared.
   * @param target
   *          The entry reader containing the target entries to be compared.
   * @param output
   *          The change record writer to which the differences are to be
   *          written.
   * @throws IOException
   *           If an unexpected IO error occurred.
   */
  public static void diff(final EntryReader source, final EntryReader target,
      final ChangeRecordWriter output) throws IOException
  {
    final SortedMap<DN, Entry> sourceEntries = readEntries(source);
    final SortedMap<DN, Entry> targetEntries = readEntries(target);
    final Iterator<Entry> sourceIterator = sourceEntries.values().iterator();
    final Iterator<Entry> targetIterator = targetEntries.values().iterator();

    Entry sourceEntry = nextEntry(sourceIterator);
    Entry targetEntry = nextEntry(targetIterator);

    while (sourceEntry != null && targetEntry != null)
    {
      final DN sourceDN = sourceEntry.getName();
      final DN targetDN = targetEntry.getName();
      final int cmp = sourceDN.compareTo(targetDN);

      if (cmp == 0)
      {
        // Modify record: entry in both source and target.
        output.writeChangeRecord(Requests.newModifyRequest(sourceEntry,
            targetEntry));
        sourceEntry = nextEntry(sourceIterator);
        targetEntry = nextEntry(targetIterator);
      }
      else if (cmp < 0)
      {
        // Delete record: entry in source but not in target.
        output.writeChangeRecord(Requests.newDeleteRequest(sourceEntry
            .getName()));
        sourceEntry = nextEntry(sourceIterator);
      }
      else
      {
        // Add record: entry in target but not in source.
        output.writeChangeRecord(Requests.newAddRequest(targetEntry));
        targetEntry = nextEntry(targetIterator);
      }
    }

    // Delete remaining source records.
    while (sourceEntry != null)
    {
      output
          .writeChangeRecord(Requests.newDeleteRequest(sourceEntry.getName()));
      sourceEntry = nextEntry(sourceIterator);
    }

    // Add remaining target records.
    while (targetEntry != null)
    {
      output.writeChangeRecord(Requests.newAddRequest(targetEntry));
      targetEntry = nextEntry(targetIterator);
    }
  }



  /**
   * Applies the set of changes contained in {@code patch} to the content of
   * {@code input} and writes the result to {@code output}, while ignoring
   * missing entries, and overwriting existing entries. This method does not
   * close the provided readers and writer.
   * <p>
   * <b>NOTE:</b> this method reads the content of {@code input} into memory
   * before applying the changes, and is therefore not suited for use in cases
   * where a very large number of entries are to be patched.
   *
   * @param input
   *          The entry reader containing the set of entries to be patched.
   * @param patch
   *          The change record reader containing the set of changes to be
   *          applied.
   * @param output
   *          The entry writer to which the updated entries are to be written.
   * @throws IOException
   *           If an unexpected IO error occurred.
   */
  public static void patch(final EntryReader input,
      final ChangeRecordReader patch, final EntryWriter output)
      throws IOException
  {
    patch(input, patch, output, RejectedChangeListener.OVERWRITE);
  }



  /**
   * Applies the set of changes contained in {@code patch} to the content of
   * {@code input} and writes the result to {@code output}. This method does not
   * close the provided readers and writer.
   * <p>
   * <b>NOTE:</b> this method reads the content of {@code input} into memory
   * before applying the changes, and is therefore not suited for use in cases
   * where a very large number of entries are to be patched.
   *
   * @param input
   *          The entry reader containing the set of entries to be patched.
   * @param patch
   *          The change record reader containing the set of changes to be
   *          applied.
   * @param output
   *          The entry writer to which the updated entries are to be written.
   * @param listener
   *          The rejected change listener.
   * @throws IOException
   *           If an unexpected IO error occurred.
   */
  public static void patch(final EntryReader input,
      final ChangeRecordReader patch, final EntryWriter output,
      final RejectedChangeListener listener) throws IOException
  {
    final SortedMap<DN, Entry> entries = readEntries(input);

    while (patch.hasNext())
    {
      final ChangeRecord change = patch.readChangeRecord();

      final DecodeException de = change.accept(
          new ChangeRecordVisitor<DecodeException, Void>()
          {

            @Override
            public DecodeException visitChangeRecord(final Void p,
                final AddRequest change)
            {
              final Entry existingEntry = entries.get(change.getName());
              if (existingEntry != null)
              {
                try
                {
                  final Entry entry = listener.handleDuplicateEntry(change,
                      existingEntry);
                  entries.put(entry.getName(), entry);
                }
                catch (final DecodeException e)
                {
                  return e;
                }
              }
              else
              {
                entries.put(change.getName(), change);
              }
              return null;
            }



            @Override
            public DecodeException visitChangeRecord(final Void p,
                final DeleteRequest change)
            {
              if (!entries.containsKey(change.getName()))
              {
                try
                {
                  listener.handleMissingEntry(change);
                }
                catch (final DecodeException e)
                {
                  return e;
                }
              }
              else
              {
                entries.remove(change.getName());
              }
              return null;
            }



            @Override
            public DecodeException visitChangeRecord(final Void p,
                final ModifyDNRequest change)
            {
              if (!entries.containsKey(change.getName()))
              {
                try
                {
                  listener.handleMissingEntry(change);
                }
                catch (final DecodeException e)
                {
                  return e;
                }
              }
              else
              {
                // Calculate the old and new DN.
                final DN oldDN = change.getName();

                DN newSuperior = change.getNewSuperior();
                if (newSuperior == null)
                {
                  newSuperior = change.getName().parent();
                  if (newSuperior == null)
                  {
                    newSuperior = DN.rootDN();
                  }
                }
                final DN newDN = newSuperior.child(change.getNewRDN());

                // Move the renamed entries into a separate map in order to
                // avoid cases where the renamed subtree overlaps.
                final SortedMap<DN, Entry> renamedEntries = new TreeMap<DN, Entry>();
                final Iterator<Map.Entry<DN, Entry>> i = entries
                    .tailMap(change.getName()).entrySet().iterator();
                while (i.hasNext())
                {
                  final Map.Entry<DN, Entry> e = i.next();
                  i.remove();

                  final DN renamedDN = e.getKey().rename(oldDN, newDN);
                  final Entry entry = e.getValue().setName(renamedDN);
                  renamedEntries.put(renamedDN, entry);
                }

                // Modify the target entry.
                final Entry entry = entries.values().iterator().next();
                if (change.isDeleteOldRDN())
                {
                  for (final AVA ava : oldDN.rdn())
                  {
                    entry.removeAttribute(ava.toAttribute(), null);
                  }
                }
                for (final AVA ava : newDN.rdn())
                {
                  entry.addAttribute(ava.toAttribute());
                }

                // Add the renamed entries.
                for (final Entry renamedEntry : renamedEntries.values())
                {
                  final Entry existingEntry = entries.get(renamedEntry
                      .getName());
                  if (existingEntry != null)
                  {
                    try
                    {
                      final Entry tmp = listener.handleDuplicateEntry(change,
                          existingEntry, renamedEntry);
                      entries.put(tmp.getName(), tmp);
                    }
                    catch (final DecodeException e)
                    {
                      return e;
                    }
                  }
                  else
                  {
                    entries.put(renamedEntry.getName(), renamedEntry);
                  }
                }
              }
              return null;
            }



            @Override
            public DecodeException visitChangeRecord(final Void p,
                final ModifyRequest change)
            {
              if (!entries.containsKey(change.getName()))
              {
                try
                {
                  listener.handleMissingEntry(change);
                }
                catch (final DecodeException e)
                {
                  return e;
                }
              }
              else
              {
                final Entry entry = entries.get(change.getName());
                for (final Modification modification : change
                    .getModifications())
                {
                  final ModificationType modType = modification
                      .getModificationType();
                  if (modType.equals(ModificationType.ADD))
                  {
                    entry.addAttribute(modification.getAttribute(), null);
                  }
                  else if (modType.equals(ModificationType.DELETE))
                  {
                    entry.removeAttribute(modification.getAttribute(), null);
                  }
                  else if (modType.equals(ModificationType.REPLACE))
                  {
                    entry.replaceAttribute(modification.getAttribute());
                  }
                  else
                  {
                    System.err.println("Unable to apply \"" + modType
                        + "\" modification to entry \"" + change.getName()
                        + "\": modification type not supported");
                  }
                }
              }
              return null;
            }

          }, null);

      if (de != null)
      {
        throw de;
      }
    }

    for (final Entry entry : entries.values())
    {
      output.writeEntry(entry);
    }
  }



  private static Entry nextEntry(final Iterator<Entry> i)
  {
    return i.hasNext() ? i.next() : null;
  }



  private static SortedMap<DN, Entry> readEntries(final EntryReader reader)
      throws IOException
  {
    final SortedMap<DN, Entry> entries = new TreeMap<DN, Entry>();
    while (reader.hasNext())
    {
      final Entry entry = reader.readEntry();
      entries.put(entry.getName(), entry);
    }
    return entries;
  }



  // Prevent instantiation.
  private LDIF()
  {
    // Do nothing.
  }
}
