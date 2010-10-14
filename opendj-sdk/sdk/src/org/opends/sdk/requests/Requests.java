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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package org.opends.sdk.requests;



import static com.sun.opends.sdk.messages.Messages.WARN_READ_LDIF_RECORD_CHANGE_RECORD_WRONG_TYPE;

import javax.net.ssl.SSLContext;
import javax.security.auth.Subject;

import org.opends.sdk.*;
import org.opends.sdk.ldif.ChangeRecord;
import org.opends.sdk.ldif.LDIFChangeRecordReader;

import com.sun.opends.sdk.util.Validator;



/**
 * This class contains various methods for creating and manipulating requests.
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
   * @param requestID
   *          The request ID of the request to be abandoned.
   * @return The new abandon request.
   */
  public static AbandonRequest newAbandonRequest(final int requestID)
  {
    return new AbandonRequestImpl(requestID);
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
  public static AddRequest newAddRequest(final DN name)
      throws NullPointerException
  {
    final Entry entry = new LinkedHashMapEntry().setName(name);
    return new AddRequestImpl(entry);
  }



  /**
   * Creates a new add request backed by the provided entry. Modifications made
   * to {@code entry} will be reflected in the returned add request. The
   * returned add request supports updates to its list of controls, as well as
   * updates to the name and attributes if the underlying entry allows.
   *
   * @param entry
   *          The entry to be added.
   * @return The new add request.
   * @throws NullPointerException
   *           If {@code entry} was {@code null} .
   */
  public static AddRequest newAddRequest(final Entry entry)
      throws NullPointerException
  {
    Validator.ensureNotNull(entry);
    return new AddRequestImpl(entry);
  }



  /**
   * Creates a new add request using the provided distinguished name decoded
   * using the default schema.
   *
   * @param name
   *          The distinguished name of the entry to be added.
   * @return The new add request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} could not be decoded using the default schema.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public static AddRequest newAddRequest(final String name)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    final Entry entry = new LinkedHashMapEntry().setName(name);
    return new AddRequestImpl(entry);
  }



  /**
   * Creates a new add request using the provided lines of LDIF decoded using
   * the default schema.
   *
   * @param ldifLines
   *          Lines of LDIF containing an LDIF add change record or an LDIF
   *          entry record.
   * @return The new add request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code ldifLines} was empty, or contained invalid LDIF, or
   *           could not be decoded using the default schema.
   * @throws NullPointerException
   *           If {@code ldifLines} was {@code null} .
   */
  public static AddRequest newAddRequest(final String... ldifLines)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    // LDIF change record reader is tolerant to missing change types.
    final ChangeRecord record = LDIFChangeRecordReader
        .valueOfLDIFChangeRecord(ldifLines);

    if (record instanceof AddRequest)
    {
      return (AddRequest) record;
    }
    else
    {
      // Wrong change type.
      final LocalizableMessage message = WARN_READ_LDIF_RECORD_CHANGE_RECORD_WRONG_TYPE
          .get("add");
      throw new LocalizedIllegalArgumentException(message);
    }
  }



  /**
   * Creates a new anonymous SASL bind request having the provided trace string.
   *
   * @param traceString
   *          The trace information, which has no semantic value, and can be
   *          used by administrators in order to identify the user.
   * @return The new anonymous SASL bind request.
   * @throws NullPointerException
   *           If {@code traceString} was {@code null}.
   */
  public static AnonymousSASLBindRequest newAnonymousSASLBindRequest(
      final String traceString) throws NullPointerException
  {
    return new AnonymousSASLBindRequestImpl(traceString);
  }



  /**
   * Creates a new cancel extended request using the provided message ID.
   *
   * @param requestID
   *          The request ID of the request to be abandoned.
   * @return The new cancel extended request.
   */
  public static CancelExtendedRequest newCancelExtendedRequest(
      final int requestID)
  {
    return new CancelExtendedRequestImpl(requestID);
  }



  /**
   * Creates a new change record (an add, delete, modify, or modify DN request)
   * using the provided lines of LDIF decoded using the default schema.
   *
   * @param ldifLines
   *          Lines of LDIF containing an LDIF change record or an LDIF entry
   *          record.
   * @return The new change record.
   * @throws LocalizedIllegalArgumentException
   *           If {@code ldifLines} was empty, or contained invalid LDIF, or
   *           could not be decoded using the default schema.
   * @throws NullPointerException
   *           If {@code ldifLines} was {@code null} .
   */
  public static ChangeRecord newChangeRecord(final String... ldifLines)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    // LDIF change record reader is tolerant to missing change types.
    return LDIFChangeRecordReader.valueOfLDIFChangeRecord(ldifLines);
  }



  /**
   * Creates a new compare request using the provided distinguished name,
   * attribute name, and assertion value.
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
  public static CompareRequest newCompareRequest(final DN name,
      final AttributeDescription attributeDescription,
      final ByteString assertionValue) throws NullPointerException
  {
    Validator.ensureNotNull(name, attributeDescription, assertionValue);
    return new CompareRequestImpl(name, attributeDescription, assertionValue);
  }



  /**
   * Creates a new compare request using the provided distinguished name,
   * attribute name, and assertion value decoded using the default schema.
   * <p>
   * If the assertion value is not an instance of {@code ByteString} then it
   * will be converted using the {@link ByteString#valueOf(Object)} method.
   *
   * @param name
   *          The distinguished name of the entry to be compared.
   * @param attributeDescription
   *          The name of the attribute to be compared.
   * @param assertionValue
   *          The assertion value to be compared.
   * @return The new compare request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} or {@code attributeDescription} could not be
   *           decoded using the default schema.
   * @throws NullPointerException
   *           If {@code name}, {@code attributeDescription}, or {@code
   *           assertionValue} was {@code null}.
   */
  public static CompareRequest newCompareRequest(final String name,
      final String attributeDescription, final Object assertionValue)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    Validator.ensureNotNull(name, attributeDescription, assertionValue);
    return new CompareRequestImpl(DN.valueOf(name), AttributeDescription
        .valueOf(attributeDescription), ByteString.valueOf(assertionValue));
  }



  /**
   * Creates a new CRAM-MD5 SASL bind request having the provided authentication
   * ID and password.
   *
   * @param authenticationID
   *          The authentication ID of the user. The authentication ID usually
   *          has the form "dn:" immediately followed by the distinguished name
   *          of the user, or "u:" followed by a user ID string, but other forms
   *          are permitted.
   * @param password
   *          The password of the user that the client wishes to bind as. The
   *          password will be converted to a UTF-8 octet string.
   * @return The new CRAM-MD5 SASL bind request.
   * @throws NullPointerException
   *           If {@code authenticationID} or {@code password} was {@code null}.
   */
  public static CRAMMD5SASLBindRequest newCRAMMD5SASLBindRequest(
      final String authenticationID, final ByteString password)
      throws NullPointerException
  {
    return new CRAMMD5SASLBindRequestImpl(authenticationID, password);
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
  public static DeleteRequest newDeleteRequest(final DN name)
      throws NullPointerException
  {
    Validator.ensureNotNull(name);
    return new DeleteRequestImpl(name);
  }



  /**
   * Creates a new delete request using the provided distinguished name decoded
   * using the default schema.
   *
   * @param name
   *          The distinguished name of the entry to be deleted.
   * @return The new delete request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} could not be decoded using the default schema.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public static DeleteRequest newDeleteRequest(final String name)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    Validator.ensureNotNull(name);
    return new DeleteRequestImpl(DN.valueOf(name));
  }



  /**
   * Creates a new DIGEST-MD5 SASL bind request having the provided
   * authentication ID and password, but no realm or authorization ID.
   *
   * @param authenticationID
   *          The authentication ID of the user. The authentication ID usually
   *          has the form "dn:" immediately followed by the distinguished name
   *          of the user, or "u:" followed by a user ID string, but other forms
   *          are permitted.
   * @param password
   *          The password of the user that the client wishes to bind as. The
   *          password will be converted to a UTF-8 octet string.
   * @return The new DIGEST-MD5 SASL bind request.
   * @throws NullPointerException
   *           If {@code authenticationID} or {@code password} was {@code null}.
   */
  public static DigestMD5SASLBindRequest newDigestMD5SASLBindRequest(
      final String authenticationID, final ByteString password)
      throws NullPointerException
  {
    return new DigestMD5SASLBindRequestImpl(authenticationID, password);
  }



  /**
   * Creates a new External SASL bind request with no authorization ID.
   *
   * @return The new External SASL bind request.
   */
  public static ExternalSASLBindRequest newExternalSASLBindRequest()
  {
    return new ExternalSASLBindRequestImpl();
  }



  /**
   * Creates a new generic bind request using an empty distinguished name,
   * authentication type, and authentication information.
   *
   * @param authenticationType
   *          The authentication mechanism identifier for this generic bind
   *          request.
   * @param authenticationValue
   *          The authentication information for this generic bind request in a
   *          form defined by the authentication mechanism.
   * @return The new generic bind request.
   * @throws NullPointerException
   *           If {@code authenticationValue} was {@code null}.
   */
  public static GenericBindRequest newGenericBindRequest(
      final byte authenticationType, final ByteString authenticationValue)
      throws NullPointerException
  {
    Validator.ensureNotNull(authenticationValue);
    return new GenericBindRequestImpl("", authenticationType,
        authenticationValue);
  }



  /**
   * Creates a new generic bind request using the provided name, authentication
   * type, and authentication information.
   * <p>
   * The LDAP protocol defines the Bind name to be a distinguished name, however
   * some LDAP implementations have relaxed this constraint and allow other
   * identities to be used, such as the user's email address.
   *
   * @param name
   *          The name of the Directory object that the client wishes to bind as
   *          (may be empty).
   * @param authenticationType
   *          The authentication mechanism identifier for this generic bind
   *          request.
   * @param authenticationValue
   *          The authentication information for this generic bind request in a
   *          form defined by the authentication mechanism.
   * @return The new generic bind request.
   * @throws NullPointerException
   *           If {@code name} or {@code authenticationValue} was {@code null}.
   */
  public static GenericBindRequest newGenericBindRequest(final String name,
      final byte authenticationType, final ByteString authenticationValue)
      throws NullPointerException
  {
    Validator.ensureNotNull(name, authenticationValue);
    return new GenericBindRequestImpl(name, authenticationType,
        authenticationValue);
  }



  /**
   * Creates a new generic extended request using the provided name and no
   * value.
   *
   * @param requestName
   *          The dotted-decimal representation of the unique OID corresponding
   *          to this extended request.
   * @return The new generic extended request.
   * @throws NullPointerException
   *           If {@code requestName} was {@code null}.
   */
  public static GenericExtendedRequest newGenericExtendedRequest(
      final String requestName) throws NullPointerException
  {
    Validator.ensureNotNull(requestName);
    return new GenericExtendedRequestImpl(requestName, null);
  }



  /**
   * Creates a new generic extended request using the provided name and optional
   * value.
   *
   * @param requestName
   *          The dotted-decimal representation of the unique OID corresponding
   *          to this extended request.
   * @param requestValue
   *          The content of this generic extended request in a form defined by
   *          the extended operation, or {@code null} if there is no content.
   * @return The new generic extended request.
   * @throws NullPointerException
   *           If {@code requestName} was {@code null}.
   */
  public static GenericExtendedRequest newGenericExtendedRequest(
      final String requestName, final ByteString requestValue)
      throws NullPointerException
  {
    Validator.ensureNotNull(requestName);
    return new GenericExtendedRequestImpl(requestName, requestValue);
  }



  /**
   * Creates a new GSSAPI SASL bind request having the provided authentication
   * ID and password, but no realm, KDC address, or authorization ID.
   *
   * @param authenticationID
   *          The authentication ID of the user. The authentication ID usually
   *          has the form "dn:" immediately followed by the distinguished name
   *          of the user, or "u:" followed by a user ID string, but other forms
   *          are permitted.
   * @param password
   *          The password of the user that the client wishes to bind as. The
   *          password will be converted to a UTF-8 octet string.
   * @return The new GSSAPI SASL bind request.
   * @throws NullPointerException
   *           If {@code authenticationID} or {@code password} was {@code null}.
   */
  public static GSSAPISASLBindRequest newGSSAPISASLBindRequest(
      final String authenticationID, final ByteString password)
      throws NullPointerException
  {
    return new GSSAPISASLBindRequestImpl(authenticationID, password);
  }



  /**
   * Creates a new GSSAPI SASL bind request having the provided subject, but no
   * authorization ID.
   *
   * @param subject
   *          The Kerberos subject of the user to be authenticated.
   * @return The new GSSAPI SASL bind request.
   * @throws NullPointerException
   *           If {@code subject} was {@code null}.
   */
  public static GSSAPISASLBindRequest newGSSAPISASLBindRequest(
      final Subject subject) throws NullPointerException
  {
    return new GSSAPISASLBindRequestImpl(subject);
  }



  /**
   * Creates a new modify DN request using the provided distinguished name and
   * new RDN.
   *
   * @param name
   *          The distinguished name of the entry to be renamed.
   * @param newRDN
   *          The new RDN of the entry.
   * @return The new modify DN request.
   * @throws NullPointerException
   *           If {@code name} or {@code newRDN} was {@code null}.
   */
  public static ModifyDNRequest newModifyDNRequest(final DN name,
      final RDN newRDN) throws NullPointerException
  {
    Validator.ensureNotNull(name, newRDN);
    return new ModifyDNRequestImpl(name, newRDN);
  }



  /**
   * Creates a new modify DN request using the provided distinguished name and
   * new RDN decoded using the default schema.
   *
   * @param name
   *          The distinguished name of the entry to be renamed.
   * @param newRDN
   *          The new RDN of the entry.
   * @return The new modify DN request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} or {@code newRDN} could not be decoded using the
   *           default schema.
   * @throws NullPointerException
   *           If {@code name} or {@code newRDN} was {@code null}.
   */
  public static ModifyDNRequest newModifyDNRequest(final String name,
      final String newRDN) throws LocalizedIllegalArgumentException,
      NullPointerException
  {
    Validator.ensureNotNull(name, newRDN);
    return new ModifyDNRequestImpl(DN.valueOf(name), RDN.valueOf(newRDN));
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
  public static ModifyRequest newModifyRequest(final DN name)
      throws NullPointerException
  {
    Validator.ensureNotNull(name);
    return new ModifyRequestImpl(name);
  }



  /**
   * Creates a new modify request containing a list of modifications which can
   * be used to transform {@code fromEntry} into entry {@code toEntry}.
   * <p>
   * The modify request is reversible: it will contain only modifications of
   * type {@link ModificationType#ADD ADD} and {@link ModificationType#DELETE
   * DELETE}.
   * <p>
   * Finally, the modify request will use the distinguished name taken from
   * {@code fromEntry}. Moreover, this method will not check to see if both
   * {@code fromEntry} and {@code toEntry} have the same distinguished name.
   * <p>
   * This method is equivalent to:
   *
   * <pre>
   * ModifyRequest request = Entries.diffEntries(fromEntry, toEntry);
   * </pre>
   *
   * @param fromEntry
   *          The source entry.
   * @param toEntry
   *          The destination entry.
   * @return A modify request containing a list of modifications which can be
   *         used to transform {@code fromEntry} into entry {@code toEntry}.
   * @throws NullPointerException
   *           If {@code fromEntry} or {@code toEntry} were {@code null}.
   * @see Entries#diffEntries(Entry, Entry)
   */
  public static final ModifyRequest newModifyRequest(Entry fromEntry,
      Entry toEntry) throws NullPointerException
  {
    return Entries.diffEntries(fromEntry, toEntry);
  }



  /**
   * Creates a new modify request using the provided distinguished name decoded
   * using the default schema.
   *
   * @param name
   *          The distinguished name of the entry to be modified.
   * @return The new modify request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} could not be decoded using the default schema.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public static ModifyRequest newModifyRequest(final String name)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    Validator.ensureNotNull(name);
    return new ModifyRequestImpl(DN.valueOf(name));
  }



  /**
   * Creates a new modify request using the provided lines of LDIF decoded using
   * the default schema.
   *
   * @param ldifLines
   *          Lines of LDIF containing a single LDIF modify change record.
   * @return The new modify request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code ldifLines} was empty, or contained invalid LDIF, or
   *           could not be decoded using the default schema.
   * @throws NullPointerException
   *           If {@code ldifLines} was {@code null} .
   */
  public static ModifyRequest newModifyRequest(final String... ldifLines)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    // LDIF change record reader is tolerant to missing change types.
    final ChangeRecord record = LDIFChangeRecordReader
        .valueOfLDIFChangeRecord(ldifLines);

    if (record instanceof ModifyRequest)
    {
      return (ModifyRequest) record;
    }
    else
    {
      // Wrong change type.
      final LocalizableMessage message = WARN_READ_LDIF_RECORD_CHANGE_RECORD_WRONG_TYPE
          .get("modify");
      throw new LocalizedIllegalArgumentException(message);
    }
  }



  /**
   * Creates a new password modify extended request, with no user identity, old
   * password, or new password.
   *
   * @return The new password modify extended request.
   */
  public static PasswordModifyExtendedRequest newPasswordModifyExtendedRequest()
  {
    return new PasswordModifyExtendedRequestImpl();
  }



  /**
   * Creates a new Plain SASL bind request having the provided authentication ID
   * and password, but no authorization ID.
   *
   * @param authenticationID
   *          The authentication ID of the user. The authentication ID usually
   *          has the form "dn:" immediately followed by the distinguished name
   *          of the user, or "u:" followed by a user ID string, but other forms
   *          are permitted.
   * @param password
   *          The password of the user that the client wishes to bind as. The
   *          password will be converted to a UTF-8 octet string.
   * @return The new Plain SASL bind request.
   * @throws NullPointerException
   *           If {@code authenticationID} or {@code password} was {@code null}.
   */
  public static PlainSASLBindRequest newPlainSASLBindRequest(
      final String authenticationID, final ByteString password)
      throws NullPointerException
  {
    return new PlainSASLBindRequestImpl(authenticationID, password);
  }



  /**
   * Creates a new search request using the provided distinguished name, scope,
   * and filter, decoded using the default schema.
   *
   * @param name
   *          The distinguished name of the base entry relative to which the
   *          search is to be performed.
   * @param scope
   *          The scope of the search.
   * @param filter
   *          The filter that defines the conditions that must be fulfilled in
   *          order for an entry to be returned.
   * @param attributeDescriptions
   *          The names of the attributes to be included with each entry.
   * @return The new search request.
   * @throws NullPointerException
   *           If the {@code name}, {@code scope}, or {@code filter} were
   *           {@code null}.
   */
  public static SearchRequest newSearchRequest(final DN name,
      final SearchScope scope, final Filter filter,
      final String... attributeDescriptions) throws NullPointerException
  {
    Validator.ensureNotNull(name, scope, filter);
    final SearchRequest request = new SearchRequestImpl(name, scope, filter);
    for (final String attributeDescription : attributeDescriptions)
    {
      request.addAttribute(attributeDescription);
    }
    return request;
  }



  /**
   * Creates a new search request using the provided distinguished name, scope,
   * and filter, decoded using the default schema.
   *
   * @param name
   *          The distinguished name of the base entry relative to which the
   *          search is to be performed.
   * @param scope
   *          The scope of the search.
   * @param filter
   *          The filter that defines the conditions that must be fulfilled in
   *          order for an entry to be returned.
   * @param attributeDescriptions
   *          The names of the attributes to be included with each entry.
   * @return The new search request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} could not be decoded using the default schema, or
   *           if {@code filter} is not a valid LDAP string representation of a
   *           filter.
   * @throws NullPointerException
   *           If the {@code name}, {@code scope}, or {@code filter} were
   *           {@code null}.
   */
  public static SearchRequest newSearchRequest(final String name,
      final SearchScope scope, final String filter,
      final String... attributeDescriptions)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    Validator.ensureNotNull(name, scope, filter);
    final SearchRequest request = new SearchRequestImpl(DN.valueOf(name),
        scope, Filter.valueOf(filter));
    for (final String attributeDescription : attributeDescriptions)
    {
      request.addAttribute(attributeDescription);
    }
    return request;
  }



  /**
   * Creates a new simple bind request having an empty name and password
   * suitable for anonymous authentication.
   *
   * @return The new simple bind request.
   */
  public static SimpleBindRequest newSimpleBindRequest()
  {
    return new SimpleBindRequestImpl("", ByteString.empty());
  }



  /**
   * Creates a new simple bind request having the provided name and password
   * suitable for name/password authentication. The name will be decoded using
   * the default schema.
   * <p>
   * The LDAP protocol defines the Bind name to be a distinguished name, however
   * some LDAP implementations have relaxed this constraint and allow other
   * identities to be used, such as the user's email address.
   *
   * @param name
   *          The name of the Directory object that the client wishes to bind
   *          as, which may be empty.
   * @param password
   *          The password of the Directory object that the client wishes to
   *          bind as, which may be empty indicating that an unauthenticated
   *          bind is to be performed.
   * @return The new simple bind request.
   * @throws NullPointerException
   *           If {@code name} or {@code password} was {@code null}.
   */
  public static SimpleBindRequest newSimpleBindRequest(final String name,
      final String password) throws NullPointerException
  {
    Validator.ensureNotNull(name, password);
    return new SimpleBindRequestImpl(name, ByteString.valueOf(password));
  }



  /**
   * Creates a new start TLS extended request which will use the provided SSL
   * context.
   *
   * @param sslContext
   *          The SSLContext that should be used when installing the TLS layer.
   * @return The new start TLS extended request.
   * @throws NullPointerException
   *           If {@code sslContext} was {@code null}.
   */
  public static StartTLSExtendedRequest newStartTLSExtendedRequest(
      final SSLContext sslContext) throws NullPointerException
  {
    return new StartTLSExtendedRequestImpl(sslContext);
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



  /**
   * Creates a new Who Am I extended request.
   *
   * @return The new Who Am I extended request.
   */
  public static WhoAmIExtendedRequest newWhoAmIExtendedRequest()
  {
    return new WhoAmIExtendedRequestImpl();
  }



  private Requests()
  {
    // Prevent instantiation.
  }
}
