/**
 *	<p>Copyright (c) 2016, Carl Stahmer - <a href="http://www.carlstahmer.com">www.carlstahmer.com</a>.</p>
 *	
 *	<p>This file is part of the ESTC Record Importer package, a server 
 *	daemon that processes incoming MARC cataloging data stored in binary
 *	MARC, .csv, and .txt formats, checks the records for scope on date,
 *	language, and place of publication, and exports the filtered
 *	records as RDF suitable for linked data exchange.</p>
 *
 *	<p>The ESTC Record Importer is free software: you can redistribute it 
 *	and/or modify it under the terms of the GNU General Public License 
 *	as published by the Free Software Foundation, either version 3 of 
 *	the License, or (at your option) any later version.</p>
 *
 *	<p>The ESTC Record Importer is distributed in the hope that it will 
 *	be useful, but WITHOUT ANY WARRANTY; without even the implied warranty 
 *	of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 *	GNU General Public License for more details.</p>
 *
 *	<p>You should have received a copy of the GNU General Public License  
 *	along with the ESTC Record Importer distribution.  If not, 
 *	see <a href="http://www.gnu.org/licenses/">http://www.gnu.org/licenses/</a>.</p>
 *
 *	<p>Development of this software was made possible through funding from 
 *	the Andrew W. Mellon Foundation which maintains a nonexclusive, 
 *  royalty-free, worldwide, perpetual, irrevocable license to distribute 
 *  this software either in wholoe or in part for scholarly and educational purposes.</p>
 */

package com.carlstahmer.estc.recordimport.daemon;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;

import org.marc4j.MarcPermissiveStreamReader;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.Subfield;

/**
 * @author cstahmer
 * 
 * <p>A class that contains methods for loading marc files from the listen directory,
 * parsing data, and inserting it into the working database.</p>
 */
public class LoadMarc {
	
	Conf config;
	SqlModel sqlObj;
	RecordUtils recs;
	Logger logger;

	/**
	 * <p>Constructor class that assigns passed Config object and SqlModel to 
	 * class instances.</p>
	 *
	 * @param  configObj    an instance of the Conf class
	 * @param  sqlModObj  	an instance of the sqlModel class
	 */
	public LoadMarc(Conf configObj, SqlModel sqlModObj) {
		config = configObj;
		sqlObj = sqlModObj;
		recs = new RecordUtils(config, sqlObj);
		logger = new Logger(config);
	}
	
