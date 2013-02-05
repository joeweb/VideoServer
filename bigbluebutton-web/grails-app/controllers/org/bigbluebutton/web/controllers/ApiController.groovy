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
package org.bigbluebutton.web.controllers


import java.text.MessageFormat;
import java.util.Collections;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.bigbluebutton.api.domain.Meeting;
import org.bigbluebutton.api.domain.UserSession;
import org.bigbluebutton.api.MeetingService;
import org.bigbluebutton.api.domain.Recording;
import org.bigbluebutton.web.services.PresentationService
import org.bigbluebutton.presentation.UploadedPresentation
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.bigbluebutton.api.ApiErrors;
import org.bigbluebutton.api.ParamsProcessorUtil;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.text.DateFormat;


class ApiController {
  private static final Integer SESSION_TIMEOUT = 14400  // 4 hours    
  private static final String CONTROLLER_NAME = 'ApiController'		
  private static final String RESP_CODE_SUCCESS = 'SUCCESS'
  private static final String RESP_CODE_FAILED = 'FAILED'
  private static final String ROLE_MODERATOR = "MODERATOR";
  private static final String ROLE_ATTENDEE = "VIEWER";
  private static final String SECURITY_SALT = '639259d4-9dd8-4b25-bf01-95f9567eaf4b'
  private static final String API_VERSION = '0.8'
    
  MeetingService meetingService;
  PresentationService presentationService
  ParamsProcessorUtil paramsProcessorUtil
  
  /* general methods */
  def index = {
    log.debug CONTROLLER_NAME + "#index"
    response.addHeader("Cache-Control", "no-cache")
    withFormat {	
      xml {
        render(contentType:"text/xml") {
          response() {
            returncode(RESP_CODE_SUCCESS)
            version(paramsProcessorUtil.getApiVersion())
          }
        }
      }
    }
  }
 
        
  /*********************************** 
   * CREATE (API) 
   ***********************************/
  def create = {
    String API_CALL = 'create'
    log.debug CONTROLLER_NAME + "#${API_CALL}"
  	
	// BEGIN - backward compatibility
	if (StringUtils.isEmpty(params.checksum)) {
		invalid("checksumError", "You did not pass the checksum security check")
		return
	}

/*
	if (StringUtils.isEmpty(params.name)) {
		invalid("missingParamName", "You must specify a name for the meeting.");
		return
	}
*/
	if (StringUtils.isEmpty(params.meetingID)) {
		invalid("missingParamMeetingID", "You must specify a meeting ID for the meeting.");
		return
	}
	
	if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
		invalid("checksumError", "You did not pass the checksum security check")
		return
	}
	// END - backward compatibility
	
	ApiErrors errors = new ApiErrors();
	paramsProcessorUtil.processRequiredCreateParams(params, errors);

    if (errors.hasErrors()) {
    	respondWithErrors(errors)
    	return
    }
            
