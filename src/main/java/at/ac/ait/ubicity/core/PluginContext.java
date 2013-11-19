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

package at.ac.ait.ubicity.core;

import at.ac.ait.ubicity.core.constants.Constants;
import at.ac.ait.ubicity.core.interfaces.UbicityPlugin;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;
import org.elasticsearch.client.Client;
import org.json.JSONObject;

/**
 *
 * @author Jan van Oort
 */
public final class PluginContext   {
    
    protected final UbicityPlugin plugin;
    protected final BlockingQueue< JSONObject > queue;
    protected final JSONConsumer associatedConsumer;

    //making this field publicly visible is against all recommendations, and considered Bad Practice;
    //the <public> modifier is here as a hack, for performance reasons
    protected final JSONProducer associatedProducer;
    protected final Thread consumerThread;
    protected final Thread producerThread;
    
    
    public PluginContext( Client _client, UbicityPlugin _plugin  )  {
        //we do not keep a reference to the client;
        plugin = _plugin;
        //PluginContext can only be instantiated by ubicity Core, and the Core keeps track of us, 
        //and of the elasticsearch client. We only need to do the set-up work
        //create a BlockingQueue for this plugin's json objects
        queue = new ArrayBlockingQueue( Constants.DEFAULT_QUEUE_SIZE );
        //create a JSONConsumer for this queue
        associatedConsumer = new JSONConsumer( _plugin, _client, queue );
        //we also need a JSONProducer for this queue; in and by itself it produces nothing, this is done by the plugin; 
        //the JSONProducer simply acts as a producer towards our queue, and contributes to fully hiding the queue from the plugin's visibility.
        associatedProducer = new JSONProducer( queue );
        consumerThread = new Thread( associatedConsumer );
        producerThread = new Thread( getAssociatedProducer());
        //the consumer thread gets a very low priority, as we want to schedule in favour of the producer thread
        consumerThread.setPriority( Thread.MIN_PRIORITY + 1 );
        //thread names come in handy for debugging purposes
        consumerThread.setName( "JSONConsumer for " + _plugin.getName() );
        producerThread.setPriority( Thread.MAX_PRIORITY - 1 );
        producerThread.setName( "JSONProducer for " + _plugin.getName() );
        //we're done, start the show ! 
        consumerThread.start();
        producerThread.start();
    }
    
    
    public void destroy()   {
        
        plugin.mustStop();
        try {
            consumerThread.join( Constants.PLUGIN_GRACEFUL_TERMINATION_DELAY );
        }
        catch( InterruptedException _interrupt )    {
            Thread.interrupted();
        }
        finally {
            try {
                producerThread.stop();
            }
            catch( Error ee )   {
                Logger.getAnonymousLogger().severe( "PluginContext.destroy() for " + this.plugin.getName() + " : caught an Error while attemptiong destruction :: " + ee.toString() );
            }
            catch( RuntimeException prettyBad ) {
                Logger.getAnonymousLogger().severe( "PluginContext.destroy() for " + this.plugin.getName() + " : caught an runtime exception while attemptiong destruction :: " + prettyBad.toString() );
            }
            catch( Throwable e )    {
                Logger.getAnonymousLogger().severe( "PluginContext.destroy() for " + this.plugin.getName() + " : caught an unspecified problem [ Throwable ] while attemptiong destruction :: " + e.toString() );
            }
            finally {
                consumerThread.stop();
            }
        }
        Logger.getAnonymousLogger().info( "plugin " + plugin.getName() + " destroyed" );
    }

    
    
    boolean prepareDestroy() {
        try {
            //let the the consumer be poisoned; delegate this task to a dedicated thread, and carry on
            ConsumerPoison poison = new ConsumerPoison();
            PluginTerminator terminator = new PluginTerminator( poison, queue );
            Thread tTerminator = new Thread( terminator );
            tTerminator.setName( "Terminator for " + plugin.getName() );
            tTerminator.start();
            return true;
            //now, life carries on as usual; at some time in the future, the plugin's consumer is going to be killed off, 
            //although only after having processed all the valid json objects in its queue; 
            //the consumer - before dying - will then notify the Core, which will call destroy() upon us, 
            //and we do the cleaning work before being removed from the Core and being <null>-ed ourselves.
        }
        catch( Throwable t )    {
            Logger.getAnonymousLogger().severe( "some problem arose while preparing destruction for plugin " + plugin.getName() + ": " + t.toString()  );
            return false;
        }
    }

    /**
     * @return the associatedProducer
     */
    public JSONProducer getAssociatedProducer() {
        return associatedProducer;
    }
}