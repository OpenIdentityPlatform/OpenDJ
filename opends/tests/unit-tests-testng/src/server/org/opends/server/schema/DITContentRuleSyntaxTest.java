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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.schema;

import org.opends.server.api.AttributeSyntax;
import org.testng.annotations.DataProvider;

/**
 * Test the DITContentRuleSyntax.
 */
public class DITContentRuleSyntaxTest extends AttributeSyntaxTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  public AttributeSyntax getRule()
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
        {"( 2.5.6.4 NAME 'full rule' DESC 'rule with all possible fields' "
              + " OBSOLETE"
              + " AUX ( posixAccount )"
              + " MUST ( cn $ sn )"
              + " MAY ( dc )"
              + " NOT ( x121Address $ telexNumber ) )"
                , true},
        {"( 2.5.6.4 NAME 'full rule' DESC 'ommit parenthesis' "
                  + " OBSOLETE"
                  + " AUX posixAccount "
                  + " MUST cn "
                  + " MAY dc "
                  + " NOT x121Address )"
              , true},
         {"( 2.5.6.4 NAME 'full rule' DESC 'use numeric OIDs' "
                + " OBSOLETE"
                + " AUX 1.3.6.1.1.1.2.0"
                + " MUST cn "
                + " MAY dc "
                + " NOT x121Address )"
                   , true},
         {"( 2.5.6.4 NAME 'full rule' DESC 'illegal OIDs' "
               + " OBSOLETE"
               + " AUX 2.5.6.."
               + " MUST cn "
               + " MAY dc "
               + " NOT x121Address )"
               , false},
         {"( 2.5.6.4 NAME 'full rule' DESC 'illegal OIDs' "
                 + " OBSOLETE"
                 + " AUX 2.5.6.x"
                 + " MUST cn "
                 + " MAY dc "
                 + " NOT x121Address )"
                 , false},
         {"( 2.5.6.4 NAME 'full rule' DESC 'missing closing parenthesis' "
                 + " OBSOLETE"
                 + " AUX posixAccount"
                 + " MUST cn "
                 + " MAY dc "
                 + " NOT x121Address"
             , false},
         {"( 2.5.6.4 NAME 'full rule' DESC 'extra parameterss' "
                 + " MUST cn "
                 + "( this is an extra parameter )"
             , true},

    };
  }

}