    // Do we agree with the checksum? If not, complain.
    if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
      errors.checksumError()
    	respondWithErrors(errors)
    	return
    }
    
    
    // Translate the external meeting id into an internal meeting id.
    String internalMeetingId = paramsProcessorUtil.convertToInternalMeetingId(params.meetingID);		
    Meeting existing = meetingService.getMeeting(internalMeetingId);
    if (existing != null) {
      log.debug "Existing conference found"
      Map<String, Object> updateParams = paramsProcessorUtil.processUpdateCreateParams(params);
      if (existing.getViewerPassword().equals(params.get("attendeePW")) && existing.getModeratorPassword().equals(params.get("moderatorPW"))) {
        paramsProcessorUtil.updateMeeting(updateParams, existing);
        // trying to create a conference a second time, return success, but give extra info
        // Ignore pre-uploaded presentations. We only allow uploading of presentation once.
        //uploadDocuments(existing);
        respondWithConference(existing, "duplicateWarning", "This conference was already in existence and may currently be in progress.");
      } else {
	  	// BEGIN - backward compatibility
	  	invalid("idNotUnique", "A meeting already exists with that meeting ID.  Please use a different meeting ID.");
		return;
	  	// END - backward compatibility
	  
        // enforce meetingID unique-ness
        errors.nonUniqueMeetingIdError()
        respondWithErrors(errors)
      } 
      
      return;    
    }
     
    Meeting newMeeting = paramsProcessorUtil.processCreateParams(params);            
    meetingService.createMeeting(newMeeting);
    
    // See if the request came with pre-uploading of presentation.
    uploadDocuments(newMeeting);    
    respondWithConference(newMeeting, null, null)
  }

  /**********************************************
   * JOIN API
   *********************************************/
  def join = {
    String API_CALL = 'join'
    log.debug CONTROLLER_NAME + "#${API_CALL}"
  	ApiErrors errors = new ApiErrors()
  	  
	// BEGIN - backward compatibility
    if (StringUtils.isEmpty(params.checksum)) {
		invalid("checksumError", "You did not pass the checksum security check")
		return
	}

	if (StringUtils.isEmpty(params.fullName)) {
		invalid("missingParamFullName", "You must specify a name for the attendee who will be joining the meeting.");
		return
	}
	
	if (StringUtils.isEmpty(params.meetingID)) {
		invalid("missingParamMeetingID", "You must specify a meeting ID for the meeting.");
		return
	}
	
	if (StringUtils.isEmpty(params.password)) {
		invalid("invalidPassword","You either did not supply a password or the password supplied is neither the attendee or moderator password for this conference.");
		return
	}
	
	if (!paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
		invalid("checksumError", "You did not pass the checksum security check")
		return
	}
	// END - backward compatibility
  
    // Do we have a checksum? If none, complain.
    if (StringUtils.isEmpty(params.checksum)) {
      errors.missingParamError("checksum");
    }

    // Do we have a name for the user joining? If none, complain.
    String fullName = params.fullName
    if (StringUtils.isEmpty(fullName)) {
      errors.missingParamError("fullName");
    }

    // Do we have a meeting id? If none, complain.
    String externalMeetingId = params.meetingID
    if (StringUtils.isEmpty(externalMeetingId)) {
      errors.missingParamError("meetingID");
    }

    // Do we have a password? If not, complain.
    String attPW = params.password
    if (StringUtils.isEmpty(attPW)) {
      errors.missingParamError("password");
    }
    
    if (errors.hasErrors()) {
    	respondWithErrors(errors)
    	return
    }
        
    // Do we agree on the checksum? If not, complain.		
    if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
      	errors.checksumError()
    	respondWithErrors(errors)
    	return
    }

    // Everything is good so far. Translate the external meeting id to an internal meeting id. If
    // we can't find the meeting, complain.					        
    String internalMeetingId = paramsProcessorUtil.convertToInternalMeetingId(externalMeetingId);
    log.info("Retrieving meeting ${internalMeetingId}")		
    Meeting meeting = meetingService.getMeeting(internalMeetingId);
    if (meeting == null) {
		// BEGIN - backward compatibility
		invalid("invalidMeetingIdentifier", "The meeting ID that you supplied did not match any existing meetings");
		return;
		// END - backward compatibility
		
	   errors.invalidMeetingIdError();
	   respondWithErrors(errors)
	   return;
    }

	// the createTime mismatch with meeting's createTime, complain
	// In the future, the createTime param will be required
	if (params.createTime != null){
		long createTime = 0;
		try{
			createTime=Long.parseLong(params.createTime);
		}catch(Exception e){
			log.warn("could not parse createTime param");
			createTime = -1;
		}
		if(createTime != meeting.getCreateTime()){
			errors.mismatchCreateTimeParam();
			respondWithErrors(errors);
			return;
		}
	}
    
    // Is this user joining a meeting that has been ended. If so, complain.
    if (meeting.isForciblyEnded()) {
		// BEGIN - backward compatibility
		invalid("meetingForciblyEnded", "You can not re-join a meeting that has already been forcibly ended.  However, once the meeting is removed from memory (according to the timeout configured on this server, you will be able to once again create a meeting with the same meeting ID");
		return;
		// END - backward compatibility
		
      errors.meetingForciblyEndedError();
      respondWithErrors(errors)
      return;
    }

    // Now determine if this user is a moderator or a viewer.
    String role = null;
    if (meeting.getModeratorPassword().equals(attPW)) {
      role = ROLE_MODERATOR;
    } else if (meeting.getViewerPassword().equals(attPW)) {
      role = ROLE_ATTENDEE;
    }
    
    if (role == null) {
		// BEGIN - backward compatibility
		invalid("invalidPassword","You either did not supply a password or the password supplied is neither the attendee or moderator password for this conference.");
		return
		// END - backward compatibility
		
    	errors.invalidPasswordError()
	    respondWithErrors(errors)
	    return;
    }
        
    String webVoice = StringUtils.isEmpty(params.webVoiceConf) ? meeting.getTelVoice() : params.webVoiceConf

    boolean redirectImm = parseBoolean(params.redirectImmediately)
    
	String internalUserID = RandomStringUtils.randomAlphanumeric(12).toLowerCase()
	
    String externUserID = params.userID
    if (StringUtils.isEmpty(externUserID)) {
      externUserID = internalUserID
    }
    
	UserSession us = new UserSession();
	us.internalUserId = internalUserID
    us.conferencename = meeting.getName()
    us.meetingID = meeting.getInternalId()
	us.externMeetingID = meeting.getExternalId()
    us.externUserID = externUserID
    us.fullname = fullName 
    us.role = role
    us.conference = meeting.getInternalId()
    us.room = meeting.getInternalId()
    us.voicebridge = meeting.getTelVoice()
    us.webvoiceconf = meeting.getWebVoice()
    us.mode = "LIVE"
    us.record = meeting.isRecord()
    us.welcome = meeting.getWelcomeMessage()
	us.logoutUrl = meeting.getLogoutUrl();
	
	if (! StringUtils.isEmpty(params.defaulLayout)) {
		us.defaultLayout = params.defaulLayout;
	}

    if (! StringUtils.isEmpty(params.avatarURL)) {
        us.avatarURL = params.avatarURL;
    } else {
        us.avatarURL = meeting.defaultAvatarURL
    }
    	     
	// Store the following into a session so we can handle
	// logout, restarts properly.
	session['meeting-id'] = us.meetingID
	session['user-token'] = us.meetingID + "-" + us.internalUserId;
	session['logout-url'] = us.logoutUrl
	
	meetingService.addUserSession(session['user-token'], us);
	
	log.info("Session user token for " + us.fullname + " [" + session['user-token'] + "]")	
    session.setMaxInactiveInterval(SESSION_TIMEOUT);
    
	//check if exists the param redirect
	boolean redirectClient = true;
	String clientURL = paramsProcessorUtil.getDefaultClientUrl();
	
	if(!StringUtils.isEmpty(params.redirect))
	{
		try{
			redirectClient = Boolean.parseBoolean(params.redirect);
		}catch(Exception e){
			redirectClient = true;
		}
	}
	if(!StringUtils.isEmpty(params.clientURL)){
		clientURL = params.clientURL;
	}
	
	if(redirectClient){
		log.info("Successfully joined. Redirecting to ${paramsProcessorUtil.getDefaultClientUrl()}"); 		
		redirect(url: clientURL);
	}
	else{
		log.info("Successfully joined. Sending XML response.");
		response.addHeader("Cache-Control", "no-cache")
		withFormat {
		  xml {
			render(contentType:"text/xml") {
			  response() {
				returncode(RESP_CODE_SUCCESS)
				messageKey("successfullyJoined")
				message("You have joined successfully.")
			  }
			}
		  }
		}
	}
  }

  /*******************************************
   * IS_MEETING_RUNNING API
   *******************************************/
  def isMeetingRunning = {
    String API_CALL = 'isMeetingRunning'
    log.debug CONTROLLER_NAME + "#${API_CALL}"

	// BEGIN - backward compatibility
	if (StringUtils.isEmpty(params.checksum)) {
		invalid("checksumError", "You did not pass the checksum security check")
		return
	}

	if (StringUtils.isEmpty(params.meetingID)) {
		invalid("missingParamMeetingID", "You must specify a meeting ID for the meeting.");
		return
	}
	
	if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
		invalid("checksumError", "You did not pass the checksum security check")
		return
	}
	// END - backward compatibility
	
  	ApiErrors errors = new ApiErrors()
  	
    // Do we have a checksum? If none, complain.
    if (StringUtils.isEmpty(params.checksum)) {
      errors.missingParamError("checksum");
    }

    // Do we have a meeting id? If none, complain.
    String externalMeetingId = params.meetingID
    if (StringUtils.isEmpty(externalMeetingId)) {
      errors.missingParamError("meetingID");
    }

    if (errors.hasErrors()) {
    	respondWithErrors(errors)
    	return
    }
    
    // Do we agree on the checksum? If not, complain.		
    if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
      	errors.checksumError()
    	respondWithErrors(errors)
    	return
    }
            
    // Everything is good so far. Translate the external meeting id to an internal meeting id. If
    // we can't find the meeting, complain.					        
    String internalMeetingId = paramsProcessorUtil.convertToInternalMeetingId(externalMeetingId);
    log.info("Retrieving meeting ${internalMeetingId}")		
    Meeting meeting = meetingService.getMeeting(internalMeetingId);
	boolean isRunning = meeting != null && meeting.isRunning();
   
    response.addHeader("Cache-Control", "no-cache")
    withFormat {	
      xml {
        render(contentType:"text/xml") {
          response() {
            returncode(RESP_CODE_SUCCESS)
            running(isRunning ? "true" : "false")
          }
        }
      }
    }
  }

  /************************************
   * END API
   ************************************/
  def end = {
    String API_CALL = "end"
    
    log.debug CONTROLLER_NAME + "#${API_CALL}"    
	
	// BEGIN - backward compatibility
	if (StringUtils.isEmpty(params.checksum)) {
		invalid("checksumError", "You did not pass the checksum security check")
		return
	}

	if (StringUtils.isEmpty(params.meetingID)) {
		invalid("missingParamMeetingID", "You must specify a meeting ID for the meeting.");
		return
	}
	
	if (StringUtils.isEmpty(params.password)) {
		invalid("invalidPassword","You must supply the moderator password for this call.");
		return
	}
	
	if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
		invalid("checksumError", "You did not pass the checksum security check")
		return
	}
	// END - backward compatibility
	
    ApiErrors errors = new ApiErrors()
    
    // Do we have a checksum? If none, complain.
    if (StringUtils.isEmpty(params.checksum)) {
      errors.missingParamError("checksum");
    }

    // Do we have a meeting id? If none, complain.
    String externalMeetingId = params.meetingID
    if (StringUtils.isEmpty(externalMeetingId)) {
      errors.missingParamError("meetingID");
    }

    // Do we have a password? If not, complain.
    String modPW = params.password
    if (StringUtils.isEmpty(modPW)) {
      errors.missingParamError("password");
    }

    if (errors.hasErrors()) {
    	respondWithErrors(errors)
    	return
    }
    
    // Do we agree on the checksum? If not, complain.		
    if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
      	errors.checksumError()
    	respondWithErrors(errors)
    	return
    }
            
    // Everything is good so far. Translate the external meeting id to an internal meeting id. If
    // we can't find the meeting, complain.					        
    String internalMeetingId = paramsProcessorUtil.convertToInternalMeetingId(externalMeetingId);
    log.info("Retrieving meeting ${internalMeetingId}")		
    Meeting meeting = meetingService.getMeeting(internalMeetingId);
    if (meeting == null) {
		// BEGIN - backward compatibility
		invalid("notFound", "We could not find a meeting with that meeting ID - perhaps the meeting is not yet running?");
		return;
		// END - backward compatibility
		
	   errors.invalidMeetingIdError();
	   respondWithErrors(errors)
	   return;
    }
    
    if (meeting.getModeratorPassword().equals(modPW) == false) {
		// BEGIN - backward compatibility
		invalid("invalidPassword","You must supply the moderator password for this call.");
		return;
		// END - backward compatibility
		
	   errors.invalidPasswordError();
	   respondWithErrors(errors)
	   return;
    }
       
    meetingService.endMeeting(meeting.getInternalId());
    
    response.addHeader("Cache-Control", "no-cache")
    withFormat {	
      xml {
        render(contentType:"text/xml") {
          response() {
            returncode(RESP_CODE_SUCCESS)
            messageKey("sentEndMeetingRequest")
            message("A request to end the meeting was sent.  Please wait a few seconds, and then use the getMeetingInfo or isMeetingRunning API calls to verify that it was ended.")
          }
        }
      }
    }
  }

  /*****************************************
   * GETMEETINGINFO API
   *****************************************/
  def getMeetingInfo = {
    String API_CALL = "getMeetingInfo"
    log.debug CONTROLLER_NAME + "#${API_CALL}"
    
	// BEGIN - backward compatibility
	if (StringUtils.isEmpty(params.checksum)) {
		invalid("checksumError", "You did not pass the checksum security check")
		return
	}

	if (StringUtils.isEmpty(params.meetingID)) {
		invalid("missingParamMeetingID", "You must specify a meeting ID for the meeting.");
		return
	}
	
	if (StringUtils.isEmpty(params.password)) {
		invalid("invalidPassword","You must supply the moderator password for this call.");
		return
	}
	
	if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
		invalid("checksumError", "You did not pass the checksum security check")
		return
	}
	// END - backward compatibility
	
    ApiErrors errors = new ApiErrors()
        
    // Do we have a checksum? If none, complain.
    if (StringUtils.isEmpty(params.checksum)) {
      errors.missingParamError("checksum");
    }

    // Do we have a meeting id? If none, complain.
    String externalMeetingId = params.meetingID
    if (StringUtils.isEmpty(externalMeetingId)) {
      errors.missingParamError("meetingID");
    }

    // Do we have a password? If not, complain.
    String modPW = params.password
    if (StringUtils.isEmpty(modPW)) {
      errors.missingParamError("password");
    }

    if (errors.hasErrors()) {
    	respondWithErrors(errors)
    	return
    }
    
    // Do we agree on the checksum? If not, complain.		
    if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
      	errors.checksumError()
    	respondWithErrors(errors)
    	return
    }
    
    // Everything is good so far. Translate the external meeting id to an internal meeting id. If
    // we can't find the meeting, complain.					        
    String internalMeetingId = paramsProcessorUtil.convertToInternalMeetingId(externalMeetingId);
    log.info("Retrieving meeting ${internalMeetingId}")		
    Meeting meeting = meetingService.getMeeting(internalMeetingId);
    if (meeting == null) {
		// BEGIN - backward compatibility
		invalid("notFound", "We could not find a meeting with that meeting ID");
		return;
		// END - backward compatibility
		
	   errors.invalidMeetingIdError();
	   respondWithErrors(errors)
	   return;
    }
    
    if (meeting.getModeratorPassword().equals(modPW) == false) {
		// BEGIN - backward compatibility
		invalid("invalidPassword","You must supply the moderator password for this call."); 
		return;
		// END - backward compatibility
		
	   errors.invalidPasswordError();
	   respondWithErrors(errors)
	   return;
    }
    
    respondWithConferenceDetails(meeting, null, null, null);
  }
  
  /************************************
   *	GETMEETINGS API
   ************************************/
  def getMeetings = {
    String API_CALL = "getMeetings"
    log.debug CONTROLLER_NAME + "#${API_CALL}"
    
	// BEGIN - backward compatibility
	if (StringUtils.isEmpty(params.checksum)) {
		invalid("checksumError", "You did not pass the checksum security check")
		return
	}
	
	if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
		invalid("checksumError", "You did not pass the checksum security check")
		return
	}
	// END - backward compatibility
	
    ApiErrors errors = new ApiErrors()
        
    // Do we have a checksum? If none, complain.
    if (StringUtils.isEmpty(params.checksum)) {
      errors.missingParamError("checksum");
    }

    if (errors.hasErrors()) {
    	respondWithErrors(errors)
    	return
    }
    
    // Do we agree on the checksum? If not, complain.		
    if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
      	errors.checksumError()
    	respondWithErrors(errors)
    	return
    }
        
    Collection<Meeting> mtgs = meetingService.getMeetings();
    
    if (mtgs == null || mtgs.isEmpty()) {
      response.addHeader("Cache-Control", "no-cache")
      withFormat {	
        xml {
          render(contentType:"text/xml") {
            response() {
              returncode(RESP_CODE_SUCCESS)
              meetings(null)
              messageKey("noMeetings")
              message("no meetings were found on this server")
            }
          }
        }
      }
      return;
    }
    
    response.addHeader("Cache-Control", "no-cache")
    withFormat {	
      xml {
        render(contentType:"text/xml") {
          response() {
            returncode(RESP_CODE_SUCCESS)
            meetings() {
              mtgs.each { m ->
                meeting() {
                  meetingID(m.getExternalId())
				  meetingName(m.getName())
				  createTime(m.getCreateTime())
                  attendeePW(m.getViewerPassword())
                  moderatorPW(m.getModeratorPassword())
                  hasBeenForciblyEnded(m.isForciblyEnded() ? "true" : "false")
                  running(m.isRunning() ? "true" : "false")
                }
              }
            }
          }
        }
      }
    }
  }

  /***********************************************
   * ENTER API
   ***********************************************/
  def enter = {	    
    if (! session["user-token"] || (meetingService.getUserSession(session['user-token']) == null)) {
      log.info("No session for user in conference.")
	  
	  Meeting meeting = null;	  
	  
	  // Determine the logout url so we can send the user there.
	  String logoutUrl = session["logout-url"]
					
	  if (! session['meeting-id']) {
		  meeting = meetingService.getMeeting(session['meeting-id']);
	  }
	
	  // Log the user out of the application.
	  session.invalidate()
	
	  if (meeting != null) {
		  log.debug("Logging out from [" + meeting.getInternalId() + "]");
		  logoutUrl = meeting.getLogoutUrl();
	  }
	  
	  if (StringUtils.isEmpty(logoutUrl))
	  	logoutUrl = paramsProcessorUtil.getDefaultLogoutUrl()
	  
      response.addHeader("Cache-Control", "no-cache")
      withFormat {				
        xml {
          render(contentType:"text/xml") {
            response() {
              returncode("FAILED")
              message("Could not find conference.")
			  logoutURL(logoutUrl)
            }
          }
        }
      }
	  
    } else {
		UserSession us = meetingService.getUserSession(session['user-token']);	
        log.info("Found conference for " + us.fullname)
        response.addHeader("Cache-Control", "no-cache")
        withFormat {				
        xml {
          render(contentType:"text/xml") {
            response() {
              returncode("SUCCESS")
              fullname(us.fullname)
              confname(us.conferencename)
              meetingID(us.meetingID)
			  externMeetingID(us.externMeetingID)
              externUserID(us.externUserID)
			  internalUserID(us.internalUserId)
              role(us.role)
              conference(us.conference)
              room(us.room)
              voicebridge(us.voicebridge)
              webvoiceconf(us.webvoiceconf)
              mode(us.mode)
              record(us.record)
              welcome(us.welcome)
			  logoutUrl(us.logoutUrl)
			  defaultLayout(us.defaultLayout)
			  avatarURL(us.avatarURL)
            }
          }
        }
      }
      }  
  }
  
  /*************************************************
   * SIGNOUT API
   *************************************************/
  def signOut = {  
	Meeting meeting = null;
  	
	if (session["user-token"] && (meetingService.getUserSession(session['user-token']) != null)) {
		  log.info("Found session for user in conference.")
		  UserSession us = meetingService.removeUserSession(session['user-token']);
		  meeting = meetingService.getMeeting(us.meetingID);
	}
		  
  	String logoutUrl = paramsProcessorUtil.getDefaultLogoutUrl()
                    
	if ((meeting == null) && (! session['meeting-id'])) {
		meeting = meetingService.getMeeting(session['meeting-id']);
	}
	
	// Log the user out of the application.
	session.invalidate()
	
  	if (meeting != null) {
  	  log.debug("Logging out from [" + meeting.getInternalId() + "]");
  		logoutUrl = meeting.getLogoutUrl();
  	}     
   
  	log.debug("Signing out. Redirecting to " + logoutUrl)
    response.addHeader("Cache-Control", "no-cache")
    withFormat {	
      xml {
        render(contentType:"text/xml") {
          response() {
            returncode(RESP_CODE_SUCCESS)
          }
        }
      }
    }
  }
 
  /******************************************************
   * GET_RECORDINGS API
   ******************************************************/
  def getRecordings = {
    String API_CALL = "getRecordings"
    log.debug CONTROLLER_NAME + "#${API_CALL}"
    
	// BEGIN - backward compatibility
	if (StringUtils.isEmpty(params.checksum)) {
		invalid("checksumError", "You did not pass the checksum security check")
		return
	}
	
	if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
		invalid("checksumError", "You did not pass the checksum security check")
		return
	}
	// END - backward compatibility
	
    ApiErrors errors = new ApiErrors()
        
    // Do we have a checksum? If none, complain.
    if (StringUtils.isEmpty(params.checksum)) {
      errors.missingParamError("checksum");
	  respondWithErrors(errors)
	  return
    }
	
    // Do we agree on the checksum? If not, complain.   
    if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
        errors.checksumError()
      respondWithErrors(errors)
      return
    }
	
	ArrayList<String> externalMeetingIds = new ArrayList<String>();
	if (!StringUtils.isEmpty(params.meetingID)) {
		externalMeetingIds=paramsProcessorUtil.decodeIds(params.meetingID);
	}
    
    // Everything is good so far. Translate the external meeting ids to an internal meeting ids.             
    ArrayList<String> internalMeetingIds = paramsProcessorUtil.convertToInternalMeetingId(externalMeetingIds);        
	HashMap<String,Recording> recs = meetingService.getRecordings(internalMeetingIds);
	
    if (recs.isEmpty()) {
      response.addHeader("Cache-Control", "no-cache")
      withFormat {  
        xml {
          render(contentType:"text/xml") {
            response() {
              returncode(RESP_CODE_SUCCESS)
              recordings(null)
              messageKey("noRecordings")
              message("There are not recordings for the meetings")
            }
          }
        }
      }
      return;
    }
    withFormat {  
      xml {
        render(contentType:"text/xml") {
          response() {
           returncode(RESP_CODE_SUCCESS)
            recordings() {
              recs.values().each { r ->
				  recording() {
                  recordID(r.getId())
				  meetingID(r.getMeetingID())
				  name(''){
					  mkp.yieldUnescaped("<![CDATA["+r.getName()+"]]>")
				  }
                  published(r.isPublished())
                  startTime(r.getStartTime())
                  endTime(r.getEndTime())
				  metadata() {
					 r.getMetadata().each { k,v ->
						 "$k"(''){ 
							 mkp.yieldUnescaped("<![CDATA[$v]]>") 
						 }
					 }
				  }
				  playback() {
					  r.getPlaybacks().each { item ->
						  format{
							  type(item.getFormat())
							  url(item.getUrl())
							  length(item.getLength())
						  }
					  }
                  }
                  
                }
              }
            }
          }
        }
      }
    }
  } 
  
  /******************************************************
  * PUBLISH_RECORDINGS API
  ******************************************************/
  
  def publishRecordings = {
	  String API_CALL = "publishRecordings"
	  log.debug CONTROLLER_NAME + "#${API_CALL}"
	  
	  // BEGIN - backward compatibility
	  if (StringUtils.isEmpty(params.checksum)) {
		  invalid("checksumError", "You did not pass the checksum security check")
		  return
	  }
	  
	  if (StringUtils.isEmpty(params.recordID)) {
		  invalid("missingParamRecordID", "You must specify a recordID.");
		  return
	  }
	  
	  if (StringUtils.isEmpty(params.publish)) {
		  invalid("missingParamPublish", "You must specify a publish value true or false.");
		  return
	  }
	  
	  if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
		  invalid("checksumError", "You did not pass the checksum security check")
		  return
	  }
	  // END - backward compatibility
	  
	  ApiErrors errors = new ApiErrors()
	  
	  // Do we have a checksum? If none, complain.
	  if (StringUtils.isEmpty(params.checksum)) {
		errors.missingParamError("checksum");
	  }
	
	  // Do we have a recording id? If none, complain.
	  String recordId = params.recordID
	  if (StringUtils.isEmpty(recordId)) {
		errors.missingParamError("recordID");
	  }
	  // Do we have a publish status? If none, complain.
	  String publish = params.publish
	  if (StringUtils.isEmpty(publish)) {
		errors.missingParamError("publish");
	  }
	
	  if (errors.hasErrors()) {
		  respondWithErrors(errors)
		  return
	  }
  
	  // Do we agree on the checksum? If not, complain.
	  if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
		  errors.checksumError()
		  respondWithErrors(errors)
		  return
	  }
	  
	  ArrayList<String> recordIdList = new ArrayList<String>();
	  if (!StringUtils.isEmpty(recordId)) {
		  recordIdList=paramsProcessorUtil.decodeIds(recordId);
	  }
	  
	  if(!meetingService.existsAnyRecording(recordIdList)){
		  // BEGIN - backward compatibility
		  invalid("notFound", "We could not find recordings");
		  return;
		  // END - backward compatibility
		  
		  errors.recordingNotFound();
		  respondWithErrors(errors);
		  return;
	  }
	  
	  meetingService.setPublishRecording(recordIdList,publish.toBoolean());
	  withFormat {
		  xml {
			render(contentType:"text/xml") {
			  response() {
				  returncode(RESP_CODE_SUCCESS)
				  published(publish)
			  }
			}
		  }
		}
  }
  
  /******************************************************
  * DELETE_RECORDINGS API
  ******************************************************/
  def deleteRecordings = {
	  String API_CALL = "deleteRecordings"
	  log.debug CONTROLLER_NAME + "#${API_CALL}"
	  
	  // BEGIN - backward compatibility
	  if (StringUtils.isEmpty(params.checksum)) {
		  invalid("checksumError", "You did not pass the checksum security check")
		  return
	  }
	  
	  if (StringUtils.isEmpty(params.recordID)) {
		  invalid("missingParamRecordID", "You must specify a recordID.");
		  return
	  }
	  
	  if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
		  invalid("checksumError", "You did not pass the checksum security check")
		  return
	  }
	  // END - backward compatibility
	  
	  ApiErrors errors = new ApiErrors()
	  
	  // Do we have a checksum? If none, complain.
	  if (StringUtils.isEmpty(params.checksum)) {
		errors.missingParamError("checksum");
	  }
	
	  // Do we have a recording id? If none, complain.
	  String recordId = params.recordID
	  if (StringUtils.isEmpty(recordId)) {
		errors.missingParamError("recordID");
	  }
	
	  if (errors.hasErrors()) {
		  respondWithErrors(errors)
		  return
	  }
  
	  // Do we agree on the checksum? If not, complain.
	  if (! paramsProcessorUtil.isChecksumSame(API_CALL, params.checksum, request.getQueryString())) {
		  errors.checksumError()
		  respondWithErrors(errors)
		  return
	  }
	  
	  ArrayList<String> recordIdList = new ArrayList<String>();
	  if (!StringUtils.isEmpty(recordId)) {
		  recordIdList=paramsProcessorUtil.decodeIds(recordId);
	  }
	  
	  if(recordIdList.isEmpty()){
		  // BEGIN - backward compatibility
		  invalid("notFound", "We could not find recordings");
		  return;
		  // END - backward compatibility
		  
		  errors.recordingNotFound();
		  respondWithErrors(errors);
		  return;
	  }
	  
	  meetingService.deleteRecordings(recordIdList);
	  withFormat {
		  xml {
			render(contentType:"text/xml") {
			  response() {
				  returncode(RESP_CODE_SUCCESS)
				  deleted(true)
			  }
			}
		  }
		}
  }
  
  def uploadDocuments(conf) { 
    log.debug("ApiController#uploadDocuments(${conf.getInternalId()})");

    String requestBody = request.inputStream == null ? null : request.inputStream.text;
    requestBody = StringUtils.isEmpty(requestBody) ? null : requestBody;

    if (requestBody == null) {
		System.out.println("No pre-uploaded presentation. Downloading default presentation.");
		downloadAndProcessDocument(presentationService.defaultUploadedPresentation, conf);
    } else {
		System.out.println("Request body: \n" + requestBody);
		log.debug "Request body: \n" + requestBody;
	
		def xml = new XmlSlurper().parseText(requestBody);
		xml.children().each { module ->
		  log.debug("module config found: [${module.@name}]");
		  if ("presentation".equals(module.@name.toString())) {
			// need to iterate over presentation files and process them
			module.children().each { document ->
			  if (!StringUtils.isEmpty(document.@url.toString())) {
				downloadAndProcessDocument(document.@url.toString(), conf);
			  } else if (!StringUtils.isEmpty(document.@name.toString())) {
				def b64 = new Base64()
				def decodedBytes = b64.decode(document.text().getBytes())
				processDocumentFromRawBytes(decodedBytes, document.@name.toString(), conf);
			  } else {
				log.debug("presentation module config found, but it did not contain url or name attributes");
			  }
			}
		  }
		}
	}

  }
  def cleanFilename(filename) {
    String fname = URLDecoder.decode(filename).trim()
    def notValidCharsRegExp = /[^0-9a-zA-Z_\.]/
    return fname.replaceAll(notValidCharsRegExp, '-')
  }

 def processDocumentFromRawBytes(bytes, filename, conf) {
    def cleanName = cleanFilename(filename);
    def nameWithoutExt = cleanName.substring(0, cleanName.lastIndexOf("."));
    File uploadDir = presentationService.uploadedPresentationDirectory(conf.getInternalId(), conf.getInternalId(), nameWithoutExt);
    def pres = new File(uploadDir.absolutePath + File.separatorChar + cleanName);

    FileOutputStream fos = new java.io.FileOutputStream(pres)
    fos.write(bytes)
    fos.flush()
    fos.close()

    processUploadedFile(nameWithoutExt, pres, conf);
  }
  
 def downloadAndProcessDocument(address, conf) {
    log.debug("ApiController#downloadAndProcessDocument({$address}, ${conf.getInternalId()})");
    String name = cleanFilename(address.tokenize("/")[-1]);
    log.debug("Uploading presentation: ${name} from ${address} [starting download]");
    String nameWithoutExt = name.substring(0, name.lastIndexOf("."));
    def out;
    def pres;
    try {
      File uploadDir = presentationService.uploadedPresentationDirectory(conf.getInternalId(), conf.getInternalId(), nameWithoutExt);
      pres = new File(uploadDir.absolutePath + File.separatorChar + name);
      out = new BufferedOutputStream(new FileOutputStream(pres))
      out << new URL(address).openStream()
    } finally {
      if (out != null) {
        out.close()
      }
    }

    processUploadedFile(nameWithoutExt, pres, conf);
  }

  
  def processUploadedFile(name, pres, conf) {
    UploadedPresentation uploadedPres = new UploadedPresentation(conf.getInternalId(), conf.getInternalId(), name);
    uploadedPres.setUploadedFile(pres);
    presentationService.processUploadedPresentation(uploadedPres);
  }
  
  def beforeInterceptor = {
    if (paramsProcessorUtil.isServiceEnabled() == false) {
      log.info("apiNotEnabled: The API service and/or controller is not enabled on this server.  To use it, you must first enable it.")
      // TODO: this doesn't stop the request - so it generates invalid XML
      //			since the request continues and renders a second response
      invalid("apiNotEnabled", "The API service and/or controller is not enabled on this server.  To use it, you must first enable it.")
    }
  }

  def respondWithConferenceDetails(meeting, room, msgKey, msg) {
    response.addHeader("Cache-Control", "no-cache")
    withFormat {				
      xml {
        render(contentType:"text/xml") {
          response() {
            returncode(RESP_CODE_SUCCESS)
			meetingName(meeting.getName())
            meetingID(meeting.getExternalId())
			createTime(meeting.getCreateTime())
			voiceBridge(meeting.getTelVoice())
            attendeePW(meeting.getViewerPassword())
            moderatorPW(meeting.getModeratorPassword())
            running(meeting.isRunning() ? "true" : "false")
			recording(meeting.isRecord() ? "true" : "false")
            hasBeenForciblyEnded(meeting.isForciblyEnded() ? "true" : "false")
            startTime(meeting.getStartTime())
            endTime(meeting.getEndTime())
            participantCount(meeting.getNumUsers())
            maxUsers(meeting.getMaxUsers())
            moderatorCount(meeting.getNumModerators())
            attendees() {
              meeting.getUsers().each { att ->
                attendee() {
                  userID("${att.externalUserId}")
                  fullName("${att.fullname}")
                  role("${att.role}")
                }
              }
            }
			metadata(){
				meeting.getMetadata().each{ k,v ->
					"$k"("$v")
				}
			}
            messageKey(msgKey == null ? "" : msgKey)
            message(msg == null ? "" : msg)
          }
        }
      }
    }			 
  }
  
  def respondWithConference(meeting, msgKey, msg) {
    response.addHeader("Cache-Control", "no-cache")
    withFormat {	
      xml {
        log.debug "Rendering as xml"
        render(contentType:"text/xml") {
          response() {
            returncode(RESP_CODE_SUCCESS)
            meetingID(meeting.getExternalId())
            attendeePW(meeting.getViewerPassword())
            moderatorPW(meeting.getModeratorPassword())
            createTime(meeting.getCreateTime())
            hasBeenForciblyEnded(meeting.isForciblyEnded() ? "true" : "false")
            messageKey(msgKey == null ? "" : msgKey)
            message(msg == null ? "" : msg)
          }
        }
      }
    }
  }
  
  def respondWithErrors(errorList) {
    log.debug CONTROLLER_NAME + "#invalid"
    response.addHeader("Cache-Control", "no-cache")
    withFormat {				
      xml {
        render(contentType:"text/xml") {
          response() {
            returncode(RESP_CODE_FAILED)
            errors() {
              ArrayList errs = errorList.getErrors();
              Iterator itr = errs.iterator();
              while (itr.hasNext()){
                String[] er = (String[]) itr.next();
                log.debug CONTROLLER_NAME + "#invalid" + er[0]
                error(key: er[0], message: er[1])
              }          
            }
          }
        }
      }
      json {
        log.debug "Rendering as json"
        render(contentType:"text/json") {
            returncode(RESP_CODE_FAILED)
            messageKey(key)
            message(msg)
        }
      }
    }  
  }
  //TODO: method added for backward compability, it will be removed in next versions after 0.8
  def invalid(key, msg) {
	  String deprecatedMsg=" Note: This xml scheme will be DEPRECATED."
	  log.debug CONTROLLER_NAME + "#invalid"
	  response.addHeader("Cache-Control", "no-cache")
	  withFormat {
		  xml {
			  render(contentType:"text/xml") {
				  response() {
					  returncode(RESP_CODE_FAILED)
					  messageKey(key)
					  message(msg)
				  }
			  }
		  }
		  json {
			  log.debug "Rendering as json"
			  render(contentType:"text/json") {
					  returncode(RESP_CODE_FAILED)
					  messageKey(key)
					  message(msg)
			  }
		  }
	  }
  }
  
  def parseBoolean(obj) {
		if (obj instanceof Number) {
			return ((Number) obj).intValue() == 1;
		}
		return false
  }  
}
