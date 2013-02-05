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
package org.bigbluebutton.modules.broadcast.managers
{
	import com.asfusion.mate.events.Dispatcher;
	
	import flash.events.AsyncErrorEvent;
	import flash.events.Event;
	import flash.events.NetStatusEvent;
	import flash.events.SecurityErrorEvent;
	import flash.media.Video;
	import flash.net.NetConnection;
	import flash.net.NetStream;
	
	import mx.core.UIComponent;
	
	import org.bigbluebutton.common.LogUtil;
	import org.bigbluebutton.common.events.OpenWindowEvent;
	import org.bigbluebutton.core.BBB;
	import org.bigbluebutton.core.managers.UserManager;
	import org.bigbluebutton.main.events.BBBEvent;
	import org.bigbluebutton.modules.broadcast.models.BroadcastOptions;
	import org.bigbluebutton.modules.broadcast.models.Stream;
	import org.bigbluebutton.modules.broadcast.models.Streams;
	import org.bigbluebutton.modules.broadcast.services.BroadcastService;
	import org.bigbluebutton.modules.broadcast.services.StreamsService;
	import org.bigbluebutton.modules.broadcast.views.BroadcastWindow;
	
	public class BroadcastManager {	
		private var broadcastWindow:BroadcastWindow;
		private var dispatcher:Dispatcher;
		private var broadcastService:BroadcastService = new BroadcastService();
		private var streamService:StreamsService;
		private var opt:BroadcastOptions;
    
		[Bindable]
		public var streams:Streams = new Streams();	
		private var curStream:Stream;
		
		public function BroadcastManager() {
			streamService = new StreamsService(this);
			LogUtil.debug("BroadcastManager Created");
		}
		
		public function start():void {
			LogUtil.debug("BroadcastManager Start");
      opt = new BroadcastOptions();
			dispatcher = new Dispatcher();
      streamService.queryAvailableStreams(opt.streamsUri);
		}
		
    public function handleStreamsListLoadedEvent():void {
      if (broadcastWindow == null){
        trace("*** BroadcastManager Opening BroadcastModule Window");

        broadcastWindow = new BroadcastWindow();
        broadcastWindow.options = opt;
        broadcastWindow.streams = streams;
        
        broadcastWindow.broadcastManager = this;
        
        var e:OpenWindowEvent = new OpenWindowEvent(OpenWindowEvent.OPEN_WINDOW_EVENT);
        e.window = broadcastWindow;
        dispatcher.dispatchEvent(e);
        
      } else {
        trace("***BroadcastManager Not Opening BroadcastModule Window");
      }
      
 //     sendWhatIsTheCurrentStreamRequest();
      
      if (UserManager.getInstance().getConference().amIPresenter()) {
        handleSwitchToPresenterMode();
      } else {
        handleSwitchToViewerMode();
      }
    }
    
		public function handleSwitchToPresenterMode():void {
      if (broadcastWindow != null) {
        broadcastWindow.becomePresenter();        
      }
		}
		
		public function handleSwitchToViewerMode():void {
      if (broadcastWindow != null) {
        broadcastWindow.becomeViewer();
      }
			
		}
		
		public function playVideo(index:int):void {
      trace("BroadcastManager::playVideo [" + streams.streamUrls[index] + "],[" + streams.streamIds[index] + "],[" + streams.streamNames[index] + "]"); 
			broadcastService.playStream(streams.streamUrls[index], streams.streamIds[index], streams.streamNames[index]);
		}
				
		public function stopVideo():void {
      trace("BroadcastManager::stopVideo"); 
			broadcastService.stopStream();
		}
		
		public function sendWhatIsTheCurrentStreamRequest():void {
			broadcastService.sendWhatIsTheCurrentStreamRequest();
		}
		
		public function handleWhatIsTheCurrentStreamRequest(event:BBBEvent):void {
			trace("BroadcastManager:: Received " + event.payload["messageID"] );
			var isPresenter:Boolean = UserManager.getInstance().getConference().amIPresenter();
			if (isPresenter && curStream != null) {
				broadcastService.sendWhatIsTheCurrentStreamReply(event.payload["requestedBy"], curStream.getStreamId());
			}
		}
		
		public function handleWhatIsTheCurrentStreamReply(event:BBBEvent):void {
			trace("BroadcastManager:: Received " + event.payload["messageID"] );
			var amIRequester:Boolean = UserManager.getInstance().getConference().amIThisUser(event.payload["requestedBy"]);
			if (amIRequester) {
				var streamId:String = event.payload["streamID"];
				var info:Object = streams.getStreamNameAndUrl(streamId);
				if (info != null) {
					playStream(info["url"], streamId, info["name"]);
				}
			}
		}		
		
		private function playStream(url:String, streamId:String, streamName:String):void {
      trace("BroadcastManager::playStream [" + url + "], [" + streamId + "], [" + streamName + "]");
			curStream = new Stream(url, streamId, streamName);
			broadcastWindow.curStream = curStream;
			curStream.play(broadcastWindow);			
		}
		
		public function handlePlayStreamRequest(event:BBBEvent):void {
			trace("BroadcastManager Received " + event.payload["messageID"]);
			playStream(event.payload["uri"], event.payload["streamID"], event.payload["streamName"]);
		}
		
		public function handleStopStreamRequest(event:BBBEvent):void {
			trace("BroadcastManager Received " + event.payload["messageID"]);
			stopPlayingStream();
		}
		
		public function stopPlayingStream():void {
			if (curStream != null)	{
				curStream.stop();
				broadcastWindow.curStream = null;
				curStream == null;
			}	
		}
	}
}