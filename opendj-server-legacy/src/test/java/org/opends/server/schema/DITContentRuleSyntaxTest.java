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
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.schema;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.util.RemoveOnceSDKSchemaIsUsed;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the DITContentRuleSyntax.
 */
@RemoveOnceSDKSchemaIsUsed
@Test
public class DITContentRuleSyntaxTest extends AttributeSyntaxTest
{

  /** {@inheritDoc} */
  @Override
  protected AttributeSyntax getRule()
  {
    return new DITContentRuleSyntax();
  }

  @Override
  @DataProvider(name="acceptableValues")
  public Object[][] createAcceptableValues()
  {
    return new Object [][] {
        {"( 2.5.6.4 DESC 'content rule for organization' NOT "
             + "( x121Address $ telexNumber ) )", true},
        {"( 2.5.6.4 NAME 'fullrule' DESC 'rule with all possible fields' "
              + " OBSOLETE"
              + " AUX ( posixAccount )"
              + " MUST ( cn $ sn )"
              + " MAY ( dc )"
              + " NOT ( x121Address $ telexNumber ) )"
                , true},
        {"( 2.5.6.4 NAME 'fullrule' DESC 'ommit parenthesis' "
                  + " OBSOLETE"
                  + " AUX posixAccount "
                  + " MUST cn "
                  + " MAY dc "
                  + " NOT x121Address )"
              , true},
         {"( 2.5.6.4 NAME 'fullrule' DESC 'use numeric OIDs' "
                + " OBSOLETE"
                + " AUX 1.3.6.1.1.1.2.0"
                + " MUST cn "
                + " MAY dc "
                + " NOT x121Address )"
                   , true},
         {"( 2.5.6.4 NAME 'fullrule' DESC 'illegal OIDs' "
               + " OBSOLETE"
               + " AUX 2.5.6.."
               + " MUST cn "
               + " MAY dc "
               + " NOT x121Address )"
               , false},
         {"( 2.5.6.4 NAME 'fullrule' DESC 'illegal OIDs' "
                 + " OBSOLETE"
                 + " AUX 2.5.6.x"
                 + " MUST cn "
                 + " MAY dc "
                 + " NOT x121Address )"
                 , false},
         {"( 2.5.6.4 NAME 'fullrule' DESC 'missing closing parenthesis' "
                 + " OBSOLETE"
                 + " AUX posixAccount"
                 + " MUST cn "
                 + " MAY dc "
                 + " NOT x121Address"
             , false},
         {"( 2.5.6.4 NAME 'fullrule' DESC 'extra parameterss' "
                 + " MUST cn "
                 + " this is an extra parameter )"
             , false},

    };
  }

}
