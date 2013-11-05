/**
 * $URL$
 * $Id$
 *
 * Copyright (c) 2009 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *			 http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sakaiproject.lti2;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;
import net.oauth.OAuthValidator;
import net.oauth.SimpleOAuthValidator;
import net.oauth.server.OAuthServlet;
import net.oauth.signature.OAuthSignatureMethod;

import org.json.simple.JSONValue;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

import org.sakaiproject.component.cover.ComponentManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.imsglobal.basiclti.BasicLTIUtil;
import org.sakaiproject.component.cover.ServerConfigurationService;
// import org.sakaiproject.tool.api.Tool;
// import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.util.ResourceLoader;
import org.sakaiproject.basiclti.util.SakaiBLTIUtil;
import org.imsglobal.basiclti.BasicLTIConstants;
import org.imsglobal.lti2.LTI2Constants;
import org.imsglobal.lti2.objects.*;
import org.sakaiproject.lti2.SakaiLTI2Services;

import org.imsglobal.json.IMSJSONRequest;

import org.sakaiproject.lti.api.LTIService;
import org.sakaiproject.util.foorm.SakaiFoorm;
import org.sakaiproject.util.foorm.FoormUtil;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;

/**
 * Notes:
 * 
 * This program is directly exposed as a URL to receive IMS Basic LTI messages
 * so it must be carefully reviewed and any changes must be looked at carefully.
 * 
 */

@SuppressWarnings("deprecation")
public class LTI2Service extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static Log M_log = LogFactory.getLog(LTI2Service.class);
	private static ResourceLoader rb = new ResourceLoader("blis");

	protected static SakaiFoorm foorm = new SakaiFoorm();

	protected static LTIService ltiService = null;

    protected String resourceUrl = null;
    protected Service_offered LTI2ResultItem = null;
    protected Service_offered LTI2LtiLinkSettings = null;
    protected Service_offered LTI2ToolProxySettings = null;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		if ( ltiService == null ) ltiService = (LTIService) ComponentManager.get("org.sakaiproject.lti.api.LTIService");

		resourceUrl = SakaiBLTIUtil.getOurServerUrl() + "/imsblis/lti2";
        LTI2ResultItem = StandardServices.LTI2ResultItem(resourceUrl);
        LTI2LtiLinkSettings = StandardServices.LTI2LtiLinkSettings(resourceUrl);
        LTI2ToolProxySettings = StandardServices.LTI2ToolProxySettings(resourceUrl);
	}

	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request,response);
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request,response);
	}

	protected void getToolConsumerProfile(HttpServletRequest request, 
			HttpServletResponse response,String profile_id)
	{
System.out.println("profile_id="+profile_id);
		Map<String,Object> deploy = ltiService.getDeployForConsumerKeyDao(profile_id);
		if ( deploy == null ) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND); 
			return;
		}
