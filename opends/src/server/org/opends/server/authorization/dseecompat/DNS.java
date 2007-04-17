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

package org.opends.server.authorization.dseecompat;

import static org.opends.server.messages.AciMessages.*;
import static org.opends.server.authorization.dseecompat.Aci.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.util.StaticUtils.*;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;

/**
 * This class implements the dns bind rule keyword.
 */
public class DNS implements KeywordBindRule {

    /*
     * List of patterns to match against.
     */
    LinkedList<String> patterns=null;

    /*
     * The enumeration representing the bind rule type of the DNS rule.
     */
    private EnumBindRuleType type=null;

    /*
     *  Regular expression group used to match a dns rule.
     */
    private static final String valueRegex = "([a-zA-Z0-9\\.\\-\\*]+)";

    /*
     * Regular expression group used to match one or more DNS values.
     */
    private static final String valuesRegExGroup =
            valueRegex + ZERO_OR_MORE_WHITESPACE +
            "(," +  ZERO_OR_MORE_WHITESPACE  +  valueRegex  +  ")*";

    /**
     * Create a class representing a dns bind rule keyword.
     * @param patterns List of dns patterns to match against.
     * @param type An enumeration representing the bind rule type.
     */
    DNS(LinkedList<String> patterns, EnumBindRuleType type) {
        this.patterns=patterns;
        this.type=type;
    }

    /**
     * Decode an string representing a dns bind rule.
     * @param expr A string representation of the bind rule.
     * @param type  An enumeration representing the bind rule type.
     * @return  A keyword bind rule class that can be used to evaluate
     * this bind rule.
     * @throws AciException  If the expression string is invalid.
     */
    public static DNS decode(String expr,  EnumBindRuleType type)
    throws AciException
    {
        if (!Pattern.matches(valuesRegExGroup, expr)) {
            int msgID = MSGID_ACI_SYNTAX_INVALID_DNS_EXPRESSION;
            String message = getMessage(msgID, expr);
            throw new AciException(msgID, message);
        }
        LinkedList<String>dns=new LinkedList<String>();
        int valuePos = 1;
        Pattern valuePattern = Pattern.compile(valueRegex);
        Matcher valueMatcher = valuePattern.matcher(expr);
        while (valueMatcher.find()) {
            String hn=valueMatcher.group(valuePos);
            String[] hnArray=hn.split("\\.", -1);
            for(int i=1, n=hnArray.length; i < n; i++) {
                if(hnArray[i].equals("*")) {
                    int msgID = MSGID_ACI_SYNTAX_INVALID_DNS_WILDCARD;
                    String message = getMessage(msgID, expr);
                    throw new AciException(msgID, message);
                }
            }

            // If the provided hostname does not contain any wildcard
            // characters, then it must be the canonical hostname for the
            // associated IP address.  If it is not, then it will not match the
            // intended target, and we should generate a warning message to let
            // the administrator know about it.  If the provided value does not
            // match the canonical name for the associated IP address, and the
            // given hostname is "localhost", then we should treat it specially
            // and also match the canonical hostname.  This is necessary because
            // "localhost" is likely to be very commonly used in these kinds of
            // rules and on some systems the canonical representation is
            // configured to be "localhost.localdomain" which may not be known
            // to the administrator.
            if (hn.indexOf("*") < 0)
            {
              try
              {
                for (InetAddress addr : InetAddress.getAllByName(hn))
                {
                  String canonicalName = addr.getCanonicalHostName();
                  if (! hn.equalsIgnoreCase(canonicalName))
                  {
                    if (hn.equalsIgnoreCase("localhost") &&
                        (! dns.contains(canonicalName)))
                    {
                      dns.add(canonicalName);

                      int msgID =
                           MSGID_ACI_LOCALHOST_DOESNT_MATCH_CANONICAL_VALUE;
                      String message = getMessage(msgID, expr, hn,
                                                  canonicalName);
                      logError(ErrorLogCategory.ACCESS_CONTROL,
                               ErrorLogSeverity.INFORMATIONAL, message, msgID);
                    }
                    else
                    {
                      int msgID =
                           MSGID_ACI_HOSTNAME_DOESNT_MATCH_CANONICAL_VALUE;
                      String message = getMessage(msgID, expr,
                                                  hn, addr.getHostAddress(),
                                                  addr.getCanonicalHostName());
                      logError(ErrorLogCategory.ACCESS_CONTROL,
                               ErrorLogSeverity.INFORMATIONAL, message, msgID);
                    }
                  }
                }
              }
              catch (Exception e)
              {
                if (debugEnabled())
                {
                  debugCaught(DebugLogLevel.ERROR, e);
                }

                int msgID = MSGID_ACI_ERROR_CHECKING_CANONICAL_HOSTNAME;
                String message = getMessage(msgID, hn, expr,
                                            stackTraceToSingleLineString(e));
                logError(ErrorLogCategory.ACCESS_CONTROL,
                         ErrorLogSeverity.INFORMATIONAL, message, msgID);
              }
            }

            dns.add(hn);
        }
        return new DNS(dns, type);
    }

    /**
     * Performs evaluation of dns keyword bind rule using the provided
     * evaluation context.
     * @param evalCtx  An evaluation context to use in the evaluation.
     * @return An enumeration evaluation result.
     */
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult matched=EnumEvalResult.FALSE;
        String[] remoteHost = evalCtx.getHostName().split("\\.", -1);
        for(String p : patterns) {
          String[] pat = p.split("\\.", -1);
          if(evalHostName(remoteHost, pat)) {
              matched=EnumEvalResult.TRUE;
              break;
          }
        }
        return matched.getRet(type, false);
    }

    /**
     * Checks an array containing the remote client's hostname against
     * patterns specified in the bind rule expression. Wild-cards are
     * only permitted in the leftmost field and the rest of the domain
     * name array components must match. A single wild-card matches any
     * hostname.
     * @param remoteHostName  Array containing components of the remote clients
     * hostname (split on ".").
     * @param pat  An array containing the pattern specified in
     * the bind rule expression. The first array slot may be a wild-card "*".
     * @return  True if the remote hostname matches the pattern.
     */
      boolean evalHostName(String[] remoteHostName, String[] pat) {
      boolean wildCard=pat[0].equals("*");
      //Check if there is a single wild-card.
      if(pat.length == 1 && wildCard)
        return true;
      int remoteHnIndex=remoteHostName.length-pat.length;
      if(remoteHnIndex < 0)
        return false;
      int patternIndex=0;
      if(!wildCard)
          remoteHnIndex=0;
      else {
          patternIndex=1;
          remoteHnIndex++;
      }
      for(int i=remoteHnIndex ;i<remoteHostName.length;i++)
            if(!pat[patternIndex++].equalsIgnoreCase(remoteHostName[i]))
                return false;
      return true;
    }
}
