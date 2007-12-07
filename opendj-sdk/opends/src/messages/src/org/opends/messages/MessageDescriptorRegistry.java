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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.messages;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Serves as a registry for messages providing access to message descriptors
 * by ID and class in which they are defined.  Classes containing message
 * descriptors are stored in the text file "descriptors.reg" which is
 * generated during the OpenDS build process.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.PRIVATE)
final class MessageDescriptorRegistry {

  private static final String REGISTRY_FILE = "descriptors.reg";

  private static final Set<Class<?>> REGISTERED_MESSAGE_CLASSES =
                  new HashSet<Class<?>>();

  private static final Map<Integer, MessageDescriptor>
          ID_TO_DESCRIPTORS =
                  new HashMap<Integer,MessageDescriptor>();

  private static final Map<Class<?>, List<MessageDescriptor>>
          CLASS_TO_DESCRIPTORS =
                  new HashMap<Class<?>,List<MessageDescriptor>>();


  static {
    InputStream is = MessageDescriptor.class
            .getResourceAsStream(REGISTRY_FILE);
    BufferedReader reader =
            new BufferedReader(new InputStreamReader(is));
    String line;
    try {
      while (null != (line = reader.readLine())) {
        String descClassName = line.trim();
        Class<?> descClass;
        try {
          descClass = Class.forName(descClassName);
          List<MessageDescriptor> mdList = new LinkedList<MessageDescriptor>();
          CLASS_TO_DESCRIPTORS.put(descClass,  mdList);
          REGISTERED_MESSAGE_CLASSES.add(descClass);
          Field[] fa = descClass.getFields();
          if (fa != null) {
            for (Field f : fa) {
              Class<?> c = f.getType();
              if (MessageDescriptor.class.isAssignableFrom(c)) {
                MessageDescriptor md = (MessageDescriptor)f.get(null);
                mdList.add(md);
                int id = md.getId();
                if (id != MessageDescriptor.NULL_ID) {
                  ID_TO_DESCRIPTORS.put(id, md);
                }
              }
            }
          }
        } catch (ClassNotFoundException e) {
          System.err.println("Message class " + descClassName +
                  " not found.");
        } catch (IllegalAccessException e) {
          System.err.println("Error accessing class " +
                  descClassName + ":  " + e.getMessage());
        }
      }
    } catch (IOException e) {
      System.err.println("Unable to register message files:  " +
              e.getMessage());
    }
  }

  /**
   * Returns a message descriptor given its unique OpenDS system-wide ID.
   *
   * @param id integer value of the message to retreive
   * @return MessageDescriptor having the ID of <code>id</code>
   */
  public static MessageDescriptor getMessageDescriptor(int id) {
    return ID_TO_DESCRIPTORS.get(id);
  }

  /**
   * Given a registered class containing message descriptors, returns a
   * list of all message descriptors defined by that class.
   *
   * @param mdClass registered class containing message descriptors
   * @return unmodifiable list of message descriptors declared in
   *         <code>mdClass</code>
   */

  public static List<MessageDescriptor> getMessageDescriptorsForClass(
          Class<?> mdClass)
  {
    return Collections.unmodifiableList(CLASS_TO_DESCRIPTORS.get(mdClass));
  }

  /**
   * Returns a set of classes registered as having message descriptor
   * declarations.
   *
   * @return list of classes
   */
  public static Set<Class<?>> getRegisteredClasses() {
    return Collections.unmodifiableSet(REGISTERED_MESSAGE_CLASSES);
  }

}
