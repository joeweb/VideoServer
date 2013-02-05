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
package org.bigbluebutton.modules.chat.services
{
  import flash.events.IEventDispatcher;
  
  import org.bigbluebutton.common.LogUtil;
  import org.bigbluebutton.core.BBB;
  import org.bigbluebutton.core.managers.ConnectionManager;
  import org.bigbluebutton.modules.chat.vo.ChatMessageVO;

  public class MessageSender
  {
    public var dispatcher:IEventDispatcher;
    
    public function getPublicChatMessages():void
    {  
      LogUtil.debug("Sending [chat.getPublicMessages] to server.");
      var _nc:ConnectionManager = BBB.initConnectionManager();
      _nc.sendMessage("chat.sendPublicChatHistory", 
        function(result:String):void { // On successful result
          LogUtil.debug(result); 
        },	                   
        function(status:String):void { // status - On error occurred
          LogUtil.error(status); 
        }
      );
    }
    
    public function sendPublicMessage(message:ChatMessageVO):void
    {  
      LogUtil.debug("Sending [chat.sendPublicMessage] to server. [" + message.message + "]");
      var _nc:ConnectionManager = BBB.initConnectionManager();
      _nc.sendMessage("chat.sendPublicMessage", 
        function(result:String):void { // On successful result
          LogUtil.debug(result); 
        },	                   
        function(status:String):void { // status - On error occurred
          LogUtil.error(status); 
        },
        message.toObj()
      );
    }
    
    public function sendPrivateMessage(message:ChatMessageVO):void
    {  
      LogUtil.debug("Sending [chat.sendPrivateMessage] to server.");
      LogUtil.debug("Sending fromUserID [" + message.fromUserID + "] to toUserID [" + message.toUserID + "]");
      var _nc:ConnectionManager = BBB.initConnectionManager();
      _nc.sendMessage("chat.sendPrivateMessage", 
        function(result:String):void { // On successful result
          LogUtil.debug(result); 
        },	                   
        function(status:String):void { // status - On error occurred
          LogUtil.error(status); 
        },
        message.toObj()
      );
    }
  }
}