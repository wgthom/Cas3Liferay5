<%
/**
 * Copyright (c) 2000-2009 Liferay, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
%>

<%@ page import="com.liferay.portal.util.*"%>

<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>

<portlet:defineObjects />

This is the <b>CAS3 Clearpass Example Portlet</b> which demonstrates 
how to get the username and password of the logged in user using
com.liferay.portal.util.PortalUtil methods in view.jsp.  The password
was retrieved by <a href="http://github.com/wgthom/Cas3Liferay5/blob/master/ext/ext-impl/src/com/liferay/portal/security/auth/CAS3AutoLogin.java">CAS3AutoLogin.java</a>
from CAS using the <a href="http://www.ja-sig.org/wiki/display/CASUM/ClearPass">Jasig Cas ClearPass extension</a>.

<p>
<script src="http://gist.github.com/323766.js?file=gistfile1.java"></script>
</p>

<%  
long userid = PortalUtil.getUserId(request);
String username = PortalUtil.getUserName(userid, null, "user.login.id");
String password = PortalUtil.getUserPassword(request);
%>

<ul>
<li>username: <b><%=username  %></b></li>
<li>password: <b><%=password  %></b></li>
</ul>

<p>
Visit the <a href="http://github.com/wgthom/Cas3Liferay5">GitHub Cas3Liferay5 repository</a> for the source and more information. 
</p>
