package com.ibm.ics.sizing.domino;

import java.io.InputStream;
//import java.sql.Savepoint;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import org.apache.commons.io.IOUtils;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheEventListenerConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
//import org.ehcache.event.CacheEvent;
//import org.ehcache.event.CacheEventListener;
import org.ehcache.event.EventType;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
//import org.ehcache.xml.model.TimeUnit;

import com.cloudant.client.api.ClientBuilder;
import com.cloudant.client.api.CloudantClient;
import com.cloudant.client.api.Database;
import com.cloudant.client.api.model.FindByIndexOptions;
import com.cloudant.client.api.model.Response;
import com.cloudant.client.org.lightcouch.NoDocumentException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
//import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
//import com.google.gson.JsonParser;
import com.google.gson.JsonParser;
import com.ibm.ics.sizing.domino.util.ListenerObject;

/**
 * This class encapsulates the code necessary to perform CRUD operations on the cloudant data
 * @author dcacy@us.ibm.com
 *
 */
public class DB {

	private static final boolean LOG = Boolean.parseBoolean(System.getenv("DOMINO_SIZING_SERVICE_LOG"));
	private static Logger logger = Logger.getLogger(DB.class.getName());
	SpreadsheetService ssService = new SpreadsheetService();

	CloudantClient client;
	Database dbQuestionnaire;
	Database dbMachineTypes;
	Cache<String, JsonObject> machineTypesCache;
	Cache<String, JsonObject> questionnaireCache;
	private final String QUESTIONNAIRE_STATE_SUBMITTED = "SUBMITTED";
	private final String SIZING_FOR_DOMINO = "domino";
	
