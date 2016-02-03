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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.admin;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.forgerock.opendj.ldap.RDN;
import org.opends.server.util.StaticUtils;

/**
 * A reference to another managed object.
 *
 * @param <C>
 *          The type of client managed object configuration that this
 *          reference refers to.
 * @param <S>
 *          The type of server managed object configuration that this
 *          reference refers to.
 */
public final class Reference<C extends ConfigurationClient,
                             S extends Configuration> {

  /**
   * Parses a DN string value as a reference using the provided
   * managed object path and relation definition.
   *
   * @param <C>
   *          The type of client managed object configuration that
   *          this reference refers to.
   * @param <S>
   *          The type of server managed object configuration that
   *          this reference refers to.
   * @param p
   *          The path of the referenced managed object's parent.
   * @param rd
   *          The instantiable relation in the parent which contains
   *          the referenced managed object.
   * @param s
   *          The DN string value.
   * @return Returns the new reference based on the provided DN string
   *         value.
   * @throws IllegalArgumentException
   *           If the DN string value could not be decoded as a DN or
   *           if the provided DN did not correspond to the provided
   *           path and relation.
   */
  public static <C extends ConfigurationClient, S extends Configuration>
  Reference<C, S> parseDN(
      ManagedObjectPath<?, ?> p, InstantiableRelationDefinition<C, S> rd,
      String s) throws IllegalArgumentException {
    AbstractManagedObjectDefinition<?, ?> d = p.getManagedObjectDefinition();
    RelationDefinition<?, ?> tmp = d.getRelationDefinition(rd.getName());
    if (tmp != rd) {
      throw new IllegalArgumentException("The relation \"" + rd.getName()
          + "\" is not associated with the definition \"" + d.getName() + "\"");
    }

    DN dn;
    try {
      dn = DN.valueOf(s);
    } catch (DirectoryException e) {
      throw new IllegalArgumentException("Unabled to decode the DN string: \""
          + s + "\"");
    }

    RDN rdn = dn.rdn();
    if (rdn == null) {
      throw new IllegalArgumentException("Unabled to decode the DN string: \""
          + s + "\"");
    }

    ByteString av = rdn.getFirstAVA().getAttributeValue();
    if (av == null) {
      throw new IllegalArgumentException("Unabled to decode the DN string: \""
          + s + "\"");
    }

    String name = av.toString();

    // Check that the DN was valid.
    DN expected = p.child(rd, name).toDN();
    if (!dn.equals(expected)) {
      throw new IllegalArgumentException("Unabled to decode the DN string: \""
          + s + "\"");
    }

    return new Reference<>(p, rd, name);
  }



  /**
   * Parses a name as a reference using the provided managed object
   * path and relation definition.
   *
   * @param <C>
   *          The type of client managed object configuration that
   *          this reference refers to.
   * @param <S>
   *          The type of server managed object configuration that
   *          this reference refers to.
   * @param p
   *          The path of the referenced managed object's parent.
   * @param rd
   *          The instantiable relation in the parent which contains
   *          the referenced managed object.
   * @param s
   *          The name of the referenced managed object.
   * @return Returns the new reference based on the provided name.
   * @throws IllegalArgumentException
   *           If the relation is not associated with the provided
   *           parent's definition, or if the provided name is empty.
   */
  public static <C extends ConfigurationClient, S extends Configuration>
  Reference<C, S> parseName(
      ManagedObjectPath<?, ?> p, InstantiableRelationDefinition<C, S> rd,
      String s) throws IllegalArgumentException {
    // Sanity checks.
    AbstractManagedObjectDefinition<?, ?> d = p.getManagedObjectDefinition();
    RelationDefinition<?, ?> tmp = d.getRelationDefinition(rd.getName());
    if (tmp != rd) {
      throw new IllegalArgumentException("The relation \"" + rd.getName()
          + "\" is not associated with the definition \"" + d.getName() + "\"");
    }

    if (s.trim().length() == 0) {
      throw new IllegalArgumentException("Empty names are not allowed");
    }

    return new Reference<>(p, rd, s);
  }

  /** The name of the referenced managed object. */
  private final String name;

  /** The path of the referenced managed object. */
  private final ManagedObjectPath<C, S> path;

  /**
   * The instantiable relation in the parent which contains the
   * referenced managed object.
   */
  private final InstantiableRelationDefinition<C, S> relation;



  /** Private constructor. */
  private Reference(ManagedObjectPath<?, ?> parent,
      InstantiableRelationDefinition<C, S> relation, String name)
      throws IllegalArgumentException {
    this.relation = relation;
    this.name = name;
    this.path = parent.child(relation, name);
  }



  /**
   * Gets the name of the referenced managed object.
   *
   * @return Returns the name of the referenced managed object.
   */
  public String getName() {
    return name;
  }



  /**
   * Gets the normalized name of the referenced managed object.
   *
   * @return Returns the normalized name of the referenced managed
   *         object.
   */
  public String getNormalizedName() {
    PropertyDefinition<?> pd = relation.getNamingPropertyDefinition();
    return normalizeName(pd);
  }



  /**
   * Gets the DN of the referenced managed object.
   *
   * @return Returns the DN of the referenced managed object.
   */
  public DN toDN() {
    return path.toDN();
  }

  @Override
  public String toString() {
    return name;
  }



  /**
   * Normalize a value using the specified naming property definition
   * if defined.
   */
  private <T> String normalizeName(PropertyDefinition<T> pd) {
    if (pd != null) {
      try {
        T tvalue = pd.decodeValue(name);
        return pd.normalizeValue(tvalue);
      } catch (PropertyException e) {
        // Fall through to default normalization.
      }
    }

    // FIXME: should really use directory string normalizer.
    String s = name.trim().replaceAll(" +", " ");
    return StaticUtils.toLowerCase(s);
  }
}
