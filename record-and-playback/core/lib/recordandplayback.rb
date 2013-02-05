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


path = File.expand_path(File.join(File.dirname(__FILE__), '../lib'))
$LOAD_PATH << path

require 'recordandplayback/audio_archiver'
require 'recordandplayback/events_archiver'
require 'recordandplayback/video_archiver'
require 'recordandplayback/presentation_archiver'
require 'recordandplayback/deskshare_archiver'
require 'recordandplayback/generators/events'
require 'recordandplayback/generators/audio'
require 'recordandplayback/generators/video'
require 'recordandplayback/generators/matterhorn_processor'
require 'recordandplayback/generators/audio_processor'
require 'recordandplayback/generators/presentation'
require 'open4'

module BigBlueButton
  class MissingDirectoryException < RuntimeError
  end
  
  class FileNotFoundException < RuntimeError
  end
  
  # BigBlueButton logs information about its progress.
  # Replace with your own logger if you desire.
  #
  # @param [Logger] log your own logger
  # @return [Logger] the logger you set
  def self.logger=(log)
    @logger = log
  end
  
  # Get BigBlueButton logger.
  #
  # @return [Logger]
  def self.logger
    return @logger if @logger
    logger = Logger.new(STDOUT)
    logger.level = Logger::INFO
    @logger = logger
  end
  
  def self.dir_exists?(dir)
    FileTest.directory?(dir)
  end
    
  def self.execute(command)
    output=""
    status = Open4::popen4(command) do | pid, stdin, stdout, stderr|
        BigBlueButton.logger.info("Executing: #{command}")

	output = stdout.readlines
        BigBlueButton.logger.info( "Output: #{output} ") unless output.empty?
 
        errors = stderr.readlines
        unless errors.empty?
          BigBlueButton.logger.error( "Error: stderr: #{errors}")
 #         raise errors.to_s 
        end
    end
    BigBlueButton.logger.info("Success ?:  #{status.success?}")
    BigBlueButton.logger.info("Process exited? #{status.exited?}")
    BigBlueButton.logger.info("Exit status: #{status.exitstatus}")
    output
  end
end
