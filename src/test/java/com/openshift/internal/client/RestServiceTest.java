/******************************************************************************* 
 * Copyright (c) 2012 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/
package com.openshift.internal.client;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

import com.openshift.internal.client.httpclient.EncodingException;
import org.junit.Before;
import org.junit.Test;

import com.openshift.client.HttpMethod;
import com.openshift.client.IHttpClient;
import com.openshift.client.Message;
import com.openshift.client.OpenShiftEndpointException;
import com.openshift.client.OpenShiftException;
import com.openshift.client.utils.MessageAssert;
import com.openshift.client.utils.OpenShiftTestConfiguration;
import com.openshift.client.utils.Samples;
import com.openshift.internal.client.httpclient.HttpClientException;
import com.openshift.internal.client.httpclient.NotFoundException;
import com.openshift.internal.client.response.Link;
import com.openshift.internal.client.response.LinkParameter;
import com.openshift.internal.client.response.LinkParameterType;
import com.openshift.internal.client.response.RestResponse;

/**
 * @author Andre Dietisheim
 */
public class RestServiceTest {

	protected static final String KEY_PROTOCOL_VERSION = "protocol_version";
	private IRestService service;
	private HttpClientMockDirector mockDirector;
	private IHttpClient clientMock;

	@Before
	public void setUp() throws FileNotFoundException, IOException, OpenShiftException, HttpClientException {
		this.mockDirector = new HttpClientMockDirector();
		String jsonResponse = "{}";
		this.clientMock = mockDirector
				.mockGetAny(jsonResponse)
				.mockPostAny(jsonResponse)
				.mockPutAny(jsonResponse)
				.mockDeleteAny(jsonResponse)
				.client();
		when(clientMock.getAcceptVersion()).thenReturn(IRestService.SERVICE_VERSION);
		
		OpenShiftTestConfiguration configuration = new OpenShiftTestConfiguration();
		this.service = new RestService(configuration.getStagingServer(), configuration.getClientId(), clientMock);
	}

	@Test(expected = OpenShiftException.class)
	public void throwsIfRequiredParameterMissing() throws OpenShiftException, SocketTimeoutException {
		// operation
		LinkParameter parameter =
				new LinkParameter("required string parameter", LinkParameterType.STRING, null, null, null);
		Link link = new Link("1 required parameter", "/dummy", HttpMethod.GET, Arrays.asList(parameter), null);
		service.request(link, new HashMap<String, Object>());
	}

	@Test
	public void shouldNotThrowIfNoReqiredParameter() throws OpenShiftException, SocketTimeoutException {
		// operation
		Link link = new Link("0 required parameter", "/dummy", HttpMethod.GET, null, null);
		service.request(link, new HashMap<String, Object>());
	}

	@Test
	public void shouldGetIfGetHttpMethod() throws OpenShiftException, SocketTimeoutException, HttpClientException {
		// operation
		service.request(new Link("0 required parameter", "http://www.redhat.com", HttpMethod.GET, null, null));
		// verifications
		mockDirector.verifyGetAny(1);
	}

	@Test
	public void shouldPostIfPostHttpMethod() throws OpenShiftException, SocketTimeoutException, HttpClientException,
            EncodingException {
		// operation
		service.request(new Link("0 required parameter", "http://www.redhat.com", HttpMethod.POST, null, null));
		// verifications
		mockDirector.verifyPostAny(1);
	}

	@Test
	public void shouldPutIfPutHttpMethod() throws OpenShiftException, SocketTimeoutException, HttpClientException,
            EncodingException {
		// operation
		service.request(new Link("0 required parameter", "http://www.redhat.com", HttpMethod.PUT, null, null));
		// verifications
		mockDirector.verifyPutAny(1);
	}

	@Test
	public void shouldDeleteIfDeleteHttpMethod() throws OpenShiftException, SocketTimeoutException,
			HttpClientException, EncodingException {
		// operation
		service.request(new Link("0 required parameter", "http://www.redhat.com", HttpMethod.DELETE, null, null));
		// verifications
		mockDirector.verifyDeleteAny(1);
	}

	@Test
	public void shouldNotAddServerToAbsUrl() throws OpenShiftException, SocketTimeoutException, HttpClientException,
			MalformedURLException {
		// operation
		String url = "http://www.redhat.com";
		service.request(new Link("0 required parameter", url, HttpMethod.GET, null, null));
		// verifications
		mockDirector.verifyGet(url, 1);
	}

	@Test
	public void shouldAddServerToPath() throws OpenShiftException, SocketTimeoutException, HttpClientException,
			MalformedURLException {
		// operation
		String url = "/adietisheim-redhat";
		service.request(new Link("0 require parameter", url, HttpMethod.GET, null, null));
		// verifications
		String targetUrl = service.getServiceUrl() + url.substring(1, url.length());
		mockDirector.verifyGet(targetUrl, 1);
	}

