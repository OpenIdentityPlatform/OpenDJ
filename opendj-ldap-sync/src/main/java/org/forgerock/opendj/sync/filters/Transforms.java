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
 *      Copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.sync.filters;



import java.io.IOException;
import java.util.Iterator;
import java.util.ListIterator;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.ChangeRecordWriter;



/**
 * Factory methods for constructing common types of transformation filters.
 */
public final class Transforms
{
  private static final class AlwaysStopTransform implements Filter
  {

    private final FilterResult result;



    private AlwaysStopTransform(final LocalizableMessage message)
    {
      this.result = FilterResult.stop(message);
    }



    @Override
    public void close()
    {
      // Nothing to do.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final AddRequest change)
    {
      return result;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final DeleteRequest change)
    {
      return result;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyDNRequest change)
    {
      return result;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyRequest change)
    {
      return result;
    }
  }



  private static final class ChangeRecordWriterTransform implements Filter
  {
    private final ChangeRecordWriter writer;



    private ChangeRecordWriterTransform(final ChangeRecordWriter writer)
    {
      this.writer = writer;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
      try
      {
        writer.close();
      }
      catch (final IOException e)
      {
        // FIXME: this could occur while flushing. Log the error?
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final AddRequest change)
    {
      try
      {
        writer.writeChangeRecord(change);
        return FilterResult.next();
      }
      catch (final IOException e)
      {
        return FilterResult.fatal(e);
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final DeleteRequest change)
    {
      try
      {
        writer.writeChangeRecord(change);
        return FilterResult.next();
      }
      catch (final IOException e)
      {
        return FilterResult.fatal(e);
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyDNRequest change)
    {
      try
      {
        writer.writeChangeRecord(change);
        return FilterResult.next();
      }
      catch (final IOException e)
      {
        return FilterResult.fatal(e);
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyRequest change)
    {
      try
      {
        writer.writeChangeRecord(change);
        return FilterResult.next();
      }
      catch (final IOException e)
      {
        return FilterResult.fatal(e);
      }
    }

  }



  private static final class RemoveAttributeTransform implements Filter
  {
    private final AttributeDescription name;
    private final boolean includeSubtypes;



    private RemoveAttributeTransform(final AttributeDescription name,
        final boolean includeSubtypes)
    {
      this.name = name;
      this.includeSubtypes = includeSubtypes;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
      // Nothing to do.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final AddRequest change)
    {
      if (includeSubtypes)
      {
        final Iterator<Attribute> i = change.getAllAttributes(name).iterator();
        while (i.hasNext())
        {
          i.next();
          i.remove();
        }
      }
      else
      {
        change.removeAttribute(name);
      }
      return FilterResult.next();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final DeleteRequest change)
    {
      return FilterResult.next();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyDNRequest change)
    {
      return FilterResult.next();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyRequest change)
    {
      final Iterator<Modification> i = change.getModifications().iterator();
      while (i.hasNext())
      {
        final Modification modification = i.next();
        final Attribute attribute = modification.getAttribute();
        if (includeSubtypes)
        {
          if (attribute.getAttributeDescription().isSubTypeOf(name))
          {
            i.remove();
          }
        }
        else
        {
          if (attribute.getAttributeDescription().equals(name))
          {
            i.remove();
          }
        }
      }
      return FilterResult.next();
    }

  }



  private static final class RenameAttributeTransform implements Filter
  {
    // TODO: do we want to rename attributes in the DN?

    private final AttributeDescription from;
    private final AttributeDescription to;



    private RenameAttributeTransform(final AttributeDescription from,
        final AttributeDescription to)
    {
      this.from = from;
      this.to = to;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
      // Nothing to do.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final AddRequest change)
    {
      final Attribute attribute = change.getAttribute(from);
      if (attribute != null)
      {
        change.removeAttribute(from);
        change.addAttribute(Attributes.renameAttribute(attribute, to));
      }
      return FilterResult.next();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final DeleteRequest change)
    {
      return FilterResult.next();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyDNRequest change)
    {
      return FilterResult.next();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyRequest change)
    {
      final ListIterator<Modification> i = change.getModifications()
          .listIterator();
      while (i.hasNext())
      {
        final Modification modification = i.next();
        final Attribute attribute = modification.getAttribute();
        if (attribute.getAttributeDescription().equals(from))
        {
          i.set(new Modification(modification.getModificationType(), Attributes
              .renameAttribute(attribute, to)));
        }
      }
      return FilterResult.next();
    }

  }



  private static final class RenameEntryTransform implements Filter
  {
    private final DN from;
    private final DN to;



    private RenameEntryTransform(final DN from, final DN to)
    {
      this.from = from;
      this.to = to;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
      // Nothing to do.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final AddRequest change)
    {
      change.setName(change.getName().rename(from, to));
      return FilterResult.next();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final DeleteRequest change)
    {
      change.setName(change.getName().rename(from, to));
      return FilterResult.next();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyDNRequest change)
    {
      change.setName(change.getName().rename(from, to));

      // Rename the new superior or the new RDN if needed.
      final DN newSuperior = change.getNewSuperior();
      if (newSuperior != null && newSuperior.isSubordinateOrEqualTo(from))
      {
        // The new superior is in scope so rename.
        change.setNewSuperior(newSuperior.rename(from, to));
      }
      else
      {
        // The new superior is not in scope, but perhaps the new DN matches
        // "from".
        final DN newDN;
        if (newSuperior != null)
        {
          newDN = newSuperior.child(change.getNewRDN());
        }
        else
        {
          newDN = change.getName().parent().child(change.getNewRDN());
        }
        if (newDN.equals(from))
        {
          change.setNewRDN(to.rdn());
        }
      }
      return FilterResult.next();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyRequest change)
    {
      change.setName(change.getName().rename(from, to));
      return FilterResult.next();
    }

  }



  private static final Filter ALWAYS_STOP = new AlwaysStopTransform(null);



  /**
   * Returns a transformation which always drops changes. This transformation
   * can be combined with matches in order to selectively drop certain changes.
   *
   * @return A transformation which always drops changes.
   */
  public static Filter alwaysStop()
  {
    return ALWAYS_STOP;
  }



  /**
   * Returns a transformation which always drops changes. This transformation
   * can be combined with matches in order to selectively drop certain changes.
   *
   * @param reason
   *          The reason why the change is being dropped.
   * @return A transformation which always drops changes.
   */
  public static Filter alwaysStop(final LocalizableMessage reason)
  {
    return new AlwaysStopTransform(reason);
  }



  /**
   * Returns a transformation which writers change records to the provided
   * writer.
   *
   * @param writer
   *          The change record writer.
   * @return A transformation which writers change records to the provided
   *         writer.
   */
  public static Filter changeRecordWriter(final ChangeRecordWriter writer)
  {
    return new ChangeRecordWriterTransform(writer);
  }



  /**
   * Returns a transformation which will remove the named attribute from Add and
   * Modify operations if it is present. Note that only the named attribute will
   * be removed and not its sub-types.
   *
   * @param name
   *          The name of the attribute to be removed.
   * @return A transformation which will remove the named attribute from Add and
   *         Modify operations if it is present.
   */
  public static Filter removeAttribute(final AttributeDescription name)
  {
    return new RemoveAttributeTransform(name, false);
  }



  /**
   * Returns a transformation which will remove the named attribute and its
   * sub-types from Add and Modify operations if it is present.
   *
   * @param name
   *          The name of the attribute and sub-types to be removed.
   * @return A transformation which will remove the named attribute and its
   *         sub-types from Add and Modify operations if it is present.
   */
  public static Filter removeAttributeAndSubtypes(
      final AttributeDescription name)
  {
    return new RemoveAttributeTransform(name, true);
  }



  /**
   * Returns a transformation which will rename attributes contained in Add and
   * Modify operations.
   *
   * @param from
   *          The name of the attribute to be renamed.
   * @param to
   *          The new name of the renamed attribute.
   * @return A transformation which will rename attributes contained in Add and
   *         Modify operations.
   */
  public static Filter renameAttribute(final AttributeDescription from,
      final AttributeDescription to)
  {
    return new RenameAttributeTransform(from, to);
  }



  /**
   * Returns a transformation which will rename the operation target DN.
   *
   * @param from
   *          The source base DN.
   * @param to
   *          The target base DN.
   * @return A transformation which will rename the operation target DN.
   */
  public static Filter renameEntry(final DN from, final DN to)
  {
    return new RenameEntryTransform(from, to);
  }



  /**
   * Returns a transformation which will delegate transformation to the provided
   * Groovy script.
   *
   * @return A transformation which will delegate transformation to the provided
   *         Groovy script.
   */
  public static Filter transformUsingGroovy()
  {
    // TODO:
    return null;
  }



  private Transforms()
  {
    // Prevent instantiation.
  }
}