System.out.println("deploy="+deploy);

		String serverUrl = SakaiBLTIUtil.getOurServerUrl();
		Product_family fam = new Product_family("SakaiCLE", "CLE", "Sakai Project",
				"Amazing open source Collaboration and Learning Environment.", 
				"http://www.sakaiproject.org", "support@sakaiproject.org");

		Product_info info = new Product_info("CTools", "4.0", "The Sakai installation for UMich", fam);
		Service_owner sowner = new Service_owner("https://ctools.umich.edu/", "CTools", "Description", "support@ctools.umich.edu");
		Service_provider powner = new Service_provider("https://ctools.umich.edu/", "CTools", "Description", "support@ctools.umich.edu");

		Product_instance instance = new Product_instance("ctools-001", info, sowner, powner, "support@ctools.umich.edu");

		ToolConsumer consumer = new ToolConsumer(profile_id+"", resourceUrl, instance);
		List<String> capabilities = consumer.getCapability_offered();

		if (foorm.getLong(deploy.get(LTIService.LTI_SENDEMAILADDR)) > 0 ) {
			capabilities.add("Person.email.primary");
		}

		if (foorm.getLong(deploy.get(LTIService.LTI_SENDNAME)) > 0 ) {
            capabilities.add("User.username");
			capabilities.add("Person.name.fullname");
			capabilities.add("Person.name.given");
			capabilities.add("Person.name.family");
            capabilities.add("Person.name.full");
		}

		List<Service_offered> services = consumer.getService_offered();
		services.add(StandardServices.LTI2Registration(serverUrl+"/imsblis/lti2/tc_registration/"+profile_id));

		if (foorm.getLong(deploy.get(LTIService.LTI_ALLOWOUTCOMES)) > 0 ) {
			services.add(LTI2ResultItem);
			services.add(StandardServices.LTI1Outcomes(serverUrl+"/imsblis/service/"));
			services.add(SakaiLTI2Services.BasicOutcomes(serverUrl+"/imsblis/service/"));
			capabilities.add("Result.sourcedId");
			capabilities.add("Result.autocreate");
			capabilities.add("Result.url");
		}
		if (foorm.getLong(deploy.get(LTIService.LTI_ALLOWROSTER)) > 0 ) {
			services.add(SakaiLTI2Services.BasicRoster(serverUrl+"/imsblis/service/"));
		}
		if (foorm.getLong(deploy.get(LTIService.LTI_ALLOWSETTINGS)) > 0 ) {
			services.add(SakaiLTI2Services.BasicSettings(serverUrl+"/imsblis/service/"));
			services.add(LTI2LtiLinkSettings);
			services.add(LTI2ToolProxySettings);
			capabilities.add("LtiLink.custom.url");
			capabilities.add("ToolProxy.custom.url");
			capabilities.add("ToolProxyBinding.custom.url");
		}

		if (foorm.getLong(deploy.get(LTIService.LTI_ALLOWLORI)) > 0 ) {
			services.add(SakaiLTI2Services.LORI_XML(serverUrl+"/imsblis/service/"));
		}

		ObjectMapper mapper = new ObjectMapper();
		try {
			// http://stackoverflow.com/questions/6176881/how-do-i-make-jackson-pretty-print-the-json-content-it-generates
			ObjectWriter writer = mapper.defaultPrettyPrintingWriter();
			// ***IMPORTANT!!!*** for Jackson 2.x use the line below instead of the one above: 
			// ObjectWriter writer = mapper.writer().withDefaultPrettyPrinter();
			// System.out.println(mapper.writeValueAsString(consumer));
			response.setContentType("application/json");
			PrintWriter out = response.getWriter();
			out.println(writer.writeValueAsString(consumer));
			// System.out.println(writer.writeValueAsString(consumer));
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	// /imsblis/lti2/part3/part4
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
		throws ServletException, IOException 
	{
		String ipAddress = request.getRemoteAddr();
		M_log.debug("Basic LTI Service request from IP=" + ipAddress);

		String rpi = request.getPathInfo();
		String uri = request.getRequestURI();
		String [] parts = uri.split("/");
		if ( parts.length < 4 ) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST); 
			doErrorJSON(request, response, null, "request.bad.url", "Incorrect url format", null);
			return;
		}
		String controller = parts[3];
		if ( "tc_profile".equals(controller) && parts.length == 5 ) {
			String profile_id = parts[4];
			getToolConsumerProfile(request,response,profile_id);
			return;
		} else if ( "tc_registration".equals(controller) && parts.length == 5 ) {
			String profile_id = parts[4];
			registerToolProviderProfile(request, response, profile_id);
			return;
		} else if ( "Result".equals(controller) && parts.length == 5 ) {
			String sourcedid = parts[4];
			handleResultRequest(request, response, sourcedid);
			return;
		} else if ( "Settings".equals(controller) && parts.length >= 6 ) {
			handleSettingsRequest(request, response, parts);
			return;
		}

System.out.println("Controller="+controller);
		IMSJSONRequest jsonRequest = new IMSJSONRequest(request);
		if ( jsonRequest.valid ) {
		    System.out.println(jsonRequest.getPostBody());
		}

		response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED); 
		doErrorJSON(request, response, null, "request.not.implemented", "Unknown request", null);
	}

	public void registerToolProviderProfile(HttpServletRequest request,HttpServletResponse response, 
			String profile_id) throws java.io.IOException
	{
System.out.println("profile_id="+profile_id);
		Map<String,Object> deploy = ltiService.getDeployForConsumerKeyDao(profile_id);
		if ( deploy == null ) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND); 
			return;
		}
		Long deployKey = foorm.getLong(deploy.get(LTIService.LTI_ID));