	@Test
	public void shouldNotAddBrokerPathIfPresent() throws OpenShiftException, SocketTimeoutException,
			HttpClientException, MalformedURLException {
		// operation
		String url = "/broker/rest/adietisheim-redhat";
		service.request(new Link("0 require parameter", url, HttpMethod.GET, null, null));
		// verifications
		String targetUrl = service.getPlatformUrl() + url;
		mockDirector.verifyGet(targetUrl, 1);
	}

	@Test
	public void shouldThrowExceptionWithResponseOnNotFound() throws Throwable {
		try {
			// pre-conditions
			mockDirector.mockGetAny(new NotFoundException(Samples.GET_DOMAINS_FOOBAR_KO_NOTFOUND.getContentAsString()));
			// operation
			service.request(new Link("0 require parameter", "/broker/rest/adietisheim", HttpMethod.GET, null, null));
			// verifications
			fail("OpenShiftEndPointException expected, did not occurr");
		} catch (OpenShiftEndpointException e) {
			assertThat(e.getRestResponse()).isNotNull();
		}
	}

	@Test
	public void shouldHaveMessageIfErrors() throws Throwable {
		try {
			// pre-conditions
			mockDirector.mockPostAny(new HttpClientException(Samples.POST_FOOBAR_DOMAINS_KO_INUSE.getContentAsString()));
			// operation
			service.request(new Link("0 require parameter", "/broker/rest/domains", HttpMethod.POST, null, null));
			// verifications
			fail("OpenShiftEndPointException expected, did not occurr");
		} catch (OpenShiftEndpointException e) {
			RestResponse restResponse = e.getRestResponse();
			assertThat(restResponse).isNotNull();
			assertThat(restResponse.getMessages().size()).isEqualTo(1);
			Message message = restResponse.getMessages().getAll().iterator().next();
			assertThat(new MessageAssert(message))
					.hasText("Namespace 'foobar' is already in use. Please choose another.")
					.hasSeverity(Severity.ERROR)
					.hasExitCode(103)
					.hasField(new Field("id"));
		}
	}

	@Test
	public void shouldReportPlatformUrlInException() throws Throwable {
		try {
			// pre-conditions
			mockDirector.mockPostAny(new HttpClientException(Samples.POST_FOOBAR_DOMAINS_KO_INUSE.getContentAsString()));
			// operation
			service.request(new Link("0 require parameter", "/broker/rest/domains", HttpMethod.POST, null, null));
			// verifications
			fail("OpenShiftEndPointException expected, did not occurr");
		} catch (OpenShiftEndpointException e) {
			assertThat(e.getMessage()).contains(service.getPlatformUrl());
		}
	}

	@Test
	public void shouldReturnPlatformWithSchema() throws Throwable {
		// pre-conditions
		final String serverUrl = "nonHttpUrl";
		IRestService service = new RestService(serverUrl, new OpenShiftTestConfiguration().getClientId(), clientMock);
		// operation
		String platformUrl = service.getPlatformUrl();
		// verifications
		assertThat(platformUrl).startsWith("https://");
		assertThat(platformUrl).isEqualTo("https://" + serverUrl);
	}

	@Test
	public void shouldReturnUnchangedPlatformUrl() throws Throwable {
		// pre-conditions
		final String serverUrl = "http://fakeUrl";
		IRestService service = new RestService(serverUrl, new OpenShiftTestConfiguration().getClientId(), clientMock);
		// operation
		String platformUrl = service.getPlatformUrl();
		// verifications
		assertThat(platformUrl).isEqualTo(serverUrl);
	}

	@Test
	public void shouldSetUserAgentToHttpClient() throws OpenShiftException, SocketTimeoutException, HttpClientException {
		// pre-condition
		RestServiceProperties properties = new RestServiceProperties() {
			protected Properties getProperties() throws IOException {
				Properties properties = new Properties();
				properties.put(KEY_USERAGENTPATTERN, "{0} {1}");
				return properties;
			}
		};
		IHttpClient httpClientMock = mock(IHttpClient.class);

		// operation
		String clientId = "unit-test";
		new RestService("jboss.org", clientId, properties, httpClientMock);

		// verifications
		String userAgent = properties.getUseragent(clientId);
		verify(httpClientMock, times(1)).setUserAgent(userAgent);
	}

	@Test
	public void shouldDefaultTo12ProtocolVersion() 
			throws OpenShiftException, SocketTimeoutException, HttpClientException, EncodingException {
		// pre-condition
		RestServiceProperties properties = new RestServiceProperties();
		IHttpClient httpClientMock = mock(IHttpClient.class);

		// operation
		String clientId = "unit-test";
		new RestService("jboss.org", clientId, properties, httpClientMock);

		// verifications
		verify(clientMock, times(1)).setAcceptVersion("1.2");
	}
	
	@Test
	public void shouldUseGivenProtocolVersion() throws OpenShiftException, SocketTimeoutException, HttpClientException {
		// pre-condition
		RestServiceProperties properties = new RestServiceProperties();
		IHttpClient httpClientMock = mock(IHttpClient.class);

		// operation
		String clientId = "unit-test";
		new RestService("jboss.org", clientId, "5.2", properties, httpClientMock);

		// verifications
		verify(httpClientMock, times(1)).setAcceptVersion("5.2");
	}
}
