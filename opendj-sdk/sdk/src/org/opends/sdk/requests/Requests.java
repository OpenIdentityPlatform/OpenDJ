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

package org.opends.sdk.requests;



import static org.opends.messages.UtilityMessages.WARN_READ_LDIF_RECORD_CHANGE_RECORD_WRONG_TYPE;

import org.opends.messages.Message;
import org.opends.sdk.*;
import org.opends.sdk.ldif.ChangeRecord;
import org.opends.sdk.ldif.LDIFChangeRecordReader;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.LocalizedIllegalArgumentException;
import org.opends.sdk.util.Validator;



/**
 * This class contains various methods for creating and manipulating
 * requests.
 * <p>
 * TODO: search request from LDAP URL.
 * <p>
 * TODO: update request from persistent search result.
 * <p>
 * TODO: synchronized requests?
 * <p>
 * TODO: copy constructors.
 */
public final class Requests
{

  /**
   * Creates a new abandon request using the provided message ID.
   *
   * @param messageID
   *          The message ID of the request to be abandoned.
   * @return The new abandon request.
   */
  public static AbandonRequest newAbandonRequest(int messageID)
  {
    return new AbandonRequestImpl(messageID);
  }



  /**
   * Creates a new add request using the provided distinguished name.
   *
   * @param name
   *          The distinguished name of the entry to be added.
   * @return The new add request.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public static AddRequest newAddRequest(DN name)
      throws NullPointerException
  {
    final Entry entry = new SortedEntry().setName(name);
    return new AddRequestImpl(entry);
  }



  /**
   * Creates a new add request backed by the provided entry.
   * Modifications made to {@code entry} will be reflected in the
   * returned add request. The returned add request supports updates to
   * its list of controls, as well as updates to the name and attributes
   * if the underlying entry allows.
   *
   * @param entry
   *          The entry to be added.
   * @return The new add request.
   * @throws NullPointerException
   *           If {@code entry} was {@code null} .
   */
  public static AddRequest newAddRequest(Entry entry)
      throws NullPointerException
  {
    Validator.ensureNotNull(entry);
    return new AddRequestImpl(entry);
  }



  /**
   * Creates a new add request using the provided distinguished name
   * decoded using the default schema.
   *
   * @param name
   *          The distinguished name of the entry to be added.
   * @return The new add request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} could not be decoded using the default
   *           schema.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public static AddRequest newAddRequest(String name)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    final Entry entry = new SortedEntry().setName(name);
    return new AddRequestImpl(entry);
  }



  /**
   * Creates a new add request using the provided lines of LDIF decoded
   * using the default schema.
   *
   * @param ldifLines
   *          Lines of LDIF containing an LDIF add change record or an
   *          LDIF entry record.
   * @return The new add request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code ldifLines} was empty, or contained invalid
   *           LDIF, or could not be decoded using the default schema.
   * @throws NullPointerException
   *           If {@code ldifLines} was {@code null} .
   */
  public static AddRequest newAddRequest(String... ldifLines)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    // LDIF change record reader is tolerant to missing change types.
    ChangeRecord record = LDIFChangeRecordReader
        .valueOfLDIFChangeRecord(ldifLines);