System.out.println("deployKey="+deployKey);

		// See if we can even register...
		Long reg_state = foorm.getLong(deploy.get(LTIService.LTI_REG_STATE));
		String key = null;
		String secret = null;
		if ( reg_state == 0 ) {
			key = (String) deploy.get(LTIService.LTI_REG_KEY);
			secret = (String) deploy.get(LTIService.LTI_REG_PASSWORD);
		} else {
			key = (String) deploy.get(LTIService.LTI_CONSUMERKEY);
			secret = (String) deploy.get(LTIService.LTI_SECRET);
		}

		IMSJSONRequest jsonRequest = new IMSJSONRequest(request);

		if ( ! jsonRequest.valid ) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			doErrorJSON(request, response, jsonRequest, "deploy.register.valid", "Request is not in a valid format", null);
			return;
		}
		// System.out.println(jsonRequest.getPostBody());

		// Lets check the signature
		if ( key == null || secret == null ) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN); 
			doErrorJSON(request, response, jsonRequest, "deploy.register.credentials", "Deployment is missing credentials", null);
			return;
		}

		jsonRequest.validateRequest(key, secret, request);
		if ( !jsonRequest.valid ) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN); 
			doErrorJSON(request, response, jsonRequest, "deploy.register.signature", "OAuth signature failure", null);
			return;
		}

System.out.println("YO");
		JSONObject providerProfile = (JSONObject) JSONValue.parse(jsonRequest.getPostBody());
		System.out.println("OBJ:"+providerProfile);
		if ( providerProfile == null  ) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			doErrorJSON(request, response, jsonRequest, "deploy.register.parse", "JSON parse failed", null);
			return;
		}

		JSONObject security_contract = (JSONObject) providerProfile.get(LTI2Constants.SECURITY_CONTRACT);
		if ( security_contract == null  ) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			doErrorJSON(request, response, jsonRequest, "deploy.register.parse", "JSON missing security_contract", null);
			return;
		}

		String shared_secret = (String) security_contract.get(LTI2Constants.SHARED_SECRET);
		if ( shared_secret == null  ) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			doErrorJSON(request, response, jsonRequest, "deploy.register.parse", "JSON missing shared_secret", null);
			return;
		}
		// Blank out the new shared secret
		security_contract.put(LTI2Constants.SHARED_SECRET, "*********");

		// Parse the tool profile bit and extract the tools with error checking
		List<Properties> theTools = new ArrayList<Properties> ();
		Properties info = new Properties();
		try {
			String [] retval = BasicLTIUtil.parseToolProfile(theTools, info, providerProfile);
System.out.println("info = " + info);
			if ( retval != null ) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				doErrorJSON(request, response, jsonRequest, retval[0], retval[1], null);
				return;
			}
		}
		catch (Exception e ) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			doErrorJSON(request, response, jsonRequest, "deploy.parse.exception", "Exception:"+ e.getLocalizedMessage(), e);
			return;
		}

		if ( theTools.size() < 1 ) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			doErrorJSON(request, response, jsonRequest, "deploy.register.notools", "No tools found in profile", null);
			return;
		}

		// TODO: Loop through and validate all of the launch urls in the tools

		// TODO: Check all the services to make sure we like them ....

		Map<String, Object> deployUpdate = new TreeMap<String, Object> ();

		// TODO: Make sure to encrypt that password...
		deployUpdate.put(LTIService.LTI_SECRET, shared_secret);

		// Indicate ready to validate and kill the interim info
		deployUpdate.put(LTIService.LTI_REG_STATE, "1");
		deployUpdate.put(LTIService.LTI_REG_KEY, "");
		deployUpdate.put(LTIService.LTI_REG_PASSWORD, "");