	/**
	 * The constructor attempts to access the datasource and initialize the cache.
	 * The cache is important because the Cloudant service limits the number of calls we can 
	 * make per second. We cache the result of a database read so we don't have to read it
	 * multiple times per sizing attempt.
	 * @throws Exception 
	 */
	public DB() throws Exception {
		try {
			log("VCAP: " + System.getenv("VCAP_SERVICES"));
			client = ClientBuilder.bluemix(System.getenv("VCAP_SERVICES")).build();
			String dbQuestionnaireName = System.getenv("DOMINO_QUESTIONNAIRE_DBNAME");
			log("attempting to access db [" + dbQuestionnaireName + "]");
			dbQuestionnaire = client.database(dbQuestionnaireName, false);
			log("questionnaire db info: " + dbQuestionnaire.info());
			String dbMachineTypesName = System.getenv("MACHINE_TYPES_DBNAME");
			log("db machine types name: " + dbMachineTypesName);
			dbMachineTypes = client.database(dbMachineTypesName, false);
			log("machine types db: " + dbMachineTypes.info());
			
			CacheEventListenerConfigurationBuilder cacheEventListenerConfiguration = CacheEventListenerConfigurationBuilder
				    .newEventListenerConfiguration(new ListenerObject(), EventType.EXPIRED) 
				    .unordered().asynchronous();
			CacheManager cacheManagerMachineType = CacheManagerBuilder.newCacheManagerBuilder()
			    	.withCache("MachineTypes",
			         CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, JsonObject.class,
			         ResourcePoolsBuilder.heap(100))
			         .withExpiry(Expirations.timeToLiveExpiration(Duration.of(60, TimeUnit.SECONDS))) 
			            .add(cacheEventListenerConfiguration) 
			               .build())
			          .build(true);
			machineTypesCache = cacheManagerMachineType.getCache("MachineTypes", String.class, JsonObject.class);
			CacheManager cacheManagerQuestionnaire = CacheManagerBuilder.newCacheManagerBuilder()
			    	.withCache("Questionnaire",
			         CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, JsonObject.class,
			         ResourcePoolsBuilder.heap(100))
			         .withExpiry(Expirations.timeToLiveExpiration(Duration.of(5, TimeUnit.SECONDS))) 
			            .add(cacheEventListenerConfiguration) 
			               .build())
			          .build(true);
			questionnaireCache = cacheManagerQuestionnaire.getCache("Questionnaire", String.class, JsonObject.class);
			
		} catch(Exception e) {
			throw new Exception(e.getMessage());
		}
	}
	
	
	public static void main(String[] args) {
		try {
			DB db = new DB();
			System.out.println(db.getOneQuestionnaire("0434f6f3718e2acf1e022c2201d1419e"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Get one questionnaire from the database. 
	 * @param id
	 * @return JsonObject the questionnaire or an error message
	 */
	public JsonObject getOneQuestionnaire(String id) {
		log("------> entering getOneQuestionnaire with id: " + id);
		JsonObject result = new JsonObject();
		
		try {
			JsonObject cached = questionnaireCache.get(id);
			if ( cached != null ) {
				log("found cached questionnaire: " + id);
				result = cached;
			} else {
				InputStream is = dbQuestionnaire.find(id);
				String string = IOUtils.toString(is, "UTF-8");
				result = new JsonParser().parse(string).getAsJsonObject();
				log("adding questionnaire to cache: " + id);
				questionnaireCache.put(id, result);
			}
		} catch (NoDocumentException e) {
			log("could not find document for id: " + id);
			result.addProperty("error", "No questionnaire found with id " + id);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		log("<----- exiting getOneQuestionnaire");
		return result;
	}
	
	/**
	 * Get the machine types from the Gartner list which match the input criteria
	 * @param vendor
	 * @param cores
	 * @param architecture
	 * @return JsonObject which contains an array of machine types or an error message
	 */
	public JsonObject getMachineTypes(String vendor, String cores, String architecture) {
		
		JsonObject result = new JsonObject();
		try {
			String selector = "{ \"Vendor\": { \"$eq\": \"" + vendor + "\" }";
			if ( cores != null && cores.length() > 0 )
				selector += ", \"Core_Count\": {\"$eq\": \"" + cores + "\"}";
			if ( architecture != null && architecture.length() > 0 )
				selector += ", \"Architecture\": {\"$eq\": \"" + architecture + "\"}";
			String query = "{selector: " + selector + "}}";
			log("query is: " + query);
			FindByIndexOptions options = new FindByIndexOptions();
			options.fields("_id").fields("_rev").fields("Vendor").fields("Server_Description")
			.fields("FamilyName").fields("Model").fields("Processor").fields("Processor_Code_name")
			.fields("Announced_Date").fields("Architecture").fields("Chip_Count").fields("Core_Count");

			List<JsonObject> machineTypes = dbMachineTypes.findByIndex(query, JsonObject.class, options);
			log(machineTypes.get(0));
			log("found machineTypes: " + machineTypes.size());
			JsonArray array = new JsonArray();
			for (Iterator<JsonObject> it = machineTypes.iterator(); it.hasNext();) {
				JsonObject machineType = it.next();
				array.add(machineType);
			}
			result.add("docs", array);
		} catch (Exception e) {
			e.printStackTrace();
			result.addProperty("error", e.getMessage());
		}
		return result;
	}
	
	/**
	 * Get just one machine type. Use this is when the questionnaire has a machine type 
	 * identified and you need to get its details.
	 * @param id
	 * @return
	 */
	public JsonObject getOneMachineType(String id) {
		log("---------> entering getOneMachineType");
		JsonObject result = new JsonObject();
		
		try {
			JsonObject cached = machineTypesCache.get("id");
			if (cached != null) {
				log("found cached machine type!");
				result = cached;
			} else {
				JsonObject mt = dbMachineTypes.find(JsonObject.class, id);
	//			log("type: " + mt.toString());
				result = mt;
				log("adding machine type to cache");
				machineTypesCache.put("id", result);
	//			return result;
			}
		} catch(Exception e) {
			e.printStackTrace();
			result.addProperty("error", e.getMessage());
		}
		
		
		log("<------- exiting getOneMachineType");
		return result;
	}
	
	/**
	 * For displaying a user's questionnaires.
	 * @param creator
	 * @return
	 */
	public JsonObject getQuestionnairesByCreator(String creator) {
		log("---------> entering getQuestionnairesByCreator");
		JsonObject result = new JsonObject();
		
		try {
			String query = "{ \"selector\": { \"Creator\": { \"$eq\": \"" + creator + "\"}}}";
			log("query is: " + query);
			List<JsonObject> list = dbQuestionnaire.findByIndex(query, JsonObject.class);
			log("this many results: " + list.size());
			JsonArray array = new JsonArray();
			for (Iterator<JsonObject> it = list.iterator(); it.hasNext();) {
				JsonObject q = it.next();
				array.add(q);
			}
			result.add("docs", array);
//			MachineType mt = dbMachineTypes.find(MachineType.class, id);
//			log("type: " + mt.toString());
//			result = new JsonParser().parse(mt.toString()).getAsJsonObject();
			log("result is: " + result);
//			return result;
		} catch(Exception e) {
			e.printStackTrace();
			result.addProperty("error", e.getMessage());
		}
		
		log("<------- exiting getQuestionnairesByCreator");
		return result;
	} 
	
	/**
	 * Save a questionnaire the first time
	 * @param q 
	 * @return JsonObject with the _id and _rev of the new record
	 */
	public JsonObject saveQuestionnaire(JsonObject q) {
		log("------> entering saveQuestionnaire");
		JsonObject result = new JsonObject();
		String date = getDateString();
		log("date string is " + date);
		q.addProperty("CreatedDate", date);
		q.addProperty("LastUpdatedDate", date);
		Response response = dbQuestionnaire.save(q);
//		log("response from save is: " + response);
		result.addProperty("_id", response.getId());
		result.addProperty("_rev", response.getRev());
		
		q.addProperty("_id", response.getId());
//		q.addProperty("_rev", response.getRev());
		sizeIfNecessary(q);
		
		log("<------ exiting saveQuestionnaire");
		return result;
	}
	
	/**
	 * Update a questionnaire. Cloudant updates the entire doc
	 * so be sure to pass the whole doc to this method.
	 * @param q
	 * @return {Object} json containing the _id and _rev of the updated doc
	 */
	public JsonObject updateQuestionnaire(JsonObject q) {
		log("------> entering updatQuestionnaire");
		JsonObject result = new JsonObject();
		JsonObject oldq = getOneQuestionnaire(q.get("_id").getAsString());
		q.addProperty("_rev", oldq.get("_rev").getAsString());
		q.addProperty("LastUpdatedDate", getDateString());
		Response response = dbQuestionnaire.update(q);
		log("response from update is: " + response);
		result.addProperty("_id", response.getId());
		result.addProperty("_rev", response.getRev());
		
//		q.addProperty("_rev", response.getRev());
		sizeIfNecessary(q);
		
		log("<------ exiting updatQuestionnaire");
		return result;
	}
	
	/**
	 * Check the status of the questionnaire.  If it's SUBMITTED that means
	 * the front end is ready for us to attempt a sizing.
	 * This method should update the actual doc in the database and therefore
	 * returns nothing...it can even be spawned as its own Thread.
	 * @param q
	 */
	private void sizeIfNecessary(JsonObject q) {
		log("------> entering sizeIfNecessary");
		JsonElement state = q.get("QuestionnaireState");
		JsonElement SizingFor = q.get("SizingFor");
		log("state is " + state + " and sizing is for " + SizingFor);
		if ( state != null 
			&& state.getAsString().equals(QUESTIONNAIRE_STATE_SUBMITTED)
			&& SizingFor != null
			&& SizingFor.getAsString().equals(SIZING_FOR_DOMINO)) {
			
			log("attempting to size questionnaire " + q.get("_id").getAsString());
			JsonObject results = ssService.performSizing(q.get("_id").getAsString());
			log("results of sizing: " + results);
		}
		log("<------ exiting sizeIfNecessary");
	}
	
	private String getDateString() {
		Date date = new Date();
		DateFormat df = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSSZ");// 2017-11-08T22:16:29.752Z
		return df.format(date);
	}
	
	private void log(Object o) {
		if (LOG)
			logger.info(o.toString());
	}
	
}
