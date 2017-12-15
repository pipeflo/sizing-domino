package com.ibm.ics.sizing.domino;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class SpreadsheetService {

	private static final boolean LOG = Boolean.parseBoolean(System.getenv("DOMINO_SIZING_SERVICE_LOG"));
	private static Logger logger = Logger.getLogger(SpreadsheetService.class.getName());
//	private final String SSLCHOICE_SSL_FOR_LOGIN_PAGE_ONLY = "SSL for Login page only (default)";
//	private final String SSLCHOICE_SSL_ALL = "SSL for all Connections traffic";
//	private final String SSLCHOICE_SSL_CLIENT = "SSL from client to Front End proxy or HTTP server";
//	private final String PROXYCHOICE_NOT_PLANNED = "Not planned";
//	private final String PROXYCHOICE_OTHER = "Other enterprise proxy server";
//	private final String SEPARATEHTTP_TRUE = "Yes";
//	private final String SEPARATEHTTP_FALSE = "No";
//	private final String DEPLOYMENTMODEL_SMALL = "Small";
//	private final String DEPLOYMENTMODEL_MEDIUM = "Medium";
//	private final String DEPLOYMENTMODEL_LARGE = "Large";
//	private final String HA_TRUE = "Yes";
//	private final String HA_FALSE = "No";
//	private final String CCMDEPLOYMENT_EMBEDDED = "Embedded installation with Connections";
//	private final String CCMDEPLOYMENT_SEPARATE = "Separate install / Use an existing FileNet to work with Connections";
//	private final String DOCSVIEWER_TRUE = "Yes";
//	private final String DOCSVIEWER_FALSE = "No";
//	private final String XCC_ENABLED = "Enabled XCC Integrations";
//	private final String XCC_DISABLED = "Disabled XCC Integrations";
	

	public static void main(String[] args) {

		SpreadsheetService ss = new SpreadsheetService();
		System.out.println(ss.performSizing("967f38b0e7aa96a61e24b91866f1ded7"));
	}
	
	/**
	 * Performs the sizing calculations
	 * @param id the unique ID of the questionnaire
	 * @return {Object} JsonObject containing the sizing results
	 */
	public JsonObject performSizing(String id) {
		log("----> entering performSizing");
		JsonObject result = new JsonObject();
				
		String fileName = System.getenv("DOMINO_SPREADSHEET_TEMPLATE");
		System.out.println("file name is " + fileName);
		String path = Thread.currentThread().getContextClassLoader().getResource(fileName).getPath();
		System.out.println("path is " + path);
//		InputStream is = SpreadsheetService.class.getClassLoader()
//				.getResourceAsStream(System.getenv("DOMINO_SPREADSHEET_TEMPLATE"));
		InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);
		System.out.println("is is " + is);
//		if ( true ) return result;
		/*
		 * Try to get the Q from the database. If successful, 
		 * make a copy of the source spreadsheet.  Update its values with the
		 * data from the spreadsheet, then perform the calculations.
		 * Read the results from the spreadsheet, and return them.
		 */
		try {
			// get questionnaire
			DB db = new DB();
			JsonObject q = db.getOneQuestionnaire(id);
			if ( q.has("error")) {
				result = q; // if we can't get the Q, stop here
			} else {
				// we need the machine type in order to update the spreadsheet
				JsonObject machineType = db.getOneMachineType(q.get("TargetServerId").getAsString());
				if ( machineType.has("error")) {
					result = machineType;
				} else {
					// make a temp copy of the spreadsheet
//					File file = new File(System.getenv("DOMINO_SPREADSHEET_TEMPLATE"));
					File file = new File(path);
					String tempfileLocation = System.getenv("TEMP_FILE_LOCATION");
					String newFileName = "delme_" + UUID.randomUUID().toString().replaceAll("-", "");
					log("new file is: " + newFileName);
					Path sourceFile = file.toPath();
					log("sourceFile is " + sourceFile);
					File newFile = new File(tempfileLocation + "/" + newFileName + ".xlsx");
					Path destFile = newFile.toPath();
					Files.copy(sourceFile, destFile);
	
					// open the spreadsheet, update the input values, and perform calculations
					XSSFWorkbook book = new XSSFWorkbook(newFile);
					JsonObject before = readWorkbook(book);
					log("before: " + before);
					// update the spreadsheet
					boolean update = updateWorkbook(book, q, machineType);
					log("result of update: " + update);
					if ( update ) {
						// calculate
						calculateWorkbook(book);
						FileOutputStream fos = new FileOutputStream("/temp/delme/" + newFileName + "_UPDATED.xlsx");
						book.write(fos);
						fos.close();
						log("wrote new file " + tempfileLocation + "/" + newFileName + "_UPDATED.xlsx");
						File updatedFile = new File(tempfileLocation + "/" + newFileName + "_UPDATED.xlsx");
						XSSFWorkbook updatedBook = new XSSFWorkbook(updatedFile);
						// read the updated values
						JsonObject after = readWorkbook(updatedBook);
						log("after: " + after);
						updatedBook.close();
						result = after;
					}
					book.close();
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			result.addProperty("error", e.getMessage());
		}
//		if ( !result.has("error")) {
//			result.addProperty("success", "true");
//		}
		log("----> exiting performSizing");
		return result;
	}
	
	/**
	 * Update the spreadsheet with the values from the Questionnaire
	 * @param book the spreadsheet (workbook) to update
	 * @param q The questionnaire
	 * @param machineType the spreadsheet has details about the machine type so we need this
	 * @return boolean true if the update was successful
	 */
	private boolean updateWorkbook(XSSFWorkbook book, JsonObject q, JsonObject machineType) {
		boolean result = true;
		
		try {
			XSSFSheet hardwareSheet = book.getSheet("Hardware"); 
	
			CellReference MaxCPURef = new CellReference("D12");
			Cell MaxCPU = hardwareSheet.getRow(MaxCPURef.getRow()).getCell(MaxCPURef.getCol());
			String maxCPU = q.get("domino").getAsJsonObject().get("Max_CPU").getAsString();
			log("maxCPU is " + maxCPU);
			MaxCPU.setCellValue(Double.parseDouble(maxCPU) / 100);	
			
			XSSFSheet mailSheet = book.getSheet("Mail");
			
			CellReference TRegistered_UsersRef = new CellReference("D7");
			Cell TRegistered_Users = mailSheet.getRow(TRegistered_UsersRef.getRow()).getCell(TRegistered_UsersRef.getCol());
			String tRegistered_Users = q.get("domino").getAsJsonObject().get("TReg_Users").getAsString();
			log("tRegistered_Users is " + tRegistered_Users);
			TRegistered_Users.setCellValue(Double.parseDouble(tRegistered_Users));	

			CellReference Concurrent_RateRef = new CellReference("D8");
			Cell Concurrent_Rate = mailSheet.getRow(Concurrent_RateRef.getRow()).getCell(Concurrent_RateRef.getCol());
			String concurrent_Rate = q.get("domino").getAsJsonObject().get("Concurrent_Rate").getAsString();
			log("concurrent_Rate is " + concurrent_Rate);
			Concurrent_Rate.setCellValue(Double.parseDouble(concurrent_Rate) / 100);	

			CellReference Power_UsersRef = new CellReference("D17");
			Cell Power_Users = mailSheet.getRow(Power_UsersRef.getRow()).getCell(Power_UsersRef.getCol());
			String power_Users = q.get("domino").getAsJsonObject().get("Power_Users").getAsString();
			log("power_Users is " + power_Users);
			Power_Users.setCellValue(Double.parseDouble(power_Users) * 100);	

			CellReference Notes_ClientsRef = new CellReference("D26");
			Cell Notes_Clients = mailSheet.getRow(Notes_ClientsRef.getRow()).getCell(Notes_ClientsRef.getCol());
			String notes_Clients = q.get("domino").getAsJsonObject().get("Notes_Clients").getAsString();
			log("notes_Clients is " + notes_Clients);
			Notes_Clients.setCellValue(100);	

			
			// etc
		}
		catch(Exception e) {
			e.printStackTrace();
			result = false;
		}
		return result;
	}
	
	/**
	 * Make the spreadsheet perform calculations. Apparently it doesn't do it 
	 * automatically if you update the cells in the way we are doing it.
	 * @param book the spreadsheet
	 * @return boolean success if the calculations were performed
	 */
	private boolean calculateWorkbook(XSSFWorkbook book) {
		boolean result = true;
		try {
			FormulaEvaluator evaluator = book.getCreationHelper().createFormulaEvaluator();
			evaluator.evaluateAll();
		} catch(Exception e) {
			e.printStackTrace();
			result = false;
		}
		return result;
	}
	
	/**
	 * Read data from the spreadsheet
	 * @param book the spreadsheet
	 * @return {Object} JsonObject containing the results
	 */
	private JsonObject readWorkbook(XSSFWorkbook book) {
		JsonObject result = new JsonObject();

		// get the sheet
		XSSFSheet hardwareSheet = book.getSheet("Hardware");
		
		// use this to get the value from each cell
		FormulaEvaluator formulaEval = book.getCreationHelper().createFormulaEvaluator();
		
		// place to store results
		JsonObject dominoSizing = new JsonObject();
		
		// get the value of the nbr of 4-core machines recommended
		CellReference MachRecomRef = new CellReference("D20");
		Cell MachRecom = hardwareSheet.getRow(MachRecomRef.getRow()).getCell(MachRecomRef.getCol());
		dominoSizing.addProperty("MachRecom", formulaEval.evaluate(MachRecom).formatAsString());

		// etc...
		
		result.add("sizing", dominoSizing);
		
		
		return result;
	}
	
	private void log(Object o) {
		if (LOG)
			logger.info(o.toString());
	}

}
