package com.ibm.ics.sizing.domino;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Servlet implementation class GetQuestionnaire
 */
@WebServlet({ "/" }) 
public class Service extends HttpServlet {
	
	private static final long serialVersionUID = 1L;
	private static final boolean LOG = Boolean.parseBoolean(System.getenv("DOMINO_SIZING_SERVICE_LOG"));
	private static Logger logger = Logger.getLogger(Service.class.getName()); 
	private DB db;
	
	/**
	 * Create an instance of the DB class.  If it fails, throw an exception
	 */
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			db = new DB();
		} catch (Exception e) {
			throw new ServletException(e.getMessage());
		}
	}
	
    /**
     * Default constructor. 
     */
    public Service() {
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		// this servlet is invoked with "/" so we need to figure out which API is called
		String uri = request.getRequestURI();
		String[] split = uri.split("/");
		String api = split.length > 0 ? split[split.length - 1] : "";
//		logIt("api is: " + api);
		JsonObject result = new JsonObject();
		switch (api) {
			case ("getQuestionnaire"):
				log("questionnaire");
				String id = request.getParameter("id");
				if ( id == null || id.equals("") ) {
					response.setContentType("application/json");
					response.getWriter().print("{error:'No ID provided'}");
				} else {
					try {
						result = db.getOneQuestionnaire(id);
						response.setContentType("application/json");
						response.getWriter().print(result.toString());
					}
					catch(Exception e) {
						logIt("error getting database!");
						e.printStackTrace();
					}
				}
				break;
			case ("saveQuestionnaire"):
				log("save...");
				String jsonString = IOUtils.toString(request.getInputStream());
//				logIt("jsonString is: " + jsonString);
				JsonObject json = new JsonParser().parse(jsonString).getAsJsonObject();
//				log("json is " + json);
				result = db.saveQuestionnaire(json);
				response.setContentType("application/json");
				response.getWriter().print(result);
				break;
			case ("MachineTypes"):
			case ("getMachineTypes"):
				String vendor = request.getParameter("vendor");
				if ( vendor == null || vendor.equals("") ) {
					response.setContentType("application/json");
					response.getWriter().print("{error:'No vendor parameter provided'}");
				} else {
					try {
						String cores = request.getParameter("cores");
						String architecture = request.getParameter("architecture");
//						DB db = new DB();
						result = db.getMachineTypes(vendor, cores, architecture);
						response.setContentType("application/json");
						response.getWriter().print(result.toString());
					}
					catch(Exception e) {
						logIt("error getting database!");
						e.printStackTrace();
					}
				}

				break;
			case ("getOneMachineType"):
				String machineTypeId = request.getParameter("id");
				if ( machineTypeId == null || machineTypeId.equals("") ) {
					response.setContentType("application/json");
					response.getWriter().print("{error:'No Machine Type Id parameter provided'}");
				} else {
					try {
						result = db.getOneMachineType(machineTypeId);
						logIt("result: " + result);
						response.setContentType("application/json");
						response.getWriter().print(result.toString());
					}
					catch(Exception e) {
						logIt("error getting database!");
						e.printStackTrace();
					}
				}
				break;
			case ("updateQuestionnaire"):
				String updatedQ = IOUtils.toString(request.getInputStream());
//				logIt("jsonString is: " + jsonString);
				JsonObject updatedJson = new JsonParser().parse(updatedQ).getAsJsonObject();
				result = db.updateQuestionnaire(updatedJson);
				response.setContentType("application/json");
				response.getWriter().print(result);
				break;
			case ("sizeQuestionnaire"):
				String sizeId = request.getParameter("id");
				SpreadsheetService ss = new SpreadsheetService();
				result = ss.performSizing(sizeId);
//				JsonObject json = new JsonParser().parse(jsonString).getAsJsonObject();
//				JsonObject result = db.sizeQuestionnaire(id);
				response.setContentType("application/json");
				response.getWriter().print(result);

				break;
			case ("getQuestionnairesByCreator") :
				String creator = request.getParameter("creator");
				if ( creator == null || creator.equals("") ) {
					response.setContentType("application/json");
					response.getWriter().print("{error:'No Creator name parameter provided'}");
				} else {
					try {
						result = db.getQuestionnairesByCreator(creator);
						logIt("result: " + result);
						response.setContentType("application/json");
						response.getWriter().print(result.toString());
					}
					catch(Exception e) {
						logIt("error getting database!");
						e.printStackTrace();
					}
				}
				break;
			default: 
				response.setContentType("application/json");
				response.getWriter().print("{error:'Invalid or no API provided'}");
				break;
		}
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}
	
	private void logIt(Object o) {
		if (LOG)
			logger.info(o.toString());
	}


}
