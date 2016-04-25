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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.authorization.dseecompat;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.SearchFilter;

import static org.opends.messages.AccessControlMessages.*;

/** This class represents the userdn keyword in a bind rule. */
public class UserDN implements KeywordBindRule {
    /** A dummy URL for invalid URLs such as: all, parent, anyone, self. */
    private static final String URL_STR = "ldap:///";

    /** This list holds a list of objects representing a EnumUserDNType URL mapping. */
    private final List<UserDNTypeURL> urlList;
    /** Enumeration of the userdn operation type. */
    private final EnumBindRuleType type;

    /**
     * Constructor that creates the userdn class. It also sets up an attribute
     * type ("userdn") needed  for wild-card matching.
     * @param type The type of  operation.
     * @param urlList  A list of enumerations containing the URL type and URL
     * object that can be retrieved at evaluation time.
     */
    private UserDN(EnumBindRuleType type, List<UserDNTypeURL> urlList) {
       this.type=type;
       this.urlList=urlList;
    }

    /**
     * Decodes an expression string representing a userdn bind rule.
     * @param expression The string representation of the userdn bind rule
     * expression.
     * @param type An enumeration of the type of the bind rule.
     * @return A KeywordBindRule class that represents the bind rule.
     * @throws AciException If the expression failed to LDAP URL decode.
     */
    public static KeywordBindRule decode(String expression,
            EnumBindRuleType type) throws AciException {
        String[] vals=expression.split("[|][|]");
        List<UserDNTypeURL> urlList = new LinkedList<>();
        for (String val : vals)
        {
            StringBuilder value = new StringBuilder(val.trim());
           /*
            * TODO Evaluate using a wild-card in the dn portion of LDAP url.
            * The current implementation (DS6) does not treat a "*"
            * as a wild-card.
            *
            * Is it allowed to have a full LDAP URL (i.e., including a base,
            * scope, and filter) in which the base DN contains asterisks to
            * make it a wildcard?  If so, then I don't think that the current
            * implementation handles that correctly.  It will probably fail
            * when attempting to create the LDAP URL because the base DN isn't a
            * valid DN.
            */
            EnumUserDNType userDNType = UserDN.getType(value);
            LDAPURL url;
            try {
               url=LDAPURL.decode(value.toString(), true);
            } catch (LocalizedIllegalArgumentException | DirectoryException e) {
                throw new AciException(WARN_ACI_SYNTAX_INVALID_USERDN_URL.get(e.getMessageObject()));
            }
            urlList.add(new UserDNTypeURL(userDNType, url));
        }
        return new UserDN(type, urlList);
      }

    /**
     * This method determines the type of the DN (suffix in URL terms)
     * part of a URL, by examining the full URL itself for known strings
     * such as (corresponding type shown in parenthesis)
     *
     *      "ldap:///anyone"    (EnumUserDNType.ANYONE)
     *      "ldap:///parent"    (EnumUserDNType.PARENT)
     *      "ldap:///all"       (EnumUserDNType.ALL)
     *      "ldap:///self"      (EnumUserDNType.SELF)
     *
     * If one of the four above are found, the URL is replaced with a dummy
     * pattern "ldap:///". This is done because the above four are invalid
     * URLs; but the syntax is valid for an userdn keyword expression. The
     * dummy URLs are never used.
     *
     * If none of the above are found, it determine if the URL DN is a
     * substring pattern, such as:
     *
     *      "ldap:///uid=*, dc=example, dc=com" (EnumUserDNType.PATTERN)
     *
     * If none of the above are determined, it checks if the URL
     * is a complete URL with scope and filter defined:
     *
     *  "ldap:///uid=test,dc=example,dc=com??sub?(cn=j*)"  (EnumUserDNType.URL)
     *
     * If none of these those types can be identified, it defaults to
     * EnumUserDNType.DN.
     *
     * @param bldr A string representation of the URL that can be modified.
     * @return  The user DN type of the URL.
     */
    private static EnumUserDNType getType(StringBuilder bldr) {
        String str=bldr.toString();
        if (str.contains("?")) {
            return EnumUserDNType.URL;
        } else  if(str.equalsIgnoreCase("ldap:///self")) {
            bldr.replace(0, bldr.length(), URL_STR);
            return EnumUserDNType.SELF;
        } else if(str.equalsIgnoreCase("ldap:///anyone")) {
            bldr.replace(0, bldr.length(), URL_STR);
            return EnumUserDNType.ANYONE;
        } else if(str.equalsIgnoreCase("ldap:///parent")) {
            bldr.replace(0, bldr.length(), URL_STR);
            return EnumUserDNType.PARENT;
        } else if(str.equalsIgnoreCase("ldap:///all")) {
            bldr.replace(0, bldr.length(), URL_STR);
            return EnumUserDNType.ALL;
        } else if (str.contains("*")) {
            return EnumUserDNType.DNPATTERN;
        } else {
            return EnumUserDNType.DN;
        }
    }

