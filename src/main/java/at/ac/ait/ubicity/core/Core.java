
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

import at.ac.ait.ubicity.core.constants.Constants;
import at.ac.ait.ubicity.core.interfaces.UbicityPlugin;


import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.xeoh.plugins.base.PluginManager;
import net.xeoh.plugins.base.impl.PluginManagerFactory;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;


import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
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
public final class Core implements Runnable {

    
    
    //here we keep track of currently running plugins
    final Map< UbicityPlugin, PluginContext > plugins;
        
    //used for implementing the singleton pattern
    private static Core singleton;
    
    //our elasticsearch indexing client
    private  static final TransportClient esclient;

    
    //the Core's own configuration. Static, as Core implements the Singleton pattern
    private static Configuration config;
    
    
    //this object is doing actual plugin management for us ( at least the loading & instantiation part ) 
    private final PluginManager pluginManager;
    
    //the place where the PluginManager is supposed to ( cyclically ) look for new plugins being dumped, in .jar form
    private final URI pluginURI;
    
    private static Logger logger;
    
    static String server;
    static String index;
    static String type;
    
    
    
    static  {
        //get our own Configuration
        try {
            //set necessary stuff for us to ueberhaupt be able to work
            config = new PropertiesConfiguration( "core.cfg" );
            server = config.getString( "ELASTICSEARCH_HOST" );
            index = config.getString( "ELASTICSEARCH_INDEX" );
            type = config.getString( "ELASTICSEARCH_TYPE" ); 
            logger.info( "Core : will index to " + server + ":" + 9200 + "/" + index + " @type " + type );
        }
        catch( ConfigurationException noConfig )    {
            //log this problem and then go along with default configuration
            logger.warning( "Core :: could not configure from core.cfg file [ not found, or there was a problem with it ], trying to revert to DefaultConfiguration  : " + noConfig.toString() );
            //here, we need not set any fields on our selves, DefaultConfiguration is clever enough to figure this out and do it for us
            config = new DefaultConfiguration();
        }
        catch( NullPointerException noConfig )  {
            config = new DefaultConfiguration();
        }
        
        //instantiate an elasticsearch client
       Settings settings = ImmutableSettings.settingsBuilder().build();
        esclient = new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress( server, 9300));
        try  {
            
            CreateIndexRequestBuilder createIndexRequestBuilder = esclient.admin().indices().prepareCreate( index );
            createIndexRequestBuilder.execute().actionGet();
        }
        catch( Throwable t )    {
            //do nothing, we may get an IndexAlreadyExistsException, but don't care about that, here and now
        }        
    }
    
    
    /**
     * 
     * Let nobody but ourselves instantiate a core. 
     * 
     */
    private Core()  {
        plugins = new HashMap();
        pluginManager = PluginManagerFactory.createPluginManager();
        pluginURI = new File( Constants.PLUGIN_DIRECTORY_NAME ).toURI();
        logger = Logger.getLogger( this.getClass().getName() );
        logger.setLevel( Level.ALL );
        //register a shutdown hook
        Runtime.getRuntime().addShutdownHook( new Thread( new CoreShutdownHook( this ) ) ) ;
    }
    
    
    
    /**
     * 
     * @return Core - an actual Core instance
     */
    public final static Core getInstance()  {
        if( singleton == null ) singleton = new Core();
        return singleton;
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
     * @param _plugin The plugin to register. A Plugin **MUST** call this method in order to be recognized by the Core
     * and benefit from its access to elasticsearch. It **IS** possible to run as a plugin without calling this method; 
     * the plugin is then, basically, an isolated POJO or - if it wishes to implement the Runnable interface - an isolated Thread with
     * the same JVM as the Core.
     * @return true if registering went OK, false in case of a problem.
     */
    public final boolean register( UbicityPlugin _plugin ) {
        try {
            PluginContext context = new PluginContext( esclient, _plugin );
            _plugin.setContext( context );
            plugins.put( _plugin, context );
            return true;
        }
        catch( Exception e )    {
            logger.warning( "caught an exception while trying to register plugin " + _plugin.getName() + " : " + e.toString() );
            return false;
        }
        catch( Error ee )   {
            logger.severe( "caught an error while trying to register plugin " + _plugin.getName() + ": " + ee.toString() );
            return false;
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
     * 
     * @param obituary The obituary for a consumer, whose thread has just died. 
     * We use it to get to the rest of our consumer's associated plugin information, 
     * and properly terminate both the plugin and its producer thread. 
     * If the plugin does not terminate properly within a standard waiting period, 
     * we force it stop. 
     */
    void callBack( Obituary obituary ) {
        UbicityPlugin p = obituary.getPlugin();
        plugins.get( p ).destroy();
        plugins.remove( p );
        p = null;
        obituary = null;
    }
    
    
    
    /**
     * Offer a JSONObject to the Core.
     * @param _json A JSONObject, offered by a plugin for processing by elasticsearch
     * @param _pC The plugin offering the object
     */
    public final void offer( JSONObject _json, PluginContext _pC )  {
        _pC.getAssociatedProducer().offer( _json );
    }
    
    
    
    /**
     * Offer more than 1 JSONObject simultaneously. 
     * @param _json 
     * @param _pC   
     */
    public final void offerBulk( JSONObject[] _json, PluginContext _pC )    {
        _pC.getAssociatedProducer().offer( _json );
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






