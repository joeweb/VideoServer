
var presenterUserID = "";

var registerListeners = function() {
  console.log("Listening for events.");
  BBB.listen("SwitchedLayoutEvent", function(bbbEvent) {
    console.log("New Layout [" + bbbEvent.layoutID + "].");
  });
  BBB.listen("NewRoleEvent", function(bbbEvent) {
    console.log("New Role Event [amIPresenter=" + bbbEvent.amIPresenter + ",role=" + bbbEvent.role + ",newPresenterUserID=" + bbbEvent.newPresenterUserID + "].");
  });
  BBB.listen("SwitchedPresenterEvent", function(bbbEvent) {
    console.log("Switched Presenter [amIPresenter=" + bbbEvent.amIPresenter + ",role=" + bbbEvent.role + ",newPresenterUserID=" + bbbEvent.newPresenterUserID + "].");
	
	presenterUserID = bbbEvent.newPresenterUserID;
	
	if (bbbEvent.amIPresenter) {
		console.log("*** I am presenter. Am I publishing webcam?");
		BBB.listen("AmISharingCamQueryResponse", function(bbbEvent2) {
			console.log("AmISharingCamQueryResponse [isPublishing=" + bbbEvent2.isPublishing + ",camIndex=" + bbbEvent2.camIndex + "]");
		});
		BBB.amISharingWebcam();
		BBB.amISharingWebcam(function(bbbEvent3) {
			console.log("amISharingWebcam [isPublishing=" + bbbEvent3.isPublishing 
						+ ",camIndex=" + bbbEvent3.camIndex 
						+ ",camWidth=" + bbbEvent3.camWidth
						+ ",camHeight=" + bbbEvent3.camHeight
						+ ",camKeyFrameInterval=" + bbbEvent3.camKeyFrameInterval
						+ ",camModeFps=" + bbbEvent3.camModeFps
						+ ",camQualityBandwidth=" + bbbEvent3.camQualityBandwidth
						+ ",camQualityPicture=" + bbbEvent3.camQualityPicture						
						+ "]");
			if (bbbEvent3.isPublishing) {	
				CAM_PREVIEW.stopPreviewCamera();
				CAM_PREVIEW.previewCamera(bbbEvent3.camIndex, bbbEvent3.camWidth, bbbEvent3.camHeight, bbbEvent3.camKeyFrameInterval,
										  bbbEvent3.camModeFps, bbbEvent3.camQualityBandwidth, bbbEvent3.camQualityPicture);
			}
		});
	} else {
		console.log("*** I am NOT presenter. Is new presenter publishing webcam?");
		BBB.listen("IsUserPublishingCamResponse", function(bbbEvent4) {
			console.log("IsUserPublishingCamResponse [isUserPublishing=" + bbbEvent4.isUserPublishing 
						+ ",uri=" + bbbEvent4.uri 
						+ ",streamName=" + bbbEvent4.streamName + "]");
		});
		BBB.isUserSharingWebcam(bbbEvent.newPresenterUserID);
		BBB.isUserSharingWebcam(bbbEvent.newPresenterUserID, function(bbbEvent5) {
			console.log("isUserSharingWebcam [isUserPublishing=" + bbbEvent5.isUserPublishing 
						+ ",uri=" + bbbEvent5.uri 
						+ ",streamName=" + bbbEvent5.streamName + "]");
			if (presenterUserID == bbbEvent.userID) {
				CAM_VIEW.stopViewWebcamStream();
				CAM_VIEW.viewWebcamStream(bbbEvent.uri, bbbEvent.streamName);
			}
		});	
		CAM_PREVIEW.stopPreviewCamera();
	}
  });
  BBB.listen("UserLeftEvent", function(bbbEvent) {
    console.log("User [" + bbbEvent.userID + "] has left.");
  });
  BBB.listen("UserJoinedEvent", function(bbbEvent) {
    console.log("User [" + bbbEvent.userID + ", [" + bbbEvent.userName + "] has joined.");
  });
  BBB.listen("NewPublicChatEvent", function(bbbEvent) {
    console.log("Received NewPublicChatEvent [" + bbbEvent.message + "]");
  });
  BBB.listen("NewPrivateChatEvent", function(bbbEvent) {
    console.log("Received NewPrivateChatEvent event");
  });
  BBB.listen("UserJoinedVoiceEvent", function(bbbEvent) {
    console.log("User [" + bbbEvent.userID + "] had joined the voice conference.");
  });
  BBB.listen("UserLeftVoiceEvent", function(bbbEvent) {
    console.log("User [" + bbbEvent.userID + "has left the voice conference.");
  });
  BBB.listen("UserVoiceMutedEvent", function(bbbEvent) {
    console.log("User [" + bbbEvent.userID + "] is muted [" + bbbEvent.muted + "]");
  });
  BBB.listen("UserLockedVoiceEvent", function(bbbEvent) {
    console.log("User [" + bbbEvent.userID + "] is locked [" + bbbEvent.locked + "]");
  });
  BBB.listen("CamStreamSharedEvent", function(bbbEvent) {
    console.log("User CamStreamSharedEvent [" + bbbEvent.uri + "," + bbbEvent.streamName + "]");
	if (presenterUserID == bbbEvent.userID) {
	    CAM_VIEW.stopViewWebcamStream();
		CAM_VIEW.viewWebcamStream(bbbEvent.uri, bbbEvent.streamName);
	}
  });
  BBB.listen("BroadcastingCameraStartedEvent", function(bbbEvent) {
    console.log("User BroadcastingCameraStartedEvent [" + bbbEvent.camIndex + "] [" + bbbEvent.camWidth + "]");
	if (bbbEvent.isPresenter) {	
		CAM_PREVIEW.stopPreviewCamera();
		CAM_PREVIEW.previewCamera(bbbEvent.camIndex, bbbEvent.camWidth, bbbEvent.camHeight, bbbEvent.camKeyFrameInterval,
								  bbbEvent.camModeFps, bbbEvent.camQualityBandwidth, bbbEvent.camQualityPicture);
	}
  });
  BBB.listen("BroadcastingCameraStoppedEvent", function(bbbEvent) {
    console.log("User BroadcastingCameraStoppedEvent ]");
    CAM_PREVIEW.stopPreviewCamera();
  });
}

