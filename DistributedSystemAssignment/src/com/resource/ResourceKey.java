package com.resource;

import java.net.URI;

/**
 * 
 * @author Group NullPointerException
 * 		Mengnan Shi		802123
 * 		Hanwei Zhu		811443
 * 		Xuelin Zhao		801736
 * 		Hangyu Xia		802971
 *
 */
public class ResourceKey {

	private String owner;
	private String channel;
	private URI uri;

	public ResourceKey(String owner, String channel, URI uri) {
		super();
		this.owner = owner;
		this.channel = channel;
		this.uri = uri;
	}
	
	@Override
	public int hashCode(){
		int code = 1;
		code *= owner.length();
		code *= channel.length();
		code *= uri.toString().length();
		return code;
	}

	@Override
	public boolean equals(Object obj) {
		ResourceKey key_2 = null;
		if (obj == null) {
			return false;
		}
		if (obj instanceof ResourceKey) {
			key_2 = (ResourceKey) obj;
		} else {
			return false;
		}
		if (this.owner.equals(key_2.owner) && this.channel.equals(key_2.owner)
				&& this.uri.toString().equals(key_2.uri.toString()))
			return true;
		return false;
	}

}
