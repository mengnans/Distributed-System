package com.resource;

import java.net.URI;
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
public class Resource {
	private String name;
	private String description;
	private LinkedList<String> tags;
	private URI uri;
	private String channel;
	private String owner;
	private Host ezserver;

	
	public Resource(){
		name = "";
		description = "";
		tags = new LinkedList<String>();
		uri  = null;
		channel = "";
		owner = "";
		ezserver = null;
	}
	
	public Resource(String name, String description, LinkedList<String> tags, URI uri, String channel, String owner,
			Host ezserver) {
		super();
		this.name = name;
		this.description = description;
		this.tags = tags;
		this.uri = uri;
		this.channel = channel;
		this.owner = owner;
		this.ezserver = ezserver;
	}
	

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public LinkedList<String> getTags() {
		return tags;
	}

	public void setTags(LinkedList<String> tags) {
		this.tags = tags;
	}

	public URI getUri() {
		return uri;
	}

	public void setUri(URI uri) {
		this.uri = uri;
	}

	public String getChannel() {
		return channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public Host getEzserver() {
		return ezserver;
	}

	public void setEzserver(Host ezserver) {
		this.ezserver = ezserver;
	}

	@Override
	public String toString() {
		return "Resource [name=" + name + ", description=" + description + ", tags=" + tags + ", uri=" + uri
				+ ", channel=" + channel + ", owner=" + owner + ",ezserver=" + ezserver + "]";
	}

	@SuppressWarnings("unchecked")
	public JSONObject getResourceJSON() {
		// return the JSON of resource
		JSONObject resource_json = new JSONObject();
		resource_json.put("name", this.getName());
		JSONArray tags_json = new JSONArray();
		Iterator<String> it = tags.iterator();
		while (it.hasNext()) {
			tags_json.add(it.next());
		}
		resource_json.put("tags", tags_json);
		resource_json.put("description", this.getDescription());
		resource_json.put("uri", this.getUri().toString());
		resource_json.put("channel", this.getChannel());
		resource_json.put("owner", this.getOwner());
		resource_json.put("ezserver", this.getEzserver().toString());
		return resource_json;
	}
	
	
	public boolean equals(Resource resource){
		if(!name.equals(resource.name)){
			return false;
		}
		if(!description.equals(resource.description)){
			return false;
		}
		if(tags.size() > resource.getTags().size()){
			return false;
		}
		else{
			for(int i = 1;i < tags.size();i ++){
				if(!tags.get(i).equals(resource.getTags().get(i))){
					return false;
				}
			}
		}
		return true;
	}
	
	
}
