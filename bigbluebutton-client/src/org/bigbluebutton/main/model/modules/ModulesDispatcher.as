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
package org.bigbluebutton.main.model.modules
{
	import com.asfusion.mate.events.Dispatcher;
	
	import flash.events.TimerEvent;
	import flash.utils.Timer;
	
	import org.bigbluebutton.common.LogUtil;
	import org.bigbluebutton.core.vo.Config;
	import org.bigbluebutton.core.vo.ConfigBuilder;
	import org.bigbluebutton.main.events.BBBEvent;
	import org.bigbluebutton.main.events.ConfigEvent;
	import org.bigbluebutton.main.events.ModuleLoadEvent;
	import org.bigbluebutton.main.events.PortTestEvent;
	import org.bigbluebutton.main.events.UserServicesEvent;
	import org.bigbluebutton.main.model.ConfigParameters;

	public class ModulesDispatcher
	{
		private var dispatcher:Dispatcher;
		private var delay:uint = 5000;
		private var repeat:uint = 30;
		private var connectTimer:Timer = null;
		
		public function ModulesDispatcher()
		{
			dispatcher = new Dispatcher();
		}
		
		public function sendLoadProgressEvent(moduleName:String, loadProgress:Number):void{
			var loadEvent:ModuleLoadEvent = new ModuleLoadEvent(ModuleLoadEvent.MODULE_LOAD_PROGRESS);
			loadEvent.moduleName = moduleName;
			loadEvent.progress = loadProgress;
			dispatcher.dispatchEvent(loadEvent);
		}
		
		public function sendModuleLoadReadyEvent(moduleName:String):void{
			var loadReadyEvent:ModuleLoadEvent = new ModuleLoadEvent(ModuleLoadEvent.MODULE_LOAD_READY);
			loadReadyEvent.moduleName = moduleName;
			dispatcher.dispatchEvent(loadReadyEvent);	
		}
		
		public function sendAllModulesLoadedEvent():void{
			dispatcher.dispatchEvent(new ModuleLoadEvent(ModuleLoadEvent.ALL_MODULES_LOADED));
			
			var loginEvent:BBBEvent = new BBBEvent(BBBEvent.LOGIN_EVENT);
			dispatcher.dispatchEvent(loginEvent);	
		}
		
		public function sendStartUserServicesEvent(application:String, host:String, isTunnelling:Boolean):void{
			var e:UserServicesEvent = new UserServicesEvent(UserServicesEvent.START_USER_SERVICES);
			e.applicationURI = application;
			e.hostURI = host;
      e.isTunnelling = isTunnelling;
			dispatcher.dispatchEvent(e);
		}
		
		public function sendPortTestEvent():void{
			var e:PortTestEvent = new PortTestEvent(PortTestEvent.TEST_RTMP);
			dispatcher.dispatchEvent(e);
			connectTimer = new Timer(delay, repeat);
			connectTimer.addEventListener(TimerEvent.TIMER, timerHandler);
			connectTimer.addEventListener(TimerEvent.TIMER_COMPLETE, completeHandler);
			connectTimer.start(); 			

		}
		
		private function timerHandler(e:TimerEvent):void{
			var evt:PortTestEvent = new PortTestEvent(PortTestEvent.PORT_TEST_UPDATE);
			dispatcher.dispatchEvent(evt);
		}
		
		private function stopTimers():void {
			if (connectTimer) {
				connectTimer.stop();
				connectTimer = null;
			}
		}
		
		private function completeHandler(e:TimerEvent):void {
			stopTimers();
		}
		
		public function sendTunnelingFailedEvent():void{
			dispatcher.dispatchEvent(new PortTestEvent(PortTestEvent.TUNNELING_FAILED));
			stopTimers();
		}
		
		public function sendPortTestSuccessEvent(port:String, host:String, protocol:String, app:String):void{
			stopTimers();
			var portEvent:PortTestEvent = new PortTestEvent(PortTestEvent.PORT_TEST_SUCCESS);
			portEvent.port = port;
			portEvent.hostname = host;
			portEvent.protocol = protocol;
			portEvent.app = app;
			dispatcher.dispatchEvent(portEvent);
			
		}
		
		public function sendPortTestFailedEvent(port:String, host:String, protocol:String, app:String):void{
			stopTimers();
			var portFailEvent:PortTestEvent = new PortTestEvent(PortTestEvent.PORT_TEST_FAILED);
			portFailEvent.port = port;
			portFailEvent.hostname = host;
			portFailEvent.protocol = protocol;
			portFailEvent.app = app;
			dispatcher.dispatchEvent(portFailEvent);
			
		}
		
		public function sendModuleLoadingStartedEvent(modules:XMLList):void{
			var event:ModuleLoadEvent = new ModuleLoadEvent(ModuleLoadEvent.MODULE_LOADING_STARTED);
			event.modules = modules;
			dispatcher.dispatchEvent(event);
		}
		
		public function sendConfigParameters(c:ConfigParameters):void{
			var event:ConfigEvent = new ConfigEvent(ConfigEvent.CONFIG_EVENT);
			var config:Config;
			config = new ConfigBuilder(c.version, c.localeVersion)
						.withApplication(c.application)
						.withHelpUrl(c.helpURL)
						.withHost(c.host)
						.withLanguageEnabled(c.languageEnabled)
						.withNumModule(c.numModules)
						.withPortTestApplication(c.portTestApplication)
						.withPortTestHost(c.portTestHost)
						.withShowDebug(c.showDebug)
						.withSkinning(c.skinning)
						.build()
			event.config = config;
			dispatcher.dispatchEvent(event);
		}
	}
}