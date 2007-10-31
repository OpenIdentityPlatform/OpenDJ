<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:variable name="tests" select="/qa/functional-tests/results/test"/>
  <xsl:variable name="groups" select="/qa/functional-tests/results/group"/>
  <xsl:template match="/">
    <!-- beginning of the html content output -->
    <xsl:element name="html">
      <xsl:element name="head">
        <link rel="SHORTCUT ICON" href="https://opends.dev.java.net/public/images/opends_favicon.gif" />
        <style type="text/css">
        /* <![CDATA[ */
         @import "/branding/css/tigris.css";
         @import "/branding/css/inst.css";
          ul.myCollapsible, ul.myCollapsible ul {
            margin: 0;
            padding: 0;
            list-style-type: none;
            /*font-family: verdana, arial, Helvetica, sans-serif;*/
            font-size: 10pt;
          
            width:100%;                  /* sets the size of the expandable blocks */
            /*border: 1px solid #000;    /* puts a black border around the menu blocks */
            /*background-color: #8aa;    /* makes the menu blocks mint green - a bg-color MUST be included for IE to work properly! */
            padding-left: 0px;           /* stops the usual indent from ul */
            cursor: default;             /* gives an arrow cursor */
            margin-left: 0px;            /* Opera 7 final's margin and margin-box model cause problems */
          }
          ul.myCollapsible li {
            display: block;
            padding: 0px 0px;
            width: 100%;
            color: #000;
            background-color: #ffffff;
            text-decoration: none;
            list-style-type: none;       /* removes the bullet points */
            margin: 0px;                 /* Opera 7 puts large spacings between li elements */
            position: relative;          /* makes the menu blocks be positioned relative to their parent menu item
                                            the lack of offset makes these appear normal, but it will make a difference
                                            to the absolutely positioned child blocks */
            /*color: #fff;               /* sets the default font colour to white */
          }
          ul.myCollapsible li > ul {     /* using the > selector prevents many lesser browsers (and IE - see below) hiding child ULs */
            display: none;               /* hides child menu blocks - one of the most important declarations */
            position: relative;          /* make child blocks hover without leaving space for them */
            top: 0px;                    /* position slightly lower than the parent menu item */
            left: 15px;                  /* this must not be more than the width of the parent block, or the mouse will
                                            have to move off the element to move between blocks, and the menu will close */
          }
          ul.myCollapsible li:hover, ul.myCollapsible li.CSStoHighlight {
            color: #000;                   /* black */
            /*background-color: #f0f0f0;*/     /* light grey background */
            text-decoration: none;         
            border-top: 1px solid #333;    /* simulate the 3d effect  */
            border-left: 1px solid #333;   /* with border colors */
            border-bottom: 1px solid #fff;
            border-right: 1px solid #fff;
          }
          ul.myCollapsible ul.CSStoShow {     /* must not be combined with the next rule or IE gets confused */
            display: block;              /* specially to go with the className changes in the behaviour file */
          }
          ul.myCollapsible li:hover > ul {    /* one of the most important declarations - the browser must detect hovering over arbitrary elements
                                                 the > targets only the child ul, not any child uls of that child ul */
            display: block;                   /* makes the child block visible - one of the most important declarations */
          }
          /* and some link styles */
          ul.myCollapsible li a { color: #00a; display: block; width: 100%; text-decoration: underline; }
          ul.myCollapsible li a:hover, ul.myCollapsible li a.CSStoHighLink { color: #00f; }
          ul.myCollapsible li:hover > a { color: #e00; } /* supports links in branch headings - should not be display: block; */
          .perfect      { background-color: #90ee90 }
          .acceptable   { background-color: #fffc00 }
          .unacceptable { background-color: #ff0000 }
          .error        { background-color: #ff0000 }
          .warning      { background-color: #ffa200 }
          table.toc     { background-color: #dfdfdf;
                          cell-spacing: 3;
                          border: 0;
                          width: 100%
                        }
          TD,TH { margin:0;
                  line-height:100%;
                  padding-left:0.5em;
                  padding-right:0.5em;
                }
          table { border-spacing:0;
                  border-collapse:collapse;
                  border-width:1px;
                  border-color: #ccc;
                  border-style:solid
                }
          TH {font-family:verdana,arial,sans-serif;font-size:10pt;} 
          td {font-family:courier,monospace;font-size:10pt;} 
          A:link {color:#0000EE;text-decoration:none;} 
          A:visited {color:#0000EE;text-decoration:none;} 
          A:hover {color:#0000EE;text-decoration:underline;} 
         /* ]]> */
        </style>
        <link rel="stylesheet" type="text/css" href="/branding/css/print.css" media="print" />
        <link rel="stylesheet" href="https://opends.dev.java.net/public/css/opends.css" type="text/css" />
        <xsl:variable name="version"  select="/qa/functional-tests/identification/version"/>
        <xsl:element name="title">
          <xsl:value-of select="concat('Functional tests report for OpenDS-',$version)"/>
        </xsl:element>
      </xsl:element>
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>


	<xsl:template match="results">
    <xsl:variable name="identification" select="/qa/functional-tests/identification"/>
    <xsl:variable name="total"          select="count($tests)"/>
    <xsl:variable name="pass"           select="count($tests[result='pass'])"/>
    <xsl:variable name="fail"           select="count($tests[result='fail'])"/>
    <xsl:variable name="known"           select="count($tests[result='known'])"/>
    <xsl:variable name="unknown"        select="count($tests[result='inconclusive'])"/>
    <xsl:variable name="rate"           select="round((($pass div $total) * 100) - 0.5)"/>
    <!--- Test Report Header Variables -->
    <table class="tertmasttable" width="100%" cellspacing="0">
      <tbody>
        <tr>
          <td><div class="collectionheader"><xsl:value-of select="concat('Test Report for OpenDS ',$identification/version)"/></div></td>
          <td width="10%"><a href="https://opends.dev.java.net/"><img src="https://opends.dev.java.net/public/images/opends_logo_sm.png" alt="OpenDS Logo" width="104" height="33" border="0" align="middle" /></a> </td>
        </tr>
      </tbody>
    </table>

    <!-- Shaded Line -->
    <hr noshade="noshade" size="1" />
  
    <!-- Percentage Result -->
    <table width="100%">
      <xsl:attribute name="class">
        <xsl:choose>
          <xsl:when test="$rate &lt; 80">
            <xsl:value-of select="'unacceptable'" />
          </xsl:when>
          <xsl:when test="$rate &lt; 90">
            <xsl:value-of select="'acceptable'" />
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="'perfect'" />
          </xsl:otherwise>
        </xsl:choose>
      </xsl:attribute>
      <tr>
        <td align="center">
          <xsl:element name="font">
            <xsl:attribute name="size">
              <xsl:value-of select="'+2'"/>
            </xsl:attribute>
            <xsl:value-of select="concat($rate,'%')"/>
          </xsl:element>
        </td>
      </tr>
    </table>

    <!-- Shaded Line -->
    <hr noshade="noshade" size="1" />

    <!-- Identification table -->
    <p>
      <table width="100%">
        <tr>
          <th align="center">Build</th>
          <th align="center">Revision</th>
          <th align="center">Platform</th>
          <th align="center">JVM</th>
          <th align="center" width="5%">Total</th>
          <th align="center" width="5%">Pass</th>
          <th align="center" width="5%">Fail</th>
          <th align="center" width="5%">Known Issues</th>
          <th align="center" width="5%">Inconclusive</th>
          <th align="center" width="5%">Coverage</th>
        </tr>
        <tr>
          <th align="center"><xsl:value-of select="$identification/buildid"/></th>
          <th align="center"><xsl:value-of select="$identification/revision"/></th>
          <th align="center"><xsl:value-of select="$identification/os-label"/></th>
          <th align="center"><xsl:value-of select="$identification/jvm-label"/></th>
          <th align="center"><xsl:value-of select="$total"/></th>
          <th align="center"><xsl:value-of select="$pass"/></th>
          <th align="center"><xsl:value-of select="$fail"/></th>
          <th align="center"><xsl:value-of select="$known"/></th>
          <th align="center"><xsl:value-of select="$unknown"/></th>
          <th align="center">
            <a>
              <!-- TODO: make this conditional -->
              <xsl:attribute name="href">
                <xsl:value-of select="'coverage/coverage.html'"/>
              </xsl:attribute>
              <xsl:value-of select="/qa/functional-tests/results/all/coverage"/>%
            </a>
          </th>
        </tr>
      </table>
    </p>
    
    <!-- table of contents -->
    <center><h2>Table of Contents</h2></center>
    <table width="100%">
      <tr>
        <td>Description</td>
        <td width="5%">Success Rate</td>
        <td width="5%">Pass</td>
        <td width="5%">Fail</td>
        <td width="5%">Known</td>
        <td width="5%">Inc.</td>
        <td width="5%">Cov.</td>
      </tr>
    </table>
    <ul class="myCollapsible">
      <xsl:for-each select="$tests">
        <xsl:sort select="group" order="ascending"/>
        <xsl:variable name="group" select="group"/>
        <xsl:if test="generate-id(.)=generate-id($tests[group=$group])">
          <xsl:variable name="group-total"    select="count($tests[group=$group])"/>
          <xsl:variable name="group-pass"    select="count($tests[group=$group][result='pass'])"/>
          <xsl:variable name="group-fail"    select="count($tests[group=$group][result='fail'])"/>
          <xsl:variable name="group-known"    select="count($tests[group=$group][result='known'])"/>
          <xsl:variable name="group-unknown" select="count($tests[group=$group][result='inconclusive'])"/>
          <xsl:variable name="group-rate"    select="round((($group-pass div $group-total) * 100) - 0.5)"/>
          <li>
            <table class="toc">
              <tr>
                <!-- define the color for the whole table row according to success rate -->
                <xsl:attribute name="class">
                  <xsl:choose>
                    <xsl:when test="$group-rate &lt; 80">
                      <xsl:value-of select="'unacceptable'" />
                    </xsl:when>
                    <xsl:when test="$group-rate &lt; 90">
                      <xsl:value-of select="'acceptable'" />
                    </xsl:when>
                    <xsl:otherwise>
                      <xsl:value-of select="'perfect'" />
                    </xsl:otherwise>
                  </xsl:choose>
                </xsl:attribute>
                <td>
                  <a>
                    <xsl:attribute name="href">
                      <xsl:value-of select="concat('#',$group)"/>
                    </xsl:attribute>
                      <xsl:value-of select="$group"/>
                  </a>
                </td>
                <td width="5%"><xsl:value-of select="$group-rate"/>%</td>
                <td width="5%"><xsl:value-of select="$group-pass"/></td>
                <td width="5%">
                  <xsl:attribute name="class">
                    <xsl:choose>
                      <xsl:when test="$group-fail &gt; 0">
                        <xsl:value-of select="'error'" />
                      </xsl:when>
                    </xsl:choose>
                  </xsl:attribute>
                  <xsl:value-of select="$group-fail"/>
                </td>
                <td width="5%">
                  <xsl:attribute name="class">
                    <xsl:choose>
                      <xsl:when test="$group-known &gt; 0">
                        <xsl:value-of select="'warning'" />
                      </xsl:when>
                    </xsl:choose>
                  </xsl:attribute>
                  <xsl:value-of select="$group-known"/>
                </td>
                <td width="5%">
                  <xsl:attribute name="class">
                    <xsl:choose>
                      <xsl:when test="$group-unknown &gt; 0">
                        <xsl:value-of select="'warning'" />
                      </xsl:when>
                    </xsl:choose>
                    </xsl:attribute>
                  <xsl:value-of select="$group-unknown"/>
                </td>
                <td width="5%">
                  <a>
                    <xsl:attribute name="href">
                      <xsl:value-of select="concat(concat('coverage/',$group),'/coverage.html')"/>
                    </xsl:attribute>
                    <xsl:value-of select="/qa/functional-tests/results/group[name='$group']/coverage"/>%
                  </a>
                </td>
              </tr>
            </table>
            <ul>
              <xsl:for-each select="$tests[group=$group]">
                <xsl:variable name="suite" select="suite"/>
                <xsl:if test="generate-id(.)=generate-id($tests[group=$group][suite=$suite])">
                  <xsl:variable name="suite-total"   select="count($tests[group=$group][suite=$suite])"/>
                  <xsl:variable name="suite-pass"    select="count($tests[group=$group][suite=$suite][result='pass'])"/>
                  <xsl:variable name="suite-fail"    select="count($tests[group=$group][suite=$suite][result='fail'])"/>
                  <xsl:variable name="suite-known"    select="count($tests[group=$group][suite=$suite][result='known'])"/>
                  <xsl:variable name="suite-unknown" select="count($tests[group=$group][suite=$suite][result='inconclusive'])"/>
                  <xsl:variable name="suite-rate"    select="round((($suite-pass div $suite-total) * 100) - 0.5)"/>
                  <li>
                    <table class="toc">
                      <tr>
                        <xsl:attribute name="class">
                          <xsl:choose>
                            <xsl:when test="$suite-rate &lt; 80">
                              <xsl:value-of select="'unacceptable'" />
                            </xsl:when>
                            <xsl:when test="$suite-rate &lt; 90">
                              <xsl:value-of select="'acceptable'" />
                            </xsl:when>
                            <xsl:otherwise>
                              <xsl:value-of select="'perfect'" />
                            </xsl:otherwise>
                          </xsl:choose>
                        </xsl:attribute>
                        <td>
                          <a>
                            <xsl:attribute name="href">
                              <xsl:value-of select="concat(concat(concat('#',$group),'-'),$suite)"/>
                            </xsl:attribute>
                            <xsl:value-of select="$suite"/>
                          </a>
                        </td>
                        <td width="5%">
                          <xsl:value-of select="concat($suite-rate,'%')"/>
                        </td>
                        <td width="5%">
                          <xsl:value-of select="$suite-pass"/>
                        </td>
                        <td width="5%">
                          <xsl:attribute name="class">
                            <xsl:choose>
                              <xsl:when test="$suite-fail &gt; 0">
                                <xsl:value-of select="'error'" />
                              </xsl:when>
                            </xsl:choose>
                          </xsl:attribute>
                          <xsl:value-of select="$suite-fail"/>
                        </td>
                        <td width="5%">
                          <xsl:attribute name="class">
                            <xsl:choose>
                              <xsl:when test="$suite-known &gt; 0">
                                <xsl:value-of select="'warning'" />
                              </xsl:when>
                            </xsl:choose>
                          </xsl:attribute>
                          <xsl:value-of select="$suite-known"/>
                        </td>
                        <td width="5%">
                          <xsl:attribute name="class">
                            <xsl:choose>
                              <xsl:when test="$suite-unknown &gt; 0">
                                <xsl:value-of select="'warning'" />
                              </xsl:when>
                            </xsl:choose>
                          </xsl:attribute>
                          <xsl:value-of select="$suite-unknown"/>
                        </td>
                      </tr>
                    </table>
                    <ul>
                      <xsl:for-each select="$tests[group=$group][suite=$suite][result='fail' or result='inconclusive' or result='known']">
                        <xsl:variable name="name" select="name"/>
                        <li>
                          <table>
                            <tr>
                              <xsl:attribute name="class">
                                <xsl:choose>
                                  <xsl:when test="result='fail'">
                                    <xsl:value-of select="'unacceptable'" />
                                  </xsl:when>
                                  <xsl:when test="result='known'">
                                    <xsl:value-of select="'acceptable'" />
                                  </xsl:when>
                                  <xsl:otherwise>
                                    <xsl:value-of select="'perfect'" />
                                  </xsl:otherwise>
                                </xsl:choose>
                              </xsl:attribute>
                              <td>
                                <a>
                                  <xsl:attribute name="href">
                                    <xsl:value-of select="concat(concat(concat(concat(concat('#',$group),'-'),$suite),'-'),$name)"/>
                                  </xsl:attribute>
                                  <xsl:value-of select="name"/>
                                </a>
                              </td>
                              <td width="10%">
                                <xsl:for-each select="./issues">
                                  <xsl:variable name="issue" select="issue" />
                                  <a target="issue">
                                    <xsl:attribute name="href">
                                      <xsl:value-of select="concat('https://opends.dev.java.net/issues/show_bug.cgi?id=',$issue)" />
                                    </xsl:attribute>
                                    <xsl:value-of select="$issue" />
                                  </a><br />
                                </xsl:for-each>
                              </td>
                            </tr>
                          </table>
                          <ul>
                            <li>Test Log<br />
                              <ul>
                                <li>
                                  <pre><xsl:value-of select="log" /></pre>
                                </li>
                              </ul>
                            </li>
                            <li>Server access Log<br />
                              <ul>
                                <li>
                                  <pre><xsl:value-of select="access" /></pre>
                                </li>
                              </ul>
                            </li>
                            <li>Server error Log<br />
                              <ul>
                                <li>
                                  <pre><xsl:value-of select="error" /></pre>
                                </li>
                              </ul>
                            </li>
                          </ul>
                        </li>
                      </xsl:for-each>
                    </ul>
                  </li>
                </xsl:if>
              </xsl:for-each>
            </ul>
          </li>
        </xsl:if>
      </xsl:for-each>
    </ul>
    
    <!-- this section generate each group's table report -->
    <xsl:for-each select="$tests">
      <xsl:sort select="start" order="ascending"/>
      <xsl:variable name="group" select="group"/>
      <xsl:if test="generate-id(.)=generate-id($tests[group=$group])">
        <xsl:variable name="group-total"    select="count($tests[group=$group])"/>
        <xsl:variable name="group-pass"     select="count($tests[group=$group][result='pass'])"/>
        <xsl:variable name="group-fail"     select="count($tests[group=$group][result='fail'])"/>
        <xsl:variable name="group-known"    select="count($tests[group=$group][result='known'])"/>
        <xsl:variable name="group-unknown"  select="count($tests[group=$group][result='inconclusive'])"/>
        <xsl:variable name="group-rate"     select="round((($group-pass div $group-total) * 100) - 0.5)"/>
        <xsl:variable name="group-coverage" select="coverage"/>
        <p>
          <table>
            <tr>
              <th>Group</th>
              <th width="5%">Success Rate</th>
              <th width="5%">Pass</th>
              <th width="5%">Fail</th>
              <th width="5%">Known</th>
              <th width="5%">Inc.</th>
              <th width="5%">Cov.</th>
            </tr>
            <tr>
              <xsl:attribute name="class">
                <xsl:choose>
                  <xsl:when test="$group-rate &lt; 80">
                    <xsl:value-of select="'unacceptable'" />
                  </xsl:when>
                  <xsl:when test="$group-rate &lt; 90">
                    <xsl:value-of select="'acceptable'" />
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:value-of select="'perfect'" />
                  </xsl:otherwise>
                </xsl:choose>
              </xsl:attribute>
              <td align="center">
                <a>
                  <xsl:attribute name="name">
                    <xsl:value-of select="group"/>
                  </xsl:attribute>  
                  <xsl:value-of select="group"/> 
                </a>
              </td>
              <td align="center"><xsl:value-of select="$group-rate"/>%</td>
              <td align="center"><xsl:value-of select="$group-pass"/></td>
              <td align="center"><xsl:value-of select="$group-fail"/></td>
              <td align="center"><xsl:value-of select="$group-known"/></td>
              <td align="center"><xsl:value-of select="$group-unknown"/></td>
              <td>
                <a>
                  <xsl:attribute name="href">
                    <xsl:value-of select="concat(concat('coverage/',$group),'/coverage.html')"/>
                  </xsl:attribute>
                  <xsl:variable name="group-cov" select="/qa/functional-tests/group[name=$group]" />
                  <xsl:value-of select="$group-cov/coverage"/>%
                </a>
              </td>
            </tr>
            <xsl:for-each select="$tests[group=$group]">
              <xsl:variable name="suite" select="suite"/>
              <xsl:if test="generate-id(.)=generate-id($tests[group=$group][suite=$suite])">
                <xsl:variable name="suite-total"   select="count($tests[group=$group][suite=$suite])"/>
                <xsl:variable name="suite-pass"    select="count($tests[group=$group][suite=$suite][result='pass'])"/>
                <xsl:variable name="suite-fail"    select="count($tests[group=$group][suite=$suite][result='fail'])"/>
                <xsl:variable name="suite-known"   select="count($tests[group=$group][suite=$suite][result='known'])"/>
                <xsl:variable name="suite-unknown" select="count($tests[group=$group][suite=$suite][result='inconclusive'])"/>
                <xsl:variable name="suite-rate"    select="round((($suite-pass div $suite-total) * 100) - 0.5)"/>
                <tr>
                  <td align="right" colspan="5">
                    <table border="0" cellspacing="3" width="90%">
                      <tr>
                        <th>Group</th>
                        <th width="40%">Suite</th>
                        <th width="5%">Success rate</th>
                        <th width="5%">Pass</th>
                        <th width="5%">Fail</th>
                        <th width="5%">Known</th>
                        <th width="5%">Inc.</th>
                      </tr>
                      <tr>
                        <xsl:attribute name="class">
                          <xsl:choose>
                            <xsl:when test="$suite-rate &lt; 80">
                              <xsl:value-of select="'unacceptable'" />
                            </xsl:when>
                            <xsl:when test="$suite-rate &lt; 90">
                              <xsl:value-of select="'acceptable'" />
                            </xsl:when>
                            <xsl:otherwise>
                              <xsl:value-of select="'perfect'" />
                            </xsl:otherwise>
                          </xsl:choose>
                        </xsl:attribute>
                        <td><xsl:value-of select="$group"/></td>
                        <td>
                          <a>
                            <xsl:attribute name="name">
                              <xsl:value-of select="concat(concat($group,'-'),$suite)"/>
                            </xsl:attribute>  
                            <xsl:value-of select="$suite"/>
                          </a>
                        </td>
                        <td><xsl:value-of select="$suite-rate"/>%</td>
                        <td><xsl:value-of select="$suite-pass"/></td>
                        <td><xsl:value-of select="$suite-fail"/></td>
                        <td><xsl:value-of select="$suite-known"/></td>
                        <td><xsl:value-of select="$suite-unknown"/></td>
                      </tr>
                    </table>
                    <table  border="0" cellspacing="3" width="80%">
                      <tr>
                        <th>Group</th>
                        <th>Suite</th>
                        <th>Test Case</th>
                        <th width="5%">Duration</th>
                        <th width="5%">Outcome</th>
                      </tr>
                      <xsl:for-each select="$tests[group=$group][suite=$suite]">
                        <xsl:variable name="name" select="name"/>
                        <tr>
                          <td>
                            <xsl:value-of select="group"/>
                          </td>
                          <td>
                            <xsl:value-of select="suite"/>
                          </td>
                          <td>
                            <a>
                              <xsl:attribute name="name">
                                <xsl:value-of select="concat(concat(concat(concat($group,'-'),$suite),'-'),$name)"/>
                              </xsl:attribute>
                              <xsl:value-of select="name"/>
                            </a>
                          </td>
                          <td>
                            <xsl:value-of select="duration"/>
                          </td>
                          <td>
                            <xsl:value-of select="result"/>
                          </td>
                        </tr>
                      </xsl:for-each>
                    </table>
                  </td>
                </tr>
              </xsl:if>
            </xsl:for-each>
          </table>
        </p>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
</xsl:stylesheet>
