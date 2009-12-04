/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.util.StaticUtils.*;

import java.util.*;

import org.opends.sdk.schema.AttributeType;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.schema.UnknownSchemaElementException;

import com.sun.opends.sdk.util.*;



/**
 * An attribute description as defined in RFC 4512 section 2.5.
 * Attribute descriptions are used to identify an attribute in an entry
 * and are composed of an attribute type and a set of zero or more
 * attribute options.
 *
 * @see <a href="http://tools.ietf.org/html/rfc4512#section-2.5">RFC
 *      4512 - Lightweight Directory Access Protocol (LDAP): Directory
 *      Information Models </a>
 */
public final class AttributeDescription implements
    Comparable<AttributeDescription>
{
  private static abstract class Impl implements Iterable<String>
  {
    protected Impl()
    {
      // Nothing to do.
    }



    public abstract int compareTo(Impl other);



    public abstract boolean containsOption(String normalizedOption);



    public abstract boolean equals(Impl other);



    public abstract String firstNormalizedOption();



    @Override
    public abstract int hashCode();



    public abstract boolean hasOptions();



    public abstract boolean isSubTypeOf(Impl other);



    public abstract boolean isSuperTypeOf(Impl other);



    public abstract int size();

  }



  private static final class MultiOptionImpl extends Impl
  {

    private final String[] normalizedOptions;

    private final String[] options;



    private MultiOptionImpl(String[] options, String[] normalizedOptions)
    {
      if (normalizedOptions.length < 2)
      {
        throw new AssertionError();
      }

      this.options = options;
      this.normalizedOptions = normalizedOptions;
    }



    @Override
    public int compareTo(Impl other)
    {
      final int thisSize = normalizedOptions.length;
      final int otherSize = other.size();

      if (thisSize < otherSize)
      {
        return -1;
      }
      else if (thisSize > otherSize)
      {
        return 1;
      }
      else
      {
        // Same number of options.
        final MultiOptionImpl otherImpl = (MultiOptionImpl) other;
        for (int i = 0; i < thisSize; i++)
        {
          final String o1 = normalizedOptions[i];
          final String o2 = otherImpl.normalizedOptions[i];
          final int result = o1.compareTo(o2);
          if (result != 0)
          {
            return result;
          }
        }

        // All options the same.
        return 0;
      }
    }



    @Override
    public boolean containsOption(String normalizedOption)
    {
      final int sz = normalizedOptions.length;
      for (int i = 0; i < sz; i++)
      {
        if (normalizedOptions[i].equals(normalizedOption))
        {
          return true;
        }
      }
      return false;
    }



    @Override
    public boolean equals(Impl other)
    {
      if (other instanceof MultiOptionImpl)
      {
        final MultiOptionImpl tmp = (MultiOptionImpl) other;
        return Arrays.equals(normalizedOptions, tmp.normalizedOptions);
      }
      else
      {
        return false;
      }
    }



    @Override
    public String firstNormalizedOption()
    {
      return normalizedOptions[0];
    }



    @Override
    public int hashCode()
    {
      return Arrays.hashCode(normalizedOptions);
    }



    @Override
    public boolean hasOptions()
    {
      return true;
    }



    @Override
    public boolean isSubTypeOf(Impl other)
    {
      // Must contain a super-set of other's options.
      if (other == ZERO_OPTION_IMPL)
      {
        return true;
      }
      else if (other.size() == 1)
      {
        return containsOption(other.firstNormalizedOption());
      }
      else if (other.size() > size())
      {
        return false;
      }
      else
      {
        // Check this contains other's options.
        //
        // This could be optimized more if required, but it's probably
        // not worth it.
        final MultiOptionImpl tmp = (MultiOptionImpl) other;
        for (final String normalizedOption : tmp.normalizedOptions)
        {
          if (!containsOption(normalizedOption))
          {
            return false;
          }
        }
        return true;
      }
    }



    @Override
    public boolean isSuperTypeOf(Impl other)
    {
      // Must contain a sub-set of other's options.
      for (final String normalizedOption : normalizedOptions)
      {
        if (!other.containsOption(normalizedOption))
        {
          return false;
        }
      }
      return true;
    }



    public Iterator<String> iterator()
    {
      return Iterators.arrayIterator(options);
    }



    @Override
    public int size()
    {
      return normalizedOptions.length;
    }

  }



  private static final class SingleOptionImpl extends Impl
  {

    private final String normalizedOption;

    private final String option;



    private SingleOptionImpl(String option, String normalizedOption)
    {
      this.option = option;
      this.normalizedOption = normalizedOption;
    }



    @Override
    public int compareTo(Impl other)
    {
      if (other == ZERO_OPTION_IMPL)
      {
        // If other has zero options then this sorts after.
        return 1;
      }
      else if (other.size() == 1)
      {
        // Same number of options, so compare.
        return normalizedOption
            .compareTo(other.firstNormalizedOption());
      }
      else
      {
        // Other has more options, so comes after.
        return -1;
      }
    }



    @Override
    public boolean containsOption(String normalizedOption)
    {
      return this.normalizedOption.equals(normalizedOption);
    }



    @Override
    public boolean equals(Impl other)
    {
      return other.size() == 1
          && other.containsOption(normalizedOption);
    }



    @Override
    public String firstNormalizedOption()
    {
      return normalizedOption;
    }



    @Override
    public int hashCode()
    {
      return normalizedOption.hashCode();
    }



    @Override
    public boolean hasOptions()
    {
      return true;
    }



    @Override
    public boolean isSubTypeOf(Impl other)
    {
      // Other must have no options or the same option.
      if (other == ZERO_OPTION_IMPL)
      {
        return true;
      }
      else
      {
        return equals(other);
      }
    }



    @Override
    public boolean isSuperTypeOf(Impl other)
    {
      // Other must have this option.
      return other.containsOption(normalizedOption);
    }



    public Iterator<String> iterator()
    {
      return Iterators.singleton(option);
    }



    @Override
    public int size()
    {
      return 1;
    }

  }



  private static final class ZeroOptionImpl extends Impl
  {
    private ZeroOptionImpl()
    {
      // Nothing to do.
    }



    @Override
    public int compareTo(Impl other)
    {
      // If other has options then this sorts before.
      return this == other ? 0 : -1;
    }



    @Override
    public boolean containsOption(String normalizedOption)
    {
      return false;
    }



    @Override
    public boolean equals(Impl other)
    {
      return this == other;
    }



    @Override
    public String firstNormalizedOption()
    {
      // No first option.
      return null;
    }



    @Override
    public int hashCode()
    {
      // Use attribute type hash code.
      return 0;
    }



    @Override
    public boolean hasOptions()
    {
      return false;
    }



    @Override
    public boolean isSubTypeOf(Impl other)
    {
      // Can only be a sub-type if other has no options.
      return this == other;
    }



    @Override
    public boolean isSuperTypeOf(Impl other)
    {
      // Will always be a super-type.
      return true;
    }



    public Iterator<String> iterator()
    {
      return Iterators.empty();
    }



    @Override
    public int size()
    {
      return 0;
    }

  }



  private static final ThreadLocal<WeakHashMap<Schema, Map<String, AttributeDescription>>> CACHE = new ThreadLocal<WeakHashMap<Schema, Map<String, AttributeDescription>>>()
  {

    /**
     * {@inheritDoc}
     */
    @Override
    protected WeakHashMap<Schema, Map<String, AttributeDescription>> initialValue()
    {
      return new WeakHashMap<Schema, Map<String, AttributeDescription>>();
    }

  };

  // Object class attribute description.
  private static final ZeroOptionImpl ZERO_OPTION_IMPL = new ZeroOptionImpl();

  private static final AttributeDescription OBJECT_CLASS;
  static
  {
    final AttributeType attributeType = Schema.getCoreSchema()
        .getAttributeType("2.5.4.0");
    OBJECT_CLASS = new AttributeDescription(attributeType
        .getNameOrOID(), attributeType, ZERO_OPTION_IMPL);
  }

  // This is the size of the per-thread per-schema attribute description
  // cache. We should be conservative here in case there are many
  // threads.
  private static final int ATTRIBUTE_DESCRIPTION_CACHE_SIZE = 512;



  /**
   * Creates an attribute description having the same attribute type and
   * options as the provided attribute description and, in addition, the
   * provided list of options.
   *
   * @param attributeDescription
   *          The attribute description.
   * @param options
   *          The attribute options.
   * @return The new attribute description containing {@code options}.
   * @throws NullPointerException
   *           If {@code attributeDescription} or {@code options} was
   *           {@code null}.
   */
  public static AttributeDescription create(
      AttributeDescription attributeDescription, String... options)
      throws NullPointerException
  {
    Validator.ensureNotNull(attributeDescription, options);

    // This should not be called very often, so don't optimize.
    AttributeDescription newAttributeDescription = attributeDescription;
    for (final String option : options)
    {
      newAttributeDescription = create(newAttributeDescription, option);
    }
    return newAttributeDescription;
  }



  /**
   * Creates an attribute description having the same attribute type and
   * options as the provided attribute description and, in addition, the
   * provided new option.
   *
   * @param attributeDescription
   *          The attribute description.
   * @param option
   *          The attribute option.
   * @return The new attribute description containing {@code option}.
   * @throws NullPointerException
   *           If {@code attributeDescription} or {@code option} was
   *           {@code null}.
   */
  public static AttributeDescription create(
      AttributeDescription attributeDescription, String option)
      throws NullPointerException
  {
    Validator.ensureNotNull(attributeDescription, option);

    final String normalizedOption = toLowerCase(option);
    if (attributeDescription.pimpl.containsOption(normalizedOption))
    {
      return attributeDescription;
    }

    final String oldAttributeDescription = attributeDescription.attributeDescription;
    final StringBuilder builder = new StringBuilder(
        oldAttributeDescription.length() + option.length() + 1);
    builder.append(oldAttributeDescription);
    builder.append(';');
    builder.append(option);
    final String newAttributeDescription = builder.toString();

    final Impl impl = attributeDescription.pimpl;
    if (impl instanceof ZeroOptionImpl)
    {
      return new AttributeDescription(newAttributeDescription,
          attributeDescription.attributeType, new SingleOptionImpl(
              option, normalizedOption));

    }
    else if (impl instanceof SingleOptionImpl)
    {
      final SingleOptionImpl simpl = (SingleOptionImpl) impl;

      final String[] newOptions = new String[2];
      newOptions[0] = simpl.option;
      newOptions[1] = option;

      final String[] newNormalizedOptions = new String[2];
      if (normalizedOption.compareTo(simpl.normalizedOption) < 0)
      {
        newNormalizedOptions[0] = normalizedOption;
        newNormalizedOptions[1] = simpl.normalizedOption;
      }

      return new AttributeDescription(newAttributeDescription,
          attributeDescription.attributeType, new MultiOptionImpl(
              newOptions, newNormalizedOptions));
    }
    else
    {
      final MultiOptionImpl mimpl = (MultiOptionImpl) impl;

      final int sz1 = mimpl.options.length;
      final String[] newOptions = new String[sz1 + 1];
      for (int i = 0; i < sz1; i++)
      {
        newOptions[i] = mimpl.options[i];
      }
      newOptions[sz1] = option;

      final int sz2 = mimpl.normalizedOptions.length;
      final String[] newNormalizedOptions = new String[sz2 + 1];
      boolean inserted = false;
      for (int i = 0; i < sz2; i++)
      {
        if (!inserted)
        {
          final String s = mimpl.normalizedOptions[i];
          if (normalizedOption.compareTo(s) < 0)
          {
            newNormalizedOptions[i] = normalizedOption;
            newNormalizedOptions[i + 1] = s;
            inserted = true;
          }
          else
          {
            newNormalizedOptions[i] = s;
          }
        }
        else
        {
          newNormalizedOptions[i + 1] = mimpl.normalizedOptions[i];
        }
      }

      if (!inserted)
      {
        newNormalizedOptions[sz2] = normalizedOption;
      }

      return new AttributeDescription(newAttributeDescription,
          attributeDescription.attributeType, new MultiOptionImpl(
              newOptions, newNormalizedOptions));
    }
  }



  /**
   * Creates an attribute description having the provided attribute type
   * and no options.
   *
   * @param attributeType
   *          The attribute type.
   * @return The attribute description.
   * @throws NullPointerException
   *           If {@code attributeType} was {@code null}.
   */
  public static AttributeDescription create(AttributeType attributeType)
      throws NullPointerException
  {
    Validator.ensureNotNull(attributeType);

    // Use object identity in case attribute type does not come from
    // core schema.
    if (attributeType == OBJECT_CLASS.getAttributeType())
    {
      return OBJECT_CLASS;
    }
    else
    {
      return new AttributeDescription(attributeType.getNameOrOID(),
          attributeType, ZERO_OPTION_IMPL);
    }
  }



  /**
   * Creates an attribute description having the provided attribute type
   * and single option.
   *
   * @param attributeType
   *          The attribute type.
   * @param option
   *          The attribute option.
   * @return The attribute description.
   * @throws NullPointerException
   *           If {@code attributeType} or {@code option} was {@code
   *           null}.
   */
  public static AttributeDescription create(
      AttributeType attributeType, String option)
      throws NullPointerException
  {
    Validator.ensureNotNull(attributeType, option);

    final String oid = attributeType.getNameOrOID();
    final StringBuilder builder = new StringBuilder(oid.length()
        + option.length() + 1);
    builder.append(oid);
    builder.append(';');
    builder.append(option);
    final String attributeDescription = builder.toString();
    final String normalizedOption = toLowerCase(option);

    return new AttributeDescription(attributeDescription,
        attributeType, new SingleOptionImpl(option, normalizedOption));
  }



  /**
   * Creates an attribute description having the provided attribute type
   * and options.
   *
   * @param attributeType
   *          The attribute type.
   * @param options
   *          The attribute options.
   * @return The attribute description.
   * @throws NullPointerException
   *           If {@code attributeType} or {@code options} was {@code
   *           null}.
   */
  public static AttributeDescription create(
      AttributeType attributeType, String... options)
      throws NullPointerException
  {
    Validator.ensureNotNull(attributeType, options);

    switch (options.length)
    {
    case 0:
      return create(attributeType);
    case 1:
      return create(attributeType, options[0]);
    default:
      final String[] optionsList = new String[options.length];
      final String[] normalizedOptions = new String[options.length];

      final String oid = attributeType.getNameOrOID();
      final StringBuilder builder = new StringBuilder(oid.length()
          + options[0].length() + options[1].length() + 2);
      builder.append(oid);

      int i = 0;
      for (final String option : options)
      {
        builder.append(';');
        builder.append(option);
        optionsList[i] = option;
        final String normalizedOption = toLowerCase(option);
        normalizedOptions[i++] = normalizedOption;
      }
      Arrays.sort(normalizedOptions);

      final String attributeDescription = builder.toString();
      return new AttributeDescription(attributeDescription,
          attributeType, new MultiOptionImpl(optionsList,
              normalizedOptions));
    }

  }



  /**
   * Returns an attribute description representing the object class
   * attribute type with no options.
   *
   * @return The object class attribute description.
   */
  public static AttributeDescription objectClass()
  {
    return OBJECT_CLASS;
  }



  /**
   * Parses the provided LDAP string representation of an attribute
   * description using the default schema.
   *
   * @param attributeDescription
   *          The LDAP string representation of an attribute
   *          description.
   * @return The parsed attribute description.
   * @throws LocalizedIllegalArgumentException
   *           If {@code attributeDescription} is not a valid LDAP
   *           string representation of an attribute description.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  public static AttributeDescription valueOf(String attributeDescription)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    return valueOf(attributeDescription, Schema.getDefaultSchema());
  }



  /**
   * Parses the provided LDAP string representation of an attribute
   * description using the provided schema.
   *
   * @param attributeDescription
   *          The LDAP string representation of an attribute
   *          description.
   * @param schema
   *          The schema to use when parsing the attribute description.
   * @return The parsed attribute description.
   * @throws LocalizedIllegalArgumentException
   *           If {@code attributeDescription} is not a valid LDAP
   *           string representation of an attribute description.
   * @throws NullPointerException
   *           If {@code attributeDescription} or {@code schema} was
   *           {@code null}.
   */
  @SuppressWarnings("serial")
  public static AttributeDescription valueOf(
      String attributeDescription, Schema schema)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    Validator.ensureNotNull(attributeDescription, schema);

    // First look up the attribute description in the cache.
    final WeakHashMap<Schema, Map<String, AttributeDescription>> threadLocalMap = CACHE
        .get();
    Map<String, AttributeDescription> schemaLocalMap = threadLocalMap
        .get(schema);

    AttributeDescription ad = null;
    if (schemaLocalMap == null)
    {
      schemaLocalMap = new LinkedHashMap<String, AttributeDescription>(
          ATTRIBUTE_DESCRIPTION_CACHE_SIZE, 0.75f, true)
      {
        @Override
        protected boolean removeEldestEntry(
            Map.Entry<String, AttributeDescription> eldest)
        {
          return size() > ATTRIBUTE_DESCRIPTION_CACHE_SIZE;
        }
      };
      threadLocalMap.put(schema, schemaLocalMap);
    }
    else
    {
      ad = schemaLocalMap.get(attributeDescription);
    }

    // Cache miss: decode and cache.
    if (ad == null)
    {
      ad = valueOf0(attributeDescription, schema);
      schemaLocalMap.put(attributeDescription, ad);
    }

    return ad;
  }



  private static int skipTrailingWhiteSpace(
      String attributeDescription, int i, int length)
      throws LocalizedIllegalArgumentException
  {
    char c;
    while (i < length)
    {
      c = attributeDescription.charAt(i);
      if (c != ' ')
      {
        final Message message = ERR_ATTRIBUTE_DESCRIPTION_INTERNAL_WHITESPACE
            .get(attributeDescription);
        throw new LocalizedIllegalArgumentException(message);
      }
      i++;
    }
    return i;
  }



  // Uncached valueOf implementation.
  private static AttributeDescription valueOf0(
      String attributeDescription, Schema schema)
      throws LocalizedIllegalArgumentException
  {
    int i = 0;
    final int length = attributeDescription.length();
    char c = 0;

    // Skip leading white space.
    while (i < length)
    {
      c = attributeDescription.charAt(i);
      if (c != ' ')
      {
        break;
      }
      i++;
    }

    // If we're already at the end then the attribute description only
    // contained whitespace.
    if (i == length)
    {
      final Message message = ERR_ATTRIBUTE_DESCRIPTION_EMPTY
          .get(attributeDescription);
      throw new LocalizedIllegalArgumentException(message);
    }

    // Validate the first non-whitespace character.
    ASCIICharProp cp = ASCIICharProp.valueOf(c);
    if (cp == null)
    {
      final Message message = ERR_ATTRIBUTE_DESCRIPTION_ILLEGAL_CHARACTER
          .get(attributeDescription, c, i);
      throw new LocalizedIllegalArgumentException(message);
    }

    // Mark the attribute type start position.
    final int attributeTypeStart = i;
    if (cp.isLetter())
    {
      // Non-numeric OID: letter + zero or more keychars.
      i++;
      while (i < length)
      {
        c = attributeDescription.charAt(i);

        if (c == ';' || c == ' ')
        {
          break;
        }

        cp = ASCIICharProp.valueOf(c);
        if (!cp.isKeyChar())
        {
          final Message message = ERR_ATTRIBUTE_DESCRIPTION_ILLEGAL_CHARACTER
              .get(attributeDescription, c, i);
          throw new LocalizedIllegalArgumentException(message);
        }
        i++;
      }

      // (charAt(i) == ';' || c == ' ' || i == length)
    }
    else if (cp.isDigit())
    {
      // Numeric OID: decimal digit + zero or more dots or decimals.
      i++;
      while (i < length)
      {
        c = attributeDescription.charAt(i);
        if (c == ';' || c == ' ')
        {
          break;
        }

        cp = ASCIICharProp.valueOf(c);
        if (c != '.' && !cp.isDigit())
        {
          final Message message = ERR_ATTRIBUTE_DESCRIPTION_ILLEGAL_CHARACTER
              .get(attributeDescription, c, i);
          throw new LocalizedIllegalArgumentException(message);
        }
        i++;
      }

      // (charAt(i) == ';' || charAt(i) == ' ' || i == length)
    }
    else
    {
      final Message message = ERR_ATTRIBUTE_DESCRIPTION_ILLEGAL_CHARACTER
          .get(attributeDescription, c, i);
      throw new LocalizedIllegalArgumentException(message);
    }

    // Skip trailing white space.
    final int attributeTypeEnd = i;
    if (c == ' ')
    {
      i = skipTrailingWhiteSpace(attributeDescription, i + 1, length);
    }

    // Determine the portion of the string containing the attribute type
    // name.
    String oid;
    if (attributeTypeStart == 0 && attributeTypeEnd == length)
    {
      oid = attributeDescription;
    }
    else
    {
      oid = attributeDescription.substring(attributeTypeStart,
          attributeTypeEnd);
    }

    if (oid.length() == 0)
    {
      final Message message = ERR_ATTRIBUTE_DESCRIPTION_NO_TYPE
          .get(attributeDescription);
      throw new LocalizedIllegalArgumentException(message);
    }

    // Get the attribute type from the schema.
    AttributeType attributeType;
    try
    {
      attributeType = schema.getAttributeType(oid);
    }
    catch (final UnknownSchemaElementException e)
    {
      final Message message = ERR_ATTRIBUTE_DESCRIPTION_TYPE_NOT_FOUND
          .get(attributeDescription, e.getMessageObject());
      throw new LocalizedIllegalArgumentException(message);
    }

    // If we're already at the end of the attribute description then it
    // does not contain any options.
    if (i == length)
    {
      // Use object identity in case attribute type does not come from
      // core schema.
      if (attributeType == OBJECT_CLASS.getAttributeType()
          && attributeDescription.equals(OBJECT_CLASS.toString()))
      {
        return OBJECT_CLASS;
      }
      else
      {
        return new AttributeDescription(attributeDescription,
            attributeType, ZERO_OPTION_IMPL);
      }
    }

    // At this point 'i' must point at a semi-colon.
    i++;
    StringBuilder builder = null;
    int optionStart = i;
    while (i < length)
    {
      c = attributeDescription.charAt(i);
      if (c == ' ' || c == ';')
      {
        break;
      }

      cp = ASCIICharProp.valueOf(c);
      if (!cp.isKeyChar())
      {
        final Message message = ERR_ATTRIBUTE_DESCRIPTION_ILLEGAL_CHARACTER
            .get(attributeDescription, c, i);
        throw new LocalizedIllegalArgumentException(message);
      }

      if (builder == null)
      {
        if (cp.isUpperCase())
        {
          // Need to normalize the option.
          builder = new StringBuilder(length - optionStart);
          builder.append(attributeDescription, optionStart, i);
          builder.append(cp.toLowerCase());
        }
      }
      else
      {
        builder.append(cp.toLowerCase());
      }
      i++;
    }

    String option = attributeDescription.substring(optionStart, i);
    String normalizedOption;
    if (builder != null)
    {
      normalizedOption = builder.toString();
    }
    else
    {
      normalizedOption = option;
    }

    if (option.length() == 0)
    {
      final Message message = ERR_ATTRIBUTE_DESCRIPTION_EMPTY_OPTION
          .get(attributeDescription);
      throw new LocalizedIllegalArgumentException(message);
    }

    // Skip trailing white space.
    if (c == ' ')
    {
      i = skipTrailingWhiteSpace(attributeDescription, i + 1, length);
    }

    // If we're already at the end of the attribute description then it
    // only contains a single option.
    if (i == length)
    {
      return new AttributeDescription(attributeDescription,
          attributeType, new SingleOptionImpl(option, normalizedOption));
    }

    // Multiple options need sorting and duplicates removed - we could
    // optimize a bit further here for 2 option attribute descriptions.
    final List<String> options = new LinkedList<String>();
    options.add(option);

    final SortedSet<String> normalizedOptions = new TreeSet<String>();
    normalizedOptions.add(normalizedOption);

    while (i < length)
    {
      // At this point 'i' must point at a semi-colon.
      i++;
      builder = null;
      optionStart = i;
      while (i < length)
      {
        c = attributeDescription.charAt(i);
        if (c == ' ' || c == ';')
        {
          break;
        }

        cp = ASCIICharProp.valueOf(c);
        if (!cp.isKeyChar())
        {
          final Message message = ERR_ATTRIBUTE_DESCRIPTION_ILLEGAL_CHARACTER
              .get(attributeDescription, c, i);
          throw new LocalizedIllegalArgumentException(message);
        }

        if (builder == null)
        {
          if (cp.isUpperCase())
          {
            // Need to normalize the option.
            builder = new StringBuilder(length - optionStart);
            builder.append(attributeDescription, optionStart, i);
            builder.append(cp.toLowerCase());
          }
        }
        else
        {
          builder.append(cp.toLowerCase());
        }
        i++;
      }

      option = attributeDescription.substring(optionStart, i);
      if (builder != null)
      {
        normalizedOption = builder.toString();
      }
      else
      {
        normalizedOption = option;
      }

      if (option.length() == 0)
      {
        final Message message = ERR_ATTRIBUTE_DESCRIPTION_EMPTY_OPTION
            .get(attributeDescription);
        throw new LocalizedIllegalArgumentException(message);
      }

      // Skip trailing white space.
      if (c == ' ')
      {
        i = skipTrailingWhiteSpace(attributeDescription, i + 1, length);
      }

      options.add(option);
      normalizedOptions.add(normalizedOption);
    }

    return new AttributeDescription(attributeDescription,
        attributeType, new MultiOptionImpl(options
            .toArray(new String[options.size()]), normalizedOptions
            .toArray(new String[normalizedOptions.size()])));
  }



  private final String attributeDescription;

  private final AttributeType attributeType;

  private final Impl pimpl;



  // Private constructor.
  private AttributeDescription(String attributeDescription,
      AttributeType attributeType, Impl pimpl)
  {
    this.attributeDescription = attributeDescription;
    this.attributeType = attributeType;
    this.pimpl = pimpl;
  }



  /**
   * Compares this attribute description to the provided attribute
   * description. The attribute types are compared first and then, if
   * equal, the options are normalized, sorted, and compared.
   *
   * @param other
   *          The attribute description to be compared.
   * @return A negative integer, zero, or a positive integer as this
   *         attribute description is less than, equal to, or greater
   *         than the specified attribute description.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public int compareTo(AttributeDescription other)
      throws NullPointerException
  {
    final int result = attributeType.compareTo(other.attributeType);
    if (result != 0)
    {
      return result;
    }
    else
    {
      // Attribute type is the same, so compare options.
      return pimpl.compareTo(other.pimpl);
    }
  }



  /**
   * Indicates whether or not this attribute description contains the
   * provided option.
   *
   * @param option
   *          The option for which to make the determination.
   * @return {@code true} if this attribute description has the provided
   *         option, or {@code false} if not.
   * @throws NullPointerException
   *           If {@code option} was {@code null}.
   */
  public boolean containsOption(String option)
      throws NullPointerException
  {
    final String normalizedOption = toLowerCase(option);
    return pimpl.containsOption(normalizedOption);
  }



  /**
   * Indicates whether the provided object is an attribute description
   * which is equal to this attribute description. It will be considered
   * equal if the attribute type and normalized sorted list of options
   * are identical.
   *
   * @param o
   *          The object for which to make the determination.
   * @return {@code true} if the provided object is an attribute
   *         description that is equal to this attribute description, or
   *         {@code false} if not.
   */
  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }

    if (!(o instanceof AttributeDescription))
    {
      return false;
    }

    final AttributeDescription other = (AttributeDescription) o;
    if (!attributeType.equals(other.attributeType))
    {
      return false;
    }

    // Attribute type is the same, compare options.
    return pimpl.equals(other.pimpl);
  }



  /**
   * Returns the attribute type associated with this attribute
   * description.
   *
   * @return The attribute type associated with this attribute
   *         description.
   */
  public AttributeType getAttributeType()
  {
    return attributeType;
  }



  /**
   * Returns an {@code Iterable} containing the options contained in
   * this attribute description. Attempts to remove options using an
   * iterator's {@code remove()} method are not permitted and will
   * result in an {@code UnsupportedOperationException} being thrown.
   *
   * @return An {@code Iterable} containing the options.
   */
  public Iterable<String> getOptions()
  {
    return pimpl;
  }



  /**
   * Returns the hash code for this attribute description. It will be
   * calculated as the sum of the hash codes of the attribute type and
   * normalized sorted list of options.
   *
   * @return The hash code for this attribute description.
   */
  @Override
  public int hashCode()
  {
    // FIXME: should we cache this?
    return attributeType.hashCode() * 31 + pimpl.hashCode();
  }



  /**
   * Indicates whether or not this attribute description has any
   * options.
   *
   * @return {@code true} if this attribute description has any options,
   *         or {@code false} if not.
   */
  public boolean hasOptions()
  {
    return pimpl.hasOptions();
  }



  /**
   * Indicates whether or not this attribute description is the {@code
   * objectClass} attribute description with no options.
   *
   * @return {@code true} if this attribute description is the {@code
   *         objectClass} attribute description with no options, or
   *         {@code false} if not.
   */
  public boolean isObjectClass()
  {
    return attributeType.isObjectClass() && !hasOptions();
  }



  /**
   * Indicates whether or not this attribute description is a sub-type
   * of the provided attribute description as defined in RFC 4512
   * section 2.5. Specifically, this method will return {@code true} if
   * and only if the following conditions are both {@code true}:
   * <ul>
   * <li>This attribute description has an attribute type which is equal
   * to, or is a sub-type of, the attribute type in the provided
   * attribute description.
   * <li>This attribute description contains all of the options
   * contained in the provided attribute description.
   * </ul>
   * Note that this method will return {@code true} if this attribute
   * description is equal to the provided attribute description.
   *
   * @param other
   *          The attribute description for which to make the
   *          determination.
   * @return {@code true} if this attribute description is a sub-type of
   *         the provided attribute description, or {@code false} if
   *         not.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public boolean isSubTypeOf(AttributeDescription other)
      throws NullPointerException
  {
    if (!attributeType.isSubTypeOf(other.attributeType))
    {
      return false;
    }
    else
    {
      return pimpl.isSubTypeOf(other.pimpl);
    }
  }



  /**
   * Indicates whether or not this attribute description is a super-type
   * of the provided attribute description as defined in RFC 4512
   * section 2.5. Specifically, this method will return {@code true} if
   * and only if the following conditions are both {@code true}:
   * <ul>
   * <li>This attribute description has an attribute type which is equal
   * to, or is a super-type of, the attribute type in the provided
   * attribute description.
   * <li>This attribute description contains a sub-set of the options
   * contained in the provided attribute description.
   * </ul>
   * Note that this method will return {@code true} if this attribute
   * description is equal to the provided attribute description.
   *
   * @param other
   *          The attribute description for which to make the
   *          determination.
   * @return {@code true} if this attribute description is a super-type
   *         of the provided attribute description, or {@code false} if
   *         not.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public boolean isSuperTypeOf(AttributeDescription other)
      throws NullPointerException
  {
    if (!other.attributeType.isSubTypeOf(attributeType))
    {
      return false;
    }
    else
    {
      return pimpl.isSuperTypeOf(other.pimpl);
    }
  }



  /**
   * Returns the string representation of this attribute description as
   * defined in RFC4512 section 2.5.
   *
   * @return The string representation of this attribute description.
   */
  @Override
  public String toString()
  {
    return attributeDescription;
  }
}