var leaveVoiceConference2 = function () {
  BBB.leaveVoiceConference();
}

var joinVoiceConference2 = function () {
  BBB.joinVoiceConference();
}

var amIPresenterAsync = function() {
  BBB.listen("AmIPresenterQueryResponse", function(bbbEvent) {
    console.log("Received AmIPresenterQueryResponse event [" + bbbEvent.amIPresenter + "]");
  });

  BBB.amIPresenter();
}

var amIPresenterSync = function() {
  BBB.amIPresenter(function(amIPresenter) {
    console.log("Am I Presenter = " + amIPresenter);
  });
}

var getMyUserInfoAsynch = function() {
  BBB.listen("GetMyUserInfoResponse", function(bbbEvent) {
    console.log("User info response [myUserID=" + bbbEvent.myUserID 
				+ ",myUsername=" + bbbEvent.myUsername + ",myAvatarURL=" + bbbEvent.myAvatarURL 
				+ ",myRole=" + bbbEvent.myRole + ",amIPresenter=" + bbbEvent.amIPresenter + "].");
  });

  BBB.getMyUserInfo();
}

var getMyUserInfoSynch = function() {
  BBB.getMyUserInfo(function(userInfo) {
    console.log("User info callback [myUserID=" + userInfo.myUserID 
				+ ",myUsername=" + userInfo.myUsername + ",myAvatarURL=" + userInfo.myAvatarURL 
				+ ",myRole=" + userInfo.myRole + ",amIPresenter=" + userInfo.amIPresenter + "].");
  });
}

var getMyRoleAsynch = function() {
  BBB.listen("GetMyRoleResponse", function(bbbEvent) {
    console.log("Received GetMyRoleResponse event [" + bbbEvent.myRole + "]");
  });

  BBB.getMyRole();
}

var getMyRoleSynch = function() {
  BBB.getMyRole(function(myRole) {
    console.log("My role = " + myRole);
  });
}

var getMyUserID = function() {
  BBB.getMyUserID(function(userID) {
    console.log("My user ID  = [" + userID + "]");
  });
}

var getMeetingID = function() {
  BBB.getMeetingID(function(meetingID) {
    console.log("Meeting ID  = [" + meetingID + "]");
  });
}

var muteMe = function() {
  BBB.muteMe();
}

var unmuteMe = function() {
  BBB.unmuteMe();
}

var muteAll = function() {
  BBB.muteAll();
}

var unmuteAll = function() {
  BBB.unmuteAll();
} 

var switchLayout = function(newLayout) {
  BBB.switchLayout(newLayout);
}

var lockLayout = function(lock) {
  BBB.lockLayout(lock);
}


var sendPublicChat = function () {
  var message = "Hello from the Javascript API";
  BBB.sendPublicChatMessage('0x7A7A7A', "en", message);
}

var sendPrivateChat = function () {
  var message = "ECHO: " + bbbEvent.message;
  BBB.sendPrivateChatMessage(bbbEvent.fromColor, bbbEvent.fromLang, message,  bbbEvent.fromUserID);
}
