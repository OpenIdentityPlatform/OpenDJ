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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.types;

import java.security.SecureRandom;
import java.util.Random;
import java.util.SortedSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;

import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class provides a data structure that makes it possible to
 * associate a name with a given set of characters.  The name must
 * consist only of ASCII alphabetic characters.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class NamedCharacterSet
{
  /** The characters contained in this character set. */
  private final char[] characters;
  /** The random number generator to use with this character set. */
  private final Random random;
  /** The name assigned to this character set. */
  private final String name;



  /**
   * Creates a new named character set with the provided information.
   *
   * @param  name        The name for this character set.
   * @param  characters  The characters to include in this character
   *                     set.
   *
   * @throws  ConfigException  If the provided name contains one or
   *                           more illegal characters.
   */
  private NamedCharacterSet(String name, char[] characters)
         throws ConfigException
  {
    this(name, characters, new SecureRandom());
  }



  /**
   * Creates a new named character set with the provided information.
   *
   * @param  name        The name for this character set.
   * @param  characters  The characters to include in this character
   *                     set.
   * @param  random      The random number generator to use with this
   *                     character set.
   *
   * @throws  ConfigException  If the provided name contains one or
   *                           more illegal characters.
   */
  private NamedCharacterSet(String name, char[] characters,
                           Random random)
         throws ConfigException
  {
    this.name       = name;
    this.characters = characters;
    this.random     = random;

    if (name == null || name.length() == 0)
    {
      LocalizableMessage message = ERR_CHARSET_CONSTRUCTOR_NO_NAME.get();
      throw new ConfigException(message);
    }

    for (int i=0; i < name.length(); i++)
    {
      if (! isAlpha(name.charAt(i)))
      {
        throw new ConfigException(ERR_CHARSET_CONSTRUCTOR_INVALID_NAME_CHAR.get(name.charAt(i), i));
      }
    }
  }



  /**
   * Retrieves the name for this character set.
   *
   * @return  The name for this character set.
   */
  public String getName()
  {
    return name;
  }



  /**
   * Retrieves the characters included in this character set.
   *
   * @return  The characters included in this character set.
   */
  public char[] getCharacters()
  {
    return characters;
  }



  /**
   * Retrieves a character at random from this named character set.
   *
   * @return  The randomly-selected character from this named
   *          character set;
   */
  public char getRandomCharacter()
  {
    if (characters == null || characters.length == 0)
    {
      return 0;
    }

    return characters[random.nextInt(characters.length)];
  }



  /**
   * Appends the specified number of characters chosen at random from
   * this character set to the provided buffer.
   *
   * @param  buffer  The buffer to which the characters should be
   *                 appended.
   * @param  count   The number of characters to append to the
   *                 provided buffer.
   */
  public void getRandomCharacters(StringBuilder buffer, int count)
  {
    if (characters == null || characters.length == 0)
    {
      return;
    }

    for (int i=0; i < count; i++)
    {
      buffer.append(characters[random.nextInt(characters.length)]);
    }
  }

  /**
   * Decodes the values of the provided configuration attribute as a
   * set of character set definitions.
   *
   * @param  values  The set of encoded character set values to
   *                 decode.
   *
   * @return  The decoded character set definitions.
   *
   * @throws  ConfigException  If a problem occurs while attempting to
   *                           decode the character set definitions.
   */
  public static NamedCharacterSet[]
                     decodeCharacterSets(SortedSet<String> values)
         throws ConfigException
  {
    NamedCharacterSet[] sets = new NamedCharacterSet[values.size()];
    int i = 0 ;
    for (String value : values)
    {
      int colonPos = value.indexOf(':');
      if (colonPos < 0)
      {
        throw new ConfigException(ERR_CHARSET_NO_COLON.get(value));
      }
      else if (colonPos == 0)
      {
        throw new ConfigException(ERR_CHARSET_NO_NAME.get(value));
      }
      else if (colonPos == (value.length() - 1))
      {
        throw new ConfigException(ERR_CHARSET_NO_CHARS.get(value));
      }
      else
      {
        String name       = value.substring(0, colonPos);
        char[] characters = value.substring(colonPos+1).toCharArray();
        sets[i] = new NamedCharacterSet(name, characters);
      }
      i++;
    }

    return sets;
  }
}

