package com.resource;

import java.net.URI;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.host.Host;

/**
 * 
 * @author Group NullPointerException
 * 		Mengnan Shi		802123
 * 		Hanwei Zhu		811443
 * 		Xuelin Zhao		801736
 * 		Hangyu Xia		802971
 *
 */
public class FileResource extends Resource {
	private byte[] data;
	private long size;
	
	public FileResource(){
		super();
		data = null;
		size = 0;
	}
	

	public FileResource(String name, String description, LinkedList<String> tags, URI uri, String channel, String owner,
			Host ezserver, byte[] data, long size) {
		super(name, description, tags, uri, channel, owner, ezserver);
		this.data = data;
		this.size = size;
	}




	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
		this.size = data.length;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public JSONObject getResourceJSON() {
		// return the JSON of resource
		JSONObject resource_json = new JSONObject();
		resource_json.put("name", this.getName());
		JSONArray tags_json = new JSONArray();
		Iterator<String> it = this.getTags().iterator();
		while (it.hasNext()) {
			tags_json.add(it.next());
		}
		resource_json.put("tags", tags_json);
		resource_json.put("description", this.getDescription());
		resource_json.put("uri", this.getUri().toString());
		resource_json.put("channel", this.getChannel());
		resource_json.put("owner", this.getOwner());
		resource_json.put("ezserver", this.getEzserver().toString());
		resource_json.put("resourceSize", size);
		
		return resource_json;
	}

	@Override
	public String toString() {
		
		return "FileResource [data=" + Arrays.toString(data) + ", size=" + size + "]";
	}
}
