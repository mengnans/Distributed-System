package com.interfaces;

import java.util.EventListener;

import com.host.Host;
import com.resource.Resource;

public interface ISubscribeEventListener extends EventListener{
	public void mactchTemplate(Resource res);
	public void subscribeToServer(Host ht);
}
