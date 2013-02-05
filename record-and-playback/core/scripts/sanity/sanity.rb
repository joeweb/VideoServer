# Set encoding to utf-8
# encoding: UTF-8
#
# BigBlueButton open source conferencing system - http://www.bigbluebutton.org/
#
# Copyright (c) 2012 BigBlueButton Inc. and by respective authors (see below).
#
# This program is free software; you can redistribute it and/or modify it under the
# terms of the GNU Lesser General Public License as published by the Free Software
# Foundation; either version 3.0 of the License, or (at your option) any later
# version.
#
# BigBlueButton is distributed in the hope that it will be useful, but WITHOUT ANY
# WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
# PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License along
# with BigBlueButton; if not, see <http://www.gnu.org/licenses/>.
#


require '../lib/recordandplayback'
require 'logger'
require 'trollop'
require 'yaml'
require "nokogiri"
require "redis"
require "fileutils"

def check_events_xml(raw_dir,meeting_id)
	filepath = "#{raw_dir}/#{meeting_id}/events.xml"
	raise Exception,  "Events file doesn't exists." if not File.exists?(filepath)
	bad_doc = Nokogiri::XML(File.open(filepath)) { |config| config.options = Nokogiri::XML::ParseOptions::STRICT }
end

def check_audio_files(raw_dir,meeting_id)
	#check every file that is in events.xml, it's in audio dir
    doc = Nokogiri::XML(File.open("#{raw_dir}/#{meeting_id}/events.xml"))

    doc.xpath("//event[@eventname='StartRecordingEvent']/filename/text()").each { |fs_audio_file| 
    	audioname = fs_audio_file.content.split("/").last
    	raw_audio_file = "#{raw_dir}/#{meeting_id}/audio/#{audioname}"
    	#checking that the audio file exists in raw directory
    	raise Exception,  "Audio file doesn't exists in raw directory." if not File.exists?(raw_audio_file)

    	#checking length
    	raise Exception,  "Audio file length is zero." if BigBlueButton::AudioEvents.determine_length_of_audio_from_file(raw_audio_file) <= 0 
    }

end

BigBlueButton.logger = Logger.new('/var/log/bigbluebutton/sanity.log', 'daily' )

opts = Trollop::options do
  opt :meeting_id, "Meeting id to archive", :default => '58f4a6b3-cd07-444d-8564-59116cb53974', :type => String
end

meeting_id = opts[:meeting_id]

# This script lives in scripts/archive/steps while bigbluebutton.yml lives in scripts/
props = YAML::load(File.open('bigbluebutton.yml'))

audio_dir = props['raw_audio_src']
recording_dir = props['recording_dir']
raw_archive_dir = "#{recording_dir}/raw"
redis_host = props['redis_host']
redis_port = props['redis_port']

begin
	BigBlueButton.logger.info("checking events.xml")
	check_events_xml(raw_archive_dir,meeting_id)
	BigBlueButton.logger.info("checking audio")
	check_audio_files(raw_archive_dir,meeting_id)
	#delete keys
	BigBlueButton.logger.info("deleting keys")
	redis = BigBlueButton::RedisWrapper.new(redis_host, redis_port)
	events_archiver = BigBlueButton::RedisEventsArchiver.new redis    
        events_archiver.delete_events(meeting_id)

	#delete audio
        #Dir.glob("#{audio_dir}/#{meeting_id}*.wav").each{ |audio_meeting|
	#	FileUtils.rm(
	#}
	
	#create done files for sanity
	BigBlueButton.logger.info("creating sanity done files")
	sanity_done = File.new("#{recording_dir}/status/sanity/#{meeting_id}.done", "w")
	sanity_done.write("sanity check #{meeting_id}")
	sanity_done.close
rescue Exception => e
	BigBlueButton.logger.error("error in sanity check: " + e.message)
	sanity_done = File.new("#{recording_dir}/status/sanity/#{meeting_id}.fail", "w")
        sanity_done.write("error: " + e.message)
        sanity_done.close
end


