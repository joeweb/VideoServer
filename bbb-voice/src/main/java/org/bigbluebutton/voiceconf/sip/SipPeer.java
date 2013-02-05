/**
* BigBlueButton open source conferencing system - http://www.bigbluebutton.org/
* 
* Copyright (c) 2012 BigBlueButton Inc. and by respective authors (see below).
*
* This program is free software; you can redistribute it and/or modify it under the
* terms of the GNU Lesser General Public License as published by the Free Software
* Foundation; either version 3.0 of the License, or (at your option) any later
* version.
* 
* BigBlueButton is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
* PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License along
* with BigBlueButton; if not, see <http://www.gnu.org/licenses/>.
*
*/
package org.bigbluebutton.voiceconf.sip;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import org.zoolu.sip.provider.*;
import org.zoolu.net.SocketAddress;
import org.slf4j.Logger;
import org.bigbluebutton.voiceconf.red5.CallStreamFactory;
import org.bigbluebutton.voiceconf.red5.ClientConnectionManager;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;

/**
 * Class that is a peer to the sip server. This class will maintain
 * all calls to it's peer server.
 * @author Richard Alam
 *
 */
public class SipPeer implements SipRegisterAgentListener {
    private static Logger log = Red5LoggerFactory.getLogger(SipPeer.class, "sip");

    private ClientConnectionManager clientConnManager;
    private CallStreamFactory callStreamFactory;
    
    private CallManager callManager = new CallManager();
    
    private SipProvider sipProvider;
    private String clientRtpIp;
    private SipRegisterAgent registerAgent;
    private final String id;
    private final AudioConferenceProvider audioconfProvider;
    
    private boolean registered = false;
    private SipPeerProfile registeredProfile;
    
    public SipPeer(String id, String sipClientRtpIp, String host, int sipPort, int startAudioPort, int stopAudioPort) {
        this.id = id;
        this.clientRtpIp = sipClientRtpIp;
        audioconfProvider = new AudioConferenceProvider(host, sipPort, startAudioPort, stopAudioPort);
        initSipProvider(host, sipPort);
    }
    
    private void initSipProvider(String host, int sipPort) {
        sipProvider = new SipProvider(host, sipPort);    
        sipProvider.setOutboundProxy(new SocketAddress(host)); 
        sipProvider.addSipProviderListener(new OptionMethodListener());    	
    }
    
    public void register(String username, String password) {
    	log.debug( "SIPUser register" );
        createRegisterUserProfile(username, password);
        if (sipProvider != null) {
        	registerAgent = new SipRegisterAgent(sipProvider, registeredProfile.fromUrl, 
        			registeredProfile.contactUrl, registeredProfile.username, 
        			registeredProfile.realm, registeredProfile.passwd);
        	registerAgent.addListener(this);
        	registerAgent.register(registeredProfile.expires, registeredProfile.expires/2, registeredProfile.keepaliveTime);
        }                              
    }
    
    private void createRegisterUserProfile(String username, String password) {    	    	
    	registeredProfile = new SipPeerProfile();
    	registeredProfile.audioPort = audioconfProvider.getStartAudioPort();
            	
        String fromURL = "\"" + username + "\" <sip:" + username + "@" + audioconfProvider.getHost() + ">";
        registeredProfile.username = username;
        registeredProfile.passwd = password;
        registeredProfile.realm = audioconfProvider.getHost();
        registeredProfile.fromUrl = fromURL;
        registeredProfile.contactUrl = "sip:" + username + "@" + sipProvider.getViaAddress();
        if (sipProvider.getPort() != SipStack.default_port) {
        	registeredProfile.contactUrl += ":" + sipProvider.getPort();
        }		
        registeredProfile.keepaliveTime=8000;
        registeredProfile.acceptTime=0;
        registeredProfile.hangupTime=20;   
        
        log.debug( "SIPUser register : {}", fromURL );
        log.debug( "SIPUser register : {}", registeredProfile.contactUrl );
    }
    

    public void call(String clientId, String callerName, String destination) {
    	if (!registered) {
    		/* 
    		 * If we failed to register with FreeSWITCH, reject all calls right away.
    		 * This way the user will know that there is a problem as quickly as possible.
    		 * If we pass the call, it take more that 30seconds for the call to timeout
    		 * (in case FS is offline) and the user will be kept wondering why the call
    		 * isn't going through.
    		 */
    		log.warn("We are not registered to FreeSWITCH. However, we will allow {} to call {}.", callerName, destination);
//    		return;
    	}
    	
    	SipPeerProfile callerProfile = SipPeerProfile.copy(registeredProfile);    	
    	CallAgent ca = new CallAgent(this.clientRtpIp, sipProvider, callerProfile, audioconfProvider, clientId);
    	ca.setClientConnectionManager(clientConnManager);
    	ca.setCallStreamFactory(callStreamFactory);
    	callManager.add(ca);
    	ca.call(callerName, destination);
    }

	public void close() {
		log.debug("SIPUser close1");
        try {
			unregister();
		} catch(Exception e) {
			log.error("close: Exception:>\n" + e);
		}

       log.debug("Stopping SipProvider");
       sipProvider.halt();
	}

    public void hangup(String clientId) {
    	log.debug( "SIPUser hangup" );

    	CallAgent ca = callManager.remove(clientId);
        if (ca != null) {
           ca.hangup();
        }
    }

    public void unregister() {
    	log.debug( "SIPUser unregister" );

    	Collection<CallAgent> calls = callManager.getAll();
    	for (Iterator<CallAgent> iter = calls.iterator(); iter.hasNext();) {
    		CallAgent ca = (CallAgent) iter.next();
    		ca.hangup();
    	}
    	
        if (registerAgent != null) {
            registerAgent.unregister();
            registerAgent = null;
        }
    }

    public void startTalkStream(String clientId, IBroadcastStream broadcastStream, IScope scope) {
    	CallAgent ca = callManager.get(clientId);
        if (ca != null) {
           ca.startTalkStream(broadcastStream, scope);
        }
    }
    
    public void stopTalkStream(String clientId, IBroadcastStream broadcastStream, IScope scope) {
    	CallAgent ca = callManager.get(clientId);
        if (ca != null) {
           ca.stopTalkStream(broadcastStream, scope);
        }
    }

	@Override
	public void onRegistrationFailure(String result) {
		log.error("Failed to register with Sip Server.");
		registered = false;
	}

	@Override
	public void onRegistrationSuccess(String result) {
		log.info("Successfully registered with Sip Server.");
		registered = true;
	}

	@Override
	public void onUnregistedSuccess() {
		log.info("Successfully unregistered with Sip Server");
		registered = false;
	}
	
	public void setCallStreamFactory(CallStreamFactory csf) {
		callStreamFactory = csf;
	}
	
	public void setClientConnectionManager(ClientConnectionManager ccm) {
		clientConnManager = ccm;
	}
}
