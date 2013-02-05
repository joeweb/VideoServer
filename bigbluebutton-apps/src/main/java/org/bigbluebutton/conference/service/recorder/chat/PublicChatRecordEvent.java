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
package org.bigbluebutton.conference.service.recorder.chat;


public class PublicChatRecordEvent extends AbstractChatRecordEvent {
	private static final String SENDER = "sender";
	private static final String MESSAGE = "message";
	private static final String LOCALE = "locale";
	private static final String COLOR = "color";
	
	public PublicChatRecordEvent() {
		super();
		setEvent("PublicChatEvent");
	}
		
	public void setSender(String sender) {
		eventMap.put(SENDER, sender);
	}
	
	public void setMessage(String message) {
		eventMap.put(MESSAGE, message);
	}
	
	public void setLocale(String locale) {
		eventMap.put(LOCALE, locale);
	}
	
	public void setColor(String color) {
		eventMap.put(COLOR, color);
	}
}