    if (record instanceof AddRequest)
    {
      return (AddRequest) record;
    }
    else
    {
      // Wrong change type.
      Message message = WARN_READ_LDIF_RECORD_CHANGE_RECORD_WRONG_TYPE
          .get("add");
      throw new LocalizedIllegalArgumentException(message);
    }
  }



  /**
   * Creates a new change record (an add, delete, modify, or modify DN
   * request) using the provided lines of LDIF decoded using the default
   * schema.
   *
   * @param ldifLines
   *          Lines of LDIF containing an LDIF change record or an LDIF
   *          entry record.
   * @return The new change record.
   * @throws LocalizedIllegalArgumentException
   *           If {@code ldifLines} was empty, or contained invalid
   *           LDIF, or could not be decoded using the default schema.
   * @throws NullPointerException
   *           If {@code ldifLines} was {@code null} .
   */
  public static ChangeRecord newChangeRecord(String... ldifLines)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    // LDIF change record reader is tolerant to missing change types.
    return LDIFChangeRecordReader.valueOfLDIFChangeRecord(ldifLines);
  }



  /**
   * Creates a new compare request using the provided distinguished
   * name, attribute name, and assertion value.
   *
   * @param name
   *          The distinguished name of the entry to be compared.
   * @param attributeDescription
   *          The name of the attribute to be compared.
   * @param assertionValue
   *          The assertion value to be compared.
   * @return The new compare request.
   * @throws NullPointerException
   *           If {@code name}, {@code attributeDescription}, or {@code
   *           assertionValue} was {@code null}.
   */
  public static CompareRequest newCompareRequest(DN name,
      AttributeDescription attributeDescription,
      ByteString assertionValue) throws NullPointerException
  {
    Validator.ensureNotNull(name, attributeDescription, assertionValue);
    return new CompareRequestImpl(name, attributeDescription,
        assertionValue);
  }



  /**
   * Creates a new compare request using the provided distinguished
   * name, attribute name, and assertion value decoded using the default
   * schema.
   * <p>
   * If the assertion value is not an instance of {@code ByteString}
   * then it will be converted using the
   * {@link ByteString#valueOf(Object)} method.
   *
   * @param name
   *          The distinguished name of the entry to be compared.
   * @param attributeDescription
   *          The name of the attribute to be compared.
   * @param assertionValue
   *          The assertion value to be compared.
   * @return The new compare request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} or {@code attributeDescription} could not
   *           be decoded using the default schema.
   * @throws NullPointerException
   *           If {@code name}, {@code attributeDescription}, or {@code
   *           assertionValue} was {@code null}.
   */
  public static CompareRequest newCompareRequest(String name,
      String attributeDescription, Object assertionValue)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    Validator.ensureNotNull(name, attributeDescription, assertionValue);
    return new CompareRequestImpl(DN.valueOf(name),
        AttributeDescription.valueOf(attributeDescription), ByteString
            .valueOf(assertionValue));
  }



  /**
   * Creates a new delete request using the provided distinguished name.
   *
   * @param name
   *          The distinguished name of the entry to be deleted.
   * @return The new delete request.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public static DeleteRequest newDeleteRequest(DN name)
      throws NullPointerException
  {
    Validator.ensureNotNull(name);
    return new DeleteRequestImpl(name);
  }



  /**
   * Creates a new delete request using the provided distinguished name
   * decoded using the default schema.
   *
   * @param name
   *          The distinguished name of the entry to be deleted.
   * @return The new delete request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} could not be decoded using the default
   *           schema.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public static DeleteRequest newDeleteRequest(String name)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    Validator.ensureNotNull(name);
    return new DeleteRequestImpl(DN.valueOf(name));
  }



  /**
   * Creates a new generic bind request using an empty distinguished
   * name, authentication type, and authentication information.
   *
   * @param authenticationType
   *          The authentication mechanism identifier for this generic
   *          bind request.
   * @param authenticationValue
   *          The authentication information for this generic bind
   *          request in a form defined by the authentication mechanism.
   * @return The new generic bind request.
   * @throws NullPointerException
   *           If {@code authenticationValue} was {@code null}.
   */
  public static GenericBindRequest newGenericBindRequest(
      byte authenticationType, ByteString authenticationValue)
      throws NullPointerException
  {
    Validator.ensureNotNull(authenticationValue);
    return new GenericBindRequestImpl(DN.rootDN(), authenticationType,
        authenticationValue);
  }



  /**
   * Creates a new generic bind request using the provided distinguished
   * name, authentication type, and authentication information.
   *
   * @param name
   *          The distinguished name of the Directory object that the
   *          client wishes to bind as (may be empty).
   * @param authenticationType
   *          The authentication mechanism identifier for this generic
   *          bind request.
   * @param authenticationValue
   *          The authentication information for this generic bind
   *          request in a form defined by the authentication mechanism.
   * @return The new generic bind request.
   * @throws NullPointerException
   *           If {@code name} or {@code authenticationValue} was
   *           {@code null}.
   */
  public static GenericBindRequest newGenericBindRequest(DN name,
      byte authenticationType, ByteString authenticationValue)
      throws NullPointerException
  {
    Validator.ensureNotNull(name, authenticationValue);
    return new GenericBindRequestImpl(name, authenticationType,
        authenticationValue);
  }



  /**
   * Creates a new generic bind request using the provided distinguished
   * name, authentication type, and authentication information.
   *
   * @param name
   *          The distinguished name of the Directory object that the
   *          client wishes to bind as (may be empty).
   * @param authenticationType
   *          The authentication mechanism identifier for this generic
   *          bind request.
   * @param authenticationValue
   *          The authentication information for this generic bind
   *          request in a form defined by the authentication mechanism.
   * @return The new generic bind request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} could not be decoded using the default
   *           schema.
   * @throws NullPointerException
   *           If {@code name} or {@code authenticationValue} was
   *           {@code null}.
   */
  public static GenericBindRequest newGenericBindRequest(String name,
      byte authenticationType, ByteString authenticationValue)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    Validator.ensureNotNull(name, authenticationValue);
    return new GenericBindRequestImpl(DN.valueOf(name),
        authenticationType, authenticationValue);
  }



  /**
   * Creates a new generic extended request using the provided name and
   * no value.
   *
   * @param requestName
   *          The dotted-decimal representation of the unique OID
   *          corresponding to this extended request.
   * @return The new generic extended request.
   * @throws NullPointerException
   *           If {@code requestName} was {@code null}.
   */
  public static GenericExtendedRequest newGenericExtendedRequest(
      String requestName) throws NullPointerException
  {
    Validator.ensureNotNull(requestName);
    return new GenericExtendedRequestImpl(requestName, null);
  }



  /**
   * Creates a new generic extended request using the provided name and
   * optional value.
   *
   * @param requestName
   *          The dotted-decimal representation of the unique OID
   *          corresponding to this extended request.
   * @param requestValue
   *          The content of this generic extended request in a form
   *          defined by the extended operation, or {@code null} if
   *          there is no content.
   * @return The new generic extended request.
   * @throws NullPointerException
   *           If {@code requestName} was {@code null}.
   */
  public static GenericExtendedRequest newGenericExtendedRequest(
      String requestName, ByteString requestValue)
      throws NullPointerException
  {
    Validator.ensureNotNull(requestName);
    return new GenericExtendedRequestImpl(requestName, requestValue);
  }



  /**
   * Creates a new modify DN request using the provided distinguished
   * name and new RDN.
   *
   * @param name
   *          The distinguished name of the entry to be renamed.
   * @param newRDN
   *          The new RDN of the entry.
   * @return The new modify DN request.
   * @throws NullPointerException
   *           If {@code name} or {@code newRDN} was {@code null}.
   */
  public static ModifyDNRequest newModifyDNRequest(DN name, RDN newRDN)
      throws NullPointerException
  {
    Validator.ensureNotNull(name, newRDN);
    return new ModifyDNRequestImpl(name, newRDN);
  }



  /**
   * Creates a new modify DN request using the provided distinguished
   * name and new RDN decoded using the default schema.
   *
   * @param name
   *          The distinguished name of the entry to be renamed.
   * @param newRDN
   *          The new RDN of the entry.
   * @return The new modify DN request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} or {@code newRDN} could not be decoded
   *           using the default schema.
   * @throws NullPointerException
   *           If {@code name} or {@code newRDN} was {@code null}.
   */
  public static ModifyDNRequest newModifyDNRequest(String name,
      String newRDN) throws LocalizedIllegalArgumentException,
      NullPointerException
  {
    Validator.ensureNotNull(name, newRDN);
    return new ModifyDNRequestImpl(DN.valueOf(name), RDN
        .valueOf(newRDN));
  }



  /**
   * Creates a new modify request using the provided distinguished name.
   *
   * @param name
   *          The distinguished name of the entry to be modified.
   * @return The new modify request.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public static ModifyRequest newModifyRequest(DN name)
      throws NullPointerException
  {
    Validator.ensureNotNull(name);
    return new ModifyRequestImpl(name);
  }



  /**
   * Creates a new modify request using the provided distinguished name
   * decoded using the default schema.
   *
   * @param name
   *          The distinguished name of the entry to be modified.
   * @return The new modify request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} could not be decoded using the default
   *           schema.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public static ModifyRequest newModifyRequest(String name)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    Validator.ensureNotNull(name);
    return new ModifyRequestImpl(DN.valueOf(name));
  }



  /**
   * Creates a new modify request using the provided lines of LDIF
   * decoded using the default schema.
   *
   * @param ldifLines
   *          Lines of LDIF containing a single LDIF modify change
   *          record.
   * @return The new modify request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code ldifLines} was empty, or contained invalid
   *           LDIF, or could not be decoded using the default schema.
   * @throws NullPointerException
   *           If {@code ldifLines} was {@code null} .
   */
  public static ModifyRequest newModifyRequest(String... ldifLines)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    // LDIF change record reader is tolerant to missing change types.
    ChangeRecord record = LDIFChangeRecordReader
        .valueOfLDIFChangeRecord(ldifLines);

    if (record instanceof ModifyRequest)
    {
      return (ModifyRequest) record;
    }
    else
    {
      // Wrong change type.
      Message message = WARN_READ_LDIF_RECORD_CHANGE_RECORD_WRONG_TYPE
          .get("modify");
      throw new LocalizedIllegalArgumentException(message);
    }
  }



  /**
   * Creates a new search request using the provided distinguished name,
   * scope, and filter, decoded using the default schema.
   *
   * @param name
   *          The distinguished name of the base entry relative to which
   *          the search is to be performed.
   * @param scope
   *          The scope of the search.
   * @param filter
   *          The filter that defines the conditions that must be
   *          fulfilled in order for an entry to be returned.
   * @param attributeDescriptions
   *          The names of the attributes to be included with each
   *          entry.
   * @return The new search request.
   * @throws NullPointerException
   *           If the {@code name}, {@code scope}, or {@code filter}
   *           were {@code null}.
   */
  public static SearchRequest newSearchRequest(DN name,
      SearchScope scope, Filter filter, String... attributeDescriptions)
      throws NullPointerException
  {
    Validator.ensureNotNull(name, scope, filter);
    return new SearchRequestImpl(name, scope, filter)
        .addAttribute(attributeDescriptions);
  }



  /**
   * Creates a new search request using the provided distinguished name,
   * scope, and filter, decoded using the default schema.
   *
   * @param name
   *          The distinguished name of the base entry relative to which
   *          the search is to be performed.
   * @param scope
   *          The scope of the search.
   * @param filter
   *          The filter that defines the conditions that must be
   *          fulfilled in order for an entry to be returned.
   * @param attributeDescriptions
   *          The names of the attributes to be included with each
   *          entry.
   * @return The new search request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} could not be decoded using the default
   *           schema, or if {@code filter} is not a valid LDAP string
   *           representation of a filter.
   * @throws NullPointerException
   *           If the {@code name}, {@code scope}, or {@code filter}
   *           were {@code null}.
   */
  public static SearchRequest newSearchRequest(String name,
      SearchScope scope, String filter, String... attributeDescriptions)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    Validator.ensureNotNull(name, scope, filter);
    return new SearchRequestImpl(DN.valueOf(name), scope, Filter
        .valueOf(filter)).addAttribute(attributeDescriptions);
  }



  /**
   * Creates a new simple bind request having an empty name and password
   * suitable for anonymous authentication.
   *
   * @return The new simple bind request.
   */
  public static SimpleBindRequest newSimpleBindRequest()
  {
    return new SimpleBindRequestImpl(DN.rootDN(), ByteString.empty());
  }



  /**
   * Creates a new simple bind request having the provided name and
   * password suitable for name/password authentication.
   *
   * @param name
   *          The distinguished name of the Directory object that the
   *          client wishes to bind as, which may be empty.
   * @param password
   *          The password of the Directory object that the client
   *          wishes to bind as, which may be empty indicating that an
   *          unauthenticated bind is to be performed.
   * @return The new simple bind request.
   * @throws NullPointerException
   *           If {@code name} or {@code password} was {@code null}.
   */
  public static SimpleBindRequest newSimpleBindRequest(DN name,
      ByteString password) throws NullPointerException
  {
    Validator.ensureNotNull(name, password);
    return new SimpleBindRequestImpl(name, password);
  }



  /**
   * Creates a new simple bind request having the provided name and
   * password suitable for name/password authentication. The name will
   * be decoded using the default schema.
   *
   * @param name
   *          The distinguished name of the Directory object that the
   *          client wishes to bind as, which may be empty..
   * @param password
   *          The password of the Directory object that the client
   *          wishes to bind as, which may be empty indicating that an
   *          unauthenticated bind is to be performed.
   * @return The new simple bind request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} could not be decoded using the default
   *           schema.
   * @throws NullPointerException
   *           If {@code name} or {@code password} was {@code null}.
   */
  public static SimpleBindRequest newSimpleBindRequest(String name,
      String password) throws LocalizedIllegalArgumentException,
      NullPointerException
  {
    Validator.ensureNotNull(name, password);
    return new SimpleBindRequestImpl(DN.valueOf(name), ByteString
        .valueOf(password));
  }



  /**
   * Creates a new unbind request.
   *
   * @return The new unbind request.
   */
  public static UnbindRequest newUnbindRequest()
  {
    return new UnbindRequestImpl();
  }



  private Requests()
  {
    // Prevent instantiation.
  }
}
