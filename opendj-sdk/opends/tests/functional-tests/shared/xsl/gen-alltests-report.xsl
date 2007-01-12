<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="1.0" 
xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:template match="/">

  <xsl:element name="html">
  
  <xsl:element name="head">

  <link rel="SHORTCUT ICON" href="https://opends.dev.java.net/public/images/opends_favicon.gif" />
  <style type="text/css">
/* <![CDATA[ */
 @import "/branding/css/tigris.css";
 @import "/branding/css/inst.css";
 /* ]]> */
  </style>
  <link rel="stylesheet" type="text/css" href="/branding/css/print.css" media="print" />
  <link rel="stylesheet" href="https://opends.dev.java.net/public/css/opends.css" type="text/css" />

    <xsl:element name="title">
      <xsl:value-of select="'Test Report'"/>
    </xsl:element>
  
    </xsl:element>
  
  <table class="tertmasttable" width="100%" cellspacing="0">
    <tbody>
      <tr>
        <td><div class="collectionheader">Test Report</div></td>
        <td width="10%"><a href="https://opends.dev.java.net/"><img src="https://opends.dev.java.net/public/images/opends_logo_sm.png" alt="OpenDS Logo" width="104" height="33" border="0" align="middle" /></a> </td>
      </tr>
    </tbody>
  </table>
  
  <!--- Test Report Header Variables -->
  <xsl:variable name="identification"     select="qa/functional-tests/identification"/>
  <xsl:variable name="testcase"     select="qa/functional-tests/results/testgroup/testsuite/testcase"/>
  <xsl:variable name="total-tests"  select="count($testcase)"/>
  <xsl:variable name="pass-tests"   select="count($testcase[@result='pass'])"/>
  <xsl:variable name="fail-tests"   select="count($testcase[@result='fail'])"/>
  <xsl:variable name="inconc-tests" select="count($testcase[@result='unknown'])"/>

  <xsl:variable name="percent-tests" select="round(($pass-tests div $total-tests) * 100)"/>

  <!-- Shaded Line -->
  <xsl:element name="hr">
    <xsl:attribute name="noshade">
      <xsl:value-of select="'noshade'"/>
    </xsl:attribute>
    <xsl:attribute name="size">
      <xsl:value-of select="1"/>
    </xsl:attribute>
  </xsl:element>

  <!-- Percentage Result -->
  <xsl:element name="table">
    <xsl:attribute name="width">
      <xsl:value-of select="'100%'"/>
    </xsl:attribute>
    <xsl:attribute name="bgcolor">
      <xsl:choose>
        <xsl:when test="$percent-tests &lt; 80">
          <xsl:value-of select="'red'" />
        </xsl:when>
        <xsl:when test="$percent-tests &lt; 90">
          <xsl:value-of select="'yellow'" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="'lightgreen'" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:attribute>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="font">
          <xsl:attribute name="size">
            <xsl:value-of select="'+2'"/>
          </xsl:attribute>
          <xsl:value-of select="concat($percent-tests,'%')"/>
        </xsl:element>
      </xsl:element>
    </xsl:element>
  </xsl:element>

  <!-- Shaded Line -->
  <xsl:element name="hr">
    <xsl:attribute name="noshade">
      <xsl:value-of select="'noshade'"/>
    </xsl:attribute>
    <xsl:attribute name="size">
      <xsl:value-of select="1"/>
    </xsl:attribute>
  </xsl:element>

  <!-- Identification -->
  <xsl:element name="table">
    <xsl:attribute name="width">
      <xsl:value-of select="'100%'"/>
    </xsl:attribute>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Platform'"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Hardware'"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Java Version'"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Total'"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Pass'"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Fail'"/>
        </xsl:element>
      </xsl:element>
            <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Inconclusive'"/>
        </xsl:element>
      </xsl:element>
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$identification/platform"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$identification/hardware"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$identification/jvm"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$total-tests"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$pass-tests"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$fail-tests"/>
        </xsl:element>
      </xsl:element>
            <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$inconc-tests"/>
        </xsl:element>
      </xsl:element>
    </xsl:element>
  </xsl:element>
  
  <!-- Shaded Line -->
  <xsl:element name="hr">
    <xsl:attribute name="noshade">
      <xsl:value-of select="'noshade'"/>
    </xsl:attribute>
    <xsl:attribute name="size">
      <xsl:value-of select="1"/>
    </xsl:attribute>
  </xsl:element>

  <xsl:element name="body">

    <!-- Test Results Table -->
    <xsl:element name="table">
      <xsl:attribute name="width">
        <xsl:value-of select="'100%'"/>
      </xsl:attribute>
      <xsl:attribute name="border">
        <xsl:value-of select="1"/>
      </xsl:attribute>
      <xsl:attribute name="cellpadding">
        <xsl:value-of select="2"/>
      </xsl:attribute>
      
      <xsl:element name="tr">
        <xsl:attribute name="bgcolor">
          <xsl:value-of select="'lightblue'"/>
        </xsl:attribute>
	
        <xsl:element name="th">
	  <xsl:value-of select="'Testcase'"/>
	</xsl:element>
	<xsl:element name="th">
	  <xsl:value-of select="'Start Time'"/>
	</xsl:element>
	<xsl:element name="th">
	  <xsl:value-of select="'End Time'"/>
	</xsl:element>
	<xsl:element name="th">
	  <xsl:value-of select="'Duration'"/>
	</xsl:element>
	<xsl:element name="th">
	  <xsl:value-of select="'Result'"/>
	</xsl:element>
	<xsl:element name="th">
	  <xsl:value-of select="'Test Logs'"/>
	</xsl:element>
	<xsl:element name="th">
	  <xsl:value-of select="'Server Logs'"/>
	</xsl:element>

      </xsl:element>

    <xsl:for-each select="$testcase">

      <xsl:element name="tr">
        <xsl:attribute name="bgcolor">
          <xsl:choose>
	    <xsl:when test="@result='fail'">
	      <xsl:value-of select="'red'"/>
	    </xsl:when>
	    <xsl:when test="@result='unknown'">
	      <xsl:value-of select="'yellow'"/>
	    </xsl:when>
	  </xsl:choose>
	</xsl:attribute>
	
        <!-- Test Name -->
        <xsl:element name="td">
          <xsl:attribute name="align">
            <xsl:value-of select="'left'"/>
          </xsl:attribute>
          <xsl:value-of select="@name"/>
        </xsl:element>
        
        <!-- Start Time -->
        <xsl:element name="td">
          <xsl:attribute name="align">
            <xsl:value-of select="'center'"/>
          </xsl:attribute>
          <xsl:value-of select="@start"/>
        </xsl:element>
        
        <!-- Stop Time -->
        <xsl:element name="td">
          <xsl:attribute name="align">
            <xsl:value-of select="'center'"/>
          </xsl:attribute>
          <xsl:value-of select="@stop"/>
        </xsl:element>
        
        <!-- Duration -->
        <xsl:element name="td">
          <xsl:attribute name="align">
            <xsl:value-of select="'center'"/>
          </xsl:attribute>
          <xsl:value-of select="@duration"/>
        </xsl:element>
        
        <!-- Result -->
        <xsl:element name="td">
          <xsl:attribute name="align">
            <xsl:value-of select="'center'"/>
          </xsl:attribute>
          <xsl:value-of select="@result"/>
        </xsl:element>
        
        <!-- Test Logs -->
        <xsl:element name="td">
          <xsl:attribute name="align">
            <xsl:value-of select="'center'"/>
          </xsl:attribute>
          <xsl:value-of select="'-'"/>
        </xsl:element>
        
        <!-- Server Logs -->
        <xsl:element name="td">
          <xsl:attribute name="align">
            <xsl:value-of select="'center'"/>
          </xsl:attribute>
          <xsl:value-of select="'-'"/>
        </xsl:element>
        
      </xsl:element>
      
    </xsl:for-each>

    </xsl:element>

  </xsl:element>
  
  </xsl:element>

</xsl:template>

</xsl:stylesheet>