System.out.println("deployUpdate="+deployUpdate);

		deployUpdate.put(LTIService.LTI_REG_PROFILE, providerProfile.toString());
		Object obj = ltiService.updateDeployDao(deployKey, deployUpdate);
		boolean success = ( obj instanceof Boolean ) && ( (Boolean) obj == Boolean.TRUE);

		Map jsonResponse = new TreeMap();
		jsonResponse.put("@context",StandardServices.TOOLPROXY_ID_CONTEXT);
		jsonResponse.put("@type", StandardServices.TOOLPROXY_ID_TYPE);
		String serverUrl = ServerConfigurationService.getServerUrl();
		jsonResponse.put("@id", resourceUrl+"/tc_registration/"+profile_id);
		jsonResponse.put(LTI2Constants.TOOL_PROXY_GUID, profile_id);
		jsonResponse.put(LTI2Constants.CUSTOM_URL, resourceUrl+"/Settings/ToolProxy/"+profile_id);
		response.setContentType(StandardServices.TOOLPROXY_ID_FORMAT);
		response.setStatus(HttpServletResponse.SC_CREATED); // TODO: Get this right
		String jsonText = JSONValue.toJSONString(jsonResponse);
		M_log.debug(jsonText);
		PrintWriter out = response.getWriter();
		out.println(jsonText);
	}

	public void handleResultRequest(HttpServletRequest request,HttpServletResponse response, 
			String sourcedid) throws java.io.IOException
	{
		Object retval = null;
		IMSJSONRequest jsonRequest = null;
		if ( "GET".equals(request.getMethod()) ) { 
			retval = SakaiBLTIUtil.getGrade(sourcedid, request, ltiService);
			if ( ! (retval instanceof Map) ) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				doErrorJSON(request,response, jsonRequest, "outcomes.error", (String) retval, null);
				return;
			}
			Map grade = (Map) retval;
			Map jsonResponse = new TreeMap();
			Map resultScore = new TreeMap();
	
			jsonResponse.put("@context",StandardServices.RESULT_CONTEXT);
			jsonResponse.put("@type", StandardServices.RESULT_TYPE);
			jsonResponse.put("comment", grade.get("comment"));
			resultScore.put("@type", "decimal");
			resultScore.put("@value", grade.get("grade"));
			jsonResponse.put("resultScore",resultScore);
			response.setContentType(StandardServices.RESULT_FORMAT);
			response.setStatus(HttpServletResponse.SC_OK);
			String jsonText = JSONValue.toJSONString(jsonResponse);
			M_log.debug(jsonText);
			PrintWriter out = response.getWriter();
			out.println(jsonText);
		} else if ( "PUT".equals(request.getMethod()) ) { 
			retval = "Error parsing input data";
			try {
				jsonRequest = new IMSJSONRequest(request);
				// System.out.println(jsonRequest.getPostBody());
				JSONObject requestData = (JSONObject) JSONValue.parse(jsonRequest.getPostBody());
				String comment = (String) requestData.get("comment");
				JSONObject resultScore = (JSONObject) requestData.get("resultScore");
				String sGrade = (String) resultScore.get("@value");
				Double dGrade = new Double(sGrade);
				retval = SakaiBLTIUtil.setGrade(sourcedid, request, ltiService, dGrade, comment);
			} catch (Exception e) {
				retval = "Error: "+ e.getMessage();
			}
			if ( retval instanceof Boolean && (Boolean) retval ) {
				response.setStatus(HttpServletResponse.SC_CREATED);
			} else {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			}
		} else {
			retval = "Unsupported operation:" + request.getMethod();
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	
		if ( retval instanceof String ) {
			doErrorJSON(request,response, jsonRequest, "outcomes.error", (String) retval, null);
			return;
		}
	}

	public void handleSettingsRequest(HttpServletRequest request,HttpServletResponse response, 
			String[] parts) throws java.io.IOException
	{
        String URL = SakaiBLTIUtil.getOurServletPath(request);
		String scope = parts[4];

		// Check to see if we are doing the bubble
		String bubbleStr = request.getParameter("bubble");
		boolean bubble = bubbleStr != null && "all".equals(bubbleStr) && "GET".equals(request.getMethod());

		// Check our input and output formats
		String acceptHdr = request.getHeader("Accept");
		String contentHdr = request.getHeader("Content-type");
		boolean acceptSimple = acceptHdr == null || acceptHdr.indexOf(StandardServices.TOOLSETTINGS_SIMPLE_FORMAT) >= 0 ;
		boolean acceptComplex = acceptHdr == null || acceptHdr.indexOf(StandardServices.TOOLSETTINGS_FORMAT) >= 0 ;
		if ( contentHdr == null ) contentHdr = request.getHeader("Content-Type");
		boolean inputSimple = contentHdr == null || contentHdr.indexOf(StandardServices.TOOLSETTINGS_SIMPLE_FORMAT) >= 0 ;
		boolean inputComplex = contentHdr != null && contentHdr.indexOf(StandardServices.TOOLSETTINGS_FORMAT) >= 0 ;
System.out.println("as="+acceptSimple+" ac="+acceptComplex+" is="+inputSimple+" ic="+inputComplex);

		// Check the JSON on PUT and check the oauth_body_hash
		IMSJSONRequest jsonRequest = null;
		JSONObject requestData = null;
		if ( "PUT".equals(request.getMethod()) ) {
			try {
				jsonRequest = new IMSJSONRequest(request);
				requestData = (JSONObject) JSONValue.parse(jsonRequest.getPostBody());
			} catch (Exception e) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				doErrorJSON(request,response, jsonRequest, "outcomes.error", "Could not parse JSON", e);
				return;
			}
		}

		String consumer_key = null;
		String siteId = null;
		String placement_id = null;

		Map<String,Object> content = null;
		Long contentKey = null;
		Map<String,Object> tool = null;
		Long toolKey = null;
		Map<String,Object> proxyBinding = null;
		Long proxyBindingKey = null;
		Map<String,Object> deploy = null;
		Long deployKey = null;

		if ( "LtiLink".equals(scope) || "ToolProxyBinding".equals(scope) ) {
			placement_id = parts[5];
System.out.println("placement_id="+placement_id);
			String contentStr = placement_id.substring(8);
			contentKey = SakaiBLTIUtil.getLongKey(contentStr);
			if ( contentKey  >= 0 ) {
				// Leave off the siteId - bypass all checking - because we need to 
				// find the siteId from the content item
				content = ltiService.getContentDao(contentKey);
				if ( content != null ) siteId = (String) content.get(LTIService.LTI_SITE_ID);
			}
	
			if ( content == null || siteId == null ) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				doErrorJSON(request,response, jsonRequest, "outcomes.error", "Bad content item", null);
				return;
			}
	
			toolKey = SakaiBLTIUtil.getLongKey(content.get(LTIService.LTI_TOOL_ID));
			if ( toolKey >= 0 ) {
				tool = ltiService.getToolDao(toolKey, siteId);
			}
		
			if ( tool == null ) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				doErrorJSON(request,response, jsonRequest, "outcomes.error", "Bad tool item", null);
				return;
			}
	
			// TODO: Check settings to see if we are allowed to do this :)
	
			// Adjust the content items based on the tool items
			ltiService.filterContent(content, tool);

		}

		if ( "ToolProxyBinding".equals(scope) || ( "LtiLink".equals(scope) && bubble ) ) {
System.out.println("ToolProxyBinding toolKey="+toolKey+" siteId="+siteId);
			proxyBinding = ltiService.getProxyBindingDao(toolKey,siteId);
			if ( proxyBinding != null ) {
				proxyBindingKey = SakaiBLTIUtil.getLongKey(proxyBinding.get(LTIService.LTI_ID));
			}
System.out.println("proxyBindingKey="+proxyBindingKey);
System.out.println("proxyBinding="+proxyBinding);
		}


		// Retrieve the deployment if needed
		if ( "ToolProxy".equals(scope) ) {
			consumer_key = parts[5];
System.out.println("consumer_key="+consumer_key);
			deploy = ltiService.getDeployForConsumerKeyDao(consumer_key);
			if ( deploy == null ) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				doErrorJSON(request,response, jsonRequest, "outcomes.error", "Bad deploy item", null);
				return;
			}
			deployKey = SakaiBLTIUtil.getLongKey(deploy.get(LTIService.LTI_ID));
