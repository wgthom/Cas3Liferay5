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

package com.liferay.portal.security.auth;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import com.liferay.portal.NoSuchUserException;
import com.liferay.portal.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.User;
import com.liferay.portal.security.ldap.PortalLDAPUtil;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portal.util.PrefsPropsUtil;
import com.liferay.portal.util.PropsKeys;
import com.liferay.portal.util.PropsUtil;
import com.liferay.portal.util.PropsValues;

import edu.yale.its.tp.cas.client.filter.CASFilter;

import javax.naming.Binding;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.jasig.cas.client.util.CommonUtils;
import org.jasig.cas.client.util.XmlUtils;
import org.jasig.cas.client.validation.Assertion;
import org.jasig.cas.client.validation.Cas20ProxyReceivingTicketValidationFilter;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * <a href="CA3AutoLogin.java.html"><b><i>View Source</i></b></a>
 *
 * @author Brian Wing Shun Chan
 * @author Jorge Ferrer
 * @author William G. Thompson, Jr.
 *
 */
public class CAS3AutoLogin implements AutoLogin {

	public String[] login(
		HttpServletRequest request, HttpServletResponse response) {

		String[] credentials = null;

		try {
			long companyId = PortalUtil.getCompanyId(request);

			
			if (!PrefsPropsUtil.getBoolean(
					companyId, PropsKeys.CAS_AUTH_ENABLED,
					PropsValues.CAS_AUTH_ENABLED)) {

				return credentials;
			}

			HttpSession session = request.getSession();
			
			Assertion assertion = null;
			String screenName = null;
			if (session.getAttribute(CONST_CAS_ASSERTION) != null) {
				assertion = (Assertion) session.getAttribute(CONST_CAS_ASSERTION);
				screenName = assertion.getPrincipal().getName();
			}

//			if (PrefsPropsUtil.getBoolean(companyId, PropsKeys.CAS_PGT_ENABLED, PropsValues.CAS_PGT_ENABLED)) {
//				// make PGT available?
//			}
//			
			
			if (Validator.isNull(screenName)) {
				return credentials;
			}

			User user = null;

			try {
				user = UserLocalServiceUtil.getUserByScreenName(
					companyId, screenName);
			}
			catch (NoSuchUserException nsue) {
				if (PrefsPropsUtil.getBoolean(
						companyId, PropsKeys.CAS_IMPORT_FROM_LDAP,
						PropsValues.CAS_IMPORT_FROM_LDAP)) {

					user = addUser(companyId, screenName);
				}
				else {
					throw nsue;
				}
			}

			credentials = new String[3];
			
			credentials[0] = String.valueOf(user.getUserId());

			// Check to see if ClearPass is enabled
			if (PrefsPropsUtil.getBoolean(companyId, PropsKeys.CAS_CLEARPASS_ENABLED, PropsValues.CAS_CLEARPASS_ENABLED)) {
				// CAS3AutoLogin is fired on every request, so we need to check if we've already got the password
				if (session.getAttribute("CAS_CLEARPASS") == null) {
					final String password = CAS3AutoLogin.getClearTextPassword(assertion, companyId);
					credentials[1] = password;
					session.setAttribute("CAS_CLEARPASS", password);
				} else {
					credentials[1] = (String) session.getAttribute("CAS_CLEARPASS");
				}
				credentials[2] = Boolean.FALSE.toString();  // password encrypted? nope.
			} else {
				credentials[1] = user.getPassword();
				credentials[2] = Boolean.TRUE.toString();			
			}

			return credentials;
		}
		catch (Exception e) {
			_log.error(e, e);
		}

		return credentials;
	}

	private static String getClearTextPassword(Assertion assertion, Long companyId) {
		
		String clearPassUrl;
		try {
			clearPassUrl = PrefsPropsUtil.getString(companyId, PropsKeys.CAS_CLEARPASS_URL, PropsValues.CAS_CLEARPASS_URL);
		} catch (SystemException e) {
			throw new RuntimeException(e);
		}
		
		final String proxyTicket = assertion.getPrincipal().getProxyTicketFor(clearPassUrl);
		
		final String clearPassRequestUrl = clearPassUrl + "?" + "ticket=" + proxyTicket + "&" + "service=" + URLEncoder.encode(clearPassUrl);
		
		final String response = CommonUtils.getResponseFromServer(clearPassRequestUrl);
		
		final String password = XmlUtils.getTextForElement(response, "credentials"); 

		return password; 
	}
	
	protected User addUser(long companyId, String screenName)
		throws SystemException {

		try {
			String baseDN = PrefsPropsUtil.getString(
				companyId, PropsKeys.LDAP_BASE_DN);

			LdapContext ctx = PortalLDAPUtil.getContext(companyId);

			if (ctx == null) {
				throw new SystemException("Failed to bind to the LDAP server");
			}

			String filter = PrefsPropsUtil.getString(
				companyId, PropsKeys.LDAP_AUTH_SEARCH_FILTER);

			if (_log.isDebugEnabled()) {
				_log.debug("Search filter before transformation " + filter);
			}

			filter = StringUtil.replace(
				filter,
				new String[] {
					"@company_id@", "@email_address@", "@screen_name@"
				},
				new String[] {
					String.valueOf(companyId), StringPool.BLANK, screenName
				});

			if (_log.isDebugEnabled()) {
				_log.debug("Search filter after transformation " + filter);
			}

			SearchControls cons = new SearchControls(
				SearchControls.SUBTREE_SCOPE, 1, 0, null, false, false);

			NamingEnumeration<SearchResult> enu = ctx.search(
				baseDN, filter, cons);

			if (enu.hasMoreElements()) {
				if (_log.isDebugEnabled()) {
					_log.debug("Search filter returned at least one result");
				}

				Binding binding = enu.nextElement();

				Attributes attrs = PortalLDAPUtil.getUserAttributes(
					companyId, ctx,
					PortalLDAPUtil.getNameInNamespace(companyId, binding));

				return PortalLDAPUtil.importLDAPUser(
					companyId, ctx, attrs, StringPool.BLANK, true);
			}
			else {
				throw new NoSuchUserException(
					"User " + screenName + " was not found in the LDAP server");
			}
		}
		catch (Exception e) {
			_log.error("Problem accessing LDAP server ", e);

			throw new SystemException(
				"Problem accessign LDAP server " + e.getMessage());
		}
	}

	private static Log _log = LogFactoryUtil.getLog(CASAutoLogin.class);
	public final static String CONST_CAS_ASSERTION= "_const_cas_assertion_";

}