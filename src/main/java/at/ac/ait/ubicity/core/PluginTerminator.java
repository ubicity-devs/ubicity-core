package at.ac.ait.ubicity.core;
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

import java.util.concurrent.BlockingQueue;
import org.json.JSONObject;

/**
 *
 * @author Jan van Oort
 * 
 * Kills a UbicityPlugin by placing a ConsumerPoison into its queue and waiting
 * for the plugin's consumer to swallow the poison
 */
public class PluginTerminator implements Runnable {
    
    
    private final JSONObject _cookie;
    
    private final BlockingQueue< JSONObject > queue;
    
    
    
    public PluginTerminator( JSONObject _poison, BlockingQueue< JSONObject > _queue )   {
        _cookie = _poison;
        queue = _queue;
    }

    
    
    
    public void run() {
            try {
                queue.put( _cookie );
            }
            catch( InterruptedException _interrupt )    {
                Thread.interrupted();
            }
    }
}