	/**
	 * <p>Loads a marc file from the file system and inserts the data into the working, local
	 * mySql database.</p>
	 *
	 * @param  strFile    	The full file path to the marc file to load
	 * @param  curCode  	The MARC institutional code for the organization that created the record
	 */
	public void loadMarcFile(String strFile, String curCode, int fileRecordId) {
		
		try {			
			
			InputStream input = new FileInputStream(strFile);
			//MarcReader reader = new MarcStreamReader(input);
			MarcReader reader = new MarcPermissiveStreamReader(input, true, true);
			
			boolean blnHasControlIdent = false;
	        while (reader.hasNext()) {
	            Record record = reader.next();
	            ArrayList<String> estcHoldingItems = new ArrayList<String>();
	            List<ControlField> controlFields = record.getControlFields();
	            String strControlNumKey = "";
	            String strLastChange = "";
	            boolean blnTypeIdentified = false;
	            // int recType = 1;  // bib
	            // int recType = 2; // hold
	            // int recType = 3; // orphan
	            int recType = 0;
	            for (int i=0;i<controlFields.size();i++) {
	            	
	            	
	            	
	            	ControlField thisControl = controlFields.get(i);
	            	
	            	if (thisControl.getTag().equals("001")) {
	            		strControlNumKey = thisControl.getData();
	            	}
	            	if (thisControl.getTag().equals("003")) {
	            		blnHasControlIdent = true;
	            	}		            	
	            	if (thisControl.getTag().equals("005")) {
	            		strLastChange = thisControl.getData();
	            	}
	            }
	            
	            	if (strControlNumKey.matches("^[S|N|R|W|T]\\d+")) {
	            		// if here then this is an ESTC bib or holding record
	            		if (blnHasControlIdent) {
	            			recType = 1;
	            			System.out.println("Processing Bib Record");
	            		} else {
	            			recType = 2;
	            			System.out.println("Processing Holding Record");
	            		}    	
		            } else {
		            	// if here, then this is an unmatched record
		            	recType = 3;
		            	System.out.println("Processing Un-Matched Record");
		            }

	            
	            
	            if (strLastChange.length() < 1) {
	            	strLastChange = "0";
	            }
	            double moddate;
	            moddate = Double.parseDouble(strLastChange);
            
	            // Now check and see if this record already exists if we got a control key
	            if (strControlNumKey.length() > 0 || recType == 0) {
	            	
	            	int intRecordId = recs.duplicateRecordCheck(curCode, strControlNumKey, moddate, recType);
	            	
	            	if (intRecordId > -1) {
	            	
	            	
		            	if (intRecordId == 0) {
		            		intRecordId = sqlObj.insertRecordRecord(fileRecordId, recType, strControlNumKey, moddate);
		            		logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Inserted New Record With Control Number "+strControlNumKey+" and System ID "+intRecordId); 
		            	} else {
		            		logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Modifying existing record with system ID "+intRecordId);
		            	}
		            	
		            	// now here I write all of the field data for the record to the system
		            	logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Writing new data for record "+intRecordId); 
	
		            	List<ControlField> controlFieldsAll = record.getControlFields();
			            for (int ivf=0;ivf<controlFieldsAll.size();ivf++) {
			            	ControlField thisControlField = controlFieldsAll.get(ivf);
			            	String thisControlTag = thisControlField.getTag();
			            	String thisControlData = thisControlField.getData();
			            	int fieldId = sqlObj.insertFieldRecord(intRecordId, thisControlTag, thisControlData, 1);	
	            			if (fieldId > 0) {
	            				logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Successfully saved field with id "+fieldId); 
	
	            			} else {
	            				logger.log(1, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Error saving field"); 
	            			}
			            }
			            // add a 003 control identifer if there isn't one
			            if (!blnHasControlIdent) {
			            	int insertControlIdent = sqlObj.insertFieldRecord(intRecordId, "003", curCode, 1);
	            			if (insertControlIdent > 0) {
	            				logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Successfully set missing institutional code in 003 field"); 
	            			} else {
	            				logger.log(1, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Error setting missing institutional code in 003 field"); 
	            			}
			            }
			            
			            List<DataField> dataFieldsAll = record.getDataFields();
			            for (int idf=0;idf<dataFieldsAll.size();idf++) {
			            	DataField thisDataField = dataFieldsAll.get(idf);
			            	String thisDataTag = thisDataField.getTag();
			            	String fieldToString = thisDataField.toString();
			            	int datafieldId = sqlObj.insertFieldRecord(intRecordId, thisDataTag, fieldToString, 2);
			            	if (datafieldId > 0) {
			            		logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Successfully inserted Field " + thisDataTag + " into database with ID " + datafieldId); 
			            	} else {
			            		logger.log(1, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Failed to insert Field " + thisDataTag + " into database."); 
			            	}
			            	
			            	// Handle Subfileds. 
			            	List<Subfield> subFields = thisDataField.getSubfields();
			            	if (subFields.size() > 0) {
					            for (int isf=0;isf<subFields.size();isf++) {
					            	Subfield thisSubfield = subFields.get(isf);
					            	String thisSubfieldTag = Character.toString(thisSubfield.getCode());
					            	String thisSubfieldData = thisSubfield.getData();
					            	
					            	// Add 852s to holdings stack if this is an ESTC record
					            	// need to convert this to a hashmap that returns all the subfield values
					            	if ((thisDataTag.equals("852")) && (curCode.equals("estc")) && (thisSubfieldTag.equals("a"))) {
					            		estcHoldingItems.add(thisSubfieldData.toString());
					            	}
					            	
					            	int intSubfieldId = sqlObj.insertSubfieldRecord(datafieldId, thisSubfieldTag, thisSubfieldData);
			            			if (intSubfieldId > 0) {
			            				logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Successfully saved subfield with id "+intSubfieldId); 
			            			} else {
			            				logger.log(1, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Failed to save subfield"); 
			            			}
					            }
			            	}
			            	
			            	// Add 852s to holdings stack if this is an ESTC record
			            	// need to convert this to a hashmap that returns all the subfield values
			            	if ((thisDataTag.equals("852")) & (curCode.equals("estc"))) {
			            		estcHoldingItems.add(fieldToString = thisDataField.toString());
			            	}
			            }
	            		
	            	} else {
	            		String skipRecType = "holding";
	            		if (recType == 1) {
	            			skipRecType = "bibliographic";
	            		}
	            		logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Skipping duplicate " + skipRecType + " record with control " + strControlNumKey + " and modification datetimestamp " + moddate);
	            	}
	            	
	            } else {
	            	logger.log(1, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Unable to process record due to missing or blank control field [001]");
	            }

	            // now create the holdings records for held items
	            for (int ihi = 0; ihi < estcHoldingItems.size(); ihi++) {
	            	loadMarcESTCHoldingRecord(record, estcHoldingItems.get(ihi), fileRecordId);
	            }
	            
	        }
	        	
		} catch (FileNotFoundException e) {
			logger.log(1, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Failed to load MARC file");
		}
		
	}
	
	/**
	 * <p>Loads a marc file from the file system and inserts the data into the working, local
	 * mySql database.</p>
	 *
	 * @param  strFile    	The full file path to the marc file to load
	 * @param  curCode  	The MARC institutional code for the organization that created the record
	 */
	public void loadMarcESTCHoldingRecord(Record record, String curCode, int fileRecordId) {		

		System.out.println("Constructing Holding Record for " + curCode);
		
        List<ControlField> controlFields = record.getControlFields();
        String strControlNumKey = "";
        String strLastChange = "";
        // manually set the type to 3, which will be the type for
        // programmatically created child records
        int recType = 3;
        for (int i=0;i<controlFields.size();i++) {
        	ControlField thisControl = controlFields.get(i);
        	if (thisControl.getTag().equals("001")) {
        		strControlNumKey = thisControl.getData();
        	}	            	
        	if (thisControl.getTag().equals("005")) {
        		strLastChange = thisControl.getData();
        	}
        }
        
        if (strLastChange.length() < 1) {
        	strLastChange = "0";
        }
        double moddate;
        moddate = Double.parseDouble(strLastChange);

        
        // Now check and see if this record already exists if we got a control key
        if (strControlNumKey.length() > 0) {
        	
        	int intRecordId = recs.duplicateRecordCheck(curCode, strControlNumKey, moddate, recType);
        	
        	if (intRecordId > -1) {
        	
        	
            	if (intRecordId == 0) {
            		intRecordId = sqlObj.insertRecordRecord(fileRecordId, recType, strControlNumKey, moddate);
            		logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Inserted New Record With Control Number "+strControlNumKey+" and System ID "+intRecordId); 
            	} else {
            		logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Modifying existing record with system ID "+intRecordId);
            	}
            	
            	// now here I write all of the field data for the record to the system
            	logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Writing new data for record "+intRecordId); 

            	List<ControlField> controlFieldsAll = record.getControlFields();
	            for (int ivf=0;ivf<controlFieldsAll.size();ivf++) {
	            	ControlField thisControlField = controlFieldsAll.get(ivf);
	            	String thisControlTag = thisControlField.getTag();
	            	String thisControlData = thisControlField.getData();
	            	int fieldId = sqlObj.insertFieldRecord(intRecordId, thisControlTag, thisControlData, 1);	
        			if (fieldId > 0) {
        				logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Successfully saved field with id "+fieldId); 

        			} else {
        				logger.log(1, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Error saving field"); 
        			}
	            }
	            // add a 003 control identifer if there isn't one
            	int insertControlIdent = sqlObj.insertFieldRecord(intRecordId, "003", curCode, 1);
    			if (insertControlIdent > 0) {
    				logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Successfully set missing institutional code in 003 field"); 
    			} else {
    				logger.log(1, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Error setting missing institutional code in 003 field"); 
    			}
	            
	            List<DataField> dataFieldsAll = record.getDataFields();
	            for (int idf=0;idf<dataFieldsAll.size();idf++) {
	            	DataField thisDataField = dataFieldsAll.get(idf);
	            	String thisDataTag = thisDataField.getTag();
	            	String fieldToString = thisDataField.toString();
	            	int datafieldId = sqlObj.insertFieldRecord(intRecordId, thisDataTag, fieldToString, 2);
	            	if (datafieldId > 0) {
	            		logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Successfully inserted Field " + thisDataTag + " into database with ID " + datafieldId); 
	            	} else {
	            		logger.log(1, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Failed to insert Field " + thisDataTag + " into database."); 
	            	}
	            	
	            	// Handle Subfileds. 
	            	List<Subfield> subFields = thisDataField.getSubfields();
	            	if (subFields.size() > 0) {
			            for (int isf=0;isf<subFields.size();isf++) {
			            	Subfield thisSubfield = subFields.get(isf);
			            	String thisSubfieldTag = Character.toString(thisSubfield.getCode());
			            	String thisSubfieldData = thisSubfield.getData();
			            	int intSubfieldId = sqlObj.insertSubfieldRecord(datafieldId, thisSubfieldTag, thisSubfieldData);
	            			if (intSubfieldId > 0) {
	            				logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Successfully saved subfield with id "+intSubfieldId); 
	            			} else {
	            				logger.log(1, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Failed to save subfield"); 
	            			}
			            }
	            	}
	            }
        		
        	} else {
        		String skipRecType = "holding";
        		if (recType == 1) {
        			skipRecType = "bibliographic";
        		}
        		logger.log(3, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Skipping duplicate " + skipRecType + " record with control " + strControlNumKey + " and modification datetimestamp " + moddate);
        	}
        	
        } else {
        	logger.log(1, Thread.currentThread().getStackTrace()[1].getFileName(), Thread.currentThread().getStackTrace()[1].getLineNumber(), "Unable to process record due to missing or blank conrole field [001]");
        }
	
	}

}
