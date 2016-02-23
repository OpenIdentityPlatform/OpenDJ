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
 * Portions Copyright 2015 ForgeRock AS.
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
