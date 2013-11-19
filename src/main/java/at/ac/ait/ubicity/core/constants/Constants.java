package at.ac.ait.ubicity.core.constants;
/**
    Copyright (C) 2013  AIT / Austrian Institute of Technology
    http://www.ait.ac.at

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see http://www.gnu.org/licenses/agpl-3.0.html
 */

/**
 *
 * @author Jan van Oort
 * @version 0.2.1
 */ 
public interface Constants {



      
    
    //the default size for the BlockingQueue, associated within the Core, with each plugin separately
    public final static int DEFAULT_QUEUE_SIZE = 10000;
    
    //delay, in milliseconds, granted to a plugin to orderly process the mustStop() method
    public final static int PLUGIN_GRACEFUL_TERMINATION_DELAY = 2000;
    
    public final static String PLUGIN_DIRECTORY_NAME = "plugins/";
    
    public static String ELASTICSEARCH_HOST = "ubicity.ait.ac.at";
    
    public  static String ELASTICSEARCH_INDEX = "ubicity";
    
    public static String ELASTICSEARCH_TYPE = "ctweet";    
}
