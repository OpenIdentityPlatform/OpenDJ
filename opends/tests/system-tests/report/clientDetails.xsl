<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xlink="http://www.w3.org/1999/xlink"
                exclude-result-prefixes="xlink"           
                version="1.0">
  <xsl:output method="html"/>

  <!-- ================= root node ================ -->
  <xsl:template match="client">
    <html><body>
    <head>
      <link href="../result.css" rel="stylesheet" type="text/css"></link>
    </head>
      
    <h1>
      Details for client 
      <xsl:value-of select="@name" />
      ( id <xsl:value-of select="@id" /> )
    </h1>
    <p>
      <ul>
      <xsl:apply-templates select="operation"/>
      </ul>
      <xsl:apply-templates select="message"/>
    </p>
    </body>
    </html>
  </xsl:template>

  <!-- ============= operation node =============== -->
  <xsl:template match="operation">
    <xsl:variable name="opName" select="normalize-space(@name)"/>
    <xsl:variable name="opDate" select="normalize-space(@date)"/>
    <li>
      <b><xsl:value-of select="$opName"/></b>
         <i>@ <xsl:value-of select="$opDate"/></i><br/>
      <xsl:apply-templates select="message"/>
      <xsl:apply-templates select="operationResult"/>
    </li>
  </xsl:template>


  <!-- ============= message node =============== -->
  <xsl:template match="message">
    <xsl:choose>
      <xsl:when test="@xlink:href">
        <a href="{@xlink:href}"><xsl:value-of select="." /></a>
      </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="." />
        </xsl:otherwise>
      </xsl:choose>
     <br/>
  </xsl:template>

  <!-- ============= operationResult node =============== -->
  <xsl:template match="operationResult">
    <xsl:variable name="rc" select="normalize-space(@returncode)"/>
    <xsl:variable name="exprc" select="normalize-space(@expected)"/>
    <xsl:variable name="result" select="normalize-space(.)"/>
    <xsl:variable name="status" select="normalize-space(@status)"/>
    
    <b>
      <xsl:choose>
        <xsl:when test="$status='SUCCESS'">
          <span class="pass"><xsl:value-of select="$status"/></span>
          [
           return code <xsl:value-of select="$rc" />,
           expected <xsl:value-of select="$exprc" />
          ] <br/>
        </xsl:when>
        <xsl:when test="$status='ERROR'">
          <span class="fail"><xsl:value-of select="$status"/></span>
          [
           return code <xsl:value-of select="$rc" />,
           expected <xsl:value-of select="$exprc" />,
           <xsl:value-of select="$result" /> 
          ] <br/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="$status"/>
        </xsl:otherwise>
      </xsl:choose>
    </b>
  </xsl:template>


  </xsl:stylesheet>

