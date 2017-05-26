package com.event;

import java.util.ArrayList;
import java.util.List;

import com.host.Host;
import com.interfaces.ISubscribeEventListener;
import com.resource.Resource;

public class SubscribeListenerManager {
	private List<ISubscribeEventListener> subscribeListeners = null;
	
	public SubscribeListenerManager(){
		this.subscribeListeners = new ArrayList<ISubscribeEventListener>();
	}
	
	public void addSubscribeEventListener(ISubscribeEventListener listener){
		subscribeListeners.add(listener);
	}
	
	public void deleteSubscribeEventListener(ISubscribeEventListener listener){
		subscribeListeners.remove(listener);
	}
	
	//notify the subscribers of newly published and shared resources
	public void informSubscribersOfNewResource(Resource res){
		for( ISubscribeEventListener subscriber : this.subscribeListeners){
			//matching new resource with the template and sending it to the client if matched.
			subscriber.mactchTemplate(res);
		}
	}
	
	//Ask all the subscribers to subscribe to new server.
	public void informSubscribersOfNewServer(Host ht){
		for( ISubscribeEventListener subscriber : this.subscribeListeners){
				subscriber.subscribeToServer(ht);
		}
	}
}
