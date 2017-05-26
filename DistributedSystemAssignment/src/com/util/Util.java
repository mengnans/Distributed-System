package com.util;

/**
 * 
 * @author Group NullPointerException
 * 		Mengnan Shi		802123
 * 		Hanwei Zhu		811443
 * 		Xuelin Zhao		801736
 * 		Hangyu Xia		802971
 *
 */
public class Util {
	/**
	 * clean the field
	 * @param field
	 * @return cleaned field
	 */
	public static String clean (String field){
		String newField = new String();
		
		field = field.trim();
		
		/** delete all invalid characters*/
		for(int i =0;i<field.length();i++){
			if(!(field.charAt(i) == '\0')){
				
				newField += field.charAt(i);
			}
		}
		field = newField;
		
		return field;
	}
	
}