    /**
     * Performs the evaluation of a userdn bind rule based on the
     * evaluation context passed to it. The evaluation stops when there
     * are no more UserDNTypeURLs to evaluate or if an UserDNTypeURL
     * evaluates to true.
     * @param evalCtx The evaluation context to evaluate with.
     * @return  An evaluation result enumeration containing the result
     * of the evaluation.
     */
    @Override
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult matched = EnumEvalResult.FALSE;
        boolean undefined=false;

        boolean isAnonUser=evalCtx.isAnonymousUser();
        Iterator<UserDNTypeURL> it=urlList.iterator();
        for(; it.hasNext() && matched != EnumEvalResult.TRUE &&
                matched != EnumEvalResult.ERR;) {
            UserDNTypeURL dnTypeURL=it.next();
            //Handle anonymous checks here
            if(isAnonUser) {
                if(dnTypeURL.getUserDNType() == EnumUserDNType.ANYONE)
                {
                  matched = EnumEvalResult.TRUE;
                }
            }
            else
            {
              matched=evalNonAnonymous(evalCtx, dnTypeURL);
            }
        }
        return matched.getRet(type, undefined);
    }

    /**
     * Performs an evaluation of a single UserDNTypeURL of a userdn bind
     * rule using the evaluation context provided. This method is called
     * for the non-anonymous user case.
     * @param evalCtx  The evaluation context to evaluate with.
     * @param dnTypeURL The URL dn type mapping to evaluate.
     * @return An evaluation result enumeration containing the result
     * of the evaluation.
     */
    private EnumEvalResult evalNonAnonymous(AciEvalContext evalCtx,
                                            UserDNTypeURL dnTypeURL) {
        return evalNonAnonymous0(evalCtx, dnTypeURL) ? EnumEvalResult.TRUE : EnumEvalResult.FALSE;
    }

    private boolean evalNonAnonymous0(AciEvalContext evalCtx,
                                            UserDNTypeURL dnTypeURL) {
        DN clientDN=evalCtx.getClientDN();
        DN resDN=evalCtx.getResourceDN();
        EnumUserDNType type=dnTypeURL.getUserDNType();
        LDAPURL url=dnTypeURL.getURL();
        switch (type) {
            case URL:
                return evalURL0(evalCtx, url);
            case ANYONE:
            case ALL:
                return true;
            case SELF:
                return clientDN.equals(resDN);
            case PARENT:
                DN parentDN = resDN.parent();
                return parentDN != null && parentDN.equals(clientDN);
            case DNPATTERN:
                return evalDNPattern(evalCtx, url);
            case DN:
                return evalDN(clientDN, url);
            default:
                return false;
        }
    }

    private boolean evalDN(DN clientDN, LDAPURL url)
    {
      try
      {
          DN dn = url.getBaseDN();
          if (clientDN.equals(dn))
          {
            return true;
          }

          // This code handles the case where a root dn entry does
          // not have bypass-acl privilege and the ACI bind rule
          // userdn DN possible is an alternate root DN.
          DN actualDN = DirectoryServer.getActualRootBindDN(dn);
          DN clientActualDN = DirectoryServer.getActualRootBindDN(clientDN);
          if (actualDN != null)
          {
            dn = actualDN;
          }
          if (clientActualDN != null)
          {
            clientDN = clientActualDN;
          }
          return clientDN.equals(dn);
      } catch (DirectoryException ex) {
          //TODO add message
          return false;
      }
    }

    /**
     * This method evaluates a DN pattern userdn expression.
     * @param evalCtx  The evaluation context to use.
     * @param url The LDAP URL containing the pattern.
     * @return An enumeration evaluation result.
     */
    private boolean evalDNPattern(AciEvalContext evalCtx, LDAPURL url) {
        PatternDN pattern;
        try {
          pattern = PatternDN.decode(url.getRawBaseDN());
        } catch (DirectoryException ex) {
          return false;
        }

        return pattern.matchesDN(evalCtx.getClientDN());
    }


    /**
     * This method evaluates an URL userdn expression. Something like:
     * ldap:///suffix??sub?(filter). It also searches for the client DN
     * entry and saves it in the evaluation context for repeat evaluations
     * that might come later in processing.
     *
     * @param evalCtx  The evaluation context to use.
     * @param url URL containing the URL to use in the evaluation.
     * @return An enumeration of the evaluation result.
     */
    public static EnumEvalResult evalURL(AciEvalContext evalCtx, LDAPURL url) {
        return evalURL0(evalCtx, url) ? EnumEvalResult.TRUE : EnumEvalResult.FALSE;
    }

    private static boolean evalURL0(AciEvalContext evalCtx, LDAPURL url) {
        DN urlDN;
        SearchFilter filter;
        try {
            urlDN=url.getBaseDN();
            filter=url.getFilter();
        } catch (DirectoryException ex) {
            return false;
        }
        SearchScope scope=url.getScope();
        if(scope == SearchScope.WHOLE_SUBTREE) {
            if(!evalCtx.getClientDN().isSubordinateOrEqualTo(urlDN))
            {
              return false;
            }
        } else if(scope == SearchScope.SINGLE_LEVEL) {
            DN parent=evalCtx.getClientDN().parent();
            if(parent != null && !parent.equals(urlDN))
            {
              return false;
            }
        } else if(scope == SearchScope.SUBORDINATES) {
            DN userDN = evalCtx.getClientDN();
            if (userDN.size() <= urlDN.size() ||
                 !userDN.isSubordinateOrEqualTo(urlDN)) {
              return false;
            }
        } else {
            if(!evalCtx.getClientDN().equals(urlDN))
            {
              return false;
            }
        }
        try {
            return (filter.matchesEntry(evalCtx.getClientEntry()));
        } catch (DirectoryException ex) {
            return false;
        }
    }

    /*
     * TODO Evaluate making this method more efficient.
     *
     * The evalDNEntryAttr method isn't as efficient as it could be.
     * It would probably be faster to to convert the clientDN to a ByteString
     * and see if the entry has that value than to decode each value as a DN
     * and see if it matches the clientDN.
     */
    /**
     * This method searches an entry for an attribute value that is
     * treated as a DN. That DN is then compared against the client
     * DN.
     * @param e The entry to get the attribute type from.
     * @param clientDN The client authorization DN to check for.
     * @param attrType The attribute type from the bind rule.
     * @return An enumeration with the result.
     */
    public static boolean evaluate(Entry e, DN clientDN,
                                           AttributeType attrType) {
        List<Attribute> attrs =  e.getAttribute(attrType);
        for(ByteString v : attrs.get(0)) {
            try {
                DN dn = DN.valueOf(v.toString());
                if(dn.equals(clientDN)) {
                    return true;
                }
            } catch (LocalizedIllegalArgumentException ignored) {
                break;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        toString(sb);
        return sb.toString();
    }

    @Override
    public final void toString(StringBuilder buffer) {
        buffer.append("userdn");
        buffer.append(this.type.getType());
        for (UserDNTypeURL url : this.urlList) {
            buffer.append("\"");
            buffer.append(URL_STR);
            buffer.append(url.getUserDNType().toString().toLowerCase());
            buffer.append("\"");
        }
    }
}
