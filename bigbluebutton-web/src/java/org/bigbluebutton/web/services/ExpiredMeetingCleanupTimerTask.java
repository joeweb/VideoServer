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

package org.bigbluebutton.web.services;

import java.util.Timer;
import java.util.TimerTask;

import org.bigbluebutton.api.MeetingService;

public class ExpiredMeetingCleanupTimerTask {

	private MeetingService service;
	private Timer cleanupTimer;
	private long runEvery = 60000;

	public void setMeetingService(MeetingService svc) {
		this.service = svc;
	}
	
	public void start() {
		cleanupTimer = new Timer("bbb-api-cleanup", true);
		cleanupTimer.scheduleAtFixedRate(new CleanupTask(), 60000, runEvery);				
	}
	
	public void setRunEvery(long v) {
		runEvery = v;
	}
	
	private class CleanupTask extends TimerTask {
        public void run() {
        	service.removeExpiredMeetings();
        }
    }
}