System.out.println("deployKey="+deployKey);
		} else if ( bubble ) {
			deployKey = SakaiBLTIUtil.getLongKey(tool.get(LTIService.LTI_DEPLOYMENT_ID));
			if ( deployKey >= 0 ) {
				deploy = ltiService.getDeployDao(deployKey);
			}
			if ( deploy == null ) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				doErrorJSON(request,response, jsonRequest, "outcomes.error", "Bad deploy item", null);
				return;
			}
			consumer_key = (String) deploy.get(LTIService.LTI_CONSUMERKEY);
		}

		// Get the old settings and secret
		String settings = null;
		String oauth_secret = null;
		if ( "LtiLink".equals(scope) ) {
			settings = (String) content.get(LTIService.LTI_SETTINGS);
			oauth_secret = (String) content.get(LTIService.LTI_SECRET);
			if ( oauth_secret == null || oauth_secret.length() < 1 ) {
				oauth_secret = (String) tool.get(LTIService.LTI_SECRET);
			}
		} else if ( "ToolProxyBinding".equals(scope) ) {
			if ( proxyBinding != null ) {
				settings = (String) proxyBinding.get(LTIService.LTI_SETTINGS);
			}
			oauth_secret = (String) tool.get(LTIService.LTI_SECRET);
		} else if ( "ToolProxy".equals(scope) ) {
			settings = (String) deploy.get(LTIService.LTI_SETTINGS);
			oauth_secret = (String) deploy.get(LTIService.LTI_SECRET);
		} else {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			doErrorJSON(request,response, jsonRequest, "outcomes.error", "Bad Setttings Scope="+scope, null);
			return;
		}

        // Validate the incoming message
        Object retval = SakaiBLTIUtil.validateMessage(request, URL, oauth_secret);
        if ( retval instanceof String ) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN); 
			doErrorJSON(request,response, jsonRequest, "outcomes.error", (String) retval, null);
			return;
		}

		if ( "GET".equals(request.getMethod()) && acceptSimple ) { 
			if ( settings == null || settings.length() < 1 ) {
				settings = "{\n}\n";
			}
			response.setContentType(StandardServices.TOOLSETTINGS_SIMPLE_FORMAT);
			response.setStatus(HttpServletResponse.SC_OK); 
			PrintWriter out = response.getWriter();
			out.println(settings);
			return;
		// Complex format
		} else if ( "GET".equals(request.getMethod()) ) {
			JSONObject jsonResponse = new JSONObject();
			jsonResponse.put("@context","http://purl.imsglobal.org/ctx/lti/v2/ToolSettings");
			JSONArray graph = new JSONArray();
			JSONObject sjson = null;
			JSONObject cjson = null;
			String settingsUrl = SakaiBLTIUtil.getOurServerUrl() + "/imsblis/lti2/Settings";
			String endpoint = null;
			boolean bubbled = false;
			if ( "LtiLink".equals(scope) ) {
				settings = (String) content.get(LTIService.LTI_SETTINGS);
				if ( settings == null || settings.length() < 1 ) {
					settings = "{\n}\n";
				}
				sjson = (JSONObject) JSONValue.parse(settings);
				if ( sjson != null ) {
					endpoint = settingsUrl + "/LtiLink/" + placement_id;
					cjson = new JSONObject();
					cjson.put("@id",endpoint);
					cjson.put("@type",scope);
					cjson.put("custom",sjson);
					graph.add(cjson);
				}
				if ( bubble ) bubbled = true;
			} 
			if ( bubbled || "ToolProxyBinding".equals(scope) ) {
				settings = null;
				if ( proxyBinding != null ) {
					settings = (String) proxyBinding.get(LTIService.LTI_SETTINGS);
				}
				if ( settings == null || settings.length() < 1 ) {
					settings = "{\n}\n";
				}
				sjson = (JSONObject) JSONValue.parse(settings);
				if ( sjson != null ) {
					endpoint = settingsUrl + "/ToolProxyBinding/" + placement_id;
					cjson = new JSONObject();
					cjson.put("@id",endpoint);
					cjson.put("@type","ToolProxyBinding");
					cjson.put("custom",sjson);
					graph.add(cjson);
				}
				if ( bubble ) bubbled = true;
			} 
			if ( deploy != null && ( bubbled || "ToolProxy".equals(scope) ) ) {
				settings = (String) deploy.get(LTIService.LTI_SETTINGS);
				if ( settings == null || settings.length() < 1 ) {
					settings = "{\n}\n";
				}
				sjson = (JSONObject) JSONValue.parse(settings);
				if ( sjson != null ) {
					endpoint = settingsUrl + "/ToolProxy/" + consumer_key;
					cjson = new JSONObject();
					cjson.put("@id",endpoint);
					cjson.put("@type","ToolProxy");
					cjson.put("custom",sjson);
					graph.add(cjson);
				}
			}
			jsonResponse.put("@graph",graph);
			response.setContentType(StandardServices.TOOLSETTINGS_FORMAT);
			response.setStatus(HttpServletResponse.SC_OK); 
			PrintWriter out = response.getWriter();
			out.println(jsonResponse.toString());
			return;
		} if ( "PUT".equals(request.getMethod()) ) {
			// This is assuming the rule that a PUT of the complex settings
			// format that there is only one entry in the graph and it is
			// the same as our current URL.  We parse without much checking.
			try {
				JSONArray graph = (JSONArray) requestData.get("@graph");
				if ( graph.size() != 1 ) {
					response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
					doErrorJSON(request,response, jsonRequest, "outcomes.error", "Only one graph entry allowed", null);
					return;
				}
				JSONObject firstChild = (JSONObject) graph.get(0);
				JSONObject custom = (JSONObject) firstChild.get("custom");
				settings = custom.toString();
			} catch (Exception e) {
				settings = jsonRequest.getPostBody();
			}

			retval = null;
			if ( "LtiLink".equals(scope) ) {
				content.put(LTIService.LTI_SETTINGS, settings);
				retval = ltiService.updateContentDao(contentKey,content,siteId);
			} else if ( "ToolProxyBinding".equals(scope) ) {
				if ( proxyBinding != null ) {
					proxyBinding.put(LTIService.LTI_SETTINGS, settings);
					retval = ltiService.updateProxyBindingDao(proxyBindingKey,proxyBinding);
				} else { 
					Properties proxyBindingNew = new Properties();
					proxyBindingNew.setProperty(LTIService.LTI_SITE_ID, siteId);
					proxyBindingNew.setProperty(LTIService.LTI_TOOL_ID, toolKey+"");
					proxyBindingNew.setProperty(LTIService.LTI_SETTINGS, settings);
System.out.println("proxyBindingNew="+proxyBindingNew);
					retval = ltiService.insertProxyBindingDao(proxyBindingNew);
System.out.println("retval="+retval);
					M_log.info("inserted ProxyBinding setting="+proxyBindingNew);
				}
			} else if ( "ToolProxy".equals(scope) ) {
				deploy.put(LTIService.LTI_SETTINGS, settings);
				retval = ltiService.updateDeployDao(deployKey,deploy);
			}
			if ( retval instanceof String || 
				( retval instanceof Boolean && ((Boolean) retval != Boolean.TRUE) ) ) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				doErrorJSON(request,response, jsonRequest, "outcomes.error", (String) retval, null);
				return;
			}
			response.setStatus(HttpServletResponse.SC_CREATED);
		} else {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			doErrorJSON(request,response, jsonRequest, "outcomes.error", "Method not handled="+request.getMethod(), null);
		}
	}

	/* IMS JSON version of Errors */
	public void doErrorJSON(HttpServletRequest request,HttpServletResponse response, 
			IMSJSONRequest json, String s, String message, Exception e) 
		throws java.io.IOException 
		{
			if (e != null) {
				M_log.error(e.getLocalizedMessage(), e);
			}
			M_log.info(message);
			response.setContentType("application/json");
			Map jsonResponse = new TreeMap();
			jsonResponse.put("ext_sakai_code", s);
			jsonResponse.put("ext_sakai_code_text", rb.getString(s));

			Map status = null;
			if ( json == null ) {
				status = IMSJSONRequest.getStatusFailure(message);
			} else {
				status = json.getStatusFailure(message);
				if ( json.base_string != null ) {
					jsonResponse.put("base_string", json.base_string);
				}
			}
			jsonResponse.put(IMSJSONRequest.STATUS, status);
			if ( e != null ) {
				jsonResponse.put("exception", e.getLocalizedMessage());
				try {
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw, true);
					e.printStackTrace(pw);
					pw.flush();
					sw.flush();
					jsonResponse.put("traceback", sw.toString() );
				} catch ( Exception f ) {
					jsonResponse.put("traceback", f.getLocalizedMessage());
				}
			}
			String jsonText = JSONValue.toJSONString(jsonResponse);
System.out.print(jsonText);
			PrintWriter out = response.getWriter();
			out.println(jsonText);
		}

	public void destroy() {
	}

}
