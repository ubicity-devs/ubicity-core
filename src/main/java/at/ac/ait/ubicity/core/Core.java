/**
    Copyright (C) 2014  AIT / Austrian Institute of Technology
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


import at.ac.ait.ubicity.commons.AbstractCore;
import at.ac.ait.ubicity.commons.Constants;
import at.ac.ait.ubicity.commons.PluginContext;
import at.ac.ait.ubicity.commons.interfaces.ReverseControllableMediumPlugin;
import at.ac.ait.ubicity.commons.interfaces.UbicityPlugin;
import at.ac.ait.ubicity.commons.protocol.Answer;
import at.ac.ait.ubicity.commons.protocol.Command;
import at.ac.ait.ubicity.commons.protocol.Medium;

import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import org.json.JSONObject;




/**
 *
 * @author Jan van Oort
 * @version 0.1
 * 
 * This class is the core of the ubicity platform. It does not perform actual data production, 
 * nor does it process any data. Core is a rather dumb plugin platform, after the K.I.S.S. principle, 
 * where classes implementing the UbicityPlugin interface may register themselves and <b>offer</b> JSONObjects. 
 * These JSONObjects are then placed -- by a separate JSONProcuer thread dedicated to the plugin -- 
 * into an ArrayBlockingQueue, of which there is one per plugin. 
 * A JSONConsumer thread, also dedicated to the plugin, then takes the JSONObject and offers it to 
 * elasticsearch for indexing. 
 *
 */
public final class Core extends AbstractCore implements Runnable {

    
    private static Core singleton;
    
    
    public static Core getInstance()    {
        
        if( singleton == null ) singleton = new Core();
        return singleton;
    }
    /**
     * 
     * Let nobody but ourselves instantiate a core. 
     * 
     */
    private Core()  {
        super();
        singleton = this;
        //register a shutdown hook
        Runtime.getRuntime().addShutdownHook( new Thread( new CoreShutdownHook( this ) ) ) ;
        
        //start the jit controller in order for the back end to react upon JIT indexing requests
        System.out.println( "[CORE] starting JitIndexingController" );
        JitIndexingController jitController = new JitIndexingController( Constants.REVERSE_COMMAND_AND_CONTROL_PORT );
        Thread jitThread = new Thread( jitController );
        jitThread.setPriority( Thread.MAX_PRIORITY - 1 );
        jitThread.start();
        System.out.println( "[CORE] successfully started JitIndexingController" );
        
        

    }
    
    
    
   
    
   
    
    @Override
    /**
     *  Cycle for ever, with three-second sleeping periods, and look for new plugins
     *  @TODO : solve a recurring cross-platform problem: this method may throw DuplicateRealmException 
     *  on one platform, but not on the other ( e.g. Windows: fine, Linux: DRE ) 
     */
    public void run() {
        while( true )   {
            try {
                pluginManager.addPluginsFrom( pluginURI );
                
                Thread.sleep( 3000 );
            }
            catch( InterruptedException _interrupt )    {
                Thread.interrupted();
            }
            catch( Error ee )   {
                logger.severe( "Core: caught an Error while running : " + ee.toString() );
            }
            catch( RuntimeException prettyBad ) {
                logger.severe( "Core: caught a runtime exception while running :: " + prettyBad.toString() );
            }
        }
    }
    
    
    


    
    
    /**
     * 
     * @param _plugin The plugin to deregister
     * @return boolean - in principle, always return true;
     * 
     */
    public final boolean deRegister( UbicityPlugin _plugin )   {
        try {
            return plugins.get( _plugin ).prepareDestroy();
        }
        catch( Error ee  )  {
            logger.severe( "Core: caught an Error while trying to deregister " + _plugin.getName()  +  " :: " + ee.toString() );
            return false;
        }
    }
    
    

    
    
    /**
     * Offer a JSONObject to the Core.
     * @param _json A JSONObject, offered by a plugin for processing by elasticsearch
     * @param _pC The plugin offering the object
     */
    public final void offer( JSONObject _json, PluginContext _pC )  {
        _pC.getAssociatedProducer().offer( _json );
    }
    
    
    @Override
      public Answer forward( Command _command )   {
          System.out.println( "[CORE] got a Command forwarded::" + _command.toRESTString() );
        for( Medium m: _command.getMedia().get() )  {
            for( UbicityPlugin p: plugins.keySet() )    {
                System.out.println( "[CORE] checking on plugin " + p.getName() + " for command " + _command.toRESTString() );
                if( p instanceof  ReverseControllableMediumPlugin ) return ( ( ReverseControllableMediumPlugin) p ).execute( _command );
            }
        }
        return Answer.FAIL;
    }
 
    
    
    
    /**
     * Mainly here for debugging purposes. 
     * @param args 
     */
    public final static void main( String[] args )  {
        Core c = Core.getInstance();
        Thread coreThread = new Thread( c );
        coreThread.setPriority( Thread.MIN_PRIORITY + 1 );
        coreThread.setName( "ubicity core execution context" );
        coreThread.start();
    }
    
    
    /**
     * Can only be called by an instance of CoreShutdownHook. Prepares an orderly Core exit. 
     * @param _caller the CoreShutdownHook calling us.
     */
    final void prepareShutdown( final CoreShutdownHook _caller )  {
        if( _caller != null )   {
            try {
                Core.esclient.close();
                Set< UbicityPlugin > _plugins = plugins.keySet();
                Iterator< UbicityPlugin > onPlugins = _plugins.iterator();
                while( onPlugins.hasNext()  )   {
                    UbicityPlugin p = onPlugins.next();
                    deRegister( p ) ;
                }
            }
            catch(  Exception | Error e )    {
                logger.severe( "Core : caught some problem while preparing shutdown :: " + e.toString() );
            }
        }
    }
}


final class CoreShutdownHook implements Runnable {

    //although the Core is accessible over getInstance(), pre-register in order to speed up operations; 
    //also, we are not certain that getInstance() is still replying under certain apocalyptic cirumstances... 
    private final Core core;
    
    
    public CoreShutdownHook( Core _core )   {
        core = _core;
    }
    
    
    @Override
    public void run() {
        Logger.getAnonymousLogger().warning( this.getClass().getName() + " :  calling prepareShutdown() on Core " );
        core.prepareShutdown( this );
    }
    
    
    
    
    
    
  
    
}






